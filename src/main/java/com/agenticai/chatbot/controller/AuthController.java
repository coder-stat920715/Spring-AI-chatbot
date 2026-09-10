package com.agenticai.chatbot.controller;

import com.agenticai.chatbot.model.ApiResponse;
import com.agenticai.chatbot.model.auth.AuthModels.*;
import com.agenticai.chatbot.security.service.ChatbotUserDetailsService;
import com.agenticai.chatbot.security.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication REST controller.
 *
 * <p>All endpoints here are public (no JWT required) except GET /api/auth/me.
 * Security rules are enforced in {@link com.agenticai.chatbot.config.SecurityConfig}.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication",
        description = "JWT authentication — register, login, token refresh, and profile")
public class AuthController {

    private final AuthenticationManager      authManager;
    private final ChatbotUserDetailsService  userDetailsService;
    private final JwtUtil                    jwtUtil;

    // ── Register ──────────────────────────────────────────────────────────

    @Operation(
            summary     = "Register a new user",
            description = """
                Creates a new account and immediately returns JWT tokens.
                Role defaults to `USER` if not specified.
                Use `ADMIN` to create an admin account (protect this in production!).
                
                **Demo users already seeded:** `admin / Admin@1234` and `user / User@1234`
                """,
            security = {}   // public — no JWT needed
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description  = "User registered successfully",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(name = "success", value = """
                    {
                      "success": true,
                      "status": 201,
                      "message": "Created",
                      "data": {
                        "accessToken": "eyJhbGci...",
                        "refreshToken": "eyJhbGci...",
                        "tokenType": "Bearer",
                        "expiresIn": 900,
                        "username": "newuser",
                        "roles": ["ROLE_USER"]
                      }
                    }
                    """))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Validation failed or username already taken")
    @PostMapping(value = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletRequest httpReq) {

        log.info("Register request for username={}", req.username());

        try {
            String role = (req.role() == null || req.role().isBlank()) ? "USER"
                    : req.role().toUpperCase().replace("ROLE_", "");

            UserDetails user = userDetailsService.registerUser(
                    req.username(), req.password(), req.email(), role);

            AuthResponse payload = buildAuthResponse(user);
            log.info("Registered new user: {}", req.username());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.created(payload, httpReq.getRequestURI()));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.conflict(e.getMessage(), httpReq.getRequestURI()));
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────

    @Operation(
            summary     = "Login and obtain JWT tokens",
            description = """
                Authenticates with username + password and returns a short-lived
                **access token** (15 min) and a long-lived **refresh token** (7 days).
                
                Copy the `accessToken` and use it in the **Authorize 🔒** button above.
                
                **Demo credentials:**
                - Admin: `admin / Admin@1234`
                - User:  `user / User@1234`
                """,
            security = {}   // public
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Login successful — tokens returned",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "status": 200,
                      "message": "OK",
                      "data": {
                        "accessToken":  "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIlJPTEVfQURNSU4iLCJST0xFX1VTRVIiXX0...",
                        "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
                        "tokenType":    "Bearer",
                        "expiresIn":    900,
                        "username":     "admin",
                        "roles":        ["ROLE_ADMIN", "ROLE_USER"],
                        "issuedAt":     "2026-05-31T10:00:00Z"
                      }
                    }
                    """)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "Invalid credentials")
    @PostMapping(value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpReq) {

        log.info("Login attempt for username={}", req.username());

        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (BadCredentialsException e) {
            log.warn("Login failed for username={}", req.username());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.unauthorized(
                            "Invalid username or password.", httpReq.getRequestURI()));
        }

        UserDetails user = userDetailsService.loadUserByUsername(req.username());
        AuthResponse payload = buildAuthResponse(user);
        log.info("Login successful for username={}", req.username());
        return ResponseEntity.ok(ApiResponse.ok(payload, httpReq.getRequestURI()));
    }

    // ── Refresh Token ─────────────────────────────────────────────────────

    @Operation(
            summary     = "Refresh access token",
            description = """
                Exchange a valid **refresh token** for a new access token.
                The refresh token itself is reissued too, implementing token rotation.
                """,
            security = {}   // public — refresh token IS the credential
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Tokens refreshed successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "Refresh token is invalid or expired")
    @PostMapping(value = "/refresh",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest req,
            HttpServletRequest httpReq) {

        String token = req.refreshToken();

        if (!jwtUtil.isTokenStructureValid(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.badRequest(
                            "Refresh token is invalid or expired.", httpReq.getRequestURI()));
        }

        String tokenType = jwtUtil.extractTokenType(token);
        if (!"refresh".equals(tokenType)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.badRequest(
                            "Provided token is not a refresh token.", httpReq.getRequestURI()));
        }

        String username = jwtUtil.extractUsername(token);
        UserDetails user = userDetailsService.loadUserByUsername(username);
        AuthResponse payload = buildAuthResponse(user);

        log.info("Token refreshed for username={}", username);
        return ResponseEntity.ok(ApiResponse.ok(payload, httpReq.getRequestURI()));
    }

    // ── Current User Info ─────────────────────────────────────────────────

    @Operation(
            summary     = "Get current user info",
            description = "Returns profile information for the currently authenticated user. Requires a valid JWT.",
            security    = { @SecurityRequirement(name = "BearerAuth") }
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "User info returned",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "status": 200,
                      "data": {
                        "username": "admin",
                        "email":    "N/A",
                        "roles":    ["ROLE_ADMIN", "ROLE_USER"],
                        "active":   true
                      }
                    }
                    """)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "Not authenticated")
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<UserInfoResponse>> me(
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest httpReq) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.badRequest("Not authenticated.", httpReq.getRequestURI()));
        }

        Set<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        var info = new UserInfoResponse(principal.getUsername(), "N/A", roles, principal.isEnabled());
        return ResponseEntity.ok(ApiResponse.ok(info, httpReq.getRequestURI()));
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(UserDetails user) {
        String accessToken  = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        Set<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return AuthResponse.of(accessToken, refreshToken,
                jwtUtil.getAccessTokenExpirationSeconds(), user.getUsername(), roles);
    }
}
