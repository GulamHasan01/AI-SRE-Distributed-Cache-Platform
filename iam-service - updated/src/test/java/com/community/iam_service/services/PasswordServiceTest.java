package com.community.iam_service.services;

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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordService")
class PasswordServiceTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private PasswordEncoder encoder;

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(userRepo, encoder);
    }

    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test
        @DisplayName("updates password when old password matches")
        void updatesPasswordSuccessfully() {
            User user = User.builder().id("u1").password("oldHashedPwd").build();
            when(userRepo.findById("u1")).thenReturn(Optional.of(user));
            when(encoder.matches("oldPwd", "oldHashedPwd")).thenReturn(true);
            when(encoder.encode("newPwd")).thenReturn("newHashedPwd");

            passwordService.changePassword("u1", "oldPwd", "newPwd");

            assertThat(user.getPassword()).isEqualTo("newHashedPwd");
            verify(userRepo).save(user);
        }

        @Test
        @DisplayName("throws UserNotFoundException when user is not found")
        void throwsUserNotFound() {
            when(userRepo.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> passwordService.changePassword("missing", "old", "new"))
                    .isInstanceOf(AppException.UserNotFoundException.class);
        }

        @Test
        @DisplayName("throws WrongPasswordException when old password is wrong")
        void throwsWrongPassword() {
            User user = User.builder().id("u1").password("oldHashedPwd").build();
            when(userRepo.findById("u1")).thenReturn(Optional.of(user));
            when(encoder.matches("wrongOldPwd", "oldHashedPwd")).thenReturn(false);

            assertThatThrownBy(() -> passwordService.changePassword("u1", "wrongOldPwd", "newPwd"))
                    .isInstanceOf(AppException.WrongPasswordException.class);

            verify(userRepo, never()).save(any());
        }
    }
}
