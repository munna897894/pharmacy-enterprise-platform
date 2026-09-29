package com.jagapathi.pharmacy.order.api.response;

import com.jagapathi.pharmacy.order.domain.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public class OrderStatusResponse {
    private UUID id;
    private OrderStatus status;
    private Instant lastUpdated;

    public OrderStatusResponse(UUID id, OrderStatus status, Instant lastUpdated) {
        this.id = id;
        this.status = status;
        this.lastUpdated = lastUpdated;
    }

    public UUID getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }
}
