package com.community.notification_service.services;

import com.community.notification_service.dtos.request.EmailRequest;
import com.community.notification_service.entity.EmailLog;
import com.community.notification_service.entity.Enum.EmailStatus;
import com.community.notification_service.entity.Enum.EmailType;
import com.community.notification_service.repository.EmailLogRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailLogRepository emailLogRepository;

    @Value("${app.mail.from-address}") private String fromAddress;
    @Value("${app.mail.from-name}")    private String fromName;
    @Value("${app.mail.frontend-url}") private String frontendUrl;
    @Value("${app.rate-limit.emails-per-hour:10}") private int maxEmailsPerHour;

    @Async
    public void sendAsync(EmailRequest request) {

        if (isRateLimited(request.getTo())) {
            log.warn("Rate limit hit for {}", request.getTo());
            saveLog(request, EmailStatus.FAILED, "Rate limit exceeded", null);
            return;
        }

        if (request.getType() == EmailType.WELCOME && request.getUserId() != null) {
            if (emailLogRepository.existsByUserIdAndType(request.getUserId(), EmailType.WELCOME)) {
                log.info("Welcome email already sent to userId={}", request.getUserId());
                return;
            }
        }

        EmailLog log_ = saveLog(request, EmailStatus.PENDING, null, null);
        doSend(request, log_);
    }

    private record EmailContent(String subject, String template) {}

    private EmailContent resolveContent(EmailType type) {
        return switch (type) {

            case OTP ->
                    new EmailContent("Your OTP Code", "otp");

            case WELCOME ->
                    new EmailContent("Welcome to Community Platform! 🎉", "welcome");

            case VERIFY_EMAIL ->
                    new EmailContent("Verify your email address", "verify-email");

            case PASSWORD_RESET ->
                    new EmailContent("Reset your password", "password-reset");

            case PASSWORD_CHANGED ->
                    new EmailContent("Your password was changed", "password-changed");

            case SUSPICIOUS_LOGIN ->
                    new EmailContent("⚠ Suspicious login detected", "suspicious-login");

            case ACCOUNT_LOCKED ->
                    new EmailContent("Your account has been temporarily locked", "account-locked");

            case ACCOUNT_DEACTIVATED ->
                    new EmailContent("Your account has been deactivated", "account-deactivated");

            case ACCOUNT_REACTIVATED ->
                    new EmailContent("Welcome back! Account reactivated", "account-reactivated");

            case CREATOR_VERIFIED ->
                    new EmailContent("🎉 You've been verified as a Creator!", "creator-verified");

            case CUSTOM ->
                    new EmailContent("Message from Community Platform", "custom");
        };
    }

    private void doSend(EmailRequest request, EmailLog log_) {

        EmailContent content = resolveContent(request.getType());

        Context ctx = new Context();
        ctx.setVariable("frontendUrl", frontendUrl);
        ctx.setVariable("platformName", fromName);

        if (request.getVariables() != null) {
            request.getVariables().forEach(ctx::setVariable);
        }

        try {
            String html = templateEngine.process("email/" + content.template(), ctx);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

            helper.setFrom(fromAddress, fromName);
            helper.setTo(request.getTo());
            helper.setSubject(content.subject());
            helper.setText(html, true);

            mailSender.send(mime);

            log_.setStatus(EmailStatus.SENT);
            log_.setSentAt(Instant.now());
            emailLogRepository.save(log_);

            log.info("Email sent: type={} to={}", request.getType(), request.getTo());

        } catch (Exception e) {
            log.error("Email send failed: type={} to={} error={}",
                    request.getType(), request.getTo(), e.getMessage());

            log_.setStatus(EmailStatus.FAILED);
            log_.setFailureReason(e.getMessage());
            emailLogRepository.save(log_);
        }
    }

    @Scheduled(fixedDelay = 900_000)
    public void retryFailed() {

        List<EmailLog> failed = emailLogRepository
                .findAllByStatusAndRetryCountLessThan(EmailStatus.FAILED, 3);

        for (EmailLog log_ : failed) {

            if (log_.getFailureReason() != null &&
                    log_.getFailureReason().contains("Rate limit")) continue;

            log_.setStatus(EmailStatus.RETRYING);
            log_.setRetryCount(log_.getRetryCount() + 1);
            emailLogRepository.save(log_);

            EmailRequest retryReq = new EmailRequest();
            retryReq.setTo(log_.getRecipient());
            retryReq.setType(log_.getType());
            retryReq.setUserId(log_.getUserId());
            retryReq.setVariables(Map.of());

            doSend(retryReq, log_);
        }
    }

    private boolean isRateLimited(String recipient) {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long count = emailLogRepository.countByRecipientAndCreatedAtAfter(recipient, oneHourAgo);
        return count >= maxEmailsPerHour;
    }

    private EmailLog saveLog(EmailRequest request, EmailStatus status,
                             String failureReason, String subject) {

        return emailLogRepository.save(EmailLog.builder()
                .userId(request.getUserId())
                .recipient(request.getTo())
                .type(request.getType())
                .status(status)
                .subject(subject)
                .failureReason(failureReason)
                .retryCount(0)
                .createdAt(Instant.now())
                .build());
    }
}