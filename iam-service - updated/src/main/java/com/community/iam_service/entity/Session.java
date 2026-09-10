package com.community.iam_service.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String refreshTokenId;

    private String ipAddress;
    private String userAgent;

    private String deviceType;
    private String os;
    private String browser;
    private String country;

    @Builder.Default
    private boolean suspicious = false;
    private String suspiciousReason;

    @Builder.Default
    private boolean active = true;

    private Instant createdAt;
    private Instant lastAccessedAt;
}