package com.agenticai.chatbot.config;

import com.agenticai.chatbot.service.ChatService;
import com.agenticai.chatbot.security.service.ChatbotUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Arrays;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link SecurityConfig}.
 *
 * <p>Starts the full Spring context and verifies that URL-level security rules
 * are enforced correctly for all endpoint categories:
 * <ul>
 *   <li>Public endpoints (no auth needed)</li>
 *   <li>Protected endpoints (JWT required → 401 without token)</li>
 *   <li>Role-restricted endpoints (403 for wrong role)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc   // ✅ from org.springframework.boot.webmvc.test.autoconfigure
@DisplayName("SecurityConfig — Authorization rules")
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    // Mock out AI services so the full context starts without API keys
    @MockitoBean private ChatService chatService;

    @Autowired
    private ChatbotUserDetailsService userDetailsService;

    @BeforeEach
    void seedUsers() {
        userDetailsService.seedDemoUsers();
    }

    /**
     * Builds a real {@link UserDetails} with ROLE_-prefixed authorities.
     * Required for .with(user(...)) so that @AuthenticationPrincipal resolves
     * correctly inside the controller, and so that jsonPath assertions on
     * "$.data.roles[0]" match the full "ROLE_USER" / "ROLE_ADMIN" strings
     * returned by getAuthority().
     */
    private UserDetails buildUserDetails(String username, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return new User(username, "password", authorities);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public endpoints — must be accessible without any token
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Public endpoints (no auth required)")
    class PublicEndpoints {

        @Test
        @DisplayName("GET /api/chat/health should return 200 without auth")
        void healthIsPublic() throws Exception {
            mockMvc.perform(get("/api/chat/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /swagger-ui.html should be accessible without auth")
        void swaggerUiIsPublic() throws Exception {
            // 302 redirect to /swagger-ui/index.html is correct behaviour
            mockMvc.perform(get("/swagger-ui.html"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("GET /v3/api-docs should be accessible without auth")
        void openApiDocsIsPublic() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("POST /api/auth/login should be accessible without auth")
        void loginIsPublic() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"Admin@1234\"}"))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("POST /api/auth/register should be accessible without auth")
        void registerIsPublic() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"testuser99\",\"password\":\"Test@1234\",\"email\":\"t@t.com\"}"))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Protected endpoints — must return 401 without a token
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Protected endpoints — 401 without JWT")
    class ProtectedEndpoints {

        @Test
        @DisplayName("POST /api/chat should return 401 without JWT")
        void chatRequiresAuth() throws Exception {
            mockMvc.perform(post("/api/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Hello\",\"streaming\":false}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("POST /api/chat/stream should return 401 without JWT")
        void streamRequiresAuth() throws Exception {
            mockMvc.perform(post("/api/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Hello\",\"streaming\":true}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /api/chat/sessions/{id} should return 401 without JWT")
        void clearSessionRequiresAuth() throws Exception {
            mockMvc.perform(delete("/api/chat/sessions/test-session"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/auth/me should return 401 without JWT")
        void meRequiresAuth() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ROLE_USER — can access chat, cannot access admin endpoints
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ROLE_USER access control")
    class UserRoleTests {

        @Test
        @DisplayName("ROLE_USER can access GET /api/auth/me")
        void userCanAccessMe() throws Exception {
            // .with(user(...)) injects a real UserDetails so that
            // @AuthenticationPrincipal resolves correctly in the controller.
            // @WithMockUser cannot be used here because its internal principal
            // stub is not a UserDetails instance, causing the controller to
            // receive null and return 401 before any role check happens.
            mockMvc.perform(get("/api/auth/me")
                            .with(user(buildUserDetails("user", "USER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("user"))
                    .andExpect(jsonPath("$.data.roles[0]").value("ROLE_USER"));
        }

        @Test
        @DisplayName("ROLE_USER cannot access /actuator/** — 403 Forbidden")
        void userCannotAccessActuator() throws Exception {
            mockMvc.perform(get("/actuator/env")
                            .with(user(buildUserDetails("user", "USER"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ROLE_ADMIN — full access
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ROLE_ADMIN access control")
    class AdminRoleTests {

        @Test
        @DisplayName("ROLE_ADMIN can access GET /api/auth/me")
        void adminCanAccessMe() throws Exception {
            // Same reason as userCanAccessMe() — @AuthenticationPrincipal
            // requires a real UserDetails instance to resolve correctly.
            mockMvc.perform(get("/api/auth/me")
                            .with(user(buildUserDetails("admin", "ADMIN", "USER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("admin"));
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("ROLE_ADMIN can access /actuator/health")
        void adminCanAccessActuator() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Invalid / malformed JWT — must return 401
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Invalid JWT handling")
    class InvalidJwtTests {

        @Test
        @DisplayName("Malformed Bearer token returns 401")
        void malformedTokenReturns401() throws Exception {
            mockMvc.perform(post("/api/chat")
                            .header("Authorization", "Bearer this.is.not.a.valid.jwt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Hello\",\"streaming\":false}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Missing Bearer prefix returns 401")
        void missingBearerPrefixReturns401() throws Exception {
            mockMvc.perform(post("/api/chat")
                            .header("Authorization", "NotBearer sometoken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Hello\",\"streaming\":false}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Empty Authorization header returns 401")
        void emptyAuthHeaderReturns401() throws Exception {
            mockMvc.perform(post("/api/chat")
                            .header("Authorization", "")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Hello\",\"streaming\":false}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}