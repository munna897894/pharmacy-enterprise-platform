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

import java.time.Instant;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();

        String method = exchange.getRequest().getMethod().toString();
        String path = exchange.getRequest().getPath().value();
        String correlationId = exchange.getAttribute("correlationId");

        MDC.put("correlationId", correlationId);
        MDC.put("method", method);
        MDC.put("path", path);

        logger.debug("Incoming {} {}", method, path);

        return chain.filter(exchange)
            .doFinally(signalType -> {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = exchange.getResponse().getStatusCode() != null ?
                    exchange.getResponse().getStatusCode().value() : 0;

                MDC.put("statusCode", String.valueOf(statusCode));
                MDC.put("duration", String.valueOf(duration));

                if (statusCode >= 400) {
                    logger.warn("Response {} {} {} ms", method, path, duration);
                } else {
                    logger.info("Response {} {} {} ms", method, path, duration);
                }

                MDC.remove("correlationId");
                MDC.remove("method");
                MDC.remove("path");
                MDC.remove("statusCode");
                MDC.remove("duration");
            });
    }

    @Override
    public int getOrder() {
        return -25; // Run last before actual routing
    }
}
