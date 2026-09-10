package com.agenticai.chatbot.security.filter;

import com.agenticai.chatbot.security.service.ChatbotUserDetailsService;
import com.agenticai.chatbot.security.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil                    jwtUtil;
    private final ChatbotUserDetailsService  userDetailsService;

    private static final String BEARER_PREFIX  = "Bearer ";
    private static final String AUTH_HEADER    = "Authorization";

    private static final String[] PUBLIC_AUTH_PATHS = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh"
    };

    private static final String[] PUBLIC_PATH_PREFIXES = {
            "/swagger-ui",
            "/v3/api-docs",
            "/actuator/health",
            "/api/chat/health",
            "/error"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Use getRequestURI() to bypass inconsistencies with getServletPath()
        String path = request.getRequestURI();

        for (String pub : PUBLIC_AUTH_PATHS) {
            if (path.equals(pub)) return true;
        }
        for (String pub : PUBLIC_PATH_PREFIXES) {
            if (path.startsWith(pub)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain          chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length());

        // Enhanced debugging to catch if jwtUtil is rejecting your token
        if (!jwtUtil.isTokenStructureValid(jwt)) {
            log.warn("JWT token structure validation failed for request on path: {}", request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        String username = jwtUtil.extractUsername(jwt);

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(jwt, userDetails)) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Successfully authenticated user '{}' for path {}", username, request.getRequestURI());
                } else {
                    log.warn("Token is invalid/expired for user '{}' on path {}", username, request.getRequestURI());
                }
            } catch (Exception e) {
                log.error("Could not authenticate user '{}': {}", username, e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}