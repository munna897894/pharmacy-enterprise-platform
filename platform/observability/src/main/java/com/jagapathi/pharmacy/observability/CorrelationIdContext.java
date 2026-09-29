package com.jagapathi.pharmacy.observability;

import org.slf4j.MDC;

public final class CorrelationIdContext {
    public static final String HEADER_NAME = "X-Correlation-ID";

    private CorrelationIdContext() {
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
