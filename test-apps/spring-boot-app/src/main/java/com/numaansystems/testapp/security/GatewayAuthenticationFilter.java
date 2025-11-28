package com.numaansystems.testapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authentication filter that processes gateway authentication headers.
 * 
 * This filter:
 * 1. Reads user identity from X-Auth-* headers set by the gateway
 * 2. Looks up user authorities from the database (via UserAuthorityService)
 * 3. Creates a Spring Security Authentication with the user's authorities
 * 
 * The gateway handles OAuth2/Azure AD authentication and passes the authenticated
 * user's identity via headers. This filter maps that identity to application-specific
 * authorities from the database.
 * 
 * SECURITY NOTE: In production, ensure that:
 * 1. The backend is NOT accessible directly from the internet
 * 2. Only the gateway can reach the backend (network isolation)
 * 3. Consider adding additional validation (e.g., shared secret header)
 */
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private final UserAuthorityService userAuthorityService;
    
    /**
     * Optional: Expected gateway secret for header validation.
     * Set via application.yml: app.gateway.secret
     * If not set, header validation is skipped (suitable for network-isolated deployments)
     */
    @Value("${app.gateway.secret:}")
    private String gatewaySecret;

    public GatewayAuthenticationFilter(UserAuthorityService userAuthorityService) {
        this.userAuthorityService = userAuthorityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // Optional: Validate gateway secret header (if configured)
        if (gatewaySecret != null && !gatewaySecret.isEmpty()) {
            String providedSecret = request.getHeader("X-Gateway-Secret");
            if (providedSecret == null || !gatewaySecret.equals(providedSecret)) {
                // Gateway secret doesn't match - don't authenticate
                filterChain.doFilter(request, response);
                return;
            }
        }
        
        // Read authentication headers from gateway
        String userEmail = request.getHeader("X-Auth-User-Email");
        String userName = request.getHeader("X-Auth-User-Name");
        String userSub = request.getHeader("X-Auth-User-Sub");

        if (userEmail != null && !userEmail.isEmpty()) {
            // Look up user authorities from database
            List<GrantedAuthority> authorities = userAuthorityService.getAuthoritiesForUser(userEmail);
            
            // Create principal object with user details
            GatewayUserPrincipal principal = new GatewayUserPrincipal(userEmail, userName, userSub);
            
            // Create authentication token with authorities
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
            
            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
