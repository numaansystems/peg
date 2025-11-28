package com.numaansystems.peg.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler for creating sessions from MSAL.js ID tokens.
 * Validates the ID token and creates an HttpOnly Secure session cookie.
 * 
 * Endpoint: POST /oauth/session/create
 */
@Component
public class SessionCreateHandler {

    private static final Logger logger = LoggerFactory.getLogger(SessionCreateHandler.class);
    
    private static final String SESSION_COOKIE_NAME = "MSAL_SESSION";
    private static final Duration SESSION_DURATION = Duration.ofHours(8);
    
    private final AzureJwtValidator jwtValidator;
    private final ObjectMapper objectMapper;
    
    /**
     * In-memory session store.
     * 
     * NOTE: This in-memory storage is suitable for single-instance deployments
     * and development/testing. For production deployments with multiple gateway
     * instances, implement a SessionStore interface backed by Redis or database.
     * 
     * To migrate to Redis:
     * 1. Create a SessionStore interface with get/put/remove methods
     * 2. Create InMemorySessionStore (current implementation) and RedisSessionStore
     * 3. Use @ConditionalOnProperty to select implementation based on config
     * 
     * The gateway already supports Redis sessions via Spring Session for OAuth2
     * authentication. This MSAL session store could be integrated with the same
     * Redis configuration.
     */
    private final ConcurrentHashMap<String, SessionData> sessions = new ConcurrentHashMap<>();

    @Value("${server.servlet.context-path:/gateway}")
    private String contextPath;

    @Value("${msal.session.secure:true}")
    private boolean secureCookie;

    public SessionCreateHandler(AzureJwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a session from a validated ID token.
     * 
     * Expected request body:
     * {
     *   "id_token": "eyJ..."
     * }
     * 
     * Response on success:
     * {
     *   "success": true,
     *   "user": {
     *     "subject": "...",
     *     "email": "...",
     *     "name": "..."
     *   }
     * }
     * 
     * Sets an HttpOnly Secure cookie for session management.
     */
    public Mono<ServerResponse> createSession(ServerRequest request) {
        return request.bodyToMono(String.class)
            .flatMap(this::parseRequestBody)
            .flatMap(idToken -> jwtValidator.validateIdToken(idToken)
                .map(claims -> {
                    AzureJwtValidator.UserInfo userInfo = jwtValidator.extractUserInfo(claims);
                    String sessionId = createSessionId();
                    
                    // Store session data
                    SessionData sessionData = new SessionData(
                        userInfo.getSubject(),
                        userInfo.getEmail(),
                        userInfo.getName(),
                        System.currentTimeMillis() + SESSION_DURATION.toMillis()
                    );
                    sessions.put(sessionId, sessionData);
                    
                    logger.info("Session created for user: {} ({})", 
                        userInfo.getEmail(), userInfo.getSubject());
                    
                    return new SessionResult(sessionId, userInfo);
                })
            )
            .flatMap(this::buildSuccessResponse)
            .onErrorResume(TokenValidationException.class, this::handleValidationError)
            .onErrorResume(Exception.class, this::handleGenericError);
    }

    private Mono<String> parseRequestBody(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            JsonNode idTokenNode = json.get("id_token");
            if (idTokenNode == null || idTokenNode.isNull() || idTokenNode.asText().isBlank()) {
                return Mono.error(new TokenValidationException("id_token is required"));
            }
            return Mono.just(idTokenNode.asText());
        } catch (Exception e) {
            return Mono.error(new TokenValidationException("Invalid request body format"));
        }
    }

    private String createSessionId() {
        return UUID.randomUUID().toString();
    }

    private Mono<ServerResponse> buildSuccessResponse(SessionResult result) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        
        Map<String, String> userMap = new HashMap<>();
        userMap.put("subject", result.userInfo.getSubject());
        if (result.userInfo.getEmail() != null) {
            userMap.put("email", result.userInfo.getEmail());
        }
        if (result.userInfo.getName() != null) {
            userMap.put("name", result.userInfo.getName());
        }
        response.put("user", userMap);
        
        ResponseCookie sessionCookie = ResponseCookie.from(SESSION_COOKIE_NAME, result.sessionId)
            .httpOnly(true)
            .secure(secureCookie)
            .path(contextPath)
            .maxAge(SESSION_DURATION)
            .sameSite("Lax")
            .build();
        
        return ServerResponse.ok()
            .cookie(sessionCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(response);
    }

    private Mono<ServerResponse> handleValidationError(TokenValidationException e) {
        logger.warn("Token validation failed: {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "Token validation failed");
        response.put("message", e.getMessage());
        
        return ServerResponse.status(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(response);
    }

    private Mono<ServerResponse> handleGenericError(Throwable e) {
        logger.error("Unexpected error during session creation", e);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "Internal server error");
        
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(response);
    }

    /**
     * Validates an existing session from cookie.
     * Returns the session data if valid, null otherwise.
     */
    public SessionData validateSession(ServerRequest request) {
        HttpCookie cookie = request.cookies()
            .getFirst(SESSION_COOKIE_NAME);
        
        if (cookie == null) {
            return null;
        }
        
        String sessionId = cookie.getValue();
        SessionData session = sessions.get(sessionId);
        
        if (session == null) {
            return null;
        }
        
        // Check expiration
        if (System.currentTimeMillis() > session.expiresAt()) {
            sessions.remove(sessionId);
            return null;
        }
        
        return session;
    }

    /**
     * Invalidates a session (logout).
     */
    public Mono<ServerResponse> destroySession(ServerRequest request) {
        HttpCookie cookie = request.cookies()
            .getFirst(SESSION_COOKIE_NAME);
        
        if (cookie != null) {
            sessions.remove(cookie.getValue());
            logger.info("Session destroyed: {}", cookie.getValue());
        }
        
        ResponseCookie clearCookie = ResponseCookie.from(SESSION_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secureCookie)
            .path(contextPath)
            .maxAge(0)
            .build();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Session destroyed");
        
        return ServerResponse.ok()
            .cookie(clearCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(response);
    }

    /**
     * Gets current session info.
     */
    public Mono<ServerResponse> getSessionInfo(ServerRequest request) {
        SessionData session = validateSession(request);
        
        if (session == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", false);
            
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(response);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        
        Map<String, String> userMap = new HashMap<>();
        userMap.put("subject", session.subject());
        if (session.email() != null) {
            userMap.put("email", session.email());
        }
        if (session.name() != null) {
            userMap.put("name", session.name());
        }
        response.put("user", userMap);
        
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(response);
    }

    /**
     * Session data stored server-side.
     */
    public record SessionData(
        String subject,
        String email,
        String name,
        long expiresAt
    ) {}

    private record SessionResult(
        String sessionId,
        AzureJwtValidator.UserInfo userInfo
    ) {}
}
