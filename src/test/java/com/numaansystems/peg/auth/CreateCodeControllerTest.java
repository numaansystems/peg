package com.numaansystems.peg.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;

import static org.mockito.Mockito.*;

/**
 * Unit tests for CreateCodeController.
 */
@ExtendWith(MockitoExtension.class)
class CreateCodeControllerTest {

    private static final String APP_CONSUME_URL = "https://app.example.org/auth/consume";
    
    private CodeStore codeStore;
    private CreateCodeController controller;
    
    @Mock
    private OidcUser oidcUser;
    
    @Mock
    private ServerHttpResponse response;
    
    @Mock
    private HttpHeaders headers;

    @BeforeEach
    void setUp() {
        codeStore = new CodeStore();
        controller = new CreateCodeController(codeStore, APP_CONSUME_URL);
    }

    @Test
    void createCode_redirectsToAppWithCode() {
        String email = "user@example.com";
        String name = "Test User";
        String sub = "user-sub-123";
        
        when(oidcUser.getEmail()).thenReturn(email);
        when(oidcUser.getName()).thenReturn(name);
        when(oidcUser.getSubject()).thenReturn(sub);
        when(response.getHeaders()).thenReturn(headers);
        when(response.setComplete()).thenReturn(Mono.empty());
        
        Mono<Void> result = controller.createCode(oidcUser, response);
        
        StepVerifier.create(result).verifyComplete();
        
        verify(response).setStatusCode(HttpStatus.FOUND);
        verify(headers).setLocation(argThat(uri -> {
            String uriString = uri.toString();
            return uriString.startsWith(APP_CONSUME_URL + "?code=") 
                    && uriString.length() > APP_CONSUME_URL.length() + 6;
        }));
    }

    @Test
    void createCode_storesCodeWithClaims() {
        String email = "user@example.com";
        String name = "Test User";
        String sub = "user-sub-123";
        
        when(oidcUser.getEmail()).thenReturn(email);
        when(oidcUser.getName()).thenReturn(name);
        when(oidcUser.getSubject()).thenReturn(sub);
        when(response.getHeaders()).thenReturn(headers);
        when(response.setComplete()).thenReturn(Mono.empty());
        
        // Capture the code from the redirect URL
        doAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            String uriString = uri.toString();
            String code = uriString.substring(uriString.indexOf("code=") + 5);
            
            // Verify the code can be consumed with correct claims
            CodeStore.Claims claims = codeStore.consumeCode(code);
            assert claims != null : "Code should be stored";
            assert email.equals(claims.email()) : "Email should match";
            assert name.equals(claims.name()) : "Name should match";
            assert sub.equals(claims.sub()) : "Sub should match";
            
            return null;
        }).when(headers).setLocation(any(URI.class));
        
        Mono<Void> result = controller.createCode(oidcUser, response);
        
        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void createCode_returnsUnauthorizedForNullUser() {
        when(response.setComplete()).thenReturn(Mono.empty());
        
        Mono<Void> result = controller.createCode(null, response);
        
        StepVerifier.create(result).verifyComplete();
        
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(response, never()).getHeaders();
    }

    @Test
    void createCode_handlesNullClaimsFromOidcUser() {
        when(oidcUser.getEmail()).thenReturn(null);
        when(oidcUser.getName()).thenReturn(null);
        when(oidcUser.getSubject()).thenReturn(null);
        when(response.getHeaders()).thenReturn(headers);
        when(response.setComplete()).thenReturn(Mono.empty());
        
        Mono<Void> result = controller.createCode(oidcUser, response);
        
        StepVerifier.create(result).verifyComplete();
        
        // Should still redirect, even with null claims
        verify(response).setStatusCode(HttpStatus.FOUND);
        verify(headers).setLocation(any(URI.class));
    }

    @Test
    void createCode_usesConfiguredConsumeUrl() {
        String customUrl = "https://custom.example.com/custom/consume";
        CreateCodeController customController = new CreateCodeController(codeStore, customUrl);
        
        when(oidcUser.getEmail()).thenReturn("test@example.com");
        when(oidcUser.getName()).thenReturn("Test");
        when(oidcUser.getSubject()).thenReturn("sub");
        when(response.getHeaders()).thenReturn(headers);
        when(response.setComplete()).thenReturn(Mono.empty());
        
        Mono<Void> result = customController.createCode(oidcUser, response);
        
        StepVerifier.create(result).verifyComplete();
        
        verify(headers).setLocation(argThat(uri -> 
            uri.toString().startsWith(customUrl + "?code=")));
    }
}
