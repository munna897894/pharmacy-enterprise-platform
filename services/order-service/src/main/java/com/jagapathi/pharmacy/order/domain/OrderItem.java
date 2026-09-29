package com.jagapathi.pharmacy.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, columnDefinition = "CHAR(36)")
    private Order order;

    @Column(name = "medication_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID medicationId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderItemStatus status;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(nullable = false)
    private Instant createdAt;

    public OrderItem() {
    }

    public OrderItem(UUID id, UUID medicationId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        this.id = id;
        this.medicationId = medicationId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
        this.status = OrderItemStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void transitionTo(OrderItemStatus newStatus) {
        if (!isValidTransition(this.status, newStatus)) {
            throw new InvalidOrderStateException("Cannot transition item from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
        if (newStatus == OrderItemStatus.DELIVERED) {
            this.fulfilledAt = Instant.now();
        }
    }

    private boolean isValidTransition(OrderItemStatus from, OrderItemStatus to) {
        return switch (from) {
            case PENDING -> to == OrderItemStatus.RESERVED;
            case RESERVED -> to == OrderItemStatus.PICKED;
            case PICKED -> to == OrderItemStatus.SHIPPED;
            case SHIPPED -> to == OrderItemStatus.DELIVERED;
            case DELIVERED -> false;
        };
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public UUID getMedicationId() {
        return medicationId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public OrderItemStatus getStatus() {
        return status;
    }

    public Instant getFulfilledAt() {
        return fulfilledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
