package com.agenticai.chatbot.security.filter;

import com.agenticai.chatbot.security.service.ChatbotUserDetailsService;
import com.agenticai.chatbot.security.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private ChatbotUserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        // FIX: Provide non-null paths universally so shouldNotFilter() doesn't throw NullPointerException
        lenient().when(request.getServletPath()).thenReturn("/api/chat");
        lenient().when(request.getRequestURI()).thenReturn("/api/chat");
        lenient().when(request.getContextPath()).thenReturn("");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testDoFilter_NoAuthorizationHeader() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilter_InvalidAuthorizationHeaderPrefix() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNzd29yZA==");

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilter_InvalidTokenStructure() throws ServletException, IOException {
        String token = "invalid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenStructureValid(token)).thenReturn(false);

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilter_ValidStructure_NullUsername() throws ServletException, IOException {
        String token = "valid-structure-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenStructureValid(token)).thenReturn(true);
        when(jwtUtil.extractUsername(token)).thenReturn(null);

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilter_ValidStructure_UsernameExtracted_AlreadyAuthenticated() throws ServletException, IOException {
        String token = "valid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenStructureValid(token)).thenReturn(true);
        when(jwtUtil.extractUsername(token)).thenReturn("user");

        Authentication existingAuth = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertEquals(existingAuth, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void testDoFilter_HappyPath_AuthenticationSuccess() throws ServletException, IOException {
        String token = "valid-token";
        String username = "john.doe";
        UserDetails userDetails = mock(UserDetails.class);
        doReturn(Collections.emptyList()).when(userDetails).getAuthorities();

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenStructureValid(token)).thenReturn(true);
        when(jwtUtil.extractUsername(token)).thenReturn(username);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
        when(jwtUtil.isTokenValid(token, userDetails)).thenReturn(true);

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(userDetails, auth.getPrincipal());
    }

    @Test
    void testDoFilter_TokenInvalidForUser() throws ServletException, IOException {
        String token = "valid-token";
        String username = "john.doe";
        UserDetails userDetails = mock(UserDetails.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenStructureValid(token)).thenReturn(true);
        when(jwtUtil.extractUsername(token)).thenReturn(username);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
        when(jwtUtil.isTokenValid(token, userDetails)).thenReturn(false);

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilter_ExceptionDuringAuthentication_ClearsContext() throws ServletException, IOException {
        String token = "valid-token";
        String username = "john.doe";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenStructureValid(token)).thenReturn(true);
        when(jwtUtil.extractUsername(token)).thenReturn(username);
        when(userDetailsService.loadUserByUsername(username)).thenThrow(new RuntimeException("Database error"));

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilter_PublicRoute_SkipsFilterEntirely() throws ServletException, IOException {
        // Explicitly set a known public path listed in your filter's whitelist bypass array
        lenient().when(request.getServletPath()).thenReturn("/api/auth/login");

        jwtAuthenticationFilter.doFilter(request, response, chain);

        // Verification verifies that the filter passes control directly without accessing any JWT utilities
        verify(chain, times(1)).doFilter(request, response);
        verifyNoInteractions(jwtUtil);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}