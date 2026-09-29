package com.jagapathi.pharmacy.order.api.response;

import com.jagapathi.pharmacy.order.domain.Order;
import com.jagapathi.pharmacy.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderResponse {
    private UUID id;
    private UUID customerId;
    private UUID prescriptionId;
    private UUID pharmacyId;
    private OrderStatus status;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private String currency;
    private List<OrderItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer version;

    public OrderResponse() {
    }

    public OrderResponse(Order order) {
        this.id = order.getId();
        this.customerId = order.getCustomerId();
        this.prescriptionId = order.getPrescriptionId();
        this.pharmacyId = order.getPharmacyId();
        this.status = order.getStatus();
        this.subtotal = order.getSubtotal();
        this.tax = order.getTax();
        this.total = order.getTotal();
        this.currency = order.getCurrency();
        this.items = order.getItems().stream()
            .map(OrderItemResponse::new)
            .toList();
        this.createdAt = order.getCreatedAt();
        this.updatedAt = order.getUpdatedAt();
        this.version = order.getVersion();
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getPrescriptionId() {
        return prescriptionId;
    }

    public void setPrescriptionId(UUID prescriptionId) {
        this.prescriptionId = prescriptionId;
    }

    public UUID getPharmacyId() {
        return pharmacyId;
    }

    public void setPharmacyId(UUID pharmacyId) {
        this.pharmacyId = pharmacyId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public void setItems(List<OrderItemResponse> items) {
        this.items = items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
