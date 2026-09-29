package com.jagapathi.pharmacy.observability;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdRecordInterceptorTest {
    private final CorrelationIdRecordInterceptor interceptor = new CorrelationIdRecordInterceptor();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void scopesCorrelationIdFromKafkaHeaderToRecordHandling() {
        MDC.put("correlationId", "before-record");
        var record = new ConsumerRecord<String, String>("events", 0, 1L, "key", "value");
        record.headers().add("X-Correlation-ID", "order-123".getBytes(StandardCharsets.UTF_8));

        interceptor.intercept(record, null);
        assertThat(MDC.get("correlationId")).isEqualTo("order-123");

        interceptor.afterRecord(record, null);
        assertThat(MDC.get("correlationId")).isEqualTo("before-record");
    }

    @Test
    void clearsInvalidCorrelationIdWhileHandlingThenRestoresPreviousValue() {
        MDC.put("correlationId", "before-record");
        var record = new ConsumerRecord<String, String>("events", 0, 1L, "key", "value");
        record.headers().add("X-Correlation-ID", "bad id".getBytes(StandardCharsets.UTF_8));

        interceptor.intercept(record, null);
        assertThat(MDC.get("correlationId")).isNull();

        interceptor.afterRecord(record, null);
        assertThat(MDC.get("correlationId")).isEqualTo("before-record");
    }

    @Test
    void scopesCloudStreamCorrelationIdToFunctionExecution() {
        MDC.put("correlationId", "before-function");

        CorrelationIdContext.runWithCorrelationId("event-456", () ->
            assertThat(MDC.get("correlationId")).isEqualTo("event-456")
        );

        assertThat(MDC.get("correlationId")).isEqualTo("before-function");
    }
}
