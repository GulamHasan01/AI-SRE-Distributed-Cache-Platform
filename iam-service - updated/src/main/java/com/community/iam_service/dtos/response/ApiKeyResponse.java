package com.community.iam_service.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ApiKeyResponse {
    private String id;
    private String name;
    private String keyPrefix;
    private String rawKey;
    private List<String> scopes;
    private boolean active;
    private Instant expiresAt;
    private Instant lastUsedAt;
    private Instant createdAt;
}