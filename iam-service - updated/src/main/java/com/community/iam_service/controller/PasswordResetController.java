package com.community.iam_service.controller;

import com.community.iam_service.services.EmailVerificationService;
import com.community.iam_service.services.PasswordResetService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordResetController {

        private final PasswordResetService passwordResetService;
        private final EmailVerificationService emailVerificationService;

        @PostMapping("/forgot-password")
        public ResponseEntity<Map<String, String>> forgotPassword(
                @RequestBody ForgotPasswordRequest request) {

            String rawToken = passwordResetService.initiateReset(request.getEmail());

            return ResponseEntity.ok(Map.of(
                    "message", "If an account exists with that email, a reset link has been sent."
            ));
        }

        @PostMapping("/reset-password")
        public ResponseEntity<Map<String, String>> resetPassword(
                @RequestBody ResetPasswordRequest request) {

            passwordResetService.resetPassword(
                    request.getToken(),
                    request.getNewPassword()
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Password reset successfully. Please login with your new password."
            ));
        }

        @PostMapping("/resend-verification")
        public ResponseEntity<Map<String, String>> resendVerification(
                @AuthenticationPrincipal String userId) {

            String rawToken = emailVerificationService.resend(userId);

            return ResponseEntity.ok(Map.of(
                    "message", "Verification email resent. Check your inbox."
            ));
        }

        @GetMapping("/verify-email")
        public ResponseEntity<Map<String, String>> verifyEmail(
                @RequestParam String userId,
                @RequestParam String token) {

            emailVerificationService.verify(userId, token);

            return ResponseEntity.ok(Map.of(
                    "message", "Email verified successfully"
            ));
        }

        @Data
        public static class ForgotPasswordRequest {
            @NotBlank @Email
            private String email;
        }

        @Data
        public static class ResetPasswordRequest {
            @NotBlank
            private String token;

            @NotBlank
            @Size(min = 8)
            @Pattern(
                    regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,}$",
                    message = "Password must be 8+ chars with uppercase, digit, and special character"
            )
            private String newPassword;
        }
    }