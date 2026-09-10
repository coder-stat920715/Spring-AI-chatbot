package com.agenticai.chatbot.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only security configuration imported by controller tests via {@code @Import}.
 *
 * <h3>Why the original TestSecurityConfig failed</h3>
 * <p>{@code HttpSecurity} is NOT a Spring bean — it is created internally by
 * Spring Security's {@code HttpSecurityConfiguration} infrastructure bean.
 * {@code @WebMvcTest} does NOT load that infrastructure, so when the original
 * {@code testSecurityFilterChain(HttpSecurity http)} method tried to receive
 * {@code HttpSecurity} via parameter injection, Spring found no qualifying bean
 * and threw:
 * <pre>
 * UnsatisfiedDependencyException: No qualifying bean of type
 * 'org.springframework.security.config.annotation.web.builders.HttpSecurity'
 * </pre>
 *
 * <h3>The fix</h3>
 * <p>Adding {@code @EnableWebSecurity} to this class makes Spring Security
 * register its own {@code HttpSecurityConfiguration} infrastructure bean as part
 * of this configuration's lifecycle. That infrastructure bean is what creates and
 * provides the {@code HttpSecurity} instance that the {@code @Bean} method below
 * receives. Without {@code @EnableWebSecurity} the infrastructure is absent and
 * the parameter cannot be satisfied.
 *
 * <h3>Purpose</h3>
 * <p>Permits all requests so controller logic can be tested independently of
 * JWT/security concerns in {@code @WebMvcTest} slices. Role-based access control
 * is tested separately in {@code SecurityConfigTest} using the full context.
 */
@TestConfiguration
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}