package com.numaansystems.peg.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Gateway filter to add authentication headers to backend requests.
 * This filter extracts user information from the OAuth2 token and
 * adds custom headers that backend applications can use.
 */
@Component
public class AuthenticationHeaderFilter extends AbstractGatewayFilterFactory<AuthenticationHeaderFilter.Config> {

    public AuthenticationHeaderFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .flatMap(authentication -> {
                    if (authentication instanceof OAuth2AuthenticationToken) {
                        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
                        Object principal = oauth2Token.getPrincipal();
                        
                        if (principal instanceof OidcUser) {
                            OidcUser oidcUser = (OidcUser) principal;
                            
                            // Build mutated request with user information headers
                            var requestBuilder = exchange.getRequest().mutate();
                            
                            // Add headers with null checks
                            String userName = oidcUser.getName();
                            if (userName != null) {
                                requestBuilder.header("X-Auth-User-Name", userName);
                            }
                            
                            String userEmail = oidcUser.getEmail();
                            if (userEmail != null) {
                                requestBuilder.header("X-Auth-User-Email", userEmail);
                            }
                            
                            String userSub = oidcUser.getSubject();
                            if (userSub != null) {
                                requestBuilder.header("X-Auth-User-Sub", userSub);
                            }
                            
                            // Optionally add the ID token for backend validation
                            if (config.isIncludeIdToken() && oidcUser.getIdToken() != null) {
                                String idToken = oidcUser.getIdToken().getTokenValue();
                                if (idToken != null) {
                                    requestBuilder.header("X-Auth-ID-Token", idToken);
                                }
                            }
                            
                            // Create new exchange with mutated request
                            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                        }
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    public static class Config {
        private boolean includeIdToken = false;

        public boolean isIncludeIdToken() {
            return includeIdToken;
        }

        public void setIncludeIdToken(boolean includeIdToken) {
            this.includeIdToken = includeIdToken;
        }
    }
}
