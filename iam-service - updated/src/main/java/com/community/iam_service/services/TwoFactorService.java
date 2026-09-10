package com.community.iam_service.services;

import com.community.iam_service.entity.TwoFactorAuth;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.repository.TwoFactorAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private final TwoFactorAuthRepository twoFactorAuthRepository;
    private final AuditLogService auditLogService;
    private static final String ISSUER = "CommunityPlatform";
    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP = 30;

    @Transactional
    public String setupTwoFactor(String userId, String email) {
        twoFactorAuthRepository.findByUserId(userId).ifPresent(tfa -> {
            if (tfa.isEnabled()) {
                throw new AppException("2FA is already enabled", HttpStatus.CONFLICT);
            }
        });

        String secret = generateSecret();

        TwoFactorAuth tfa = twoFactorAuthRepository.findByUserId(userId)
                .orElse(TwoFactorAuth.builder().userId(userId).createdAt(Instant.now()).build());
        tfa.setSecret(secret);
        tfa.setEnabled(false);
        twoFactorAuthRepository.save(tfa);

        return buildOtpAuthUrl(email, secret);
    }

    @Transactional
    public void confirmSetup(String userId, String code) {
        TwoFactorAuth tfa = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException("2FA setup not initiated", HttpStatus.BAD_REQUEST));

        if (!verifyCode(tfa.getSecret(), code)) {
            throw new AppException("Invalid TOTP code", HttpStatus.UNAUTHORIZED);
        }

        tfa.setEnabled(true);
        tfa.setEnabledAt(Instant.now());
        twoFactorAuthRepository.save(tfa);
        auditLogService.log2FAEnabled(userId);
    }

    public void verify(String userId, String code) {
        TwoFactorAuth tfa = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException("2FA not configured", HttpStatus.UNAUTHORIZED));

        if (!tfa.isEnabled()) {
            throw new AppException("2FA is not enabled", HttpStatus.UNAUTHORIZED);
        }

        if (!verifyCode(tfa.getSecret(), code)) {
            throw new AppException("Invalid or expired 2FA code", HttpStatus.UNAUTHORIZED);
        }
    }

    public boolean isEnabled(String userId) {
        return twoFactorAuthRepository.findByUserId(userId)
                .map(TwoFactorAuth::isEnabled)
                .orElse(false);
    }

    @Transactional
    public void disable(String userId) {
        TwoFactorAuth tfa = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException("2FA not configured", HttpStatus.BAD_REQUEST));
        tfa.setEnabled(false);
        twoFactorAuthRepository.save(tfa);
        auditLogService.log2FADisabled(userId);
    }

    private boolean verifyCode(String secret, String code) {
        long currentTime = Instant.now().getEpochSecond() / TIME_STEP;
        for (long t = currentTime - 1; t <= currentTime + 1; t++) {
            if (generateTotp(secret, t).equals(code)) return true;
        }
        return false;
    }

    private String generateTotp(String secret, long timeStep) {
        try {
            byte[] key = Base64.getDecoder().decode(secret);
            byte[] msg = new byte[8];
            for (int i = 7; i >= 0; i--) {
                msg[i] = (byte)(timeStep & 0xFF);
                timeStep >>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);
            int offset = hash[hash.length - 1] & 0xF;
            int otp = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%0" + CODE_DIGITS + "d", otp % (int) Math.pow(10, CODE_DIGITS));
        } catch (Exception e) {
            throw new AppException("TOTP generation failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String buildOtpAuthUrl(String email, String secret) {
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d",
                ISSUER, email, secret, ISSUER, CODE_DIGITS, TIME_STEP
        );
    }
}