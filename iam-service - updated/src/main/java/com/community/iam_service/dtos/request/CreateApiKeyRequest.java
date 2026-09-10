package com.community.iam_service.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CreateApiKeyRequest {

    @NotBlank(message = "Key name is required")
    @Size(min = 1, max = 50, message = "Name must be 1-50 characters")
    private String name;

    @NotEmpty(message = "At least one scope is required")
    private List<String> scopes;

    private Instant expiresAt;
}