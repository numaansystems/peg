package com.numaansystems.testapp.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the backend application.
 * 
 * This configuration:
 * 1. Disables default form login (authentication is handled by gateway)
 * 2. Adds the GatewayAuthenticationFilter to process gateway headers
 * 3. Enables method-level security (@PreAuthorize, @Secured)
 * 4. Uses stateless sessions (gateway manages the session)
 * 
 * The gateway authenticates users via Azure AD OAuth2 and passes the
 * authenticated user's identity via X-Auth-* headers. This configuration
 * processes those headers and looks up user authorities from the database.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {

    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    public SecurityConfig(GatewayAuthenticationFilter gatewayAuthenticationFilter) {
        this.gatewayAuthenticationFilter = gatewayAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for API endpoints (gateway handles CSRF protection)
            .csrf(csrf -> csrf.disable())
            
            // Stateless sessions - gateway manages sessions
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Add gateway authentication filter before standard auth filter
            .addFilterBefore(gatewayAuthenticationFilter, 
                           UsernamePasswordAuthenticationFilter.class)
            
            // Disable form login - gateway handles authentication
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            
            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Health endpoint is public
                .requestMatchers("/health", "/actuator/health").permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
