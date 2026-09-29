package com.jagapathi.pharmacy.gateway.filter;

import com.jagapathi.pharmacy.gateway.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    private static final List<String> EXEMPT_ENDPOINTS = Arrays.asList(
        "/actuator/health",
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh",
        "/api/v1/auth/.well-known/jwks.json"
    );

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rate-limit.per-user-per-minute:100}")
    private long userRateLimit;

    @Value("${rate-limit.per-ip-per-minute:1000}")
    private long ipRateLimit;

    private static final long WINDOW_SECONDS = 60;

    public RateLimitingFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip rate limiting for exempt endpoints
        if (isExemptEndpoint(path)) {
            return chain.filter(exchange);
        }

        String userId = getUserId(exchange);
        String clientIp = getClientIp(request);

        // Check user rate limit
        if (userId != null) {
            String userKey = "rate-limit:user:" + userId;
            if (!checkRateLimit(userKey, userRateLimit)) {
                logger.warn("User-scoped rate limit exceeded");
                return rateLimitExceededResponse(exchange, WINDOW_SECONDS);
            }
        }

        // Check IP rate limit
        String ipKey = "rate-limit:ip:" + clientIp;
        if (!checkRateLimit(ipKey, ipRateLimit)) {
            logger.warn("IP-scoped rate limit exceeded");
            return rateLimitExceededResponse(exchange, WINDOW_SECONDS);
        }

        return chain.filter(exchange);
    }

    private boolean checkRateLimit(String key, long limit) {
        try {
            Long currentCount = redisTemplate.opsForValue().increment(key);

            // Set TTL on first increment
            if (currentCount == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS));
            }

            return currentCount <= limit;
        } catch (Exception e) {
            logger.warn("Rate-limit check unavailable; request is allowed. exceptionType={}",
                e.getClass().getSimpleName());
            // Fail open - allow request if Redis is unavailable
            return true;
        }
    }

    private String getUserId(ServerWebExchange exchange) {
        return exchange.getAttribute("userId");
    }

    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String remoteAddress = request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        return remoteAddress;
    }

    private Mono<Void> rateLimitExceededResponse(ServerWebExchange exchange, long retryAfterSeconds) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(retryAfterSeconds));
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        String jsonResponse = "{\"type\":\"https://example.com/errors/rate-limit\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded\",\"retryAfter\":" + retryAfterSeconds + "}";
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(jsonResponse.getBytes()))
        );
    }

    private boolean isExemptEndpoint(String path) {
        for (String endpoint : EXEMPT_ENDPOINTS) {
            if (path.equals(endpoint) || path.startsWith(endpoint + "/")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return -75; // Run after correlation ID and auth, but before request logging
    }
}
