package com.numaansystems.peg.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Servlet that handles user logout.
 * 
 * <p>This servlet:
 * <ul>
 *   <li>Invalidates the user's session</li>
 *   <li>Optionally redirects to Azure AD logout endpoint</li>
 *   <li>Redirects to the specified post-logout URL</li>
 * </ul>
 * 
 * <p>URL pattern: /oauth/logout
 * 
 * <p>Query parameters:
 * <ul>
 *   <li>{@code redirect} - URL to redirect to after logout (default: /)</li>
 *   <li>{@code azure_logout} - If "true", also logout from Azure AD</li>
 * </ul>
 * 
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code AZURE_TENANT_ID} - Azure AD tenant ID (required for Azure logout)</li>
 * </ul>
 */
public class LogoutServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(LogoutServlet.class);
    
    private final String tenantId;
    
    /**
     * Creates a new LogoutServlet with configuration from environment variables.
     */
    public LogoutServlet() {
        this(System.getenv("AZURE_TENANT_ID"));
    }
    
    /**
     * Creates a new LogoutServlet with explicit configuration.
     * Useful for testing.
     * 
     * @param tenantId Azure AD tenant ID
     */
    public LogoutServlet(String tenantId) {
        this.tenantId = tenantId;
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Get redirect URL parameter
        String redirectUrl = request.getParameter("redirect");
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            redirectUrl = "/";
        }
        
        // Check if Azure logout is requested
        boolean azureLogout = "true".equalsIgnoreCase(request.getParameter("azure_logout"));
        
        // Invalidate session
        HttpSession session = request.getSession(false);
        if (session != null) {
            String userEmail = null;
            TokenUtils.UserInfo userInfo = 
                    (TokenUtils.UserInfo) session.getAttribute(OAuthCallbackServlet.SESSION_ATTR_USER);
            if (userInfo != null) {
                userEmail = userInfo.getEmail();
            }
            
            logger.info("Logging out user: {}", userEmail);
            session.invalidate();
        }
        
        // Redirect to Azure logout or specified URL
        if (azureLogout && tenantId != null && !tenantId.isEmpty()) {
            // Build full redirect URL for post-logout redirect
            String fullRedirectUrl = buildFullUrl(request, redirectUrl);
            String logoutUrl = TokenUtils.buildLogoutUrl(tenantId, fullRedirectUrl);
            logger.debug("Redirecting to Azure logout: {}", logoutUrl);
            response.sendRedirect(logoutUrl);
        } else {
            logger.debug("Redirecting to: {}", redirectUrl);
            response.sendRedirect(redirectUrl);
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // Support both GET and POST for logout
        doGet(request, response);
    }
    
    /**
     * Builds a full URL from a relative path.
     */
    private String buildFullUrl(HttpServletRequest request, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        
        StringBuilder url = new StringBuilder();
        url.append(request.getScheme())
           .append("://")
           .append(request.getServerName());
        
        int port = request.getServerPort();
        if ((request.getScheme().equals("http") && port != 80) ||
            (request.getScheme().equals("https") && port != 443)) {
            url.append(":").append(port);
        }
        
        if (!path.startsWith("/")) {
            url.append("/");
        }
        url.append(path);
        
        return url.toString();
    }
}
