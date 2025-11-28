package com.numaansystems.peg.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthFilter.
 */
@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Mock
    private HttpSession session;

    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthFilter();
    }

    @Test
    void doFilter_shouldPassThroughForOAuthPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/oauth/login");
        when(request.getContextPath()).thenReturn("");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void doFilter_shouldPassThroughForStaticAssets() throws Exception {
        when(request.getRequestURI()).thenReturn("/styles/main.css");
        when(request.getContextPath()).thenReturn("");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void doFilter_shouldPassThroughForJavaScript() throws Exception {
        when(request.getRequestURI()).thenReturn("/js/app.js");
        when(request.getContextPath()).thenReturn("");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void doFilter_shouldPassThroughForImages() throws Exception {
        when(request.getRequestURI()).thenReturn("/images/logo.png");
        when(request.getContextPath()).thenReturn("");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void doFilter_shouldPassThroughForActuator() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        when(request.getContextPath()).thenReturn("");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void doFilter_shouldRedirectToLoginWhenNoSession() throws Exception {
        when(request.getRequestURI()).thenReturn("/protected-page");
        when(request.getContextPath()).thenReturn("");
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect(contains("/oauth/login"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_shouldRedirectToLoginWhenNoUser() throws Exception {
        when(request.getRequestURI()).thenReturn("/protected-page");
        when(request.getContextPath()).thenReturn("");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(OAuthCallbackServlet.SESSION_ATTR_USER)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect(contains("/oauth/login"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_shouldAllowRequestWhenUserAuthenticated() throws Exception {
        TokenUtils.UserInfo userInfo = new TokenUtils.UserInfo(
                "subject", "user@example.com", "Test User", "testuser");
        
        when(request.getRequestURI()).thenReturn("/protected-page");
        when(request.getContextPath()).thenReturn("");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(OAuthCallbackServlet.SESSION_ATTR_USER)).thenReturn(userInfo);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
        verify(request).setAttribute(AuthFilter.REQUEST_ATTR_USER, userInfo);
    }

    @Test
    void doFilter_shouldHandleContextPath() throws Exception {
        when(request.getRequestURI()).thenReturn("/app/oauth/login");
        when(request.getContextPath()).thenReturn("/app");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void doFilter_shouldPreserveQueryStringInRedirect() throws Exception {
        when(request.getRequestURI()).thenReturn("/protected-page");
        when(request.getQueryString()).thenReturn("param=value");
        when(request.getContextPath()).thenReturn("");
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect(contains("orig="));
    }
}
