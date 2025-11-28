package com.numaansystems.peg.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Servlet filter that protects application URLs by requiring authentication.
 * 
 * <p>This filter:
 * <ul>
 *   <li>Checks for an authenticated user in the session</li>
 *   <li>Redirects unauthenticated requests to the OAuth login page</li>
 *   <li>Skips authentication checks for static assets and OAuth paths</li>
 *   <li>Adds user information to request attributes for downstream processing</li>
 * </ul>
 * 
 * <p>The filter can be configured with:
 * <ul>
 *   <li>{@code excludePatterns} - Comma-separated list of path patterns to exclude</li>
 *   <li>{@code loginPath} - Path to the OAuth login servlet (default: /oauth/login)</li>
 * </ul>
 * 
 * <p>By default, the following paths are excluded:
 * <ul>
 *   <li>/oauth/* - OAuth flow paths</li>
 *   <li>*.css, *.js, *.png, *.jpg, *.gif, *.ico, *.svg, *.woff, *.woff2 - Static assets</li>
 *   <li>/actuator/* - Health check endpoints</li>
 * </ul>
 */
public class AuthFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);
    
    /** Request attribute key for the authenticated user */
    public static final String REQUEST_ATTR_USER = "authenticated_user";
    
    /** Default login path */
    private static final String DEFAULT_LOGIN_PATH = "/oauth/login";
    
    /** Default excluded path prefixes */
    private static final Set<String> DEFAULT_EXCLUDED_PREFIXES = new HashSet<>(Arrays.asList(
        "/oauth/",
        "/actuator/",
        "/health"
    ));
    
    /** Default excluded file extensions for static assets */
    private static final Set<String> STATIC_ASSET_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
        ".woff", ".woff2", ".ttf", ".eot", ".map"
    ));
    
    private String loginPath = DEFAULT_LOGIN_PATH;
    private Set<String> excludedPrefixes = new HashSet<>(DEFAULT_EXCLUDED_PREFIXES);
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Get custom login path if configured
        String configuredLoginPath = filterConfig.getInitParameter("loginPath");
        if (configuredLoginPath != null && !configuredLoginPath.isEmpty()) {
            this.loginPath = configuredLoginPath;
        }
        
        // Get additional exclude patterns if configured
        String excludePatterns = filterConfig.getInitParameter("excludePatterns");
        if (excludePatterns != null && !excludePatterns.isEmpty()) {
            for (String pattern : excludePatterns.split(",")) {
                excludedPrefixes.add(pattern.trim());
            }
        }
        
        logger.info("AuthFilter initialized with loginPath: {}, excludedPrefixes: {}", 
                   loginPath, excludedPrefixes);
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String requestPath = httpRequest.getRequestURI();
        String contextPath = httpRequest.getContextPath();
        
        // Remove context path from request path for matching
        String pathToMatch = requestPath;
        if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
            pathToMatch = requestPath.substring(contextPath.length());
        }
        
        // Check if path should be excluded from authentication
        if (isExcludedPath(pathToMatch)) {
            logger.trace("Skipping authentication for excluded path: {}", pathToMatch);
            chain.doFilter(request, response);
            return;
        }
        
        // Check for authenticated user in session
        HttpSession session = httpRequest.getSession(false);
        TokenUtils.UserInfo userInfo = null;
        
        if (session != null) {
            userInfo = (TokenUtils.UserInfo) session.getAttribute(OAuthCallbackServlet.SESSION_ATTR_USER);
        }
        
        if (userInfo == null) {
            // User not authenticated - redirect to login
            logger.debug("Unauthenticated request to {}, redirecting to login", requestPath);
            
            // Build the original URL to return to after login
            String originalUrl = buildOriginalUrl(httpRequest);
            String redirectUrl = contextPath + loginPath + "?orig=" + 
                    URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
            
            httpResponse.sendRedirect(redirectUrl);
            return;
        }
        
        // User is authenticated - add user info to request and continue
        logger.trace("Authenticated request from user: {}", userInfo.getEmail());
        httpRequest.setAttribute(REQUEST_ATTR_USER, userInfo);
        
        chain.doFilter(request, response);
    }
    
    @Override
    public void destroy() {
        // No cleanup needed
    }
    
    /**
     * Checks if a path should be excluded from authentication.
     * 
     * @param path The request path (without context path)
     * @return true if the path should be excluded
     */
    private boolean isExcludedPath(String path) {
        // Check excluded prefixes
        for (String prefix : excludedPrefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        
        // Check static asset extensions
        String lowerPath = path.toLowerCase();
        for (String extension : STATIC_ASSET_EXTENSIONS) {
            if (lowerPath.endsWith(extension)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Builds the original URL including query string.
     * 
     * @param request The HTTP request
     * @return The full original URL
     */
    private String buildOriginalUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder(request.getRequestURI());
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            url.append("?").append(queryString);
        }
        return url.toString();
    }
}
