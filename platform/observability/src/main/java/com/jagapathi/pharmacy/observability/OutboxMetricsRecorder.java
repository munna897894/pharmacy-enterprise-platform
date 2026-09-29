package com.jagapathi.pharmacy.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicLong;

public final class OutboxMetricsRecorder {
    private final AtomicLong unpublishedCount = new AtomicLong();
    private final AtomicLong oldestUnpublishedAgeSeconds = new AtomicLong();
    private final io.micrometer.core.instrument.Counter publishedCounter;
    private final io.micrometer.core.instrument.Counter failedCounter;

    public OutboxMetricsRecorder(MeterRegistry registry) {
        Gauge.builder("pharmacy.outbox.unpublished", unpublishedCount, AtomicLong::get)
            .description("Number of currently unpublished transactional outbox events")
            .register(registry);
        Gauge.builder("pharmacy.outbox.oldest.unpublished.seconds", oldestUnpublishedAgeSeconds, AtomicLong::get)
            .description("Age of the oldest unpublished transactional outbox event")
            .register(registry);
        this.publishedCounter = registry.counter("pharmacy.outbox.publish.events", "result", "success");
        this.failedCounter = registry.counter("pharmacy.outbox.publish.events", "result", "failure");
    }

    public void record(OutboxMetricsSnapshot snapshot) {
        unpublishedCount.set(snapshot.unpublishedCount());
        oldestUnpublishedAgeSeconds.set(snapshot.oldestUnpublishedAgeSeconds());
        publishedCounter.increment(snapshot.publishedCount());
        failedCounter.increment(snapshot.failedCount());
    }
}
