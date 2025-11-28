package com.numaansystems.peg.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AzureJwtValidator.
 */
class AzureJwtValidatorTest {

    private AzureJwtValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AzureJwtValidator();
        ReflectionTestUtils.setField(validator, "tenantId", "test-tenant-id");
        ReflectionTestUtils.setField(validator, "clientId", "test-client-id");
        validator.init();
    }

    @Test
    void shouldRejectNullToken() {
        Mono<com.nimbusds.jwt.JWTClaimsSet> result = validator.validateIdToken(null);
        
        StepVerifier.create(result)
            .expectError(IllegalArgumentException.class)
            .verify();
    }

    @Test
    void shouldRejectEmptyToken() {
        Mono<com.nimbusds.jwt.JWTClaimsSet> result = validator.validateIdToken("");
        
        StepVerifier.create(result)
            .expectError(IllegalArgumentException.class)
            .verify();
    }

    @Test
    void shouldRejectBlankToken() {
        Mono<com.nimbusds.jwt.JWTClaimsSet> result = validator.validateIdToken("   ");
        
        StepVerifier.create(result)
            .expectError(IllegalArgumentException.class)
            .verify();
    }

    @Test
    void shouldExtractUserInfoWithEmail() {
        com.nimbusds.jwt.JWTClaimsSet claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
            .subject("user-sub")
            .claim("email", "user@example.com")
            .claim("name", "Test User")
            .build();
        
        AzureJwtValidator.UserInfo userInfo = validator.extractUserInfo(claims);
        
        assertEquals("user-sub", userInfo.getSubject());
        assertEquals("user@example.com", userInfo.getEmail());
        assertEquals("Test User", userInfo.getName());
    }

    @Test
    void shouldExtractUserInfoWithPreferredUsername() {
        com.nimbusds.jwt.JWTClaimsSet claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
            .subject("user-sub")
            .claim("preferred_username", "user@example.com")
            .claim("given_name", "Test")
            .build();
        
        AzureJwtValidator.UserInfo userInfo = validator.extractUserInfo(claims);
        
        assertEquals("user-sub", userInfo.getSubject());
        assertEquals("user@example.com", userInfo.getEmail());
        assertEquals("Test", userInfo.getName());
    }

    @Test
    void shouldExtractUserInfoWithUpn() {
        com.nimbusds.jwt.JWTClaimsSet claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
            .subject("user-sub")
            .claim("upn", "user@domain.local")
            .build();
        
        AzureJwtValidator.UserInfo userInfo = validator.extractUserInfo(claims);
        
        assertEquals("user-sub", userInfo.getSubject());
        assertEquals("user@domain.local", userInfo.getEmail());
        assertNull(userInfo.getName());
    }

    @Test
    void shouldExtractUserInfoWithMissingClaims() {
        com.nimbusds.jwt.JWTClaimsSet claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
            .subject("user-sub")
            .build();
        
        AzureJwtValidator.UserInfo userInfo = validator.extractUserInfo(claims);
        
        assertEquals("user-sub", userInfo.getSubject());
        assertNull(userInfo.getEmail());
        assertNull(userInfo.getName());
    }
}
