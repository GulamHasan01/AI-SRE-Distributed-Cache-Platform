package com.community.iam_service.entity;

import com.community.iam_service.entity.Enum.AuthProvider;
import com.community.iam_service.entity.Enum.Role;
import com.community.iam_service.entity.Enum.UserStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "users")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    @NotBlank
    private String email;

    @Indexed(unique = true, sparse = true)
    private String username;

    private String password;

    @Builder.Default
    private List<Role> roles = List.of(Role.USER);

    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    @Builder.Default
    private boolean emailVerified = false;

    @Builder.Default
    private boolean verified = false;

    private String referredBy;

    @Builder.Default
    private int failedLoginAttempts = 0;
    private Instant lockedUntil;

    @Builder.Default
    private boolean deactivated = false;
    private Instant deactivatedAt;

    @Builder.Default
    private int profileCompletionScore = 0;

    private String lastLoginIp;

    private Instant lastLoginAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private boolean enabled;
}