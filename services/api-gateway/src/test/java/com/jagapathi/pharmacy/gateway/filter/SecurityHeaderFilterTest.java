package com.jagapathi.pharmacy.gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityHeaderFilterTest {

    private SecurityHeaderFilter filter;
    private MockChain chain;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeaderFilter();
        chain = new MockChain();
    }

    @Test
    void shouldAddXContentTypeOptionsHeader() {
        ServerWebExchange exchange = createExchange("/api/v1/medications");

        filter.filter(exchange, chain).block();

        assertEquals("nosniff", exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"));
    }

    @Test
    void shouldAddXFrameOptionsHeader() {
        ServerWebExchange exchange = createExchange("/api/v1/medications");

        filter.filter(exchange, chain).block();

        assertEquals("DENY", exchange.getResponse().getHeaders().getFirst("X-Frame-Options"));
    }

    @Test
    void shouldAddXXSSProtectionHeader() {
        ServerWebExchange exchange = createExchange("/api/v1/medications");

        filter.filter(exchange, chain).block();

        assertEquals("1; mode=block", exchange.getResponse().getHeaders().getFirst("X-XSS-Protection"));
    }

    @Test
    void shouldAddStrictTransportSecurityHeader() {
        ServerWebExchange exchange = createExchange("/api/v1/medications");

        filter.filter(exchange, chain).block();

        assertEquals("max-age=31536000; includeSubDomains", 
            exchange.getResponse().getHeaders().getFirst("Strict-Transport-Security"));
    }

    @Test
    void shouldAddContentSecurityPolicyHeader() {
        ServerWebExchange exchange = createExchange("/api/v1/medications");

        filter.filter(exchange, chain).block();

        assertEquals("default-src 'self'", exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"));
    }

    private ServerWebExchange createExchange(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        return MockServerWebExchange.from(request);
    }

    private static class MockChain implements org.springframework.cloud.gateway.filter.GatewayFilterChain {
        @Override
        public reactor.core.publisher.Mono<Void> filter(ServerWebExchange exchange) {
            return reactor.core.publisher.Mono.empty();
        }
    }
}
