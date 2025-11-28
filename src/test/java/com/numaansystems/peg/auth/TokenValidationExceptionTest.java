package com.numaansystems.peg.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenValidationException.
 */
class TokenValidationExceptionTest {

    @Test
    void shouldCreateExceptionWithMessage() {
        TokenValidationException exception = new TokenValidationException("Invalid token");
        
        assertEquals("Invalid token", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void shouldCreateExceptionWithMessageAndCause() {
        Throwable cause = new RuntimeException("Root cause");
        TokenValidationException exception = new TokenValidationException("Invalid token", cause);
        
        assertEquals("Invalid token", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
