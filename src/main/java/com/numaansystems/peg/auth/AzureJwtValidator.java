package com.numaansystems.peg.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Validates Azure AD JWT tokens (ID tokens) from MSAL.js client.
 * Uses Nimbus JOSE+JWT library to validate token signature against Azure JWKS endpoint.
 */
@Component
public class AzureJwtValidator {

    private static final Logger logger = LoggerFactory.getLogger(AzureJwtValidator.class);
    
    private static final Duration JWKS_REFRESH_INTERVAL = Duration.ofHours(24);
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    @Value("${spring.cloud.azure.active-directory.profile.tenant-id:your-tenant-id}")
    private String tenantId;

    @Value("${spring.cloud.azure.active-directory.credential.client-id:your-client-id}")
    private String clientId;

    private final AtomicReference<CachedJWKSet> cachedJwkSet = new AtomicReference<>();
    
    private String jwksUri;
    private String issuer;
    private String issuerV1;

    @PostConstruct
    public void init() {
        // Azure AD v2.0 JWKS endpoint
        this.jwksUri = String.format(
            "https://login.microsoftonline.com/%s/discovery/v2.0/keys", 
            tenantId
        );
        // Azure AD v2.0 issuer
        this.issuer = String.format(
            "https://login.microsoftonline.com/%s/v2.0", 
            tenantId
        );
        // Azure AD v1.0 issuer (for compatibility)
        this.issuerV1 = String.format(
            "https://sts.windows.net/%s/", 
            tenantId
        );
        logger.info("Azure JWT Validator initialized for tenant: {}", tenantId);
    }

    /**
     * Validates an ID token from MSAL.js client.
     * 
     * @param idToken The raw ID token string
     * @return Mono containing validated claims or error
     */
    public Mono<JWTClaimsSet> validateIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return Mono.error(new IllegalArgumentException("ID token cannot be null or empty"));
        }

        return getOrRefreshJwkSet()
            .flatMap(jwkSet -> validateTokenWithJwkSet(idToken, jwkSet))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<JWKSet> getOrRefreshJwkSet() {
        CachedJWKSet cached = cachedJwkSet.get();
        
        if (cached != null && !cached.isExpired()) {
            return Mono.just(cached.jwkSet);
        }
        
        return fetchJwkSet();
    }

    private Mono<JWKSet> fetchJwkSet() {
        return Mono.fromCallable(() -> {
            logger.debug("Fetching JWKS from: {}", jwksUri);
            URL url = URI.create(jwksUri).toURL();
            JWKSet jwkSet = JWKSet.load(url);
            cachedJwkSet.set(new CachedJWKSet(jwkSet, Instant.now().plus(JWKS_REFRESH_INTERVAL)));
            logger.debug("JWKS fetched successfully, {} keys loaded", jwkSet.getKeys().size());
            return jwkSet;
        }).onErrorMap(IOException.class, e -> {
            logger.error("Failed to fetch JWKS from Azure AD", e);
            return new TokenValidationException("Failed to fetch signing keys from Azure AD", e);
        }).onErrorMap(ParseException.class, e -> {
            logger.error("Failed to parse JWKS response", e);
            return new TokenValidationException("Failed to parse signing keys", e);
        });
    }

    private Mono<JWTClaimsSet> validateTokenWithJwkSet(String idToken, JWKSet jwkSet) {
        return Mono.fromCallable(() -> {
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            
            // Configure JWT processor with JWKS
            JWKSource<SecurityContext> keySource = new ImmutableJWKSet<>(jwkSet);
            JWSKeySelector<SecurityContext> keySelector = 
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
            jwtProcessor.setJWSKeySelector(keySelector);
            
            // Configure claims verification
            jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                // Required claims that must be present
                new JWTClaimsSet.Builder().build(),
                new HashSet<>(Arrays.asList("sub", "iss", "aud", "exp", "iat"))
            ));
            
            // Process and validate the token
            JWTClaimsSet claims = jwtProcessor.process(idToken, null);
            
            // Additional claim validations
            validateClaims(claims);
            
            logger.debug("Token validated successfully for subject: {}", claims.getSubject());
            return claims;
        }).onErrorMap(BadJOSEException.class, e -> {
            logger.warn("Token signature validation failed: {}", e.getMessage());
            return new TokenValidationException("Invalid token signature", e);
        }).onErrorMap(JOSEException.class, e -> {
            logger.warn("Token processing failed: {}", e.getMessage());
            return new TokenValidationException("Token processing error", e);
        }).onErrorMap(ParseException.class, e -> {
            logger.warn("Token parsing failed: {}", e.getMessage());
            return new TokenValidationException("Invalid token format", e);
        });
    }

    private void validateClaims(JWTClaimsSet claims) throws TokenValidationException {
        // Validate issuer (accept both v1 and v2 format)
        String tokenIssuer = claims.getIssuer();
        if (!issuer.equals(tokenIssuer) && !issuerV1.equals(tokenIssuer)) {
            throw new TokenValidationException(
                String.format("Invalid issuer: expected '%s' or '%s', got '%s'", 
                    issuer, issuerV1, tokenIssuer));
        }
        
        // Validate audience (must be our client ID)
        if (claims.getAudience() == null || !claims.getAudience().contains(clientId)) {
            throw new TokenValidationException(
                String.format("Invalid audience: expected '%s', got '%s'", 
                    clientId, claims.getAudience()));
        }
        
        // Validate expiration with clock skew tolerance
        if (claims.getExpirationTime() == null) {
            throw new TokenValidationException("Token missing expiration time");
        }
        Instant now = Instant.now();
        Instant expiration = claims.getExpirationTime().toInstant();
        if (now.isAfter(expiration.plus(CLOCK_SKEW))) {
            throw new TokenValidationException("Token has expired");
        }
        
        // Validate not-before time if present
        if (claims.getNotBeforeTime() != null) {
            Instant notBefore = claims.getNotBeforeTime().toInstant();
            if (now.isBefore(notBefore.minus(CLOCK_SKEW))) {
                throw new TokenValidationException("Token is not yet valid");
            }
        }
    }

    /**
     * Extracts user information from validated claims.
     */
    public UserInfo extractUserInfo(JWTClaimsSet claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new TokenValidationException("Token missing subject claim");
        }
        
        String email = null;
        String name = null;
        
        // Try to get email from various claims
        Object emailClaim = claims.getClaim("email");
        if (emailClaim == null) {
            emailClaim = claims.getClaim("preferred_username");
        }
        if (emailClaim == null) {
            emailClaim = claims.getClaim("upn");
        }
        if (emailClaim != null) {
            email = emailClaim.toString();
        }
        
        // Try to get name from various claims
        Object nameClaim = claims.getClaim("name");
        if (nameClaim == null) {
            nameClaim = claims.getClaim("given_name");
        }
        if (nameClaim != null) {
            name = nameClaim.toString();
        }
        
        return new UserInfo(
            subject,
            email,
            name
        );
    }

    /**
     * Cached JWKS with expiration tracking.
     */
    private static class CachedJWKSet {
        private final JWKSet jwkSet;
        private final Instant expiresAt;
        
        CachedJWKSet(JWKSet jwkSet, Instant expiresAt) {
            this.jwkSet = jwkSet;
            this.expiresAt = expiresAt;
        }
        
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * User information extracted from validated token.
     */
    public static class UserInfo {
        private final String subject;
        private final String email;
        private final String name;
        
        public UserInfo(String subject, String email, String name) {
            this.subject = subject;
            this.email = email;
            this.name = name;
        }
        
        public String getSubject() { return subject; }
        public String getEmail() { return email; }
        public String getName() { return name; }
    }
}
