package com.agenticai.chatbot.security.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatbotUserDetailsServiceTest {

    @Mock
    private UserStore userStore;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ChatbotUserDetailsService userDetailsService;

    @Test
    void testSeedDemoUsers() {
        userDetailsService.seedDemoUsers();
        verify(userStore, times(1)).seed(passwordEncoder);
    }

    @Test
    void testLoadUserByUsername_Success() {
        String username = "testuser";
        UserDetails mockUser = mock(UserDetails.class);
        when(userStore.find(username)).thenReturn(Optional.of(mockUser));

        UserDetails result = userDetailsService.loadUserByUsername(username);

        assertNotNull(result);
        assertEquals(mockUser, result);
        verify(userStore).find(username);
    }

    @Test
    void testLoadUserByUsername_NotFound() {
        String username = "unknownuser";
        when(userStore.find(username)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            userDetailsService.loadUserByUsername(username);
        });

        verify(userStore).find(username);
    }

    @Test
    void testRegisterUser_Success() {
        String username = "NewUser";
        String rawPassword = "password123";
        String email = "newuser@example.com";
        String[] roles = {"USER"};

        when(userStore.exists(username)).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn("encodedPassword123");

        UserDetails registeredUser = userDetailsService.registerUser(username, rawPassword, email, roles);

        assertNotNull(registeredUser);
        assertEquals("newuser", registeredUser.getUsername());
        assertEquals("encodedPassword123", registeredUser.getPassword());
        verify(userStore).exists(username);
        verify(passwordEncoder).encode(rawPassword);
        verify(userStore).put(registeredUser);
    }

    @Test
    void testRegisterUser_AlreadyExists() {
        String username = "existinguser";
        String rawPassword = "password123";
        String email = "existing@example.com";
        String[] roles = {"USER"};

        when(userStore.exists(username)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> {
            userDetailsService.registerUser(username, rawPassword, email, roles);
        });

        verify(userStore).exists(username);
        verify(passwordEncoder, never()).encode(any());
        verify(userStore, never()).put(any());
    }
}