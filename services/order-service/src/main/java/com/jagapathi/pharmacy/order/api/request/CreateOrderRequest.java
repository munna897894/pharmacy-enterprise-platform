package com.jagapathi.pharmacy.order.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class CreateOrderRequest {
    @NotNull(message = "customerId is required")
    private UUID customerId;

    @NotNull(message = "prescriptionId is required")
    private UUID prescriptionId;

    @NotNull(message = "pharmacyId is required")
    private UUID pharmacyId;

    @NotNull(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be 3 characters")
    private String currency;

    @NotNull(message = "total is required")
    @DecimalMin(value = "0.01", message = "total must be greater than 0")
    private BigDecimal total;

    @NotEmpty(message = "items list cannot be empty")
    @Valid
    private List<OrderItemRequest> items;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(UUID customerId, UUID prescriptionId, UUID pharmacyId, String currency, BigDecimal total, List<OrderItemRequest> items) {
        this.customerId = customerId;
        this.prescriptionId = prescriptionId;
        this.pharmacyId = pharmacyId;
        this.currency = currency;
        this.total = total;
        this.items = items;
    }

    // Getters and setters
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

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }

    public static class OrderItemRequest {
        @NotNull(message = "medicationId is required")
        private UUID medicationId;

        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.0001", message = "quantity must be greater than 0")
        @DecimalMax(value = "9999.9999", message = "quantity must not exceed 9999.9999")
        private BigDecimal quantity;

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.01", message = "unitPrice must be greater than 0")
        @DecimalMax(value = "99999.99", message = "unitPrice must not exceed 99999.99")
        private BigDecimal unitPrice;

        public OrderItemRequest() {
        }

        public OrderItemRequest(UUID medicationId, BigDecimal quantity, BigDecimal unitPrice) {
            this.medicationId = medicationId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
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
    }
}
