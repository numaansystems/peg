package com.numaansystems.peg.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenUtils class.
 * Note: Full integration tests require a valid Azure AD setup.
 */
class TokenUtilsTest {

    private static final String TEST_CLIENT_ID = "test-client-id";
    private static final String TEST_TENANT_ID = "test-tenant-id";
    private static final String TEST_REDIRECT_URI = "https://example.com/callback";
    private static final String TEST_STATE = "random-state-value";
    private static final String TEST_NONCE = "random-nonce-value";

    @Test
    void buildAuthorizationUrl_shouldBuildValidUrl() {
        String url = TokenUtils.buildAuthorizationUrl(
                TEST_CLIENT_ID, 
                TEST_REDIRECT_URI, 
                TEST_TENANT_ID, 
                TEST_STATE, 
                TEST_NONCE
        );

        assertNotNull(url);
        assertTrue(url.startsWith("https://login.microsoftonline.com/" + TEST_TENANT_ID + "/oauth2/v2.0/authorize"));
        assertTrue(url.contains("client_id=" + TEST_CLIENT_ID));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("redirect_uri="));
        assertTrue(url.contains("state=" + TEST_STATE));
        assertTrue(url.contains("nonce=" + TEST_NONCE));
        assertTrue(url.contains("scope=openid"));
    }

    @Test
    void buildAuthorizationUrl_shouldWorkWithoutNonce() {
        String url = TokenUtils.buildAuthorizationUrl(
                TEST_CLIENT_ID, 
                TEST_REDIRECT_URI, 
                TEST_TENANT_ID, 
                TEST_STATE, 
                null
        );

        assertNotNull(url);
        assertTrue(url.contains("state=" + TEST_STATE));
        assertFalse(url.contains("nonce="));
    }

    @Test
    void buildLogoutUrl_shouldBuildValidUrl() {
        String postLogoutUri = "https://example.com/logged-out";
        
        String url = TokenUtils.buildLogoutUrl(TEST_TENANT_ID, postLogoutUri);

        assertNotNull(url);
        assertTrue(url.startsWith("https://login.microsoftonline.com/" + TEST_TENANT_ID + "/oauth2/v2.0/logout"));
        assertTrue(url.contains("post_logout_redirect_uri="));
    }

    @Test
    void buildLogoutUrl_shouldWorkWithoutRedirectUri() {
        String url = TokenUtils.buildLogoutUrl(TEST_TENANT_ID, null);

        assertNotNull(url);
        assertEquals("https://login.microsoftonline.com/" + TEST_TENANT_ID + "/oauth2/v2.0/logout", url);
    }

    @Test
    void tokenResponse_shouldStoreAllFields() {
        TokenUtils.TokenResponse response = new TokenUtils.TokenResponse(
                "access-token",
                "id-token",
                "refresh-token",
                "Bearer",
                3600
        );

        assertEquals("access-token", response.getAccessToken());
        assertEquals("id-token", response.getIdToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600, response.getExpiresIn());
    }

    @Test
    void userInfo_shouldStoreAllFields() {
        TokenUtils.UserInfo userInfo = new TokenUtils.UserInfo(
                "subject-id",
                "user@example.com",
                "Test User",
                "testuser"
        );

        assertEquals("subject-id", userInfo.getSubject());
        assertEquals("user@example.com", userInfo.getEmail());
        assertEquals("Test User", userInfo.getName());
        assertEquals("testuser", userInfo.getPreferredUsername());
    }

    @Test
    void tokenExchangeException_shouldPreserveMessage() {
        String message = "Token exchange failed";
        TokenUtils.TokenExchangeException exception = new TokenUtils.TokenExchangeException(message);
        
        assertEquals(message, exception.getMessage());
    }

    @Test
    void tokenExchangeException_shouldPreserveCause() {
        String message = "Token exchange failed";
        RuntimeException cause = new RuntimeException("Root cause");
        TokenUtils.TokenExchangeException exception = new TokenUtils.TokenExchangeException(message, cause);
        
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void tokenValidationException_shouldPreserveMessage() {
        String message = "Token validation failed";
        TokenUtils.TokenValidationException exception = new TokenUtils.TokenValidationException(message);
        
        assertEquals(message, exception.getMessage());
    }

    @Test
    void tokenValidationException_shouldPreserveCause() {
        String message = "Token validation failed";
        RuntimeException cause = new RuntimeException("Root cause");
        TokenUtils.TokenValidationException exception = new TokenUtils.TokenValidationException(message, cause);
        
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
