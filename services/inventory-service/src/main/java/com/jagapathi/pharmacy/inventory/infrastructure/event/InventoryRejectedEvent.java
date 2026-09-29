package com.jagapathi.pharmacy.inventory.infrastructure.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class InventoryRejectedEvent implements Serializable {
    public final UUID eventId;
    public final String eventType = "InventoryRejected";
    public final UUID orderId;
    public final String reasonCode;
    public final Instant occurredAt;

    public InventoryRejectedEvent(UUID eventId, UUID orderId, String reasonCode, Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.reasonCode = reasonCode;
        this.occurredAt = occurredAt;
    }
}
