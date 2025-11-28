package com.numaansystems.testapp.security;

import java.security.Principal;

/**
 * Principal object representing a user authenticated through the gateway.
 * 
 * Contains user information extracted from gateway authentication headers:
 * - email: User's email address (used as unique identifier)
 * - name: User's display name
 * - subject: Azure AD subject identifier (unique ID)
 */
public class GatewayUserPrincipal implements Principal {

    private final String email;
    private final String name;
    private final String subject;

    public GatewayUserPrincipal(String email, String name, String subject) {
        this.email = email;
        this.name = name;
        this.subject = subject;
    }

    @Override
    public String getName() {
        return email;  // Use email as the principal name
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return name;
    }

    public String getSubject() {
        return subject;
    }

    @Override
    public String toString() {
        return "GatewayUserPrincipal{" +
                "email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", subject='" + subject + '\'' +
                '}';
    }
}
