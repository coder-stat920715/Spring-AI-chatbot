package com.agenticai.chatbot.config;

import com.agenticai.chatbot.security.filter.JwtAuthenticationFilter;
import com.agenticai.chatbot.security.service.ChatbotUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Security configuration for the Spring AI Chatbot.
 *
 * <h3>Security architecture</h3>
 * <ul>
 * <li><b>Stateless</b> — no HTTP sessions; JWT tokens carry all auth state.</li>
 * <li><b>JWT filter</b> — {@link JwtAuthenticationFilter} runs before every request.</li>
 * <li><b>RBAC</b> — Role-Based Access Control via {@code @PreAuthorize} and URL rules.</li>
 * <li><b>BCrypt</b> — password hashing with strength 12.</li>
 * <li><b>CSRF disabled</b> — correct for stateless REST APIs (no cookies / session).</li>
 * </ul>
 *
 * <h3>Role hierarchy</h3>
 * <pre>
 * ROLE_ADMIN  → full access (all endpoints)
 * ROLE_USER   → access to /api/chat/** only
 * anonymous   → access to /api/auth/**, /swagger-ui/**, /v3/api-docs/**, /actuator/health
 * </pre>
 *
 * <h3>Endpoint access rules</h3>
 * <pre>
 * POST  /api/auth/login      → public
 * POST  /api/auth/register   → public
 * POST  /api/auth/refresh    → public
 * GET   /api/auth/me         → ROLE_USER or ROLE_ADMIN
 * POST  /api/chat/** → ROLE_USER or ROLE_ADMIN
 * GET   /api/chat/health     → public (health probe)
 * DELETE /api/chat/sessions/ → ROLE_USER or ROLE_ADMIN
 * GET   /swagger-ui/** → public
 * GET   /v3/api-docs/** → public
 * GET   /actuator/health     → public
 * everything else            → ROLE_ADMIN only
 * </pre>
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)   // enables @PreAuthorize / @PostAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter    jwtAuthFilter;
    private final ChatbotUserDetailsService  userDetailsService;
    private final PasswordEncoder            passwordEncoder;
    private final ObjectMapper               objectMapper = new ObjectMapper();

    // ── Public paths ──────────────────────────────────────────────────────

    private static final String[] PUBLIC_GET_PATHS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/actuator/health",
            "/api/chat/health",
            "/api/chat/info"
    };

    private static final String[] PUBLIC_POST_PATHS = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh"
    };

    // ── Security Filter Chain ─────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // ── CSRF: disabled for stateless REST (no cookies / sessions) ──
                .csrf(AbstractHttpConfigurer::disable)

                // ── Session: STATELESS — JWT is the only auth mechanism ────────
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── Authorization rules ────────────────────────────────────────
                .authorizeHttpRequests(auth -> auth

                        // Public GET endpoints (swagger, health, docs)
                        .requestMatchers(HttpMethod.GET,  PUBLIC_GET_PATHS).permitAll()

                        // Public POST endpoints (login, register, refresh)
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()

                        // Chat endpoints — any authenticated user (USER or ADMIN)
                        .requestMatchers(HttpMethod.POST,   "/api/chat/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/chat/sessions/**").hasAnyRole("USER", "ADMIN")

                        // Auth info endpoint — authenticated users only
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").hasAnyRole("USER", "ADMIN")

                        // Admin-only: actuator full access, user management
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Everything else requires ADMIN
                        .anyRequest().hasRole("ADMIN")
                )

                // ── Custom 401 / 403 handlers ──────────────────────────────────
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))

                // ── Authentication provider ────────────────────────────────────
                .authenticationProvider(authenticationProvider())

                // ── JWT filter before Spring Security's default auth filter ────
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("SecurityFilterChain configured — JWT stateless mode, RBAC enabled");
        return http.build();
    }

    // ── Beans ─────────────────────────────────────────────────────────────

    /**
     * DAO-based authentication provider — validates username/password
     * against the {@link ChatbotUserDetailsService}.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Exposes the {@link AuthenticationManager} for use in the
     * {@link com.agenticai.chatbot.controller.AuthController}.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    // ── Custom error handlers ─────────────────────────────────────────────

    /**
     * 401 Unauthorized — returned when a request reaches a protected endpoint
     * without a valid JWT (missing, expired, or malformed token).
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (HttpServletRequest request,
                HttpServletResponse response,
                AuthenticationException ex) -> {

            log.warn("Unauthorized access attempt on {}: {}", request.getRequestURI(), ex.getMessage());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success",   false);
            body.put("status",    401);
            body.put("message",   "Unauthorized");
            body.put("error",     Map.of("code", "UNAUTHORIZED",
                    "detail", "Missing or invalid JWT token. "
                            + "Include 'Authorization: Bearer <token>' header."));
            body.put("path",      request.getRequestURI());
            body.put("timestamp", Instant.now().toString());

            objectMapper.writeValue(response.getWriter(), body);
        };
    }

    /**
     * 403 Forbidden — returned when a valid JWT is present but the user's
     * role is insufficient to access the endpoint.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest request,
                HttpServletResponse response,
                org.springframework.security.access.AccessDeniedException ex) -> {

            log.warn("Access denied on {} for user: {}", request.getRequestURI(), ex.getMessage());

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success",   false);
            body.put("status",    403);
            body.put("message",   "Forbidden");
            body.put("error",     Map.of("code", "FORBIDDEN",
                    "detail", "You do not have permission to access this resource."));
            body.put("path",      request.getRequestURI());
            body.put("timestamp", Instant.now().toString());

            objectMapper.writeValue(response.getWriter(), body);
        };
    }
}