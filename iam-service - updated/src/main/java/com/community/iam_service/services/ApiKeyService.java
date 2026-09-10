package com.community.iam_service.services;

import com.community.iam_service.dtos.request.CreateApiKeyRequest;
import com.community.iam_service.dtos.response.ApiKeyResponse;
import com.community.iam_service.entity.ApiKey;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final AuditLogService auditLogService;

    private static final int MAX_KEYS_PER_USER = 10;

    @Transactional
    public ApiKeyResponse create(String userId, CreateApiKeyRequest request) {
        long existingCount = apiKeyRepository.findAllByUserIdAndActiveTrue(userId).size();
        if (existingCount >= MAX_KEYS_PER_USER) {
            throw new AppException("Maximum " + MAX_KEYS_PER_USER + " API keys allowed", HttpStatus.BAD_REQUEST);
        }

        String rawKey = "cpk_" + generateRandom(40);
        String prefix = rawKey.substring(0, 12);
        String hash   = sha256(rawKey);

        ApiKey key = apiKeyRepository.save(ApiKey.builder()
                .userId(userId)
                .name(request.getName())
                .keyHash(hash)
                .keyPrefix(prefix)
                .scopes(request.getScopes())
                .active(true)
                .expiresAt(request.getExpiresAt())
                .createdAt(Instant.now())
                .build());

        auditLogService.logApiKeyCreated(userId, request.getName());

        return ApiKeyResponse.builder()
                .id(key.getId())
                .name(key.getName())
                .keyPrefix(prefix)
                .rawKey(rawKey)
                .scopes(key.getScopes())
                .active(true)
                .expiresAt(key.getExpiresAt())
                .createdAt(key.getCreatedAt())
                .build();
    }

    public List<ApiKeyResponse> list(String userId) {
        return apiKeyRepository.findAllByUserIdAndActiveTrue(userId).stream()
                .map(k -> ApiKeyResponse.builder()
                        .id(k.getId())
                        .name(k.getName())
                        .keyPrefix(k.getKeyPrefix())
                        .rawKey(null)
                        .scopes(k.getScopes())
                        .active(k.isActive())
                        .expiresAt(k.getExpiresAt())
                        .lastUsedAt(k.getLastUsedAt())
                        .createdAt(k.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public void revoke(String keyId, String userId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .filter(k -> k.getUserId().equals(userId))
                .orElseThrow(() -> new AppException.UserNotFoundException(keyId));
        key.setActive(false);
        apiKeyRepository.save(key);
        auditLogService.logApiKeyRevoked(userId, keyId);
    }

    public ApiKey validate(String rawKey) {
        if (rawKey == null || rawKey.length() < 12) {
            throw new AppException.InvalidTokenException("Malformed API key");
        }

        String prefix = rawKey.substring(0, 12);
        String hash   = sha256(rawKey);

        return apiKeyRepository.findAllByKeyPrefix(prefix).stream()
                .filter(ApiKey::isActive)
                .filter(k -> k.getExpiresAt() == null || k.getExpiresAt().isAfter(Instant.now()))
                .filter(k -> k.getKeyHash().equals(hash))
                .findFirst()
                .orElseThrow(() -> new AppException.InvalidTokenException("Invalid or revoked API key"));
    }

    private String generateRandom(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes()));
        } catch (Exception e) {
            throw new AppException("Hashing failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}