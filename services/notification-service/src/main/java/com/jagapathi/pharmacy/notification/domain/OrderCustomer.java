package com.jagapathi.pharmacy.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Local read model mapping orders to customers, built from OrderCreated events so payment
 * events (which carry only orderId) can be routed to the right customer.
 */
@Entity
@Table(name = "order_customers")
public class OrderCustomer {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected OrderCustomer() {
    }

    public OrderCustomer(UUID orderId, UUID customerId, Instant recordedAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.recordedAt = recordedAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
