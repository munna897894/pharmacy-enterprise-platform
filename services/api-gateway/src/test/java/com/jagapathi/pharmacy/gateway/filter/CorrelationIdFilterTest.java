package com.jagapathi.pharmacy.gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private MockChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        chain = new MockChain();
    }

    @Test
    void shouldGenerateCorrelationIdWhenNotProvided() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/medications")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        String correlationId = exchange.getAttribute("correlationId");
        assertNotNull(correlationId);
        assertTrue(isValidUUID(correlationId));
        assertTrue(exchange.getResponse().getHeaders().containsKey("X-Correlation-ID"));
    }

    @Test
    void shouldPreserveProvidedCorrelationId() {
        String providedId = UUID.randomUUID().toString();
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/medications")
            .header("X-Correlation-ID", providedId)
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        String correlationId = exchange.getAttribute("correlationId");
        assertEquals(providedId, correlationId);
    }

    @Test
    void shouldReplaceUnsafeCorrelationIdAndForwardSafeId() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/medications")
            .header("X-Correlation-ID", "unsafe id")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, forwardedExchange -> {
            String forwardedId = forwardedExchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
            assertNotNull(forwardedId);
            assertTrue(isValidUUID(forwardedId));
            return reactor.core.publisher.Mono.empty();
        }).block();

        assertTrue(isValidUUID(exchange.getAttribute("correlationId")));
    }

    @Test
    void shouldPropagateCorrelationIdInResponse() {
        String providedId = UUID.randomUUID().toString();
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/medications")
            .header("X-Correlation-ID", providedId)
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertEquals(providedId, exchange.getResponse().getHeaders().getFirst("X-Correlation-ID"));
    }

    private boolean isValidUUID(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static class MockChain implements org.springframework.cloud.gateway.filter.GatewayFilterChain {
        @Override
        public reactor.core.publisher.Mono<Void> filter(ServerWebExchange exchange) {
            return reactor.core.publisher.Mono.empty();
        }
    }
}
