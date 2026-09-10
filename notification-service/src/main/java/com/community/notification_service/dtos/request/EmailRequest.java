package com.community.notification_service.dtos.request;

import com.community.notification_service.entity.Enum.EmailType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class EmailRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Must be a valid email address")
    private String to;

    @NotNull(message = "Email type is required")
    private EmailType type;

    private String userId;

    private Map<String, String> variables;
}
