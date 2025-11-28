package com.numaansystems.peg.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Controller to generate one-time codes for cross-domain authentication.
 * 
 * After the gateway successfully authenticates the user with Azure AD,
 * this endpoint generates a one-time code mapping to minimal claims
 * and redirects the browser to the application's consume endpoint.
 * 
 * Flow:
 * 1. User authenticates with Azure AD via gateway
 * 2. User visits /auth/create-code endpoint
 * 3. Gateway generates one-time code with user claims
 * 4. Gateway redirects browser to app: https://app.example.org/auth/consume?code=CODE
 * 5. App backend calls /internal/validate-code to exchange code for claims
 */
@Controller
public class CreateCodeController {

    private final CodeStore codeStore;
    private final String appConsumeUrl;

    /**
     * Creates a new CreateCodeController.
     * 
     * @param codeStore the code store for generating codes
     * @param appConsumeUrl the application's consume endpoint URL
     */
    public CreateCodeController(
            CodeStore codeStore,
            @Value("${gateway.code-exchange.app-consume-url:https://app.example.org/auth/consume}") String appConsumeUrl) {
        this.codeStore = codeStore;
        this.appConsumeUrl = appConsumeUrl;
    }

    /**
     * Generates a one-time code and redirects to the application's consume endpoint.
     * 
     * This endpoint requires authentication (handled by security config).
     * 
     * @param oidcUser the authenticated OIDC user from Azure AD
     * @param response the server response for setting redirect
     * @return empty Mono after redirect is set
     */
    @GetMapping("/auth/create-code")
    public Mono<Void> createCode(
            @AuthenticationPrincipal OidcUser oidcUser,
            ServerHttpResponse response) {
        
        if (oidcUser == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        String email = oidcUser.getEmail();
        String name = oidcUser.getName();
        String sub = oidcUser.getSubject();

        String code = codeStore.generateCode(email, name, sub);

        // Construct redirect URL with code parameter
        String redirectUrl = appConsumeUrl + "?code=" + code;
        
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(redirectUrl));
        return response.setComplete();
    }
}
