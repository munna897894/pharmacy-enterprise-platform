package com.jagapathi.pharmacy.order.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "order_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private OrderStatus toStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "event_id", columnDefinition = "CHAR(36)")
    private UUID eventId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public OrderStatusHistory() {
    }

    public OrderStatusHistory(UUID id, UUID orderId, OrderStatus fromStatus, OrderStatus toStatus, String reason, UUID eventId) {
        this.id = id;
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.eventId = eventId;
        this.changedAt = Instant.now();
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
