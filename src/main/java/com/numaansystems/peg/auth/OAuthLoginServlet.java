package com.numaansystems.peg.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Servlet that initiates the OAuth2 Authorization Code flow with Azure AD.
 * 
 * <p>This servlet:
 * <ul>
 *   <li>Generates a secure random state parameter for CSRF protection</li>
 *   <li>Stores the state and original URL in the session</li>
 *   <li>Redirects the user to Azure AD's authorize endpoint</li>
 * </ul>
 * 
 * <p>URL pattern: /oauth/login
 * 
 * <p>Query parameters:
 * <ul>
 *   <li>{@code orig} - Original URL to redirect back to after successful login</li>
 *   <li>{@code popup} - If "true", indicates this is a popup login flow</li>
 * </ul>
 * 
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code AZURE_CLIENT_ID} - Azure AD application client ID</li>
 *   <li>{@code AZURE_TENANT_ID} - Azure AD tenant ID</li>
 *   <li>{@code AZURE_REDIRECT_URI} - OAuth2 redirect URI (callback URL)</li>
 * </ul>
 */
public class OAuthLoginServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(OAuthLoginServlet.class);
    
    /** Session attribute key for storing the OAuth state parameter */
    public static final String SESSION_ATTR_OAUTH_STATE = "oauth_state";
    
    /** Session attribute key for storing the original URL */
    public static final String SESSION_ATTR_ORIGINAL_URL = "oauth_original_url";
    
    /** Session attribute key for indicating popup flow */
    public static final String SESSION_ATTR_IS_POPUP = "oauth_is_popup";
    
    /** Session attribute key for storing the nonce */
    public static final String SESSION_ATTR_NONCE = "oauth_nonce";
    
    private static final SecureRandom secureRandom = new SecureRandom();
    
    private final String clientId;
    private final String tenantId;
    private final String redirectUri;
    
    /**
     * Creates a new OAuthLoginServlet with configuration from environment variables.
     */
    public OAuthLoginServlet() {
        this(
            getRequiredEnv("AZURE_CLIENT_ID"),
            getRequiredEnv("AZURE_TENANT_ID"),
            getRequiredEnv("AZURE_REDIRECT_URI")
        );
    }
    
    /**
     * Creates a new OAuthLoginServlet with explicit configuration.
     * Useful for testing.
     * 
     * @param clientId Azure AD application client ID
     * @param tenantId Azure AD tenant ID
     * @param redirectUri OAuth2 redirect URI
     */
    public OAuthLoginServlet(String clientId, String tenantId, String redirectUri) {
        this.clientId = clientId;
        this.tenantId = tenantId;
        this.redirectUri = redirectUri;
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Get the original URL from the request parameter
        String originalUrl = request.getParameter("orig");
        if (originalUrl == null || originalUrl.isEmpty()) {
            originalUrl = "/";
        }
        
        // Check if this is a popup flow
        boolean isPopup = "true".equalsIgnoreCase(request.getParameter("popup"));
        
        // Generate secure random state for CSRF protection
        String state = generateSecureToken();
        
        // Generate nonce for ID token validation
        String nonce = generateSecureToken();
        
        // Store state and original URL in session
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_ATTR_OAUTH_STATE, state);
        session.setAttribute(SESSION_ATTR_ORIGINAL_URL, originalUrl);
        session.setAttribute(SESSION_ATTR_IS_POPUP, isPopup);
        session.setAttribute(SESSION_ATTR_NONCE, nonce);
        
        logger.debug("Initiating OAuth login for original URL: {}, isPopup: {}", originalUrl, isPopup);
        
        // Build the authorization URL
        String authUrl = TokenUtils.buildAuthorizationUrl(clientId, redirectUri, tenantId, state, nonce);
        
        logger.debug("Redirecting to Azure AD: {}", authUrl);
        
        // Redirect to Azure AD
        response.sendRedirect(authUrl);
    }
    
    /**
     * Generates a cryptographically secure random token.
     * 
     * @return A base64-encoded random token (32 bytes / 256 bits)
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Gets a required environment variable or throws an exception.
     */
    private static String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required environment variable not set: " + name);
        }
        return value;
    }
}
