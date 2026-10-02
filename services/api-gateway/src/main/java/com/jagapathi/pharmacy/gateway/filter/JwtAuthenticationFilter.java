package com.jagapathi.pharmacy.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh",
        "/api/v1/auth/.well-known/jwks.json"
    );

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final Propagator propagator;

    public JwtAuthenticationFilter(JwtService jwtService, Tracer tracer, Propagator propagator) {
        this.jwtService = jwtService;
        this.objectMapper = new ObjectMapper();
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip authentication for public endpoints
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        // Require JWT for all other endpoints
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            logger.warn("Missing Authorization header for path: {}", path);
            return unauthorizedResponse(exchange, "Missing Authorization header");
        }

        if (!authHeader.startsWith("Bearer ")) {
            logger.warn("Invalid Authorization header format for path: {}", path);
            return unauthorizedResponse(exchange, "Invalid Authorization header format");
        }

        String token = authHeader.substring(7);

        return validateWithObservation(token, request.getHeaders())
            .flatMap(claims -> {
                String userId = claims.get("sub", String.class);
                List<?> rawRoles = claims.get("roles", List.class);
                String roles = rawRoles != null
                    ? rawRoles.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))
                    : "";

                // Add claims to request headers for downstream services
                ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Roles", roles)
                    .header("X-Token-Jti", claims.getId())
                    .build();

                ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(mutatedRequest)
                    .build();

                return chain.filter(mutatedExchange);
            })
            .onErrorResume(e -> {
                logger.error("JWT validation failed for path: {}", path, e);
                return unauthorizedResponse(exchange, "Invalid or expired token");
            });
    }

    private Mono<Claims> validateWithObservation(String token, HttpHeaders headers) {
        return Mono.defer(() -> {
            Span parent = tracer.currentSpan();
            Span span = parent != null
                ? tracer.nextSpan(parent).name("auth.jwt.validation").start()
                : propagator.extract(headers, HttpHeaders::getFirst)
                    .name("auth.jwt.validation").start();
            return validateAndExtractClaims(token)
                .doOnSuccess(claims -> span.tag("auth.outcome", "accepted"))
                .doOnError(error -> span.tag("auth.outcome", "rejected"))
                .doFinally(signal -> span.end());
        });
    }

    private Mono<Claims> validateAndExtractClaims(String token) {
        try {
            // Decode token without signature verification first to get kid
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Mono.error(new IllegalArgumentException("Invalid token format"));
            }

            // Decode header to get kid
            JsonNode header = objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(parts[0]));
            String kid = header.get("kid").asText();
            String alg = header.get("alg").asText();

            if (!alg.equals("RS256")) {
                return Mono.error(new IllegalArgumentException("Unsupported algorithm: " + alg));
            }

            // Get public key for this kid
            return jwtService.getPublicKey(kid)
                .flatMap(publicKey -> {
                    try {
                        Jws<Claims> jws = Jwts.parser()
                            .verifyWith((PublicKey) publicKey)
                            .build()
                            .parseSignedClaims(token);

                        Claims claims = jws.getPayload();

                        // Verify expiry
                        if (claims.getExpiration().before(new Date())) {
                            return Mono.error(new IllegalArgumentException("Token expired"));
                        }

                        return Mono.just(claims);
                    } catch (SignatureException e) {
                        return Mono.error(new IllegalArgumentException("Invalid signature", e));
                    } catch (Exception e) {
                        return Mono.error(new IllegalArgumentException("Invalid token: " + e.getMessage(), e));
                    }
                });
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        try {
            String correlationId = exchange.getAttribute("correlationId");
            String errorResponse = objectMapper.writeValueAsString(
                new ProblemDetailResponse(
                    "https://example.com/errors/unauthorized",
                    "Unauthorized",
                    HttpStatus.UNAUTHORIZED.value(),
                    message,
                    exchange.getRequest().getPath().value(),
                    correlationId
                )
            );
            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(errorResponse.getBytes()))
            );
        } catch (Exception e) {
            logger.error("Error creating error response", e);
            return exchange.getResponse().writeWith(Mono.empty());
        }
    }

    private boolean isPublicEndpoint(String path) {
        for (String endpoint : PUBLIC_ENDPOINTS) {
            if (path.equals(endpoint) || path.matches("^" + endpoint.replace(".", "\\.") + ".*")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return -200; // Run before CorrelationIdFilter
    }

    public static class ProblemDetailResponse {
        public String type;
        public String title;
        public int status;
        public String detail;
        public String instance;
        public String correlationId;
        public String timestamp;

        public ProblemDetailResponse(String type, String title, int status, String detail, String instance, String correlationId) {
            this.type = type;
            this.title = title;
            this.status = status;
            this.detail = detail;
            this.instance = instance;
            this.correlationId = correlationId;
            this.timestamp = java.time.Instant.now().toString();
        }
    }
}
