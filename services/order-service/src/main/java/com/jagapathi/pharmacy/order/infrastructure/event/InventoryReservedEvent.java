package com.jagapathi.pharmacy.order.infrastructure.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class InventoryReservedEvent implements Serializable {
    public final UUID eventId;
    public final UUID orderId;
    public final UUID reservationId;
    public final Instant occurredAt;

    public InventoryReservedEvent(UUID eventId, UUID orderId, UUID reservationId, Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.reservationId = reservationId;
        this.occurredAt = occurredAt;
    }
}
