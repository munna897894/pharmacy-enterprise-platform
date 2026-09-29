package com.jagapathi.pharmacy.inventory.api.response;

import com.jagapathi.pharmacy.inventory.domain.model.InventoryReservation;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservationResponse(
    UUID id,
    UUID orderId,
    String pharmacyId,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
    public static InventoryReservationResponse from(InventoryReservation reservation) {
        return new InventoryReservationResponse(
            reservation.getId(),
            reservation.getOrderId(),
            reservation.getPharmacyId(),
            reservation.getStatus(),
            reservation.getCreatedAt(),
            reservation.getUpdatedAt()
        );
    }
}
