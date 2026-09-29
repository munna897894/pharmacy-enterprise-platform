package com.jagapathi.pharmacy.platform.events;

import java.time.Instant;
import java.util.UUID;

public record DomainEvent<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        String producer,
        UUID correlationId,
        UUID causationId,
        String actorId,
        T payload) {
}
