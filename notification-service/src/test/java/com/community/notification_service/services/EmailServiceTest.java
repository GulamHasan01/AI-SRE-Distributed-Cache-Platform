package com.community.notification_service.services;

import com.community.notification_service.dtos.request.EmailRequest;
import com.community.notification_service.entity.EmailLog;
import com.community.notification_service.entity.Enum.EmailStatus;
import com.community.notification_service.entity.Enum.EmailType;
import com.community.notification_service.repository.EmailLogRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService")
class EmailServiceTest {

    @Mock private JavaMailSender   mailSender;
    @Mock private TemplateEngine   templateEngine;
    @Mock private EmailLogRepository emailLogRepository;
    @Mock private MimeMessage       mimeMessage;

    private EmailService emailService;

    private static final String FROM_ADDRESS  = "noreply@test.com";
    private static final String FROM_NAME     = "TestPlatform";
    private static final String FRONTEND_URL  = "http://localhost:3000";
    private static final int    MAX_PER_HOUR  = 10;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, templateEngine, emailLogRepository);
        ReflectionTestUtils.setField(emailService, "fromAddress",   FROM_ADDRESS);
        ReflectionTestUtils.setField(emailService, "fromName",      FROM_NAME);
        ReflectionTestUtils.setField(emailService, "frontendUrl",   FRONTEND_URL);
        ReflectionTestUtils.setField(emailService, "maxEmailsPerHour", MAX_PER_HOUR);
    }

    private EmailRequest request(String to, EmailType type) {
        EmailRequest req = new EmailRequest();
        req.setTo(to);
        req.setType(type);
        req.setUserId("user-1");
        req.setVariables(Map.of("username", "alice"));
        return req;
    }

    private EmailLog pendingLog(String id, String recipient, EmailType type) {
        return EmailLog.builder()
                .id(id)
                .recipient(recipient)
                .type(type)
                .status(EmailStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("sendAsync() — rate limiting")
    class RateLimitTests {

        @Test
        @DisplayName("saves FAILED log and skips send when recipient is rate-limited")
        void skipsWhenRateLimited() {
            when(emailLogRepository.countByRecipientAndCreatedAtAfter(
                    eq("alice@test.com"), any(Instant.class)))
                    .thenReturn((long) MAX_PER_HOUR);

            EmailRequest req = request("alice@test.com", EmailType.VERIFY_EMAIL);

            emailService.sendAsync(req);

            ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
            verify(emailLogRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(EmailStatus.FAILED);
            assertThat(captor.getValue().getFailureReason()).containsIgnoringCase("rate limit");

            verify(mailSender, never()).createMimeMessage();
        }

        @Test
        @DisplayName("proceeds to send when recipient is below rate limit")
        void sendsWhenBelowRateLimit() throws Exception {
            when(emailLogRepository.countByRecipientAndCreatedAtAfter(
                    eq("alice@test.com"), any(Instant.class)))
                    .thenReturn(0L);
            when(emailLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("<html></html>");
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            emailService.sendAsync(request("alice@test.com", EmailType.VERIFY_EMAIL));

            verify(mailSender).send(mimeMessage);
        }
    }

    @Nested
    @DisplayName("sendAsync() — duplicate WELCOME guard")
    class WelcomeDuplicateGuardTests {

        @Test
        @DisplayName("skips send when WELCOME email was already sent to that userId")
        void skipsWhenWelcomeAlreadySent() {
            when(emailLogRepository.countByRecipientAndCreatedAtAfter(
                    eq("alice@test.com"), any(Instant.class))).thenReturn(0L);
            when(emailLogRepository.existsByUserIdAndType("user-1", EmailType.WELCOME))
                    .thenReturn(true);

            emailService.sendAsync(request("alice@test.com", EmailType.WELCOME));

            verify(mailSender, never()).createMimeMessage();
            verify(emailLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("sends WELCOME when no prior WELCOME exists for that userId")
        void sendsWelcomeWhenNoPrior() throws Exception {
            when(emailLogRepository.countByRecipientAndCreatedAtAfter(
                    eq("alice@test.com"), any(Instant.class))).thenReturn(0L);
            when(emailLogRepository.existsByUserIdAndType("user-1", EmailType.WELCOME))
                    .thenReturn(false);
            when(emailLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("<html></html>");
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            emailService.sendAsync(request("alice@test.com", EmailType.WELCOME));

            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("duplicate guard only applies to WELCOME type — OTP always sends")
        void otpNotBlockedByWelcomeGuard() throws Exception {
            when(emailLogRepository.countByRecipientAndCreatedAtAfter(
                    eq("alice@test.com"), any(Instant.class))).thenReturn(0L);
            when(emailLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("<html></html>");
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            emailService.sendAsync(request("alice@test.com", EmailType.OTP));

            verify(emailLogRepository, never()).existsByUserIdAndType(any(), any());
            verify(mailSender).send(mimeMessage);
        }
    }

    @Nested
    @DisplayName("sendAsync() — successful send")
    class SuccessfulSendTests {

        @BeforeEach
        void setUpHappyPath() throws Exception {
            when(emailLogRepository.countByRecipientAndCreatedAtAfter(
                    eq("alice@test.com"), any(Instant.class))).thenReturn(0L);
            when(emailLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("<html>body</html>");
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        }

        @Test
        @DisplayName("logs are saved twice: once PENDING, once SENT after successful send")
        void logsAreSavedPendingThenSent() {
            List<EmailStatus> savedStatuses = new java.util.ArrayList<>();
            when(emailLogRepository.save(any())).thenAnswer(inv -> {
                EmailLog log = inv.getArgument(0);
                savedStatuses.add(log.getStatus());
                return log;
            });

            emailService.sendAsync(request("alice@test.com", EmailType.VERIFY_EMAIL));

            assertThat(savedStatuses).hasSize(2);
            assertThat(savedStatuses.get(0)).isEqualTo(EmailStatus.PENDING);
            assertThat(savedStatuses.get(1)).isEqualTo(EmailStatus.SENT);
        }

        @Test
        @DisplayName("passes frontendUrl and platformName to Thymeleaf context")
        void passesCorrectVariablesToThymeleaf() {
            emailService.sendAsync(request("alice@test.com", EmailType.VERIFY_EMAIL));

            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
            verify(templateEngine).process(any(String.class), ctxCaptor.capture());
            Context ctx = ctxCaptor.getValue();
            assertThat(ctx.getVariable("frontendUrl")).isEqualTo(FRONTEND_URL);
            assertThat(ctx.getVariable("platformName")).isEqualTo(FROM_NAME);
        }

        @Test
        @DisplayName("processes the correct Thymeleaf template for each email type")
        void usesCorrectTemplateForType() {
            emailService.sendAsync(request("alice@test.com", EmailType.PASSWORD_RESET));

            verify(templateEngine).process(eq("email/password-reset"), any(Context.class));
        }
    }

    @Nested
    @DisplayName("sendAsync() — send failure handling")
    class SendFailureTests {

        @Test
        @DisplayName("saves FAILED log when mail send throws an exception")
        void savesFailedLogOnMailException() {
            when(emailLogRepository.countByRecipientAndCreatedAtAfter(
                    eq("alice@test.com"), any(Instant.class))).thenReturn(0L);
            when(emailLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("<html></html>");
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doThrow(new RuntimeException("SMTP connection refused")).when(mailSender).send(any(MimeMessage.class));

            emailService.sendAsync(request("alice@test.com", EmailType.VERIFY_EMAIL));

            ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
            verify(emailLogRepository, times(2)).save(captor.capture());
            EmailLog failLog = captor.getAllValues().get(1);
            assertThat(failLog.getStatus()).isEqualTo(EmailStatus.FAILED);
            assertThat(failLog.getFailureReason()).contains("SMTP connection refused");
        }
    }

    @Nested
    @DisplayName("retryFailed()")
    class RetryFailedTests {

        @Test
        @DisplayName("skips retrying emails that failed due to rate limit")
        void skipsRateLimitedFailures() throws Exception {
            EmailLog rateLimitedLog = EmailLog.builder()
                    .id("log-1").recipient("alice@test.com")
                    .type(EmailType.VERIFY_EMAIL)
                    .status(EmailStatus.FAILED)
                    .failureReason("Rate limit exceeded")
                    .retryCount(0).createdAt(Instant.now()).build();

            when(emailLogRepository.findAllByStatusAndRetryCountLessThan(EmailStatus.FAILED, 3))
                    .thenReturn(List.of(rateLimitedLog));

            emailService.retryFailed();

            verify(emailLogRepository, never()).save(any());
            verify(mailSender, never()).createMimeMessage();
        }

        @Test
        @DisplayName("retries eligible failed emails and increments retryCount")
        void retriesEligibleFailedEmails() throws Exception {
            EmailLog failedLog = EmailLog.builder()
                    .id("log-2").recipient("bob@test.com")
                    .type(EmailType.VERIFY_EMAIL)
                    .userId("user-2")
                    .status(EmailStatus.FAILED)
                    .failureReason("Timeout")
                    .retryCount(0).createdAt(Instant.now()).build();

            when(emailLogRepository.findAllByStatusAndRetryCountLessThan(EmailStatus.FAILED, 3))
                    .thenReturn(List.of(failedLog));

            List<EmailStatus> savedStatuses  = new java.util.ArrayList<>();
            List<Integer>     savedRetryCounts = new java.util.ArrayList<>();
            when(emailLogRepository.save(any())).thenAnswer(inv -> {
                EmailLog log = inv.getArgument(0);
                savedStatuses.add(log.getStatus());
                savedRetryCounts.add(log.getRetryCount());
                return log;
            });
            when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("<html></html>");
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            emailService.retryFailed();

            assertThat(savedStatuses).isNotEmpty();
            assertThat(savedStatuses.get(0)).isEqualTo(EmailStatus.RETRYING);
            assertThat(savedRetryCounts.get(0)).isEqualTo(1);
        }
    }
}
