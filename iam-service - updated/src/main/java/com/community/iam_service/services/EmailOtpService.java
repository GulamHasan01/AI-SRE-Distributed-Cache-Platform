package com.community.iam_service.services;

import com.community.iam_service.entity.EmailOtp;
import com.community.iam_service.repository.EmailOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class EmailOtpService {

    private final EmailOtpRepository repo;

    public String generateOtp(String email) {
        String otp = String.valueOf(100000 + new Random().nextInt(900000));

        repo.deleteByEmail(email);

        repo.save(EmailOtp.builder()
                .email(email)
                .otp(otp)
                .expiryTime(Instant.now().plusSeconds(300))
                .build());

        return otp;
    }

    public void verifyOtp(String email, String otp) {
        EmailOtp record = repo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("OTP not found"));

        if (!record.getOtp().equals(otp)
                || record.getExpiryTime().isBefore(Instant.now())) {
            throw new RuntimeException("Invalid or expired OTP");
        }

        repo.delete(record);
    }
}