package com.jagapathi.pharmacy.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextHeadersTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void capturesW3cTraceContextAndCorrelationId() {
        SpanContext spanContext = SpanContext.createFromRemoteParent(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            TraceFlags.getSampled(),
            TraceState.getDefault()
        );
        MDC.put("correlationId", "order-123");

        try (var ignored = Span.wrap(spanContext).makeCurrent()) {
            var headers = TraceContextHeaders.capture();

            assertThat(headers).containsEntry(
                "traceparent",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
            );
            assertThat(headers).containsEntry("X-Correlation-ID", "order-123");
        }
    }
}
