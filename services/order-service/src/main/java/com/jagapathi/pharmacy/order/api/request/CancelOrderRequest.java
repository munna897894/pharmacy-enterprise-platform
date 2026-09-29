package com.jagapathi.pharmacy.order.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CancelOrderRequest {
    @NotNull(message = "orderId is required")
    private UUID orderId;

    @NotBlank(message = "reason is required")
    private String reason;

    public CancelOrderRequest() {
    }

    public CancelOrderRequest(UUID orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
