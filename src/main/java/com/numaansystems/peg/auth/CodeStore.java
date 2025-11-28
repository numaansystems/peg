package com.numaansystems.peg.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory one-time code store with TTL and single-use consume semantics.
 * 
 * This store is used for the secure back-channel code exchange flow:
 * 1. After successful Azure AD authentication, a one-time code is generated
 * 2. The code maps to minimal user claims (email, name, sub)
 * 3. The code can only be consumed once and expires after TTL
 * 
 * Note: This implementation is suitable for single-instance gateway deployments.
 * For multi-instance deployments, consider using Redis or a database-backed store.
 */
@Component
public class CodeStore {

    private static final int CODE_LENGTH_BYTES = 32;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final long CLEANUP_INTERVAL_MS = 60_000; // Cleanup at most once per minute
    
    private final SecureRandom secureRandom;
    private final Map<String, CodeEntry> codeStore;
    private final Duration ttl;
    private final AtomicLong lastCleanupTime;
    
    /**
     * Represents a stored code entry with claims and expiration.
     */
    public record CodeEntry(Claims claims, Instant expiresAt) {
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
    
    /**
     * Minimal user claims stored with the code.
     */
    public record Claims(String email, String name, String sub) {}
    
    public CodeStore() {
        this(DEFAULT_TTL);
    }
    
    /**
     * Creates a CodeStore with custom TTL.
     * 
     * @param ttl the time-to-live for codes
     */
    public CodeStore(Duration ttl) {
        this.secureRandom = new SecureRandom();
        this.codeStore = new ConcurrentHashMap<>();
        this.ttl = ttl;
        this.lastCleanupTime = new AtomicLong(0);
    }
    
    /**
     * Generates a cryptographically secure one-time code and stores it with the given claims.
     * 
     * @param email user's email address
     * @param name user's display name
     * @param sub user's subject identifier
     * @return the generated code
     */
    public String generateCode(String email, String name, String sub) {
        // Clean up expired entries periodically (throttled)
        cleanupExpiredThrottled();
        
        byte[] randomBytes = new byte[CODE_LENGTH_BYTES];
        secureRandom.nextBytes(randomBytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        
        Claims claims = new Claims(email, name, sub);
        Instant expiresAt = Instant.now().plus(ttl);
        codeStore.put(code, new CodeEntry(claims, expiresAt));
        
        return code;
    }
    
    /**
     * Consumes a one-time code and returns the associated claims.
     * The code is removed from the store after consumption (single-use).
     * 
     * @param code the code to consume
     * @return the claims if the code is valid and not expired, null otherwise
     */
    public Claims consumeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        
        CodeEntry entry = codeStore.remove(code);
        if (entry == null) {
            return null;
        }
        
        if (entry.isExpired()) {
            return null;
        }
        
        return entry.claims();
    }
    
    /**
     * Removes expired entries from the store.
     * Throttled to run at most once per minute to avoid performance impact under high load.
     */
    private void cleanupExpiredThrottled() {
        long now = System.currentTimeMillis();
        long lastCleanup = lastCleanupTime.get();
        
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            // Try to update the last cleanup time atomically
            if (lastCleanupTime.compareAndSet(lastCleanup, now)) {
                Instant currentTime = Instant.now();
                codeStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(currentTime));
            }
        }
    }
    
    /**
     * Returns the current size of the code store.
     * Useful for monitoring purposes.
     */
    public int size() {
        return codeStore.size();
    }
    
    /**
     * Returns the TTL configured for this store.
     */
    public Duration getTtl() {
        return ttl;
    }
}
