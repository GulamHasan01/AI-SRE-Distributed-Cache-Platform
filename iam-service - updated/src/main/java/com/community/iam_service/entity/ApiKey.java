package com.community.iam_service.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "api_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    private String id;

    private String userId;

    private String name;

    @Indexed(unique = true)
    private String keyHash;

    private String keyPrefix;

    private List<String> scopes;

    @Builder.Default
    private boolean active = true;

    private Instant expiresAt;

    private Instant lastUsedAt;

    private Instant createdAt;
}