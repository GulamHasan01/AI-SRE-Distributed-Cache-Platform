package com.community.iam_service.mapper;

import com.community.iam_service.dtos.response.UserResponse;
import com.community.iam_service.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public static UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .username(user.getUsername())
                .roles(user.getRoles())
                .emailVerified(user.isEmailVerified())
                .verified(user.isVerified())
                .status(user.getStatus())
                .profileCompletionScore(user.getProfileCompletionScore())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}