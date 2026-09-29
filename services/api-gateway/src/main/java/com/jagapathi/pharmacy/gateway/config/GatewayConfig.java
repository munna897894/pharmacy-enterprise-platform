package com.jagapathi.pharmacy.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // Auth Service - Public endpoints
            .route("auth-login", r -> r
                .path("/api/v1/auth/login")
                .and()
                .method(HttpMethod.POST)
                .uri("http://auth-service:8081"))
            .route("auth-register", r -> r
                .path("/api/v1/auth/register")
                .and()
                .method(HttpMethod.POST)
                .uri("http://auth-service:8081"))
            .route("auth-refresh", r -> r
                .path("/api/v1/auth/refresh")
                .and()
                .method(HttpMethod.POST)
                .uri("http://auth-service:8081"))
            .route("auth-logout", r -> r
                .path("/api/v1/auth/logout")
                .and()
                .method(HttpMethod.POST)
                .uri("http://auth-service:8081"))
            .route("auth-jwks", r -> r
                .path("/api/v1/auth/.well-known/jwks.json")
                .and()
                .method(HttpMethod.GET)
                .uri("http://auth-service:8081"))

            // Product Service
            .route("product-list", r -> r
                .path("/api/v1/medications/**")
                .and()
                .method(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)
                .uri("http://product-service:8082"))

            // Customer Service
            .route("customer", r -> r
                .path("/api/v1/customers/**")
                .uri("http://customer-service:8083"))

            // Pharmacy Service
            .route("pharmacy", r -> r
                .path("/api/v1/pharmacies/**")
                .uri("http://pharmacy-service:8084"))

            // Inventory Service
            .route("inventory", r -> r
                .path("/api/v1/inventory/**")
                .uri("http://inventory-service:8085"))

            // Prescription Service
            .route("prescription", r -> r
                .path("/api/v1/prescriptions/**")
                .uri("http://prescription-service:8086"))

            // Order Service
            .route("order", r -> r
                .path("/api/v1/orders/**")
                .uri("http://order-service:8087"))

            // Payment Service
            .route("payment", r -> r
                .path("/api/v1/payments/**")
                .uri("http://payment-service:8088"))

            // Notification Service
            .route("notification", r -> r
                .path("/api/v1/notifications/**")
                .uri("http://notification-service:8089"))

            // Audit Service
            .route("audit", r -> r
                .path("/api/v1/audit/**")
                .and()
                .method(HttpMethod.GET)
                .uri("http://audit-service:8090"))

            // External mock service - test/local failure-mode controls only
            .route("mock", r -> r
                .path("/api/v1/mock/**")
                .uri("http://external-mock-service:8080"))

            .build();
    }
}
