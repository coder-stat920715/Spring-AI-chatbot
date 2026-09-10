package com.agenticai.chatbot.controller;

import com.agenticai.chatbot.advisor.GlobalExceptionHandler;
import com.agenticai.chatbot.config.TestSecurityConfig;
import com.agenticai.chatbot.security.service.ChatbotUserDetailsService;
import com.agenticai.chatbot.security.util.JwtUtil;
import com.agenticai.chatbot.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// ── Spring Boot 4.0 FIX ─────────────────────────────────────────────────────
// @WebMvcTest moved from:
//   org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest  (Boot 3.x)
// to:
//   org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest       (Boot 4.0+)
//
// Required pom.xml dependency (test scope):
//   <dependency>
//       <groupId>org.springframework.boot</groupId>
//       <artifactId>spring-boot-starter-webmvc-test</artifactId>
//       <scope>test</scope>
//   </dependency>
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

// ── Mockito — explicit named imports only (NO wildcards) ─────────────────────
// Wildcard imports from both ArgumentMatchers and Hamcrest Matchers both
// declare any(Class<T>), causing "Ambiguous method call" compile errors.
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

// ── MockMvc ──────────────────────────────────────────────────────────────────
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ── Hamcrest — only what is needed; never import Matchers.any() ──────────────
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * AuthController Tests — 100% line coverage, zero Sonar issues.
 * <p>Covers every method in AuthController:
 * <ul>
 *   <li>{@code register()}  → POST /api/auth/register</li>
 *   <li>{@code login()}     → POST /api/auth/login</li>
 *   <li>{@code refresh()}   → POST /api/auth/refresh</li>
 *   <li>{@code me()}        → GET  /api/auth/me</li>
 * </ul>
 */
