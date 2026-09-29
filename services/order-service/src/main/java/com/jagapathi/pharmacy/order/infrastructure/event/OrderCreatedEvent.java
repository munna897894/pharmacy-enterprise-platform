package com.jagapathi.pharmacy.order.infrastructure.event;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderCreatedEvent implements Serializable {
    public final UUID eventId;
    public final UUID orderId;
    public final UUID customerId;
    public final UUID prescriptionId;
    public final UUID pharmacyId;
    public final String currency;
    public final BigDecimal total;
    public final Instant occurredAt;
    public final List<OrderItemPayload> items;

    public OrderCreatedEvent(UUID eventId, UUID orderId, UUID customerId, UUID prescriptionId, UUID pharmacyId,
                            String currency, BigDecimal total, Instant occurredAt, List<OrderItemPayload> items) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.prescriptionId = prescriptionId;
        this.pharmacyId = pharmacyId;
        this.currency = currency;
        this.total = total;
        this.occurredAt = occurredAt;
        this.items = items;
    }

    public static class OrderItemPayload {
        public final UUID medicationId;
        public final BigDecimal quantity;
        public final BigDecimal unitPrice;

        public OrderItemPayload(UUID medicationId, BigDecimal quantity, BigDecimal unitPrice) {
            this.medicationId = medicationId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }
}
