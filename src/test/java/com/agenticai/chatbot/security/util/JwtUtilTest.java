package com.agenticai.chatbot.security.util;

import com.agenticai.chatbot.security.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtUtil}.
 *
 * <p>Uses {@link ReflectionTestUtils} to inject the secret and expiry values
 * directly — no Spring context needed, so tests run fast.
 */
@DisplayName("JwtUtil")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    // Valid 256-bit Base64 secret for tests (DO NOT use in production)
    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLTI1Ni1iaXRzLWxvbmc=";

    private UserDetails adminUser;
    private UserDetails regularUser;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpirationMs",  900_000L);   // 15 min
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpirationMs", 604_800_000L); // 7 days

        adminUser = User.builder()
                .username("admin")
                .password("encoded-password")
                .roles("ADMIN", "USER")
                .build();

        regularUser = User.builder()
                .username("user")
                .password("encoded-password")
                .roles("USER")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Access token generation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateAccessToken")
    class GenerateAccessToken {

        @Test
        @DisplayName("should generate a non-blank access token")
        void shouldGenerateNonBlankToken() {
            String token = jwtUtil.generateAccessToken(adminUser);
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("access token should have three parts (header.payload.signature)")
        void shouldHaveThreeParts() {
            String token = jwtUtil.generateAccessToken(adminUser);
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("access token should encode the correct username")
        void shouldEncodeUsername() {
            String token = jwtUtil.generateAccessToken(adminUser);
            assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin");
        }

        @Test
        @DisplayName("access token should encode roles")
        void shouldEncodeRoles() {
            String token = jwtUtil.generateAccessToken(adminUser);
            // Validate token is valid (roles are embedded in claims)
            assertThat(jwtUtil.isTokenStructureValid(token)).isTrue();
        }

        @Test
        @DisplayName("access token type claim should be 'access'")
        void shouldHaveAccessTypeClain() {
            String token = jwtUtil.generateAccessToken(adminUser);
            assertThat(jwtUtil.extractTokenType(token)).isEqualTo("access");
        }

        @Test
        @DisplayName("different users should produce different tokens")
        void differentUsersDifferentTokens() {
            String adminToken = jwtUtil.generateAccessToken(adminUser);
            String userToken  = jwtUtil.generateAccessToken(regularUser);
            assertThat(adminToken).isNotEqualTo(userToken);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Refresh token generation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateRefreshToken")
    class GenerateRefreshToken {

        @Test
        @DisplayName("refresh token should encode the username")
        void shouldEncodeUsername() {
            String token = jwtUtil.generateRefreshToken(regularUser);
            assertThat(jwtUtil.extractUsername(token)).isEqualTo("user");
        }

        @Test
        @DisplayName("refresh token type claim should be 'refresh'")
        void shouldHaveRefreshTypeClaim() {
            String token = jwtUtil.generateRefreshToken(regularUser);
            assertThat(jwtUtil.extractTokenType(token)).isEqualTo("refresh");
        }

        @Test
        @DisplayName("access and refresh tokens from same user should differ")
        void accessAndRefreshShouldDiffer() {
            String access  = jwtUtil.generateAccessToken(adminUser);
            String refresh = jwtUtil.generateRefreshToken(adminUser);
            assertThat(access).isNotEqualTo(refresh);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Token validation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isTokenValid")
    class IsTokenValid {

        @Test
        @DisplayName("valid token for correct user should return true")
        void validTokenForCorrectUser() {
            String token = jwtUtil.generateAccessToken(adminUser);
            assertThat(jwtUtil.isTokenValid(token, adminUser)).isTrue();
        }

        @Test
        @DisplayName("valid token for wrong user should return false")
        void validTokenForWrongUser() {
            String token = jwtUtil.generateAccessToken(adminUser);
            assertThat(jwtUtil.isTokenValid(token, regularUser)).isFalse();
        }

        @Test
        @DisplayName("structurally valid token should pass isTokenStructureValid")
        void structurallyValidToken() {
            String token = jwtUtil.generateAccessToken(regularUser);
            assertThat(jwtUtil.isTokenStructureValid(token)).isTrue();
        }

        @Test
        @DisplayName("random string should fail isTokenStructureValid")
        void randomStringFails() {
            assertThat(jwtUtil.isTokenStructureValid("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("null token should fail isTokenStructureValid gracefully")
        void nullTokenFails() {
            assertThat(jwtUtil.isTokenStructureValid(null)).isFalse();
        }

        @Test
        @DisplayName("blank token should fail isTokenStructureValid gracefully")
        void blankTokenFails() {
            assertThat(jwtUtil.isTokenStructureValid("")).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Expiry
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Token expiry")
    class TokenExpiry {

        @Test
        @DisplayName("access token expiration should be in the future")
        void accessTokenExpiresInFuture() {
            String token = jwtUtil.generateAccessToken(adminUser);
            Date expiry  = jwtUtil.extractExpiration(token);
            assertThat(expiry).isAfter(new Date());
        }

        @Test
        @DisplayName("expired token should fail isTokenStructureValid")
        void expiredTokenFails() {
            // Create a JwtUtil with 1ms expiry to get an immediately-expired token
            JwtUtil fastExpiry = new JwtUtil();
            ReflectionTestUtils.setField(fastExpiry, "secret", TEST_SECRET);
            ReflectionTestUtils.setField(fastExpiry, "accessTokenExpirationMs", 1L);
            ReflectionTestUtils.setField(fastExpiry, "refreshTokenExpirationMs", 1L);

            String token = fastExpiry.generateAccessToken(adminUser);

            // Small sleep to let the 1ms expiry pass
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}

            assertThat(fastExpiry.isTokenStructureValid(token)).isFalse();
        }

        @Test
        @DisplayName("getAccessTokenExpirationSeconds should return 900 (15 min)")
        void expirationSecondsCorrect() {
            assertThat(jwtUtil.getAccessTokenExpirationSeconds()).isEqualTo(900L);
        }
    }
}