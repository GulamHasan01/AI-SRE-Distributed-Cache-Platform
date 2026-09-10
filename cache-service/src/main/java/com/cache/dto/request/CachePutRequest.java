package com.cache.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for storing a key-value pair in the cache")
public record CachePutRequest(

        @Schema(description = "Cache key — must be unique per namespace", example = "user:1001:profile")
        @NotBlank(message = "Key must not be blank")
        @Size(max = 256, message = "Key must not exceed 256 characters")
        String key,

        @Schema(description = "Value to store — stored as a string; serialize complex objects to JSON before sending",
                example = "{\"name\":\"Alice\"}")
        @NotBlank(message = "Value must not be blank")
        @Size(max = 10_000, message = "Value must not exceed 10,000 characters")
        String value,

        @Schema(description = "Time-to-live in seconds. Omit or set to null to use the cache default. " +
                "The entry will be automatically removed after this many seconds.",
                example = "300",
                nullable = true)
        @Min(value = 1, message = "TTL must be at least 1 second when specified")
        Long ttlSeconds

) {

    public static CachePutRequest withoutTtl(String key, String value) {
        return new CachePutRequest(key, value, null);
    }
}