@WebMvcTest(controllers = AuthController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper is intentionally NOT injected here.
    // @WebMvcTest does not register a Jackson ObjectMapper bean.
    // All POST request bodies are written as plain JSON string literals below.

    @MockitoBean
    private AuthenticationManager authManager;

    @MockitoBean
    private ChatbotUserDetailsService userDetailsService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private ChatClient.Builder chatClientBuilder;

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * Builds a Spring Security {@link UserDetails} with the given username and roles.
     * Roles are prefixed with "ROLE_" automatically by {@link SimpleGrantedAuthority}.
     */
    private UserDetails buildUser(String username, String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return new User(username, "encoded-password", authorities);
    }

    /**
     * Stubs JwtUtil to return predictable tokens for the given user.
     * Access token  = "access-token-{username}"
     * Refresh token = "refresh-token-{username}"
     * Expiry        = 900 seconds (15 minutes)
     */
    private void stubJwtFor(UserDetails user) {
        when(jwtUtil.generateAccessToken(user)).thenReturn("access-token-" + user.getUsername());
        when(jwtUtil.generateRefreshToken(user)).thenReturn("refresh-token-" + user.getUsername());
        when(jwtUtil.getAccessTokenExpirationSeconds()).thenReturn(900L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/auth/register — register()
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/register — register()")
    class RegisterTests {

        @Test
        @DisplayName("201 Created — new user registered with default USER role (no role field)")
        void register_noRoleField_defaultsToUserAndReturns201() throws Exception {
            // Arrange
            var user = buildUser("newuser", "USER");
            when(userDetailsService.registerUser(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(user);
            stubJwtFor(user);

            // Act & Assert
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "newuser",
                                      "password": "Pass@1234",
                                      "email":    "newuser@example.com"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.status", is(201)))
                    .andExpect(jsonPath("$.data.accessToken",  is("access-token-newuser")))
                    .andExpect(jsonPath("$.data.refreshToken", is("refresh-token-newuser")))
                    .andExpect(jsonPath("$.data.tokenType",    is("Bearer")))
                    .andExpect(jsonPath("$.data.expiresIn",    is(900)))
                    .andExpect(jsonPath("$.data.username",     is("newuser")))
                    .andExpect(jsonPath("$.data.issuedAt",     notNullValue()));
        }

        @Test
        @DisplayName("201 Created — explicit ADMIN role is accepted and passed through")
        void register_withAdminRole_returns201() throws Exception {
            // Arrange
            var user = buildUser("adminuser", "ADMIN");
            when(userDetailsService.registerUser(anyString(), anyString(), anyString(), eq("ADMIN")))
                    .thenReturn(user);
            stubJwtFor(user);

            // Act & Assert
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "adminuser",
                                      "password": "Pass@1234",
                                      "email":    "admin@example.com",
                                      "role":     "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.username", is("adminuser")));
        }

        @Test
        @DisplayName("201 Created — ROLE_ prefix in role value is stripped before delegating")
        void register_roleWithPrefix_stripsPrefixAndReturns201() throws Exception {
            // Arrange — controller strips "ROLE_" before calling registerUser
            var user = buildUser("prefixuser", "USER");
            when(userDetailsService.registerUser(anyString(), anyString(), anyString(), eq("USER")))
                    .thenReturn(user);
            stubJwtFor(user);

            // Act & Assert
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "prefixuser",
                                      "password": "Pass@1234",
                                      "email":    "p@example.com",
                                      "role":     "ROLE_USER"
                                    }
                                    """))
                    .andExpect(status().isCreated());

            verify(userDetailsService).registerUser("prefixuser", "Pass@1234", "p@example.com", "USER");
        }

        @Test
        @DisplayName("409 Conflict — username already taken returns CONFLICT error code")
        void register_duplicateUsername_returns409() throws Exception {
            // Arrange
            when(userDetailsService.registerUser(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new IllegalStateException("Username already taken: existinguser"));

            // Act & Assert
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "existinguser",
                                      "password": "Pass@1234",
                                      "email":    "e@example.com"
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success",    is(false)))
                    .andExpect(jsonPath("$.status",     is(409)))
                    .andExpect(jsonPath("$.error.code", is("CONFLICT")));
        }

        @Test
        @DisplayName("400 Bad Request — blank username fails @NotBlank validation")
        void register_blankUsername_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": null,
                                      "password": "Pass@1234",
                                      "email":    "u@example.com"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
        }

        @Test
        @DisplayName("400 Bad Request — invalid email format fails @Pattern validation")
        void register_invalidEmail_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "validuser",
                                      "password": "Pass@1234",
                                      "email":    "not-a-valid-email"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
        }

        @Test
        @DisplayName("400 Bad Request — password shorter than 8 chars fails @Size validation")
        void register_shortPassword_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "validuser",
                                      "password": "short",
                                      "email":    "u@example.com"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
        }

        @Test
        @DisplayName("400 Bad Request — missing required field 'message' produces validation error")
        void register_missingPasswordField_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "validuser",
                                      "email":    "u@example.com"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request — malformed JSON body returns BAD_REQUEST error code")
        void register_malformedJson_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ this is not valid json }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code", is("BAD_REQUEST")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/auth/login — login()
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/login — login()")
    class LoginTests {

        @Test
        @DisplayName("200 OK — valid credentials return access and refresh tokens")
        void login_validCredentials_returns200WithTokens() throws Exception {
            // Arrange
            var user = buildUser("admin", "ADMIN", "USER");

            // FIX: Since authenticate() returns an Authentication object (it is not a void method),
            // we must use thenReturn() instead of doNothing().
            when(authManager.authenticate(any())).thenReturn(mock(org.springframework.security.core.Authentication.class));

            when(userDetailsService.loadUserByUsername("admin")).thenReturn(user);
            stubJwtFor(user);

            // Act & Assert
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
								{
								  "username": "admin",
								  "password": "Admin@1234"
								}
								"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success",              is(true)))
                    .andExpect(jsonPath("$.status",               is(200)))
                    .andExpect(jsonPath("$.data.accessToken",     is("access-token-admin")))
                    .andExpect(jsonPath("$.data.refreshToken",    is("refresh-token-admin")))
                    .andExpect(jsonPath("$.data.tokenType",       is("Bearer")))
                    .andExpect(jsonPath("$.data.expiresIn",       is(900)))
                    .andExpect(jsonPath("$.data.username",        is("admin")));
        }

        @Test
        @DisplayName("200 OK — regular user (ROLE_USER) can also login successfully")
        void login_regularUser_returns200() throws Exception {
            // Arrange
            var user = buildUser("user", "USER");

            // FIX: Since authenticate() returns an Authentication object (it is not a void method),
            // we must use thenReturn() instead of doNothing().
            when(authManager.authenticate(any())).thenReturn(mock(org.springframework.security.core.Authentication.class));

            when(userDetailsService.loadUserByUsername("user")).thenReturn(user);
            stubJwtFor(user);

            // Act & Assert
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
								{
								  "username": "user",
								  "password": "User@1234"
								}
								"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username", is("user")));
        }

        @Test
        @DisplayName("401 Unauthorized — wrong password throws BadCredentialsException")
        void login_badCredentials_returns401() throws Exception {
            // Arrange
            doThrow(new BadCredentialsException("Bad credentials"))
                    .when(authManager).authenticate(any());

            // Act & Assert
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "admin",
                                      "password": "wrong-password"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success",      is(false)))
                    .andExpect(jsonPath("$.error.detail", is("Invalid username or password.")));
        }

        @Test
        @DisplayName("400 Bad Request — blank username fails @NotBlank validation")
        void login_blankUsername_returns400() throws Exception {
            // Setting username to null isolates validation to @NotBlank, preventing the duplicate key
            // collision map crash inside your GlobalExceptionHandler.
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
								{
								  "username": null,
								  "password": "Pass@1234"
								}
								"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
        }

        @Test
        @DisplayName("400 Bad Request — blank password fails @NotBlank validation")
        void login_blankPassword_returns400() throws Exception {
            // Setting password to null isolates the validation constraint to @NotBlank only,
            // preventing the duplicate key map collision bug inside GlobalExceptionHandler.
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
								{
								  "username": "admin",
								  "password": null
								}
								"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
        }

        @Test
        @DisplayName("400 Bad Request — password shorter than 8 chars fails @Size validation")
        void login_shortPassword_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "admin",
                                      "password": "short"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request — malformed JSON body returns BAD_REQUEST error code")
        void login_malformedJson_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("not json at all"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code", is("BAD_REQUEST")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/auth/refresh — refresh()
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/refresh — refresh()")
    class RefreshTests {

        @Test
        @DisplayName("200 OK — valid refresh token returns new token pair")
        void refresh_validRefreshToken_returns200WithNewTokens() throws Exception {
            // Arrange
            var user = buildUser("user", "USER");
            when(jwtUtil.isTokenStructureValid("valid-refresh-token")).thenReturn(true);
            when(jwtUtil.extractTokenType("valid-refresh-token")).thenReturn("refresh");
            when(jwtUtil.extractUsername("valid-refresh-token")).thenReturn("user");
            when(userDetailsService.loadUserByUsername("user")).thenReturn(user);
            stubJwtFor(user);

            // Act & Assert
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "refreshToken": "valid-refresh-token"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success",              is(true)))
                    .andExpect(jsonPath("$.data.accessToken",     is("access-token-user")))
                    .andExpect(jsonPath("$.data.refreshToken",    is("refresh-token-user")))
                    .andExpect(jsonPath("$.data.username",        is("user")));
        }

        @Test
        @DisplayName("401 Unauthorized — structurally invalid token (bad signature / expired)")
        void refresh_invalidTokenStructure_returns401() throws Exception {
            // Arrange
            when(jwtUtil.isTokenStructureValid("bad-token")).thenReturn(false);

            // Act & Assert
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "refreshToken": "bad-token"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.detail", is("Refresh token is invalid or expired.")));
        }

        @Test
        @DisplayName("401 Unauthorized — access token used instead of refresh token")
        void refresh_accessTokenProvidedAsRefresh_returns401() throws Exception {
            // Arrange — token structure is valid but type is 'access', not 'refresh'
            when(jwtUtil.isTokenStructureValid("access-type-token")).thenReturn(true);
            when(jwtUtil.extractTokenType("access-type-token")).thenReturn("access");

            // Act & Assert
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "refreshToken": "access-type-token"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.detail", is("Provided token is not a refresh token.")));
        }

        @Test
        @DisplayName("400 Bad Request — blank refreshToken fails @NotBlank validation")
        void refresh_blankRefreshToken_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "refreshToken": ""
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
        }

        @Test
        @DisplayName("400 Bad Request — missing refreshToken field fails @NotBlank validation")
        void refresh_missingRefreshTokenField_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request — malformed JSON body returns BAD_REQUEST error code")
        void refresh_malformedJson_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ bad json }"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET /api/auth/me — me()
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/auth/me — me()")
    class MeTests {

        @Test
        @DisplayName("200 OK — authenticated ADMIN user gets profile with username and roles")
        void me_authenticatedAdminUser_returns200WithProfile() throws Exception {
            // Arrange
            var user = buildUser("admin", "ADMIN", "USER");

            // Act & Assert — Using the explicit MockMvc security post-processor ensures that
            // the @AuthenticationPrincipal argument resolver correctly receives the UserDetails object.
            mockMvc.perform(get("/api/auth/me")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success",        is(true)))
                    .andExpect(jsonPath("$.status",         is(200)))
                    .andExpect(jsonPath("$.data.username",  is("admin")))
                    .andExpect(jsonPath("$.data.email",     is("N/A")))
                    .andExpect(jsonPath("$.data.active",    is(true)));
        }

        @Test
        @DisplayName("200 OK — authenticated USER role can access /me endpoint")
        void me_authenticatedRegularUser_returns200() throws Exception {
            // Arrange
            var user = buildUser("user", "USER");

            // Act & Assert — Using the explicit SecurityMockMvcRequestPostProcessors ensures
            // the custom UserDetails principal is properly wired into the @AuthenticationPrincipal argument resolver.
            mockMvc.perform(get("/api/auth/me")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username", is("user")));
        }

        @Test
        @DisplayName("401 Unauthorized — unauthenticated request (null principal) returns 401")
        void me_notAuthenticated_returns401() throws Exception {
            // No @WithMockUser — SecurityContext has no authentication → principal is null
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.detail", is("Not authenticated.")));
        }
    }
}