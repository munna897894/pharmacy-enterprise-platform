package com.jagapathi.pharmacy.observability;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class TraceContextHeaders {
    private static final Set<String> ALLOWED_HEADERS = Set.of(
        "traceparent", "tracestate", "baggage", CorrelationIdContext.HEADER_NAME
    );

    private TraceContextHeaders() {
    }

    public static boolean isAllowedHeader(String name) {
        return ALLOWED_HEADERS.contains(name);
    }

    public static Map<String, String> capture() {
        Map<String, String> headers = new LinkedHashMap<>();
        ContextPropagators propagators = ContextPropagators.create(
            io.opentelemetry.context.propagation.TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance()
            )
        );
        propagators.getTextMapPropagator().inject(Context.current(), headers, (carrier, key, value) ->
            carrier.put(key, value));
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            headers.put(CorrelationIdFilter.HEADER_NAME, correlationId);
        }
        return Map.copyOf(headers);
    }
}
