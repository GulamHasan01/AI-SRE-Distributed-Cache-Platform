package com.community.iam_service.oauth;

import com.community.iam_service.entity.Enum.AuthProvider;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuthUserInfo {
    private String providerUserId;
    private String email;
    private AuthProvider provider;
}
