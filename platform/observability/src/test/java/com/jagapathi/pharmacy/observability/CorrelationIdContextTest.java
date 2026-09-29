package com.jagapathi.pharmacy.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdContextTest {

    private static final String CORRELATION_ID = "6c3925df-133d-4217-9fb6-2c2a03402ada";

    @Test
    void readsStringHeader() {
        assertThat(CorrelationIdContext.fromHeader(CORRELATION_ID)).isEqualTo(CORRELATION_ID);
    }

    @Test
    void decodesByteArrayHeader() {
        assertThat(CorrelationIdContext.fromHeader(CORRELATION_ID.getBytes(StandardCharsets.UTF_8)))
            .isEqualTo(CORRELATION_ID);
    }

    @Test
    void ignoresMissingOrUnsupportedHeader() {
        assertThat(CorrelationIdContext.fromHeader(null)).isNull();
        assertThat(CorrelationIdContext.fromHeader(42)).isNull();
    }

    @Test
    void setsAndRestoresMdcAroundAction() {
        AtomicReference<String> observed = new AtomicReference<>();
        MDC.remove("correlationId");

        CorrelationIdContext.runWithCorrelationId(CORRELATION_ID, () -> observed.set(MDC.get("correlationId")));

        assertThat(observed.get()).isEqualTo(CORRELATION_ID);
        assertThat(MDC.get("correlationId")).isNull();
    }
}
