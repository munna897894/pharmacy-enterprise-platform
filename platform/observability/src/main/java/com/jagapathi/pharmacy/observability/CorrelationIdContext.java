package com.jagapathi.pharmacy.observability;

import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

public final class CorrelationIdContext {
    public static final String HEADER_NAME = "X-Correlation-ID";

    private CorrelationIdContext() {
    }

    /**
     * Kafka binders may surface custom headers as raw bytes rather than strings.
     */
    public static String fromHeader(Object headerValue) {
        if (headerValue instanceof String value) {
            return value;
        }
        if (headerValue instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return null;
    }

    public static void runWithCorrelationId(String correlationId, Runnable action) {
        String previous = MDC.get("correlationId");
        if (CorrelationIdFilter.isValid(correlationId)) {
            MDC.put("correlationId", correlationId);
        } else {
            MDC.remove("correlationId");
        }
        try {
            action.run();
        } finally {
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    }
}
