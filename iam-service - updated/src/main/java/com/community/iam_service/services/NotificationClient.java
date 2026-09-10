package com.community.iam_service.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationClient {

    private final RestTemplate restTemplate;

    @Async
    public void sendOtpEmail(String email, String otp) {
        try {
            String url = "http://localhost:8084/notify/email";

            Map<String, Object> body = Map.of(
                    "to", email,
                    "type", "OTP",
                    "variables", Map.of(
                            "otp", otp,
                            "expiry", "5 minutes"
                    )
            );

            restTemplate.postForObject(url, body, Void.class);

        } catch (Exception e) {
            System.out.println("⚠️ Notification failed: " + e.getMessage());

        }
    }
}