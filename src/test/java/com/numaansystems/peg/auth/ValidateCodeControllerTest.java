package com.numaansystems.peg.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidateCodeController.
 */
class ValidateCodeControllerTest {

    private static final String SHARED_SECRET = "test-shared-secret";
    private static final String VALID_AUTH_HEADER = "Bearer " + SHARED_SECRET;
    
    private CodeStore codeStore;
    private ValidateCodeController controller;

    @BeforeEach
    void setUp() {
        codeStore = new CodeStore();
        controller = new ValidateCodeController(codeStore, SHARED_SECRET);
    }

    @Test
    void validateCode_returnsClaimsForValidCodeAndAuth() {
        String email = "user@example.com";
        String name = "Test User";
        String sub = "user-sub-123";
        
        String code = codeStore.generateCode(email, name, sub);
        var request = new ValidateCodeController.ValidateCodeRequest(code);
        
        Mono<ResponseEntity<?>> result = controller.validateCode(VALID_AUTH_HEADER, request);
        
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertTrue(response.getBody() instanceof ValidateCodeController.ValidateCodeResponse);
                    
                    var claims = (ValidateCodeController.ValidateCodeResponse) response.getBody();
                    assertEquals(email, claims.email());
                    assertEquals(name, claims.name());
                    assertEquals(sub, claims.sub());
                })
                .verifyComplete();
    }

    @Test
    void validateCode_returnsUnauthorizedForMissingAuthHeader() {
        String code = codeStore.generateCode("test@example.com", "Test", "sub");
        var request = new ValidateCodeController.ValidateCodeRequest(code);
        
        Mono<ResponseEntity<?>> result = controller.validateCode(null, request);
        
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                    assertTrue(response.getBody() instanceof ValidateCodeController.ErrorResponse);
                })
                .verifyComplete();
    }

    @Test
    void validateCode_returnsUnauthorizedForInvalidAuthHeader() {
        String code = codeStore.generateCode("test@example.com", "Test", "sub");
        var request = new ValidateCodeController.ValidateCodeRequest(code);
        
        Mono<ResponseEntity<?>> result = controller.validateCode("Bearer wrong-secret", request);
        
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    void validateCode_returnsUnauthorizedForNonBearerHeader() {
        String code = codeStore.generateCode("test@example.com", "Test", "sub");
        var request = new ValidateCodeController.ValidateCodeRequest(code);
        
        Mono<ResponseEntity<?>> result = controller.validateCode("Basic " + SHARED_SECRET, request);
        
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    void validateCode_returnsBadRequestForNullRequest() {
        Mono<ResponseEntity<?>> result = controller.validateCode(VALID_AUTH_HEADER, null);
        
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    void validateCode_returnsBadRequestForNullCode() {
        var request = new ValidateCodeController.ValidateCodeRequest(null);
        
        Mono<ResponseEntity<?>> result = controller.validateCode(VALID_AUTH_HEADER, request);
        
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    void validateCode_returnsBadRequestForBlankCode() {
        var request = new ValidateCodeController.ValidateCodeRequest("   ");
        
        Mono<ResponseEntity<?>> result = controller.validateCode(VALID_AUTH_HEADER, request);
        
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    void validateCode_returnsUnauthorizedForInvalidCode() {
        var request = new ValidateCodeController.ValidateCodeRequest("invalid-code");
        
        Mono<ResponseEntity<?>> result = controller.validateCode(VALID_AUTH_HEADER, request);
        
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                    assertTrue(response.getBody() instanceof ValidateCodeController.ErrorResponse);
                    var error = (ValidateCodeController.ErrorResponse) response.getBody();
                    assertEquals("Invalid or expired code", error.error());
                })
                .verifyComplete();
    }

    @Test
    void validateCode_returnsUnauthorizedForAlreadyConsumedCode() {
        String code = codeStore.generateCode("test@example.com", "Test", "sub");
        var request = new ValidateCodeController.ValidateCodeRequest(code);
        
        // First call should succeed
        Mono<ResponseEntity<?>> firstResult = controller.validateCode(VALID_AUTH_HEADER, request);
        StepVerifier.create(firstResult)
                .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatusCode()))
                .verifyComplete();
        
        // Second call with same code should fail
        Mono<ResponseEntity<?>> secondResult = controller.validateCode(VALID_AUTH_HEADER, request);
        StepVerifier.create(secondResult)
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    void validateCode_rejectsAllWhenNoSharedSecretConfigured() {
        ValidateCodeController noSecretController = new ValidateCodeController(codeStore, "");
        
        String code = codeStore.generateCode("test@example.com", "Test", "sub");
        var request = new ValidateCodeController.ValidateCodeRequest(code);
        
        Mono<ResponseEntity<?>> result = noSecretController.validateCode(VALID_AUTH_HEADER, request);
        
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    void validateCode_rejectsWhenSharedSecretIsNull() {
        ValidateCodeController nullSecretController = new ValidateCodeController(codeStore, null);
        
        String code = codeStore.generateCode("test@example.com", "Test", "sub");
        var request = new ValidateCodeController.ValidateCodeRequest(code);
        
        Mono<ResponseEntity<?>> result = nullSecretController.validateCode("Bearer anything", request);
        
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                })
                .verifyComplete();
    }
}
