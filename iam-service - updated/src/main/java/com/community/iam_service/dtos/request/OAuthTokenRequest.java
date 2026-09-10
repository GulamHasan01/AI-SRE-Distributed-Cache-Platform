package com.community.iam_service.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuthTokenRequest {

    @NotBlank(message = "Token is required")
    private String idToken;
}
