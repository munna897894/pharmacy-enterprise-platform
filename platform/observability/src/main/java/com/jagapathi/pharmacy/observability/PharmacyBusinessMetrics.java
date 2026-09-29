package com.jagapathi.pharmacy.observability;

import io.micrometer.core.instrument.MeterRegistry;

public final class PharmacyBusinessMetrics {
    private final MeterRegistry meterRegistry;

    public PharmacyBusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(BusinessMetric metric, BusinessOutcome outcome) {
        meterRegistry.counter(
            "pharmacy.business.events",
            "operation", metric.name().toLowerCase(java.util.Locale.ROOT),
            "outcome", outcome.name().toLowerCase(java.util.Locale.ROOT)
        ).increment();
    }
}
