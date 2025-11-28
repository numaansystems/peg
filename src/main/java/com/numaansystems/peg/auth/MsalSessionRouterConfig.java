package com.numaansystems.peg.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

/**
 * Router configuration for MSAL.js session endpoints.
 * 
 * Endpoints:
 * - POST /oauth/session/create - Create session from ID token
 * - POST /oauth/session/destroy - Destroy current session (logout)
 * - GET /oauth/session/info - Get current session info
 */
@Configuration
public class MsalSessionRouterConfig {

    private final SessionCreateHandler sessionHandler;

    public MsalSessionRouterConfig(SessionCreateHandler sessionHandler) {
        this.sessionHandler = sessionHandler;
    }

    @Bean
    public RouterFunction<ServerResponse> msalSessionRoutes() {
        return RouterFunctions
            .route(POST("/oauth/session/create"), sessionHandler::createSession)
            .andRoute(POST("/oauth/session/destroy"), sessionHandler::destroySession)
            .andRoute(GET("/oauth/session/info"), sessionHandler::getSessionInfo);
    }
}
