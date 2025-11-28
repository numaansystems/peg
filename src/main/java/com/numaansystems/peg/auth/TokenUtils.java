package com.numaansystems.peg.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for OAuth2 token exchange and JWT validation.
 * Uses Apache HttpClient for HTTP operations and Nimbus JOSE+JWT for token validation.
 * 
 * <p>This class provides:
 * <ul>
 *   <li>Token exchange with Azure AD token endpoint</li>
 *   <li>ID token signature validation using Azure JWKS</li>
 *   <li>Token claims validation (issuer, audience, expiration)</li>
 * </ul>
 */
public final class TokenUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(TokenUtils.class);
    
    /** Azure AD v2.0 OIDC discovery URL template */
    private static final String OIDC_DISCOVERY_URL_TEMPLATE = 
            "https://login.microsoftonline.com/%s/v2.0/.well-known/openid-configuration";
    
    /** Azure AD token endpoint URL template */
    private static final String TOKEN_ENDPOINT_TEMPLATE = 
            "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    
    /** Azure AD v2.0 JWKS URL template */
    private static final String JWKS_URL_TEMPLATE = 
            "https://login.microsoftonline.com/%s/discovery/v2.0/keys";
    
    /** Cache for JWK sets to avoid repeated fetches */
    private static final Map<String, CachedJwkSet> jwkCache = new ConcurrentHashMap<>();
    
    /** JWK set cache TTL in milliseconds (1 hour) */
    private static final long JWK_CACHE_TTL_MS = 3600000L;
    
    /** HTTP client for JWKS fetching */
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private TokenUtils() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Represents the result of a token exchange operation.
     */
    public static class TokenResponse {
        private final String accessToken;
        private final String idToken;
        private final String refreshToken;
        private final String tokenType;
        private final long expiresIn;
        
        public TokenResponse(String accessToken, String idToken, String refreshToken, 
                           String tokenType, long expiresIn) {
            this.accessToken = accessToken;
            this.idToken = idToken;
            this.refreshToken = refreshToken;
            this.tokenType = tokenType;
            this.expiresIn = expiresIn;
        }
        
        public String getAccessToken() { return accessToken; }
        public String getIdToken() { return idToken; }
        public String getRefreshToken() { return refreshToken; }
        public String getTokenType() { return tokenType; }
        public long getExpiresIn() { return expiresIn; }
    }
    
    /**
     * Represents validated user information from the ID token.
     */
    public static class UserInfo {
        private final String subject;
        private final String email;
        private final String name;
        private final String preferredUsername;
        
        public UserInfo(String subject, String email, String name, String preferredUsername) {
            this.subject = subject;
            this.email = email;
            this.name = name;
            this.preferredUsername = preferredUsername;
        }
        
        public String getSubject() { return subject; }
        public String getEmail() { return email; }
        public String getName() { return name; }
        public String getPreferredUsername() { return preferredUsername; }
    }
    
    /**
     * Exchanges an authorization code for tokens.
     * 
     * @param code The authorization code received from Azure AD
     * @param clientId The Azure AD application client ID
     * @param clientSecret The Azure AD application client secret
     * @param redirectUri The redirect URI used in the authorization request
     * @param tenantId The Azure AD tenant ID
     * @return TokenResponse containing access token, ID token, and refresh token
     * @throws IOException if the HTTP request fails
     * @throws TokenExchangeException if the token exchange fails
     */
    public static TokenResponse exchangeCodeForTokens(String code, String clientId, 
            String clientSecret, String redirectUri, String tenantId) 
            throws IOException, TokenExchangeException {
        
        String tokenEndpoint = String.format(TOKEN_ENDPOINT_TEMPLATE, tenantId);
        
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", clientId));
        params.add(new BasicNameValuePair("client_secret", clientSecret));
        params.add(new BasicNameValuePair("code", code));
        params.add(new BasicNameValuePair("redirect_uri", redirectUri));
        params.add(new BasicNameValuePair("grant_type", "authorization_code"));
        params.add(new BasicNameValuePair("scope", "openid profile email"));
        
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(tokenEndpoint);
            post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            
            try (var response = client.execute(post)) {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                
                if (statusCode != 200) {
                    logger.error("Token exchange failed with status {}: {}", statusCode, responseBody);
                    throw new TokenExchangeException("Token exchange failed: " + responseBody);
                }
                
                JsonNode json = objectMapper.readTree(responseBody);
                
                return new TokenResponse(
                        getTextOrNull(json, "access_token"),
                        getTextOrNull(json, "id_token"),
                        getTextOrNull(json, "refresh_token"),
                        getTextOrNull(json, "token_type"),
                        json.has("expires_in") ? json.get("expires_in").asLong() : 0
                );
            } catch (org.apache.hc.core5.http.ParseException e) {
                throw new TokenExchangeException("Failed to parse HTTP response", e);
            }
        }
    }
    
    /**
     * Validates an ID token and extracts user information.
     * 
     * <p>Validation includes:
     * <ul>
     *   <li>Signature verification using Azure AD JWKS</li>
     *   <li>Issuer validation</li>
     *   <li>Audience validation</li>
     *   <li>Expiration check</li>
     *   <li>Not-before check</li>
     * </ul>
     * 
     * @param idToken The ID token to validate
     * @param clientId The expected audience (client ID)
     * @param tenantId The Azure AD tenant ID
     * @return UserInfo containing validated user claims
     * @throws TokenValidationException if validation fails
     */
    public static UserInfo validateIdToken(String idToken, String clientId, String tenantId) 
            throws TokenValidationException {
        
        try {
            // Get JWKS from Azure AD (with caching)
            JWKSet jwkSet = getJwkSet(tenantId);
            
            // Configure the JWT processor
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            
            JWKSource<SecurityContext> keySource = new ImmutableJWKSet<>(jwkSet);
            JWSKeySelector<SecurityContext> keySelector = 
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
            
            jwtProcessor.setJWSKeySelector(keySelector);
            
            // Process the token (validates signature)
            JWTClaimsSet claims = jwtProcessor.process(idToken, null);
            
            // Validate issuer
            String expectedIssuer = String.format("https://login.microsoftonline.com/%s/v2.0", tenantId);
            String actualIssuer = claims.getIssuer();
            if (!expectedIssuer.equals(actualIssuer)) {
                throw new TokenValidationException("Invalid issuer: " + actualIssuer);
            }
            
            // Validate audience
            List<String> audiences = claims.getAudience();
            if (audiences == null || !audiences.contains(clientId)) {
                throw new TokenValidationException("Invalid audience: " + audiences);
            }
            
            // Validate expiration
            Date expirationTime = claims.getExpirationTime();
            if (expirationTime == null || expirationTime.before(new Date())) {
                throw new TokenValidationException("Token has expired");
            }
            
            // Validate not-before (nbf) if present
            Date notBefore = claims.getNotBeforeTime();
            if (notBefore != null && notBefore.after(new Date())) {
                throw new TokenValidationException("Token not yet valid");
            }
            
            // Extract user information
            return new UserInfo(
                    claims.getSubject(),
                    claims.getStringClaim("email"),
                    claims.getStringClaim("name"),
                    claims.getStringClaim("preferred_username")
            );
            
        } catch (ParseException | JOSEException | BadJOSEException e) {
            throw new TokenValidationException("Token validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the JWKS for a tenant, using cache when available.
     */
    private static JWKSet getJwkSet(String tenantId) throws TokenValidationException {
        CachedJwkSet cached = jwkCache.get(tenantId);
        
        if (cached != null && !cached.isExpired()) {
            return cached.jwkSet;
        }
        
        try {
            String jwksUrl = String.format(JWKS_URL_TEMPLATE, tenantId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new TokenValidationException("Failed to fetch JWKS: " + response.statusCode());
            }
            
            JWKSet jwkSet = JWKSet.parse(response.body());
            jwkCache.put(tenantId, new CachedJwkSet(jwkSet));
            
            return jwkSet;
            
        } catch (IOException | InterruptedException | ParseException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TokenValidationException("Failed to fetch JWKS: " + e.getMessage(), e);
        }
    }
    
    /**
     * Builds the Azure AD authorization URL.
     * 
     * @param clientId The Azure AD application client ID
     * @param redirectUri The redirect URI for the callback
     * @param tenantId The Azure AD tenant ID
     * @param state The state parameter to prevent CSRF
     * @param nonce Optional nonce for ID token validation
     * @return The complete authorization URL
     */
    public static String buildAuthorizationUrl(String clientId, String redirectUri, 
            String tenantId, String state, String nonce) {
        
        StringBuilder url = new StringBuilder();
        url.append("https://login.microsoftonline.com/")
           .append(tenantId)
           .append("/oauth2/v2.0/authorize?");
        
        url.append("client_id=").append(encode(clientId));
        url.append("&response_type=code");
        url.append("&redirect_uri=").append(encode(redirectUri));
        url.append("&response_mode=query");
        url.append("&scope=").append(encode("openid profile email"));
        url.append("&state=").append(encode(state));
        
        if (nonce != null) {
            url.append("&nonce=").append(encode(nonce));
        }
        
        return url.toString();
    }
    
    /**
     * Builds the Azure AD logout URL.
     * 
     * @param tenantId The Azure AD tenant ID
     * @param postLogoutRedirectUri Optional URI to redirect after logout
     * @return The complete logout URL
     */
    public static String buildLogoutUrl(String tenantId, String postLogoutRedirectUri) {
        StringBuilder url = new StringBuilder();
        url.append("https://login.microsoftonline.com/")
           .append(tenantId)
           .append("/oauth2/v2.0/logout");
        
        if (postLogoutRedirectUri != null) {
            url.append("?post_logout_redirect_uri=").append(encode(postLogoutRedirectUri));
        }
        
        return url.toString();
    }
    
    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    
    private static String getTextOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : null;
    }
    
    /**
     * Cache wrapper for JWK sets with expiration.
     */
    private static class CachedJwkSet {
        final JWKSet jwkSet;
        final long expiresAt;
        
        CachedJwkSet(JWKSet jwkSet) {
            this.jwkSet = jwkSet;
            this.expiresAt = System.currentTimeMillis() + JWK_CACHE_TTL_MS;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
    
    /**
     * Exception thrown when token exchange fails.
     */
    public static class TokenExchangeException extends Exception {
        public TokenExchangeException(String message) {
            super(message);
        }
        
        public TokenExchangeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception thrown when token validation fails.
     */
    public static class TokenValidationException extends Exception {
        public TokenValidationException(String message) {
            super(message);
        }
        
        public TokenValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
