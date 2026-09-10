package com.agenticai.chatbot.security.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Spring Security {@link UserDetailsService} backed by the in-memory {@link UserStore}.
 *
 * <p>Spring Security calls {@link #loadUserByUsername} when validating credentials
 * during both form-login and JWT filter token verification.
 *
 * <p><b>Production note:</b> Replace the {@link UserStore} dependency with a
 * JPA {@code UserRepository} and load the entity from the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotUserDetailsService implements UserDetailsService {

    private final UserStore      userStore;
    private final PasswordEncoder passwordEncoder;

    /**
     * Called once at startup to populate the demo user accounts.
     */
    @PostConstruct
    public void seedDemoUsers() {
        userStore.seed(passwordEncoder);
    }

    /**
     * Loads a user by their username.
     *
     * @param username case-insensitive username.
     * @throws UsernameNotFoundException if no matching user exists.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userStore.find(username)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });
    }

    /**
     * Registers a new user in the store.
     *
     * @param username  Desired username (must be unique).
     * @param rawPassword Plain-text password — will be BCrypt-encoded before storage.
     * @param email     User's email (stored for informational purposes only here).
     * @param roles     Spring Security role names, e.g. {@code "USER"}, {@code "ADMIN"}.
     * @throws IllegalStateException if the username already exists.
     */
    public UserDetails registerUser(String username, String rawPassword,
                                    String email, String... roles) {
        if (userStore.exists(username)) {
            throw new IllegalStateException("Username already taken: " + username);
        }

        var user = org.springframework.security.core.userdetails.User.builder()
                .username(username.toLowerCase())
                .password(passwordEncoder.encode(rawPassword))
                .roles(roles)
                .build();

        userStore.put(user);
        log.info("Registered new user: {} with roles: {}", username, roles);
        return user;
    }
}
