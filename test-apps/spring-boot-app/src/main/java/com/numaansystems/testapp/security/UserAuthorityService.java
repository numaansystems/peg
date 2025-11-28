package com.numaansystems.testapp.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service to look up user authorities from the database.
 * 
 * This service queries the USER_AUTHORITIES table to find roles/permissions
 * associated with a user's email address. These authorities are then used
 * for authorization decisions in the application.
 * 
 * Expected database table schema:
 * 
 * CREATE TABLE USER_AUTHORITIES (
 *     ID NUMBER PRIMARY KEY,
 *     USER_EMAIL VARCHAR2(255) NOT NULL,
 *     AUTHORITY VARCHAR2(100) NOT NULL
 * );
 * 
 * Example data:
 * INSERT INTO USER_AUTHORITIES VALUES (1, 'john@example.com', 'ROLE_USER');
 * INSERT INTO USER_AUTHORITIES VALUES (2, 'john@example.com', 'ROLE_ADMIN');
 * INSERT INTO USER_AUTHORITIES VALUES (3, 'jane@example.com', 'ROLE_USER');
 */
@Service
public class UserAuthorityService {

    private final JdbcTemplate jdbcTemplate;
    
    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public UserAuthorityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Look up authorities for a user by their email address.
     * 
     * @param userEmail the user's email address
     * @return list of granted authorities for the user
     */
    public List<GrantedAuthority> getAuthoritiesForUser(String userEmail) {
        // Validate email format to prevent injection attacks
        if (userEmail == null || !EMAIL_PATTERN.matcher(userEmail).matches()) {
            return Collections.emptyList();
        }
        
        String sql = "SELECT AUTHORITY FROM USER_AUTHORITIES WHERE USER_EMAIL = ?";
        
        List<String> authorities = jdbcTemplate.queryForList(sql, String.class, userEmail);
        
        return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
