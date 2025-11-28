package com.numaansystems.peg.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Servlet that returns the current authentication status as JSON.
 * 
 * <p>This servlet is useful for GWT or JavaScript applications to check
 * if the user is authenticated without triggering a redirect.
 * 
 * <p>URL pattern: /oauth/status
 * 
 * <p>Response format:
 * <pre>
 * {
 *   "authenticated": true,
 *   "user": {
 *     "subject": "user-subject-id",
 *     "email": "user@example.com",
 *     "name": "User Name",
 *     "preferredUsername": "username"
 *   }
 * }
 * </pre>
 * 
 * <p>If not authenticated:
 * <pre>
 * {
 *   "authenticated": false,
 *   "user": null
 * }
 * </pre>
 */
public class OAuthStatusServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(OAuthStatusServlet.class);
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        
        HttpSession session = request.getSession(false);
        TokenUtils.UserInfo userInfo = null;
        
        if (session != null) {
            userInfo = (TokenUtils.UserInfo) session.getAttribute(OAuthCallbackServlet.SESSION_ATTR_USER);
        }
        
        ObjectNode responseJson = objectMapper.createObjectNode();
        
        if (userInfo != null) {
            responseJson.put("authenticated", true);
            
            ObjectNode userNode = objectMapper.createObjectNode();
            userNode.put("subject", userInfo.getSubject());
            userNode.put("email", userInfo.getEmail());
            userNode.put("name", userInfo.getName());
            userNode.put("preferredUsername", userInfo.getPreferredUsername());
            
            responseJson.set("user", userNode);
            
            logger.debug("Status check: authenticated user {}", userInfo.getEmail());
        } else {
            responseJson.put("authenticated", false);
            responseJson.putNull("user");
            
            logger.debug("Status check: not authenticated");
        }
        
        try (PrintWriter out = response.getWriter()) {
            objectMapper.writeValue(out, responseJson);
        }
    }
}
