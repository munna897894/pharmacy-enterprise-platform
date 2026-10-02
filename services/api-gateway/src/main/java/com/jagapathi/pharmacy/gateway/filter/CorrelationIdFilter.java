package com.jagapathi.pharmacy.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);

        // Generate new correlation ID if not present
        if (correlationId == null || !SAFE_CORRELATION_ID.matcher(correlationId).matches()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Store in exchange attribute for downstream use
        exchange.getAttributes().put("correlationId", correlationId);

        // Mutate request to add/preserve correlation ID
        exchange = exchange.mutate()
            .request(exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build())
            .build();

        // Add to response headers
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlationId)) {
            logger.debug("Request {} with correlation ID: {}", exchange.getRequest().getPath(), correlationId);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100; // Run early, after JwtAuthenticationFilter but before others
    }
}
