package com.numaansystems.peg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Proxy Gateway.
 * This gateway provides centralized authentication using Azure AD
 * and proxies requests to various backend applications.
 * 
 * Routes are configured in application.yml
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
