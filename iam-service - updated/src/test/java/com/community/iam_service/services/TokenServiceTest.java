package com.community.iam_service.services;

import com.community.iam_service.entity.Enum.AuthProvider;
import com.community.iam_service.entity.Enum.Role;
import com.community.iam_service.entity.Enum.UserStatus;
import com.community.iam_service.entity.User;
import com.community.iam_service.exception.AppException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TokenService")
class TokenServiceTest {

    private TokenService tokenService;

    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "test-secret-key-which-is-at-least-64-bytes-long-for-HS512-signing!!".getBytes());
    private static final long ACCESS_TOKEN_EXPIRY = 3_600_000L;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(tokenService, "accessTokenExpiry", ACCESS_TOKEN_EXPIRY);
    }

    private User testUser(String id, String email) {
        return User.builder()
                .id(id).email(email)
                .roles(List.of(Role.USER))
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
    }

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateTokenTests {

        @Test
        @DisplayName("returns a non-null, non-blank token string")
        void returnsNonBlankToken() {
            String token = tokenService.generateAccessToken(testUser("u1", "alice@gmail.com"));
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("token contains three JWT segments separated by dots")
        void tokenHasThreeSegments() {
            String token = tokenService.generateAccessToken(testUser("u1", "alice@gmail.com"));
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("subject claim equals the user's ID")
        void subjectClaimEqualsUserId() {
            String token = tokenService.generateAccessToken(testUser("user-42", "alice@gmail.com"));
            Claims claims = tokenService.validateAndParseClaims(token);
            assertThat(claims.getSubject()).isEqualTo("user-42");
        }

        @Test
        @DisplayName("email claim equals the user's email")
        void emailClaimEqualsUserEmail() {
            String token = tokenService.generateAccessToken(testUser("u1", "alice@gmail.com"));
            Claims claims = tokenService.validateAndParseClaims(token);
            assertThat(claims.get("email", String.class)).isEqualTo("alice@gmail.com");
        }

        @Test
        @DisplayName("roles claim contains the user's role names")
        void rolesClaimContainsRoleNames() {
            User user = testUser("u1", "alice@gmail.com");
            user.setRoles(List.of(Role.USER));
            String token = tokenService.generateAccessToken(user);
            Claims claims = tokenService.validateAndParseClaims(token);
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            assertThat(roles).containsExactly("USER");
        }

        @Test
        @DisplayName("different users get different tokens")
        void differentUsersGetDifferentTokens() {
            String t1 = tokenService.generateAccessToken(testUser("u1", "alice@gmail.com"));
            String t2 = tokenService.generateAccessToken(testUser("u2", "bob@gmail.com"));
            assertThat(t1).isNotEqualTo(t2);
        }
    }

    @Nested
    @DisplayName("validateAndParseClaims()")
    class ValidateClaimsTests {

        @Test
        @DisplayName("returns valid Claims for a freshly generated token")
        void returnsClaimsForValidToken() {
            String token = tokenService.generateAccessToken(testUser("u1", "alice@gmail.com"));
            Claims claims = tokenService.validateAndParseClaims(token);
            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("u1");
        }

        @Test
        @DisplayName("throws InvalidTokenException for a tampered token")
        void throwsForTamperedToken() {
            String token = tokenService.generateAccessToken(testUser("u1", "alice@gmail.com"));
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";

            assertThatThrownBy(() -> tokenService.validateAndParseClaims(tampered))
                    .isInstanceOf(AppException.InvalidTokenException.class);
        }

        @Test
        @DisplayName("throws InvalidTokenException for a completely invalid string")
        void throwsForGarbageInput() {
            assertThatThrownBy(() -> tokenService.validateAndParseClaims("not.a.jwt"))
                    .isInstanceOf(AppException.InvalidTokenException.class);
        }

        @Test
        @DisplayName("throws InvalidTokenException for blank input")
        void throwsForBlankInput() {
            assertThatThrownBy(() -> tokenService.validateAndParseClaims(""))
                    .isInstanceOf(AppException.InvalidTokenException.class);
        }
    }

    @Nested
    @DisplayName("extractUserId()")
    class ExtractUserIdTests {

        @Test
        @DisplayName("extracts the correct userId from a valid token")
        void extractsUserId() {
            String token = tokenService.generateAccessToken(testUser("user-99", "alice@gmail.com"));
            assertThat(tokenService.extractUserId(token)).isEqualTo("user-99");
        }

        @Test
        @DisplayName("throws InvalidTokenException when extracting from tampered token")
        void throwsForTamperedToken() {
            String token = tokenService.generateAccessToken(testUser("u1", "alice@gmail.com"));
            String tampered = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJoYWNrZXIifQ.invalidsig";

            assertThatThrownBy(() -> tokenService.extractUserId(tampered))
                    .isInstanceOf(AppException.InvalidTokenException.class);
        }
    }
}
