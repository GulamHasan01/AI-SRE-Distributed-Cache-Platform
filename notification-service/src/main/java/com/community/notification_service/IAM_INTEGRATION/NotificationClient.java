package com.community.notification_service.IAM_INTEGRATION;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${services.notification-url:http://localhost:8084}")
    private String notificationUrl;

    @Async
    public void sendWelcomeEmail(String userId, String email, String username) {
        sendEmail(Map.of(
                "to",       email,
                "type",     "WELCOME",
                "userId",   userId,
                "variables", Map.of(
                        "username", username != null ? username : email.split("@")[0],
                        "email",    email
                )
        ));
    }

    @Async
    public void sendVerifyEmail(String userId, String email, String username, String verifyLink) {
        sendEmail(Map.of(
                "to",       email,
                "type",     "VERIFY_EMAIL",
                "userId",   userId,
                "variables", Map.of(
                        "username",   username != null ? username : "there",
                        "verifyLink", verifyLink
                )
        ));
    }
    @Async
    public void sendOtpEmail(String email, String otp) {
        sendEmail(Map.of(
                "to", email,
                "type", "OTP",
                "variables", Map.of(
                        "otp", otp,
                        "expiry", "5 minutes"
                )
        ));
    }

    @Async
    public void sendPasswordResetEmail(String userId, String email, String username, String resetLink) {
        sendEmail(Map.of(
                "to",       email,
                "type",     "PASSWORD_RESET",
                "userId",   userId,
                "variables", Map.of(
                        "username",  username != null ? username : "there",
                        "resetLink", resetLink
                )
        ));
    }

    @Async
    public void sendPasswordChangedEmail(String userId, String email, String username) {
        sendEmail(Map.of(
                "to",     email,
                "type",   "PASSWORD_CHANGED",
                "userId", userId,
                "variables", Map.of(
                        "username",  username != null ? username : "there",
                        "changedAt", java.time.Instant.now().toString()
                )
        ));
    }

    @Async
    public void sendSuspiciousLoginEmail(String userId, String email, String username,
                                         String ipAddress, String location, String device) {
        sendEmail(Map.of(
                "to",     email,
                "type",   "SUSPICIOUS_LOGIN",
                "userId", userId,
                "variables", Map.of(
                        "username",  username != null ? username : "there",
                        "ipAddress", ipAddress != null ? ipAddress : "Unknown",
                        "location",  location  != null ? location  : "Unknown",
                        "device",    device    != null ? device    : "Unknown",
                        "loginTime", java.time.Instant.now().toString()
                )
        ));
    }

    @Async
    public void sendAccountLockedEmail(String userId, String email, String username,
                                        int failedAttempts, String lockedUntil) {
        sendEmail(Map.of(
                "to",     email,
                "type",   "ACCOUNT_LOCKED",
                "userId", userId,
                "variables", Map.of(
                        "username",       username != null ? username : "there",
                        "failedAttempts", String.valueOf(failedAttempts),
                        "lockedUntil",    lockedUntil
                )
        ));
    }

    @Async
    public void sendCreatorVerifiedEmail(String userId, String email, String username) {
        sendEmail(Map.of(
                "to",     email,
                "type",   "CREATOR_VERIFIED",
                "userId", userId,
                "variables", Map.of(
                        "username", username != null ? username : "there"
                )
        ));
    }

    private void sendEmail(Map<String, Object> body) {
        try {
            restClientBuilder
                    .baseUrl(notificationUrl)
                    .build()
                    .post()
                    .uri("/notify/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Email queued: type={} to={}", body.get("type"), body.get("to"));

        } catch (Exception e) {
            log.warn("Failed to queue email — notification service may be down: {}", e.getMessage());
        }
    }
}
