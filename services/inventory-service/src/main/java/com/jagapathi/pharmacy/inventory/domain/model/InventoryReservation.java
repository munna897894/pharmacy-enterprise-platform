package com.jagapathi.pharmacy.inventory.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks the saga-scoped outcome of an order's stock reservation.
 * itemsJson stores the reserved {@code [{productId, quantity}]} lines so they
 * can be released (stock added back) if payment ultimately fails.
 */
@Entity
@Table(name = "inventory_reservation", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"order_id"})
})
public class InventoryReservation {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "order_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID orderId;

    @Column(name = "pharmacy_id", nullable = false, length = 64)
    private String pharmacyId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "items_json", nullable = false, columnDefinition = "LONGTEXT")
    private String itemsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryReservation() {
    }

    public InventoryReservation(UUID orderId, String pharmacyId, String itemsJson) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.pharmacyId = pharmacyId;
        this.itemsJson = itemsJson;
        this.status = "RESERVED";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void commit() {
        this.status = "COMMITTED";
        this.updatedAt = Instant.now();
    }

    public void release() {
        this.status = "RELEASED";
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getPharmacyId() {
        return pharmacyId;
    }

    public String getStatus() {
        return status;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
