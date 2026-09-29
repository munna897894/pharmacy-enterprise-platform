package com.jagapathi.pharmacy.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void runsActionAsContinuationOfCapturedTraceAndCorrelationId() {
        String correlationId = "bf9bd65b-872f-467c-8d99-81d79a5458da";
        Map<String, String> captured = Map.of(
            "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            "X-Correlation-ID", correlationId
        );
        AtomicReference<SpanContext> observedSpan = new AtomicReference<>();
        AtomicReference<String> observedCorrelation = new AtomicReference<>();

        TraceContextHeaders.runInCapturedContext(captured, () -> {
            observedSpan.set(Span.current().getSpanContext());
            observedCorrelation.set(MDC.get("correlationId"));
        });

        assertThat(observedSpan.get().getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(observedSpan.get().getSpanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(observedSpan.get().isRemote()).isTrue();
        assertThat(observedCorrelation.get()).isEqualTo(correlationId);
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void runsActionWithoutParentWhenNothingWasCaptured() {
        AtomicReference<SpanContext> observedSpan = new AtomicReference<>();

        TraceContextHeaders.runInCapturedContext(null, () -> observedSpan.set(Span.current().getSpanContext()));

        assertThat(observedSpan.get().isValid()).isFalse();
    }
}
