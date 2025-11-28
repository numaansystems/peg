package com.numaansystems.peg.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot configuration for registering servlet-based OAuth2 components.
 * 
 * <p>This configuration registers:
 * <ul>
 *   <li>{@link OAuthLoginServlet} at /oauth/login</li>
 *   <li>{@link OAuthCallbackServlet} at /oauth/callback</li>
 *   <li>{@link LogoutServlet} at /oauth/logout</li>
 *   <li>{@link OAuthStatusServlet} at /oauth/status</li>
 *   <li>{@link AuthFilter} for application URLs (when enabled)</li>
 * </ul>
 * 
 * <p>The configuration is enabled when the property {@code peg.auth.servlet.enabled=true}
 * is set. This allows the servlet-based OAuth2 flow to coexist with or replace
 * Spring Security OAuth2 depending on the deployment scenario.
 * 
 * <p>Required environment variables when enabled:
 * <ul>
 *   <li>{@code AZURE_CLIENT_ID} - Azure AD application client ID</li>
 *   <li>{@code AZURE_CLIENT_SECRET} - Azure AD application client secret</li>
 *   <li>{@code AZURE_TENANT_ID} - Azure AD tenant ID</li>
 *   <li>{@code AZURE_REDIRECT_URI} - OAuth2 redirect URI (e.g., http://localhost:8080/oauth/callback)</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "peg.auth.servlet.enabled", havingValue = "true")
public class OAuthServletConfig {
    
    /**
     * Registers the OAuth login servlet.
     * 
     * @return Servlet registration for /oauth/login
     */
    @Bean
    public ServletRegistrationBean<OAuthLoginServlet> oauthLoginServlet() {
        ServletRegistrationBean<OAuthLoginServlet> registration = 
                new ServletRegistrationBean<>(new OAuthLoginServlet(), "/oauth/login");
        registration.setName("oauthLoginServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
    
    /**
     * Registers the OAuth callback servlet.
     * 
     * @return Servlet registration for /oauth/callback
     */
    @Bean
    public ServletRegistrationBean<OAuthCallbackServlet> oauthCallbackServlet() {
        ServletRegistrationBean<OAuthCallbackServlet> registration = 
                new ServletRegistrationBean<>(new OAuthCallbackServlet(), "/oauth/callback");
        registration.setName("oauthCallbackServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
    
    /**
     * Registers the logout servlet.
     * 
     * @return Servlet registration for /oauth/logout
     */
    @Bean
    public ServletRegistrationBean<LogoutServlet> oauthLogoutServlet() {
        ServletRegistrationBean<LogoutServlet> registration = 
                new ServletRegistrationBean<>(new LogoutServlet(), "/oauth/logout");
        registration.setName("oauthLogoutServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
    
    /**
     * Registers the status servlet.
     * 
     * <p>This servlet returns the current authentication status as JSON,
     * useful for JavaScript/GWT applications to check authentication
     * without triggering a redirect.
     * 
     * @return Servlet registration for /oauth/status
     */
    @Bean
    public ServletRegistrationBean<OAuthStatusServlet> oauthStatusServlet() {
        ServletRegistrationBean<OAuthStatusServlet> registration = 
                new ServletRegistrationBean<>(new OAuthStatusServlet(), "/oauth/status");
        registration.setName("oauthStatusServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
    
    /**
     * Registers the authentication filter.
     * 
     * <p>The filter is applied to all URLs (/*) but automatically excludes:
     * <ul>
     *   <li>OAuth paths (/oauth/*)</li>
     *   <li>Static assets (*.css, *.js, images, fonts)</li>
     *   <li>Actuator endpoints (/actuator/*)</li>
     * </ul>
     * 
     * @return Filter registration for /*
     */
    @Bean
    public FilterRegistrationBean<AuthFilter> authFilter() {
        FilterRegistrationBean<AuthFilter> registration = 
                new FilterRegistrationBean<>(new AuthFilter());
        registration.addUrlPatterns("/*");
        registration.setName("authFilter");
        registration.setOrder(1);
        return registration;
    }
}
