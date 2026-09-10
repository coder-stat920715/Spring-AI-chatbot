package com.agenticai.chatbot.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory user store — simulates a user repository.
 *
 * <p>Pre-seeded with two demo accounts on startup:
 * <ul>
 *   <li>{@code admin / Admin@1234}  → roles: ROLE_ADMIN, ROLE_USER</li>
 *   <li>{@code user  / User@1234}   → roles: ROLE_USER</li>
 * </ul>
 *
 * <p><b>Production note:</b> Replace this class with a proper JPA
 * {@code UserRepository} backed by PostgreSQL / MySQL. Inject the
 * repository into {@link ChatbotUserDetailsService} instead.
 */
@Slf4j
@Component
public class UserStore {

    private final Map<String, UserDetails> store = new ConcurrentHashMap<>();

    /**
     * Seeds the store with demo users.
     * Called once by {@link ChatbotUserDetailsService} after the
     * {@link PasswordEncoder} bean is available.
     */
    public void seed(PasswordEncoder encoder) {
        put(User.builder()
                .username("admin")
                .password(encoder.encode("Admin@1234"))
                .roles("ADMIN", "USER")
                .build());

        put(User.builder()
                .username("user")
                .password(encoder.encode("User@1234"))
                .roles("USER")
                .build());

        log.info("UserStore seeded with demo accounts: admin (ADMIN+USER), user (USER)");
    }

    public void put(UserDetails userDetails) {
        store.put(userDetails.getUsername().toLowerCase(), userDetails);
    }

    public Optional<UserDetails> find(String username) {
        return Optional.ofNullable(store.get(username == null ? "" : username.toLowerCase()));
    }

    public boolean exists(String username) {
        return store.containsKey(username == null ? "" : username.toLowerCase());
    }
}