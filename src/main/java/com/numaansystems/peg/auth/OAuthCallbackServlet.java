package com.numaansystems.peg.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Servlet that handles the OAuth2 callback from Azure AD.
 * 
 * <p>This servlet:
 * <ul>
 *   <li>Receives the authorization code from Azure AD</li>
 *   <li>Validates the state parameter against the session</li>
 *   <li>Exchanges the code for tokens</li>
 *   <li>Validates the ID token (signature, issuer, audience, expiration)</li>
 *   <li>Stores user information in the session</li>
 *   <li>Redirects to the original URL or handles popup flow</li>
 * </ul>
 * 
 * <p>URL pattern: /oauth/callback
 * 
 * <p>Expected query parameters from Azure AD:
 * <ul>
 *   <li>{@code code} - Authorization code</li>
 *   <li>{@code state} - State parameter for CSRF validation</li>
 *   <li>{@code error} - Error code (if authentication failed)</li>
 *   <li>{@code error_description} - Error description (if authentication failed)</li>
 * </ul>
 * 
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code AZURE_CLIENT_ID} - Azure AD application client ID</li>
 *   <li>{@code AZURE_CLIENT_SECRET} - Azure AD application client secret</li>
 *   <li>{@code AZURE_TENANT_ID} - Azure AD tenant ID</li>
 *   <li>{@code AZURE_REDIRECT_URI} - OAuth2 redirect URI (callback URL)</li>
 * </ul>
 */
public class OAuthCallbackServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(OAuthCallbackServlet.class);
    
    /** Session attribute key for the authenticated user */
    public static final String SESSION_ATTR_USER = "authenticated_user";
    
    /** Session attribute key for the refresh token */
    public static final String SESSION_ATTR_REFRESH_TOKEN = "oauth_refresh_token";
    
    /** Session attribute key for the access token */
    public static final String SESSION_ATTR_ACCESS_TOKEN = "oauth_access_token";
    
    /** Session attribute key for the ID token */
    public static final String SESSION_ATTR_ID_TOKEN = "oauth_id_token";
    
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final String redirectUri;
    
    /**
     * Creates a new OAuthCallbackServlet with configuration from environment variables.
     */
    public OAuthCallbackServlet() {
        this(
            getRequiredEnv("AZURE_CLIENT_ID"),
            getRequiredEnv("AZURE_CLIENT_SECRET"),
            getRequiredEnv("AZURE_TENANT_ID"),
            getRequiredEnv("AZURE_REDIRECT_URI")
        );
    }
    
    /**
     * Creates a new OAuthCallbackServlet with explicit configuration.
     * Useful for testing.
     * 
     * @param clientId Azure AD application client ID
     * @param clientSecret Azure AD application client secret
     * @param tenantId Azure AD tenant ID
     * @param redirectUri OAuth2 redirect URI
     */
    public OAuthCallbackServlet(String clientId, String clientSecret, 
                                String tenantId, String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId;
        this.redirectUri = redirectUri;
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Check for error response from Azure AD
        String error = request.getParameter("error");
        if (error != null) {
            String errorDescription = request.getParameter("error_description");
            logger.error("OAuth error from Azure AD: {} - {}", error, errorDescription);
            sendError(response, "Authentication failed: " + error + 
                     (errorDescription != null ? " - " + errorDescription : ""));
            return;
        }
        
        // Get authorization code
        String code = request.getParameter("code");
        if (code == null || code.isEmpty()) {
            logger.error("No authorization code received");
            sendError(response, "No authorization code received");
            return;
        }
        
        // Get state parameter
        String state = request.getParameter("state");
        if (state == null || state.isEmpty()) {
            logger.error("No state parameter received");
            sendError(response, "No state parameter received");
            return;
        }
        
        // Validate state against session
        HttpSession session = request.getSession(false);
        if (session == null) {
            logger.error("No session found");
            sendError(response, "Session expired. Please try again.");
            return;
        }
        
        String storedState = (String) session.getAttribute(OAuthLoginServlet.SESSION_ATTR_OAUTH_STATE);
        if (storedState == null || !storedState.equals(state)) {
            logger.error("State mismatch. Expected: {}, Received: {}", storedState, state);
            sendError(response, "Invalid state parameter. Possible CSRF attack.");
            return;
        }
        
        // Get stored session attributes
        String originalUrl = (String) session.getAttribute(OAuthLoginServlet.SESSION_ATTR_ORIGINAL_URL);
        Boolean isPopup = (Boolean) session.getAttribute(OAuthLoginServlet.SESSION_ATTR_IS_POPUP);
        
        // Clean up OAuth state from session
        session.removeAttribute(OAuthLoginServlet.SESSION_ATTR_OAUTH_STATE);
        session.removeAttribute(OAuthLoginServlet.SESSION_ATTR_ORIGINAL_URL);
        session.removeAttribute(OAuthLoginServlet.SESSION_ATTR_IS_POPUP);
        session.removeAttribute(OAuthLoginServlet.SESSION_ATTR_NONCE);
        
        try {
            // Exchange code for tokens
            logger.debug("Exchanging authorization code for tokens");
            TokenUtils.TokenResponse tokens = TokenUtils.exchangeCodeForTokens(
                code, clientId, clientSecret, redirectUri, tenantId
            );
            
            if (tokens.getIdToken() == null) {
                logger.error("No ID token received from token exchange");
                sendError(response, "No ID token received");
                return;
            }
            
            // Validate ID token
            logger.debug("Validating ID token");
            TokenUtils.UserInfo userInfo = TokenUtils.validateIdToken(
                tokens.getIdToken(), clientId, tenantId
            );
            
            logger.info("Successfully authenticated user: {}", userInfo.getEmail());
            
            // Store user and tokens in session
            session.setAttribute(SESSION_ATTR_USER, userInfo);
            session.setAttribute(SESSION_ATTR_ID_TOKEN, tokens.getIdToken());
            
            if (tokens.getAccessToken() != null) {
                session.setAttribute(SESSION_ATTR_ACCESS_TOKEN, tokens.getAccessToken());
            }
            
            if (tokens.getRefreshToken() != null) {
                session.setAttribute(SESSION_ATTR_REFRESH_TOKEN, tokens.getRefreshToken());
            }
            
            // Handle response based on flow type
            if (Boolean.TRUE.equals(isPopup)) {
                // For popup flow, redirect to popup handler page
                response.sendRedirect(request.getContextPath() + "/oauth/popup.html");
            } else {
                // For regular flow, redirect to original URL
                String redirectUrl = originalUrl != null ? originalUrl : "/";
                response.sendRedirect(redirectUrl);
            }
            
        } catch (TokenUtils.TokenExchangeException e) {
            logger.error("Token exchange failed", e);
            sendError(response, "Failed to exchange authorization code: " + e.getMessage());
        } catch (TokenUtils.TokenValidationException e) {
            logger.error("Token validation failed", e);
            sendError(response, "Token validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Sends an error response as HTML.
     */
    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("text/html;charset=UTF-8");
        
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head><title>Authentication Error</title></head>");
            out.println("<body>");
            out.println("<h1>Authentication Error</h1>");
            out.println("<p>" + escapeHtml(message) + "</p>");
            out.println("<p><a href=\"/\">Return to home</a></p>");
            out.println("</body>");
            out.println("</html>");
        }
    }
    
    /**
     * Escapes HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
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
