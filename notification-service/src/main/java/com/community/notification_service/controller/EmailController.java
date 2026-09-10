package com.community.notification_service.controller;

import com.community.notification_service.dtos.request.EmailRequest;
import com.community.notification_service.services.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/email")
    public ResponseEntity<Map<String, String>> sendEmail(
            @Valid @RequestBody EmailRequest request) {

        emailService.sendAsync(request);

        return ResponseEntity.accepted().body(Map.of(
                "status",  "queued",
                "message", "Email queued for delivery"
        ));
    }
}
