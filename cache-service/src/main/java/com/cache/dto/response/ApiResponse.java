package com.cache.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope used by all endpoints")
public record ApiResponse<T>(

        @Schema(description = "true if the operation succeeded, false otherwise")
        boolean success,

        @Schema(description = "Human-readable message describing the result")
        String message,

        @Schema(description = "UTC timestamp of the response")
        Instant timestamp,

        @Schema(description = "Response payload; absent on error responses")
        T data,

        @Schema(description = "Error detail; absent on success responses")
        String error

) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, Instant.now(), data, null);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, Instant.now(), null, null);
    }

    public static <T> ApiResponse<T> failure(String message, String error) {
        return new ApiResponse<>(false, message, Instant.now(), null, error);
    }
}
