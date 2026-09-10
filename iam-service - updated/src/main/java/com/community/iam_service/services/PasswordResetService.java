package com.community.iam_service.services;

import com.community.iam_service.entity.PasswordResetToken;
import com.community.iam_service.entity.User;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.repository.PasswordResetTokenRepository;
import com.community.iam_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    private static final int EXPIRY_MINUTES = 15;

    @Transactional
    public String initiateReset(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .map(user -> {
                    tokenRepository.deleteAllByUserId(user.getId());

                    String rawToken = generateRaw();
                    tokenRepository.save(PasswordResetToken.builder()
                            .userId(user.getId())
                            .tokenHash(passwordEncoder.encode(rawToken))
                            .expiryDate(Instant.now().plus(EXPIRY_MINUTES, ChronoUnit.MINUTES))
                            .build());

                    auditLogService.log(user.getId(), "PASSWORD_RESET_REQUESTED",
                            null, null, true, null);
                    return rawToken;
                })
                .orElse(null);
    }

    private String generateRaw() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        var tokens = tokenRepository.findAll();

        PasswordResetToken match = tokens.stream()
                .filter(t -> t.getExpiryDate().isAfter(Instant.now()))
                .filter(t -> passwordEncoder.matches(rawToken, t.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new AppException("Invalid or expired reset link",
                        HttpStatus.BAD_REQUEST));

        User user = userRepository.findById(match.getUserId())
                .orElseThrow(() -> new AppException.UserNotFoundException(match.getUserId()));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.delete(match);
        refreshTokenService.revokeAll(user.getId());

        auditLogService.log(user.getId(), "PASSWORD_RESET_COMPLETED",
                null, null, true, null);
    }
}