package com.jagapathi.pharmacy.inventory.infrastructure.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class InventoryReservedEvent implements Serializable {
    public final UUID eventId;
    public final String eventType = "InventoryReserved";
    public final UUID orderId;
    public final UUID reservationId;
    public final String pharmacyId;
    public final Instant occurredAt;

    public InventoryReservedEvent(UUID eventId, UUID orderId, UUID reservationId, String pharmacyId, Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.reservationId = reservationId;
        this.pharmacyId = pharmacyId;
        this.occurredAt = occurredAt;
    }
}
