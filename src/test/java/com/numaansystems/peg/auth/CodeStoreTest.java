package com.numaansystems.peg.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CodeStore.
 */
class CodeStoreTest {

    private CodeStore codeStore;

    @BeforeEach
    void setUp() {
        codeStore = new CodeStore();
    }

    @Test
    void generateCode_createsUniqueCode() {
        String code1 = codeStore.generateCode("test@example.com", "Test User", "sub123");
        String code2 = codeStore.generateCode("test@example.com", "Test User", "sub123");
        
        assertNotNull(code1);
        assertNotNull(code2);
        assertNotEquals(code1, code2, "Each generated code should be unique");
    }

    @Test
    void generateCode_createsNonEmptyCode() {
        String code = codeStore.generateCode("test@example.com", "Test User", "sub123");
        
        assertNotNull(code);
        assertFalse(code.isBlank());
        assertTrue(code.length() > 0);
    }

    @Test
    void consumeCode_returnsClaimsForValidCode() {
        String email = "user@example.com";
        String name = "Test User";
        String sub = "user-sub-123";
        
        String code = codeStore.generateCode(email, name, sub);
        CodeStore.Claims claims = codeStore.consumeCode(code);
        
        assertNotNull(claims);
        assertEquals(email, claims.email());
        assertEquals(name, claims.name());
        assertEquals(sub, claims.sub());
    }

    @Test
    void consumeCode_returnsClaims_whenNullValues() {
        String code = codeStore.generateCode(null, null, null);
        CodeStore.Claims claims = codeStore.consumeCode(code);
        
        assertNotNull(claims);
        assertNull(claims.email());
        assertNull(claims.name());
        assertNull(claims.sub());
    }

    @Test
    void consumeCode_returnsNullForInvalidCode() {
        CodeStore.Claims claims = codeStore.consumeCode("invalid-code");
        
        assertNull(claims);
    }

    @Test
    void consumeCode_returnsNullForNullCode() {
        CodeStore.Claims claims = codeStore.consumeCode(null);
        
        assertNull(claims);
    }

    @Test
    void consumeCode_returnsNullForBlankCode() {
        CodeStore.Claims claims = codeStore.consumeCode("   ");
        
        assertNull(claims);
    }

    @Test
    void consumeCode_onlyWorksOnce_singleUse() {
        String code = codeStore.generateCode("test@example.com", "Test User", "sub123");
        
        // First consumption should succeed
        CodeStore.Claims firstClaim = codeStore.consumeCode(code);
        assertNotNull(firstClaim);
        
        // Second consumption should fail (code was already consumed)
        CodeStore.Claims secondClaim = codeStore.consumeCode(code);
        assertNull(secondClaim, "Code should only be valid for single use");
    }

    @Test
    void consumeCode_returnsNullForExpiredCode() throws InterruptedException {
        // Create a store with very short TTL
        CodeStore shortTtlStore = new CodeStore(Duration.ofMillis(50));
        
        String code = shortTtlStore.generateCode("test@example.com", "Test User", "sub123");
        
        // Wait for the code to expire
        Thread.sleep(100);
        
        CodeStore.Claims claims = shortTtlStore.consumeCode(code);
        assertNull(claims, "Expired code should not be consumable");
    }

    @Test
    void size_increasesAfterGeneratingCodes() {
        assertEquals(0, codeStore.size());
        
        codeStore.generateCode("test1@example.com", "User 1", "sub1");
        assertEquals(1, codeStore.size());
        
        codeStore.generateCode("test2@example.com", "User 2", "sub2");
        assertEquals(2, codeStore.size());
    }

    @Test
    void size_decreasesAfterConsumingCodes() {
        String code1 = codeStore.generateCode("test1@example.com", "User 1", "sub1");
        String code2 = codeStore.generateCode("test2@example.com", "User 2", "sub2");
        
        assertEquals(2, codeStore.size());
        
        codeStore.consumeCode(code1);
        assertEquals(1, codeStore.size());
        
        codeStore.consumeCode(code2);
        assertEquals(0, codeStore.size());
    }

    @Test
    void getTtl_returnsConfiguredTtl() {
        CodeStore defaultStore = new CodeStore();
        assertEquals(Duration.ofMinutes(5), defaultStore.getTtl());
        
        Duration customTtl = Duration.ofMinutes(10);
        CodeStore customStore = new CodeStore(customTtl);
        assertEquals(customTtl, customStore.getTtl());
    }

    @Test
    void cleanupExpired_removesExpiredEntriesDuringGeneration() throws InterruptedException {
        // Create a store with very short TTL
        CodeStore shortTtlStore = new CodeStore(Duration.ofMillis(50));
        
        shortTtlStore.generateCode("test@example.com", "User", "sub");
        assertEquals(1, shortTtlStore.size());
        
        // Wait for the code to expire
        Thread.sleep(100);
        
        // Generate a new code, which triggers cleanup
        shortTtlStore.generateCode("test2@example.com", "User 2", "sub2");
        
        // Only the new code should remain (expired one was cleaned up)
        assertEquals(1, shortTtlStore.size());
    }
}
