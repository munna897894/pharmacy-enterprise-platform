package com.jagapathi.pharmacy.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PharmacyBusinessMetricsTest {

    @Test
    void businessMetricTagsAreBoundedAndNeverIncludeResourceIds() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().commonTags("service", "order-service", "environment", "test");
        PharmacyBusinessMetrics metrics = new PharmacyBusinessMetrics(registry);

        metrics.record(BusinessMetric.ORDER_CREATED, BusinessOutcome.SUCCESS);

        var meter = registry.get("pharmacy.business.events")
            .tag("operation", "order_created")
            .tag("outcome", "success")
            .counter();
        assertThat(meter.count()).isEqualTo(1.0);
        assertThat(meter.getId().getTags())
            .extracting("key")
            .containsExactlyInAnyOrder("service", "environment", "operation", "outcome");
    }
}
