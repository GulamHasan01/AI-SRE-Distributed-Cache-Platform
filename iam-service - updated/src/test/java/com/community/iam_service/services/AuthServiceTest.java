package com.community.iam_service.services;

import com.community.iam_service.dtos.request.RegisterRequest;
import com.community.iam_service.entity.Enum.AuthProvider;
import com.community.iam_service.entity.Enum.Role;
import com.community.iam_service.entity.Enum.UserStatus;
import com.community.iam_service.entity.User;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private UserRepository     userRepository;
    @Mock private PasswordEncoder    passwordEncoder;
    @Mock private AuditLogService    auditLogService;
    @Mock private EmailOtpService    emailOtpService;
    @Mock private NotificationClient notificationClient;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, passwordEncoder, auditLogService,
                emailOtpService, notificationClient);
    }

    private User activeUser(String id, String email, String hashedPwd) {
        return User.builder()
                .id(id).email(email).password(hashedPwd)
                .roles(List.of(Role.USER)).status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL).emailVerified(true)
                .enabled(true).failedLoginAttempts(0)
                .build();
    }

    private RegisterRequest regRequest(String email) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("Secret@123");
        return req;
    }

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("returns user when ID exists")
        void returnsUserWhenFound() {
            User user = activeUser("u1", "alice@gmail.com", "hashed");
            when(userRepository.findById("u1")).thenReturn(Optional.of(user));

            assertThat(authService.getById("u1")).isEqualTo(user);
        }

        @Test
        @DisplayName("throws UserNotFoundException when ID does not exist")
        void throwsWhenNotFound() {
            when(userRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getById("missing"))
                    .isInstanceOf(AppException.UserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("verifyPassword()")
    class VerifyPasswordTests {

        @Test
        @DisplayName("passes silently when password matches")
        void passesWhenMatches() {
            User user = activeUser("u1", "alice@gmail.com", "hashed");
            when(passwordEncoder.matches("rawPwd", "hashed")).thenReturn(true);

            authService.verifyPassword(user, "rawPwd");
        }

        @Test
        @DisplayName("throws WrongPasswordException when password does not match")
        void throwsWhenWrongPassword() {
            User user = activeUser("u1", "alice@gmail.com", "hashed");
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            assertThatThrownBy(() -> authService.verifyPassword(user, "wrong"))
                    .isInstanceOf(AppException.WrongPasswordException.class);
        }
    }

    @Nested
    @DisplayName("initiateRegistration() — domain validation & OTP dispatch")
    class InitiateRegistrationTests {

        @Test
        @DisplayName("generates OTP and sends email for valid allowed-domain address")
        void sendsOtpForAllowedDomain() {
            when(userRepository.existsByEmail("alice@gmail.com")).thenReturn(false);
            when(emailOtpService.generateOtp("alice@gmail.com")).thenReturn("123456");

            authService.initiateRegistration(regRequest("alice@gmail.com"));

            verify(emailOtpService).generateOtp("alice@gmail.com");
            verify(notificationClient).sendOtpEmail("alice@gmail.com", "123456");
        }

        @Test
        @DisplayName("normalises email to lowercase before checking existence")
        void normalisesEmailToLowercase() {
            when(userRepository.existsByEmail("alice@gmail.com")).thenReturn(false);
            when(emailOtpService.generateOtp("alice@gmail.com")).thenReturn("111111");

            authService.initiateRegistration(regRequest("ALICE@GMAIL.COM"));

            verify(userRepository).existsByEmail("alice@gmail.com");
        }

        @Test
        @DisplayName("throws EmailAlreadyExistsException when email already registered")
        void throwsWhenEmailTaken() {
            when(userRepository.existsByEmail("alice@gmail.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.initiateRegistration(regRequest("alice@gmail.com")))
                    .isInstanceOf(AppException.EmailAlreadyExistsException.class);
        }

        @Test
        @DisplayName("throws AppException for disallowed email domain")
        void throwsForDisallowedDomain() {
            assertThatThrownBy(() -> authService.initiateRegistration(regRequest("alice@forbidden.io")))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("gmail/yahoo/outlook");
        }

        @Test
        @DisplayName("allows all configured domains: gmail, yahoo, outlook, rungta.org")
        void allowsAllConfiguredDomains() {
            List<String> allowed = List.of(
                    "a@gmail.com", "b@yahoo.com", "c@outlook.com", "d@rungta.org");

            for (String email : allowed) {
                when(userRepository.existsByEmail(email)).thenReturn(false);
                when(emailOtpService.generateOtp(email)).thenReturn("000000");
                authService.initiateRegistration(regRequest(email));
            }
        }
    }

    @Nested
    @DisplayName("completeRegistration() — OTP verification + user creation")
    class CompleteRegistrationTests {

        @Test
        @DisplayName("creates user with correct defaults after valid OTP")
        void createsUserWithCorrectDefaults() {
            RegisterRequest req = regRequest("bob@gmail.com");
            when(passwordEncoder.encode("Secret@123")).thenReturn("hashed-Secret@123");
            when(userRepository.save(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId("new-user-id");
                return u;
            });

            User result = authService.completeRegistration(req, "654321");

            assertThat(result.getId()).isEqualTo("new-user-id");
            assertThat(result.getEmail()).isEqualTo("bob@gmail.com");
            assertThat(result.getPassword()).isEqualTo("hashed-Secret@123");
            assertThat(result.isEmailVerified()).isTrue();
            assertThat(result.isEnabled()).isTrue();
            assertThat(result.getRoles()).containsExactly(Role.USER);
            assertThat(result.getProvider()).isEqualTo(AuthProvider.LOCAL);
            assertThat(result.getProfileCompletionScore()).isEqualTo(20);

            verify(emailOtpService).verifyOtp("bob@gmail.com", "654321");
            verify(auditLogService).logRegister(eq("new-user-id"), any(), any());
        }
    }

    @Nested
    @DisplayName("login() — authentication, lockout, status checks")
    class LoginTests {

        private static final String IP = "192.168.1.1";
        private static final String UA = "Mozilla/5.0";

        @Test
        @DisplayName("returns user and resets failed-attempts counter on successful login")
        void successfulLogin() {
            User user = activeUser("u1", "alice@gmail.com", "hashed");
            user.setFailedLoginAttempts(2);
            when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("rawPwd", "hashed")).thenReturn(true);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User result = authService.login("alice@gmail.com", "rawPwd", IP, UA);

            assertThat(result.getFailedLoginAttempts()).isZero();
            assertThat(result.getLockedUntil()).isNull();
            assertThat(result.getLastLoginAt()).isNotNull();
            assertThat(result.getLastLoginIp()).isEqualTo(IP);
            verify(auditLogService).logLoginSuccess("u1", IP, UA);
        }

        @Test
        @DisplayName("throws InvalidCredentialsException when email not found")
        void throwsWhenEmailNotFound() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login("ghost@gmail.com", "pwd", IP, UA))
                    .isInstanceOf(AppException.InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("throws AccountSuspendedException for SUSPENDED account")
        void throwsForSuspendedAccount() {
            User user = activeUser("u1", "alice@gmail.com", "hashed");
            user.setStatus(UserStatus.SUSPENDED);
            when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login("alice@gmail.com", "rawPwd", IP, UA))
                    .isInstanceOf(AppException.AccountSuspendedException.class);
        }

        @Test
        @DisplayName("throws InvalidCredentialsException for DELETED account (prevents enumeration)")
        void throwsInvalidCredentialsForDeletedAccount() {
            User user = activeUser("u1", "alice@gmail.com", "hashed");
            user.setStatus(UserStatus.DELETED);
            when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login("alice@gmail.com", "rawPwd", IP, UA))
                    .isInstanceOf(AppException.InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("throws AppException (TOO_MANY_REQUESTS) when account is currently locked")
        void throwsWhenLocked() {
            User user = activeUser("u1", "alice@gmail.com", "hashed");
            user.setLockedUntil(Instant.now().plusSeconds(900));
            when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login("alice@gmail.com", "rawPwd", IP, UA))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("locked");
        }

        @Test
        @DisplayName("throws InvalidCredentialsException on wrong password and increments failedAttempts")
        void incrementsFailedAttemptsOnWrongPassword() {
            User user = activeUser("u1", "alice@gmail.com", "hashed");
            user.setFailedLoginAttempts(1);
            when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> authService.login("alice@gmail.com", "wrong", IP, UA))
                    .isInstanceOf(AppException.InvalidCredentialsException.class);

            assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
            verify(auditLogService).logLoginFailed("u1", IP, UA);
        }

        @Test
        @DisplayName("locks account and resets failedAttempts to 0 after 5th failed attempt")
        void locksAccountAfterFiveFailedAttempts() {
            User user = activeUser("u1", "alice@gmail.com", "hashed");
            user.setFailedLoginAttempts(4);
            when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> authService.login("alice@gmail.com", "wrong", IP, UA))
                    .isInstanceOf(AppException.InvalidCredentialsException.class);

            assertThat(user.getLockedUntil()).isNotNull();
            assertThat(user.getLockedUntil()).isAfter(Instant.now());
            assertThat(user.getFailedLoginAttempts()).isZero();
            verify(auditLogService).logAccountLocked("u1", IP, UA);
        }

        @Test
        @DisplayName("reactivates DEACTIVATED account on successful login")
        void reactivatesDeactivatedAccountOnLogin() {
            User user = activeUser("u1", "alice@gmail.com", "hashed");
            user.setStatus(UserStatus.DEACTIVATED);
            user.setDeactivated(true);
            when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("rawPwd", "hashed")).thenReturn(true);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.login("alice@gmail.com", "rawPwd", IP, UA);

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.isDeactivated()).isFalse();
        }
    }
}
