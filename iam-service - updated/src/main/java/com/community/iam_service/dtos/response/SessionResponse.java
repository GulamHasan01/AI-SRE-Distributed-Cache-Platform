package com.community.iam_service.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SessionResponse {
    private String id;
    private String deviceType;
    private String os;
    private String browser;
    private String ipAddress;
    private String country;
    private boolean suspicious;
    private boolean active;
    private Instant createdAt;
    private Instant lastAccessedAt;
}