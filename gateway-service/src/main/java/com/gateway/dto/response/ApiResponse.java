package com.gateway.dto.response;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String message,
        Instant timestamp,
        T data,
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
