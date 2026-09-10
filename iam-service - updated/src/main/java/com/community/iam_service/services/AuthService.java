package com.community.iam_service.services;

import com.community.iam_service.dtos.request.RegisterRequest;
import com.community.iam_service.entity.Enum.AuthProvider;
import com.community.iam_service.entity.Enum.Role;
import com.community.iam_service.entity.Enum.UserStatus;
import com.community.iam_service.entity.User;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final EmailOtpService emailOtpService;
    private final NotificationClient notificationClient;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 30;

    public User getById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException.UserNotFoundException(userId));
    }

    public void verifyPassword(User user, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new AppException.WrongPasswordException();
        }
    }

    @Transactional
    public void initiateRegistration(RegisterRequest req) {

        String email = req.getEmail().toLowerCase().trim();

        validateEmailDomain(email);

        if (userRepository.existsByEmail(email)) {
            throw new AppException.EmailAlreadyExistsException(email);
        }

        String otp = emailOtpService.generateOtp(email);

        notificationClient.sendOtpEmail(email, otp);
    }

    @Transactional
    public User completeRegistration(RegisterRequest req, String otp) {

        String email = req.getEmail().toLowerCase().trim();

        emailOtpService.verifyOtp(email, otp);

        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .roles(List.of(Role.USER))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .profileCompletionScore(20)
                .build());

        auditLogService.logRegister(user.getId(), null, null);

        return user;
    }

    @Transactional
    public User login(String email, String rawPassword, String ip, String userAgent) {

        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(AppException.InvalidCredentialsException::new);

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            long minutesLeft = ChronoUnit.MINUTES.between(Instant.now(), user.getLockedUntil());
            throw new AppException("Account is locked. Try again in " + minutesLeft + " minutes",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        if (user.getStatus() == UserStatus.SUSPENDED) throw new AppException.AccountSuspendedException();
        if (user.getStatus() == UserStatus.DELETED) throw new AppException.InvalidCredentialsException();

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            user.setStatus(UserStatus.ACTIVE);
            user.setDeactivated(false);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            handleFailedLogin(user, ip, userAgent);
            throw new AppException.InvalidCredentialsException();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(ip);

        userRepository.save(user);

        auditLogService.logLoginSuccess(user.getId(), ip, userAgent);

        return user;
    }

    private void handleFailedLogin(User user, String ip, String userAgent) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES));
            user.setFailedLoginAttempts(0);
            auditLogService.logAccountLocked(user.getId(), ip, userAgent);
        } else {
            auditLogService.logLoginFailed(user.getId(), ip, userAgent);
        }

        userRepository.save(user);
    }

    private void validateEmailDomain(String email) {

        String domain = email.substring(email.indexOf("@") + 1);

        List<String> allowedDomains = List.of(
                "gmail.com",
                "yahoo.com",
                "outlook.com",
                "rungta.org"
        );

        if (!allowedDomains.contains(domain)) {
            throw new AppException(
                    "Use gmail/yahoo/outlook email",
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}