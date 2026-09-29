package com.jagapathi.pharmacy.observability;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class TraceContextHeaders {
    private static final Set<String> ALLOWED_HEADERS = Set.of(
        "traceparent", "tracestate", "baggage", CorrelationIdContext.HEADER_NAME
    );

    private static final TextMapPropagator PROPAGATOR = TextMapPropagator.composite(
        W3CTraceContextPropagator.getInstance(),
        W3CBaggagePropagator.getInstance()
    );

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private TraceContextHeaders() {
    }

    /**
     * Runs an action (typically an outbox relay send) as a continuation of the trace and
     * correlation ID captured when the outbox row was written, so producer spans join the
     * originating request's trace instead of the relay's scheduler.
     */
    public static void runInCapturedContext(Map<String, String> capturedHeaders, Runnable action) {
        Map<String, String> headers = capturedHeaders == null ? Map.of() : capturedHeaders;
        Context parent = PROPAGATOR.extract(Context.root(), headers, MAP_GETTER);
        try (Scope ignored = parent.makeCurrent()) {
            CorrelationIdContext.runWithCorrelationId(headers.get(CorrelationIdContext.HEADER_NAME), action);
        }
    }

    public static boolean isAllowedHeader(String name) {
        return ALLOWED_HEADERS.contains(name);
    }

    public static Map<String, String> capture() {
        Map<String, String> headers = new LinkedHashMap<>();
        PROPAGATOR.inject(Context.current(), headers, (carrier, key, value) ->
            carrier.put(key, value));
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            headers.put(CorrelationIdFilter.HEADER_NAME, correlationId);
        }
        return Map.copyOf(headers);
    }
}
