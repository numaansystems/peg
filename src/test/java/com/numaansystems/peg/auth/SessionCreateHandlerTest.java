package com.numaansystems.peg.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SessionCreateHandler.
 */
@ExtendWith(MockitoExtension.class)
class SessionCreateHandlerTest {

    @Mock
    private AzureJwtValidator jwtValidator;

    @Mock
    private ServerRequest serverRequest;

    private SessionCreateHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SessionCreateHandler(jwtValidator);
        ReflectionTestUtils.setField(handler, "contextPath", "/gateway");
        ReflectionTestUtils.setField(handler, "secureCookie", false);
    }

    @Test
    void shouldRejectMissingIdToken() {
        when(serverRequest.bodyToMono(String.class))
            .thenReturn(Mono.just("{}"));
        
        StepVerifier.create(handler.createSession(serverRequest))
            .assertNext(response -> {
                assertEquals(401, response.statusCode().value());
            })
            .verifyComplete();
    }

    @Test
    void shouldRejectEmptyIdToken() {
        when(serverRequest.bodyToMono(String.class))
            .thenReturn(Mono.just("{\"id_token\": \"\"}"));
        
        StepVerifier.create(handler.createSession(serverRequest))
            .assertNext(response -> {
                assertEquals(401, response.statusCode().value());
            })
            .verifyComplete();
    }

    @Test
    void shouldRejectInvalidJson() {
        when(serverRequest.bodyToMono(String.class))
            .thenReturn(Mono.just("invalid json"));
        
        StepVerifier.create(handler.createSession(serverRequest))
            .assertNext(response -> {
                assertEquals(401, response.statusCode().value());
            })
            .verifyComplete();
    }

    @Test
    void shouldCreateSessionForValidToken() {
        String validToken = "eyJ.valid.token";
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject("user-sub")
            .claim("email", "user@example.com")
            .claim("name", "Test User")
            .expirationTime(new Date(System.currentTimeMillis() + 3600000))
            .build();
        
        AzureJwtValidator.UserInfo userInfo = new AzureJwtValidator.UserInfo(
            "user-sub", "user@example.com", "Test User"
        );
        
        when(serverRequest.bodyToMono(String.class))
            .thenReturn(Mono.just("{\"id_token\": \"" + validToken + "\"}"));
        when(jwtValidator.validateIdToken(validToken))
            .thenReturn(Mono.just(claims));
        when(jwtValidator.extractUserInfo(claims))
            .thenReturn(userInfo);
        
        StepVerifier.create(handler.createSession(serverRequest))
            .assertNext(response -> {
                assertEquals(200, response.statusCode().value());
                assertTrue(response.cookies().containsKey("MSAL_SESSION"));
            })
            .verifyComplete();
    }

    @Test
    void shouldRejectInvalidToken() {
        String invalidToken = "eyJ.invalid.token";
        
        when(serverRequest.bodyToMono(String.class))
            .thenReturn(Mono.just("{\"id_token\": \"" + invalidToken + "\"}"));
        when(jwtValidator.validateIdToken(invalidToken))
            .thenReturn(Mono.error(new TokenValidationException("Invalid signature")));
        
        StepVerifier.create(handler.createSession(serverRequest))
            .assertNext(response -> {
                assertEquals(401, response.statusCode().value());
            })
            .verifyComplete();
    }

    @Test
    void validateSessionShouldReturnNullWithoutCookie() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        ServerRequest testRequest = ServerRequest.create(exchange, java.util.Collections.emptyList());
        
        SessionCreateHandler.SessionData result = handler.validateSession(testRequest);
        
        assertNull(result);
    }
}
