package com.jagapathi.pharmacy.observability;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

public final class CorrelationIdRecordInterceptor implements RecordInterceptor<String, String> {
    private final ThreadLocal<String> previousCorrelationId = new ThreadLocal<>();

    @Override
    public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record,
                                                    Consumer<String, String> consumer) {
        previousCorrelationId.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        var header = record.headers().lastHeader(CorrelationIdContext.HEADER_NAME);
        String correlationId = header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
        if (CorrelationIdFilter.isValid(correlationId)) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        } else {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        restoreCorrelationId();
    }

    @Override
    public void clearThreadState(Consumer<?, ?> consumer) {
        restoreCorrelationId();
    }

    private void restoreCorrelationId() {
        String previous = previousCorrelationId.get();
        if (previous == null) {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        } else {
            MDC.put(CorrelationIdFilter.MDC_KEY, previous);
        }
        previousCorrelationId.remove();
    }
}
