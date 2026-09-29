package com.jagapathi.pharmacy.order.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Backs the order-creation Idempotency-Key contract: the same key plus the same
 * request body replays the original order; the same key with a different body
 * is a conflict. Scoped per customer since keys are only unique within a caller's
 * own request stream.
 */
@Entity
@Table(name = "idempotency_record", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"customer_id", "idempotency_key"})
})
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID customerId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, UUID customerId, String requestHash, UUID orderId) {
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
