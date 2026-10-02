package com.jagapathi.pharmacy.gateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);
    private final Tracer tracer;

    public LoggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();

        String method = exchange.getRequest().getMethod().toString();
        String path = exchange.getRequest().getPath().value();
        String correlationId = exchange.getAttribute("correlationId");

        return Mono.defer(() -> {
            try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlationId)) {
                logger.debug("Incoming {} {}", method, path);
            }
            return chain.filter(exchange);
        }).doOnEach(signal -> {
            if (signal.isOnComplete() || signal.isOnError()) {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = exchange.getResponse().getStatusCode() != null ?
                    exchange.getResponse().getStatusCode().value() : 0;
                String status = signal.isOnError() ? "unhandled" : String.valueOf(statusCode);
                Span span = tracer.currentSpan();
                try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlationId);
                     MDC.MDCCloseable trace = span == null ? null :
                         MDC.putCloseable("traceId", span.context().traceId());
                     MDC.MDCCloseable spanId = span == null ? null :
                         MDC.putCloseable("spanId", span.context().spanId())) {
                    if (statusCode >= 400 || signal.isOnError()) {
                        logger.warn("http.request.completed method={} path={} status={} durationMs={} outcome=error",
                            method, path, status, duration);
                    } else {
                        logger.info("http.request.completed method={} path={} status={} durationMs={} outcome=success",
                            method, path, status, duration);
                    }
                }
            }
        });
    }

    @Override
    public int getOrder() {
        return -25; // Run last before actual routing
    }
}
