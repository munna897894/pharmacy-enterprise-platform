package com.jagapathi.pharmacy.order.infrastructure.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class PaymentProcessedEvent implements Serializable {
    public final UUID eventId;
    public final UUID orderId;
    public final String status; // SUCCESS or FAILED
    public final Instant occurredAt;

    public PaymentProcessedEvent(UUID eventId, UUID orderId, String status, Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.status = status;
        this.occurredAt = occurredAt;
    }
}
