package com.community.iam_service.controller;

import com.community.iam_service.dtos.request.LoginRequest;
import com.community.iam_service.dtos.request.RegisterRequest;
import com.community.iam_service.dtos.request.TwoFactorRequest;
import com.community.iam_service.dtos.response.AuthResponse;
import com.community.iam_service.entity.User;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.services.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final SessionService sessionService;
    private final TwoFactorService twoFactorService;
    private final AuditLogService auditLogService;

    @PostMapping("/register/init")
    public ResponseEntity<?> initiate(
            @Valid @RequestBody RegisterRequest req) {

        authService.initiateRegistration(req);

        return ResponseEntity.ok(Map.of(
                "message", "OTP sent to email"
        ));
    }

    @PostMapping("/register/complete")
    public ResponseEntity<?> complete(
            @Valid @RequestBody RegisterRequest req,
            @RequestParam String otp) {

        authService.completeRegistration(req, otp);

        return ResponseEntity.ok(Map.of(
                "message", "Account created successfully"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpRequest) {

        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        User user = authService.login(req.getEmail(), req.getPassword(), ip, userAgent);

        if (!user.isEnabled()) {
            throw new AppException(
                    "Account not active",
                    HttpStatus.FORBIDDEN
            );
        }

        if (twoFactorService.isEnabled(user.getId())) {
            return ResponseEntity.ok(Map.of(
                    "requiresTwoFactor", true,
                    "userId", user.getId()
            ));
        }

        String accessToken = tokenService.generateAccessToken(user);
        String rawRefreshToken = refreshTokenService.createAndReturnRaw(user.getId());

        sessionService.createSession(user.getId(), null, ip, userAgent);

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .build());
    }

    @PostMapping("/2fa/verify-login")
    public ResponseEntity<AuthResponse> verifyTwoFactor(
            @RequestParam String userId,
            @Valid @RequestBody TwoFactorRequest.Verify req,
            HttpServletRequest httpRequest) {

        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        twoFactorService.verify(userId, req.getCode());

        User user = authService.getById(userId);

        if (!user.isEnabled()) {
            throw new AppException("Account not active", HttpStatus.FORBIDDEN);
        }

        String accessToken = tokenService.generateAccessToken(user);
        String rawRefreshToken = refreshTokenService.createAndReturnRaw(user.getId());

        sessionService.createSession(user.getId(), null, ip, userAgent);

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .build());
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<Map<String, String>> setupTwoFactor(
            @AuthenticationPrincipal String userId) {

        User user = authService.getById(userId);
        String otpAuthUrl = twoFactorService.setupTwoFactor(userId, user.getEmail());

        return ResponseEntity.ok(Map.of(
                "otpAuthUrl", otpAuthUrl,
                "message", "Scan QR code in authenticator app and confirm with code"
        ));
    }

    @PostMapping("/2fa/confirm")
    public ResponseEntity<Map<String, String>> confirmTwoFactor(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody TwoFactorRequest.VerifySetup req) {

        twoFactorService.confirmSetup(userId, req.getCode());

        return ResponseEntity.ok(Map.of(
                "message", "Two-factor authentication enabled"
        ));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<Map<String, String>> disableTwoFactor(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody TwoFactorRequest.Disable req) {

        User user = authService.getById(userId);
        authService.verifyPassword(user, req.getPassword());

        twoFactorService.disable(userId);

        return ResponseEntity.ok(Map.of(
                "message", "Two-factor authentication disabled"
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {

        String rawToken = body.get("refreshToken");
        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        var oldToken = refreshTokenService.validate(rawToken);
        refreshTokenService.revoke(oldToken);

        User user = authService.getById(oldToken.getUserId());

        String accessToken = tokenService.generateAccessToken(user);
        String newRawToken = refreshTokenService.createAndReturnRaw(user.getId());

        sessionService.createSession(user.getId(), null, ip, userAgent);

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRawToken)
                .tokenType("Bearer")
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal String userId,
            HttpServletRequest httpRequest) {

        refreshTokenService.revokeAll(userId);
        sessionService.revokeAllSessions(userId);

        auditLogService.logLogout(
                userId,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.noContent().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null)
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}