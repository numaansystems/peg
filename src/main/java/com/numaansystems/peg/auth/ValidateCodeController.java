package com.numaansystems.peg.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Server-to-server endpoint for validating one-time codes.
 * 
 * This endpoint is called by the backend application to exchange
 * a one-time code for user claims. It requires a shared secret
 * for authentication.
 * 
 * Endpoint: POST /internal/validate-code
 * 
 * Request:
 * - Header: Authorization: Bearer <shared-secret>
 * - Body: { "code": "..." }
 * 
 * Response (success):
 * - Status: 200 OK
 * - Body: { "email": "...", "name": "...", "sub": "..." }
 * 
 * Response (invalid code or unauthorized):
 * - Status: 401 Unauthorized
 * - Body: { "error": "..." }
 */
@RestController
public class ValidateCodeController {

    private final CodeStore codeStore;
    private final String sharedSecret;

    /**
     * Request body for code validation.
     */
    public record ValidateCodeRequest(String code) {}

    /**
     * Response body with user claims.
     */
    public record ValidateCodeResponse(String email, String name, String sub) {}

    /**
     * Error response body.
     */
    public record ErrorResponse(String error) {}

    /**
     * Creates a new ValidateCodeController.
     * 
     * @param codeStore the code store for validating codes
     * @param sharedSecret the shared secret for authorization
     */
    public ValidateCodeController(
            CodeStore codeStore,
            @Value("${gateway.code-exchange.shared-secret:}") String sharedSecret) {
        this.codeStore = codeStore;
        this.sharedSecret = sharedSecret;
    }

    /**
     * Validates a one-time code and returns user claims.
     * 
     * @param authHeader the Authorization header with Bearer token
     * @param request the request body containing the code
     * @return the user claims if valid, or an error response
     */
    @PostMapping(
            path = "/internal/validate-code",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> validateCode(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ValidateCodeRequest request) {
        
        // Validate authorization header
        if (!isAuthorized(authHeader)) {
            return Mono.just(ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing authorization")));
        }
        
        // Validate request
        if (request == null || request.code() == null || request.code().isBlank()) {
            return Mono.just(ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Code is required")));
        }
        
        // Consume the code
        CodeStore.Claims claims = codeStore.consumeCode(request.code());
        
        if (claims == null) {
            return Mono.just(ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or expired code")));
        }
        
        return Mono.just(ResponseEntity.ok(
                new ValidateCodeResponse(claims.email(), claims.name(), claims.sub())));
    }

    /**
     * Validates the Authorization header.
     * Expected format: "Bearer <shared-secret>"
     */
    private boolean isAuthorized(String authHeader) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            // If no shared secret is configured, reject all requests
            return false;
        }
        
        if (authHeader == null || authHeader.isBlank()) {
            return false;
        }
        
        if (!authHeader.startsWith("Bearer ")) {
            return false;
        }
        
        String token = authHeader.substring(7);
        return sharedSecret.equals(token);
    }
}
