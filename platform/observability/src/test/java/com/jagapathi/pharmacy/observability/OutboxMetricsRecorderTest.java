package com.jagapathi.pharmacy.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxMetricsRecorderTest {

    @Test
    void recordsOutboxBacklogAgeAndPublishOutcomesWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxMetricsRecorder recorder = new OutboxMetricsRecorder(registry);

        recorder.record(new OutboxMetricsSnapshot(2, 45, 3, 1));

        assertThat(registry.get("pharmacy.outbox.unpublished").gauge().value()).isEqualTo(2);
        assertThat(registry.get("pharmacy.outbox.oldest.unpublished.seconds").gauge().value()).isEqualTo(45);
        assertThat(registry.get("pharmacy.outbox.publish.events").tag("result", "success").counter().count())
            .isEqualTo(3);
        assertThat(registry.get("pharmacy.outbox.publish.events").tag("result", "failure").counter().count())
            .isEqualTo(1);
    }
}
