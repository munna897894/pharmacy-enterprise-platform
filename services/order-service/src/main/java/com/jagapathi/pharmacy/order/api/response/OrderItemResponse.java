package com.jagapathi.pharmacy.order.api.response;

import com.jagapathi.pharmacy.order.domain.OrderItem;
import com.jagapathi.pharmacy.order.domain.OrderItemStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class OrderItemResponse {
    private UUID id;
    private UUID medicationId;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private OrderItemStatus status;
    private Instant fulfilledAt;
    private Instant createdAt;

    public OrderItemResponse() {
    }

    public OrderItemResponse(OrderItem item) {
        this.id = item.getId();
        this.medicationId = item.getMedicationId();
        this.quantity = item.getQuantity();
        this.unitPrice = item.getUnitPrice();
        this.lineTotal = item.getLineTotal();
        this.status = item.getStatus();
        this.fulfilledAt = item.getFulfilledAt();
        this.createdAt = item.getCreatedAt();
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMedicationId() {
        return medicationId;
    }

    public void setMedicationId(UUID medicationId) {
        this.medicationId = medicationId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public OrderItemStatus getStatus() {
        return status;
    }

    public void setStatus(OrderItemStatus status) {
        this.status = status;
    }

    public Instant getFulfilledAt() {
        return fulfilledAt;
    }

    public void setFulfilledAt(Instant fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
