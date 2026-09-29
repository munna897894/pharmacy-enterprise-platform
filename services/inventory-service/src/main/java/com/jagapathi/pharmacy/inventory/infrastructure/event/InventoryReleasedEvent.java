package com.jagapathi.pharmacy.inventory.infrastructure.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class InventoryReleasedEvent implements Serializable {
    public final UUID eventId;
    public final String eventType = "InventoryReleased";
    public final UUID orderId;
    public final UUID reservationId;
    public final String reasonCode;
    public final Instant occurredAt;

    public InventoryReleasedEvent(UUID eventId, UUID orderId, UUID reservationId, String reasonCode, Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.reservationId = reservationId;
        this.reasonCode = reasonCode;
        this.occurredAt = occurredAt;
    }
}
