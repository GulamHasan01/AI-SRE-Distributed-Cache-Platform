package com.community.iam_service.services;

import com.community.iam_service.entity.EmailOtp;
import com.community.iam_service.repository.EmailOtpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailOtpService")
class EmailOtpServiceTest {

    @Mock
    private EmailOtpRepository repo;

    private EmailOtpService emailOtpService;

    @BeforeEach
    void setUp() {
        emailOtpService = new EmailOtpService(repo);
    }

    @Nested
    @DisplayName("generateOtp()")
    class GenerateOtpTests {

        @Test
        @DisplayName("deletes previous OTP for email and saves new 6-digit OTP")
        void generatesNewOtp() {
            String email = "test@gmail.com";

            String otp = emailOtpService.generateOtp(email);

            assertThat(otp).hasSize(6).matches("\\d{6}");
            verify(repo).deleteByEmail(email);

            ArgumentCaptor<EmailOtp> captor = ArgumentCaptor.forClass(EmailOtp.class);
            verify(repo).save(captor.capture());
            EmailOtp saved = captor.getValue();
            assertThat(saved.getEmail()).isEqualTo(email);
            assertThat(saved.getOtp()).isEqualTo(otp);
            assertThat(saved.getExpiryTime()).isAfter(Instant.now());
        }
    }

    @Nested
    @DisplayName("verifyOtp()")
    class VerifyOtpTests {

        @Test
        @DisplayName("verifies valid OTP and deletes record")
        void verifiesValidOtp() {
            String email = "test@gmail.com";
            String otp = "123456";
            EmailOtp record = EmailOtp.builder()
                    .email(email)
                    .otp(otp)
                    .expiryTime(Instant.now().plusSeconds(300))
                    .build();

            when(repo.findByEmail(email)).thenReturn(Optional.of(record));

            emailOtpService.verifyOtp(email, otp);

            verify(repo).delete(record);
        }

        @Test
        @DisplayName("throws exception when OTP is not found")
        void throwsWhenNotFound() {
            when(repo.findByEmail("missing@gmail.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emailOtpService.verifyOtp("missing@gmail.com", "123456"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("OTP not found");
        }

        @Test
        @DisplayName("throws exception when OTP does not match")
        void throwsWhenMismatch() {
            String email = "test@gmail.com";
            EmailOtp record = EmailOtp.builder()
                    .email(email)
                    .otp("123456")
                    .expiryTime(Instant.now().plusSeconds(300))
                    .build();

            when(repo.findByEmail(email)).thenReturn(Optional.of(record));

            assertThatThrownBy(() -> emailOtpService.verifyOtp(email, "654321"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid or expired OTP");
        }

        @Test
        @DisplayName("throws exception when OTP is expired")
        void throwsWhenExpired() {
            String email = "test@gmail.com";
            String otp = "123456";
            EmailOtp record = EmailOtp.builder()
                    .email(email)
                    .otp(otp)
                    .expiryTime(Instant.now().minusSeconds(10))
                    .build();

            when(repo.findByEmail(email)).thenReturn(Optional.of(record));

            assertThatThrownBy(() -> emailOtpService.verifyOtp(email, otp))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid or expired OTP");
        }
    }
}
