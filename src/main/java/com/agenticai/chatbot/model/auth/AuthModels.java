package com.agenticai.chatbot.model.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/**
 * Authentication domain models — request and response records for
 * register, login, and token refresh endpoints.
 */
public final class AuthModels {

    private AuthModels() {}

    // ── Requests ──────────────────────────────────────────────────────────

    /**
     * Login request payload.
     *
     * @param username The username (3–50 alphanumeric chars).
     * @param password The password (8–100 chars).
     */
    public record LoginRequest(

            @NotBlank(message = "username must not be blank")
            @Size(min = 3, max = 50, message = "username must be 3–50 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_]+$",
                    message = "username may only contain letters, digits, or underscores")
            @JsonProperty("username")
            String username,

            @NotBlank(message = "password must not be blank")
            @Size(min = 8, max = 100, message = "password must be 8–100 characters")
            @JsonProperty("password")
            String password
    ) {}

    /**
     * Register request payload.
     *
     * @param username Desired username (unique, 3–50 chars).
     * @param password Password (min 8 chars).
     * @param email    Valid e-mail address.
     * @param role     Desired role: USER or ADMIN (defaults to USER if omitted).
     */
    public record RegisterRequest(

            @NotBlank(message = "username must not be blank")
            @Size(min = 3, max = 50, message = "username must be 3–50 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_]+$",
                    message = "username may only contain letters, digits, or underscores")
            @JsonProperty("username")
            String username,

            @NotBlank(message = "password must not be blank")
            @Size(min = 8, max = 100, message = "password must be at least 8 characters")
            @JsonProperty("password")
            String password,

            @NotBlank(message = "email must not be blank")
            @Pattern(regexp = "^[\\w.%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$",
                    message = "must be a valid email address")
            @JsonProperty("email")
            String email,

            @JsonProperty("role")
            String role    // "USER" or "ADMIN" — defaults to USER if null/blank
    ) {}

    /**
     * Refresh token request.
     */
    public record RefreshTokenRequest(

            @NotBlank(message = "refreshToken must not be blank")
            @JsonProperty("refreshToken")
            String refreshToken
    ) {}

    // ── Responses ─────────────────────────────────────────────────────────

    /**
     * Successful authentication response — returned for login and register.
     *
     * @param accessToken  Short-lived JWT (default 15 min).
     * @param refreshToken Long-lived refresh JWT (default 7 days).
     * @param tokenType    Always "Bearer".
     * @param expiresIn    Access token lifetime in seconds.
     * @param username     The authenticated user's username.
     * @param roles        The user's assigned roles.
     */
    public record AuthResponse(
            @JsonProperty("accessToken")  String      accessToken,
            @JsonProperty("refreshToken") String      refreshToken,
            @JsonProperty("tokenType")    String      tokenType,
            @JsonProperty("expiresIn")    long        expiresIn,
            @JsonProperty("username")     String      username,
            @JsonProperty("roles")        Set<String> roles,
            @JsonProperty("issuedAt")     Instant     issuedAt
    ) {
        public static AuthResponse of(String accessToken, String refreshToken,
                                      long expiresInSeconds, String username,
                                      Set<String> roles) {
            return new AuthResponse(accessToken, refreshToken, "Bearer",
                    expiresInSeconds, username, roles, Instant.now());
        }
    }

    /**
     * Authenticated user detail — returned by GET /api/auth/me.
     */
    public record UserInfoResponse(
            @JsonProperty("username") String      username,
            @JsonProperty("email")    String      email,
            @JsonProperty("roles")    Set<String> roles,
            @JsonProperty("active")   boolean     active
    ) {}
}