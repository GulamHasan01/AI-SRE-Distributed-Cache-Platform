package com.community.iam_service.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "two_factor_auth")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorAuth {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String secret;

    @Builder.Default
    private boolean enabled = false;

    private Instant enabledAt;
    private Instant createdAt;
}