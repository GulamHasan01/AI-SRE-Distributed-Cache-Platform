package com.community.iam_service.controller;

import com.community.iam_service.dtos.request.UpdateUserRequest;
import com.community.iam_service.dtos.request.UpdateUserSelfRequest;
import com.community.iam_service.dtos.response.AuthResponse;
import com.community.iam_service.dtos.response.UserResponse;
import com.community.iam_service.entity.RefreshToken;
import com.community.iam_service.entity.User;
import com.community.iam_service.mapper.UserMapper;
import com.community.iam_service.services.PasswordService;
import com.community.iam_service.services.RefreshTokenService;
import com.community.iam_service.services.TokenService;
import com.community.iam_service.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;
    private final PasswordService passwordService;

    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal String userId) {
        User user = userService.getById(userId);
        return UserMapper.toResponse(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal String userId) {
        refreshTokenService.revokeAll(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, String> req
    ) {
        passwordService.changePassword(
                userId,
                req.get("oldPassword"),
                req.get("newPassword")
        );

        refreshTokenService.revokeAll(userId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateSelf(
            @AuthenticationPrincipal String userId,
            @RequestBody UpdateUserSelfRequest req
    ) {
        return ResponseEntity.ok(
                UserMapper.toResponse(userService.updateSelf(userId, req))
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/{userId}")
    public ResponseEntity<UserResponse> adminUpdate(
            @PathVariable String userId,
            @RequestBody UpdateUserRequest req
    ) {
        return ResponseEntity.ok(
                UserMapper.toResponse(userService.updateUser(userId, req))
        );
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal String userId) {
        userService.softDelete(userId);
        refreshTokenService.revokeAll(userId);
        return ResponseEntity.noContent().build();
    }
}
