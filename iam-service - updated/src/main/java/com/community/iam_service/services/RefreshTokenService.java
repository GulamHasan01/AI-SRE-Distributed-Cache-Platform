package com.community.iam_service.services;

import com.community.iam_service.entity.RefreshToken;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final PasswordEncoder encoder;

    public String createAndReturnRaw(String userId) {
        String rawToken = UUID.randomUUID().toString().replace("-", "") +
                          UUID.randomUUID().toString().replace("-", "");

        String prefix = rawToken.substring(0, 8);

        repo.save(RefreshToken.builder()
                .userId(userId)
                .tokenPrefix(prefix)
                .tokenHash(encoder.encode(rawToken))
                .expiryDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .revoked(false)
                .createdAt(Instant.now())
                .build());

        return rawToken;
    }

    public RefreshToken validate(String rawToken) {
        if (rawToken == null || rawToken.length() < 8) {
            throw new AppException.InvalidTokenException("Malformed refresh token");
        }

        String prefix = rawToken.substring(0, 8);

        return repo.findAllByTokenPrefix(prefix).stream()
                .filter(t -> !t.isRevoked())
                .filter(t -> t.getExpiryDate().isAfter(Instant.now()))
                .filter(t -> encoder.matches(rawToken, t.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new AppException.InvalidTokenException("Token not found, expired, or revoked"));
    }

    public void revokeAll(String userId) {
        List<RefreshToken> tokens = repo.findAllByUserId(userId);
        tokens.forEach(t -> t.setRevoked(true));
        repo.saveAll(tokens);
    }

    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        repo.save(token);
    }
}
