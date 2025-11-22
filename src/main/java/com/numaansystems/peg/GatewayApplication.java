package com.numaansystems.peg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

/**
 * Main application class for the Proxy Gateway.
 * This gateway provides centralized authentication using Azure AD
 * and proxies requests to various backend applications.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * Configure routes for the gateway.
     * Routes can be defined programmatically here or in application.yml.
     * This bean provides examples for different types of applications.
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Example route for a modern Spring Boot application
                .route("spring-boot-app", r -> r
                        .path("/api/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .preserveHostHeader())
                        .uri("http://localhost:8081"))
                
                // Example route for a legacy Spring Framework application
                .route("legacy-spring-app", r -> r
                        .path("/legacy/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .preserveHostHeader())
                        .uri("http://localhost:8082"))
                
                // Example route for a GWT application
                .route("gwt-app", r -> r
                        .path("/gwt/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .preserveHostHeader())
                        .uri("http://localhost:8083"))
                
                .build();
    }
}
