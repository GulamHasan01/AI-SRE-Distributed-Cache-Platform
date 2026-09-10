package com.community.iam_service.oauth;

import com.community.iam_service.entity.User;
import com.community.iam_service.entity.Enum.AuthProvider;
import com.community.iam_service.services.OAuthService;
import com.community.iam_service.services.TokenService;
import com.community.iam_service.services.RefreshTokenService;
import com.community.iam_service.services.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthService oAuthService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final SessionService sessionService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId().toUpperCase();

        OAuth2User oAuth2User = token.getPrincipal();

        String email;
        String providerUserId;

        if (provider.equals("GOOGLE") || provider.equals("LINKEDIN")) {

            email = oAuth2User.getAttribute("email");
            if (email == null || email.isBlank()) {
                response.sendRedirect("http://localhost:3000/login-error?reason=email_required");
                return;
            }
            providerUserId = oAuth2User.getAttribute("sub");
        }
        else {
            throw new RuntimeException("Unsupported OAuth provider: " + provider);
        }

        OAuthUserInfo userInfo = OAuthUserInfo.builder()
                .email(email)
                .providerUserId(providerUserId)
                .provider(AuthProvider.valueOf(provider))
                .build();

        User user = oAuthService.findOrCreateUser(userInfo);

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createAndReturnRaw(user.getId());

        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        sessionService.createSession(user.getId(), null, ip, userAgent);

        response.sendRedirect(
                "http://localhost:3000/oauth-success?accessToken=" + accessToken +
                        "&refreshToken=" + refreshToken
        );
    }
}