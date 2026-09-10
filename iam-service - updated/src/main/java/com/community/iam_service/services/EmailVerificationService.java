package com.community.iam_service.services;

import com.community.iam_service.entity.EmailVerificationToken;
import com.community.iam_service.entity.User;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.repository.EmailVerificationTokenRepository;
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
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    private static final int EXPIRY_HOURS = 24;

    @Transactional
    public String generateToken(String userId) {

        tokenRepository.deleteAllByUserId(userId);

        String rawToken = generateRaw();
        String hash = passwordEncoder.encode(rawToken);

        tokenRepository.save(EmailVerificationToken.builder()
                .userId(userId)
                .tokenHash(hash)
                .expiryDate(Instant.now().plus(EXPIRY_HOURS, ChronoUnit.HOURS))
                .build());

        return rawToken;
    }

    @Transactional
    public void verify(String userId, String rawToken) {

        EmailVerificationToken token = tokenRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(
                        "Invalid verification request",
                        HttpStatus.BAD_REQUEST
                ));

        if (!passwordEncoder.matches(rawToken, token.getTokenHash())
                || token.getExpiryDate().isBefore(Instant.now())) {
            throw new AppException(
                    "Invalid or expired verification link",
                    HttpStatus.BAD_REQUEST
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException.UserNotFoundException(userId));

        if (user.isEmailVerified()) {
            throw new AppException(
                    "Email already verified",
                    HttpStatus.CONFLICT
            );
        }

        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setProfileCompletionScore(user.getProfileCompletionScore() + 20);

        userRepository.save(user);

        tokenRepository.delete(token);

        auditLogService.log(user.getId(), "EMAIL_VERIFIED", null, null, true, null);
    }

    @Transactional
    public String resend(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException.UserNotFoundException(userId));

        if (user.isEmailVerified()) {
            throw new AppException("Email is already verified", HttpStatus.CONFLICT);
        }

        return generateToken(userId);
    }

    private String generateRaw() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}