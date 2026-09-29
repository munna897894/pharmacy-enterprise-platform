package com.jagapathi.pharmacy.observability;

public record OutboxMetricsSnapshot(
    long unpublishedCount,
    long oldestUnpublishedAgeSeconds,
    long publishedCount,
    long failedCount
) {
}
