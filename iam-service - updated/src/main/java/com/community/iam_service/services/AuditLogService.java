package com.community.iam_service.services;

import com.community.iam_service.entity.AuditLog;
import com.community.iam_service.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(String userId, String action, String ipAddress,
                    String userAgent, boolean success, String metadata) {
        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action(action)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(success)
                .metadata(metadata)
                .createdAt(Instant.now())
                .build());
    }

    @Async
    public void logLoginSuccess(String userId, String ip, String userAgent) {
        log(userId, "LOGIN_SUCCESS", ip, userAgent, true, null);
    }

    @Async
    public void logLoginFailed(String userId, String ip, String userAgent) {
        log(userId, "LOGIN_FAILED", ip, userAgent, false, null);
    }

    @Async
    public void logLogout(String userId, String ip, String userAgent) {
        log(userId, "LOGOUT", ip, userAgent, true, null);
    }

    @Async
    public void logPasswordChange(String userId, String ip, String userAgent) {
        log(userId, "PASSWORD_CHANGED", ip, userAgent, true, null);
    }

    @Async
    public void logRegister(String userId, String ip, String userAgent) {
        log(userId, "REGISTER", ip, userAgent, true, null);
    }

    @Async
    public void logAccountLocked(String userId, String ip, String userAgent) {
        log(userId, "ACCOUNT_LOCKED", ip, userAgent, false, "Too many failed login attempts");
    }

    @Async
    public void logSuspiciousLogin(String userId, String ip, String userAgent, String reason) {
        log(userId, "SUSPICIOUS_LOGIN", ip, userAgent, true, reason);
    }

    @Async
    public void log2FAEnabled(String userId) {
        log(userId, "2FA_ENABLED", null, null, true, null);
    }

    @Async
    public void log2FADisabled(String userId) {
        log(userId, "2FA_DISABLED", null, null, true, null);
    }

    @Async
    public void logApiKeyCreated(String userId, String keyName) {
        log(userId, "API_KEY_CREATED", null, null, true, "Key: " + keyName);
    }

    @Async
    public void logApiKeyRevoked(String userId, String keyId) {
        log(userId, "API_KEY_REVOKED", null, null, true, "KeyId: " + keyId);
    }
}