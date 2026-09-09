package com.gateway.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CachePutRequest(
        @NotBlank(message = "Key must not be blank")
        @Size(max = 256, message = "Key must not exceed 256 characters")
        String key,

        @NotBlank(message = "Value must not be blank")
        @Size(max = 10000, message = "Value must not exceed 10,000 characters")
        String value,

        @Min(value = 1, message = "TTL must be at least 1 second when specified")
        Long ttlSeconds
) {}
