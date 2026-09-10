package com.community.iam_service.dtos.response;

import com.community.iam_service.entity.Enum.Role;
import com.community.iam_service.entity.Enum.UserStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class UserResponse {
    private String id;
    private String name;
    private String email;
    private String username;
    private List<Role> roles;
    private boolean emailVerified;
    private boolean verified;
    private UserStatus status;
    private int profileCompletionScore;
    private Instant lastLoginAt;
    private Instant createdAt;
}