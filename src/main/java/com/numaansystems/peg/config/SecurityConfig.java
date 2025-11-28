package com.numaansystems.peg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;

/**
 * Security configuration for the gateway.
 * Configures OAuth2/OIDC authentication with Azure AD.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Configure security for the gateway.
     * All requests require authentication except for actuator endpoints and MSAL session endpoints.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        
        http
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints - no authentication required
                .pathMatchers("/gateway/actuator/health", "/gateway/actuator/info").permitAll()
                // MSAL session endpoints - allow unauthenticated access for token exchange
                // The token validation happens inside the handler
                .pathMatchers("/oauth/session/**").permitAll()
                // Static JS files for MSAL integration
                .pathMatchers("/js/**").permitAll()
                // All other requests require authentication
                .anyExchange().authenticated()
            )
            .oauth2Login(oauth2 -> {
                // Default OAuth2 login configuration
            })
            .logout(logout -> logout
                .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
            )
            .csrf(csrf -> csrf.disable()); // CSRF is disabled for API gateway/proxy scenarios
                                           // The gateway acts as a reverse proxy and doesn't maintain 
                                           // state-changing forms. Backend services should implement
                                           // their own CSRF protection if needed.
                                           // Consider enabling for production if the gateway serves
                                           // user-facing forms directly.

        return http.build();
    }

    /**
     * Configure logout to work with Azure AD OIDC.
     * This ensures proper logout from Azure AD when users log out.
     */
    private ServerLogoutSuccessHandler oidcLogoutSuccessHandler(
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedServerLogoutSuccessHandler successHandler =
                new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
        successHandler.setPostLogoutRedirectUri("{baseUrl}/gateway");
        return successHandler;
    }
}
