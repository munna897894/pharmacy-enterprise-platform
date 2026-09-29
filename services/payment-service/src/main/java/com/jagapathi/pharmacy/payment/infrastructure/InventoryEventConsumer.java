package com.jagapathi.pharmacy.payment.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.payment.application.PaymentService;
import com.jagapathi.pharmacy.payment.domain.PaymentMethod;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@code pharmacy.inventory.events.v1} and triggers payment once stock
 * has been reserved for an order. No field on Order/CreateOrderRequest today
 * captures a customer-chosen payment method, so a documented default of
 * CREDIT_CARD is used; idempotency reuses the existing unique
 * payment.idempotency_key column, keyed deterministically per order so
 * redelivery of InventoryReserved never double-charges.
 */
@Component
public class InventoryEventConsumer {

    private final PaymentService paymentService;
    private final OrderLookupClient orderLookupClient;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(PaymentService paymentService, OrderLookupClient orderLookupClient, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.orderLookupClient = orderLookupClient;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "pharmacy.inventory.events.v1",
        groupId = "payment-inventory-reserved-v1",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.get("eventType").asText();
            if (!"InventoryReserved".equals(eventType)) {
                return;
            }

            UUID orderId = UUID.fromString(node.get("orderId").asText());
            var orderSummary = orderLookupClient.getOrderSummary(orderId);

            paymentService.processPayment(
                orderId,
                orderSummary.customerId(),
                orderSummary.total(),
                orderSummary.currency(),
                PaymentMethod.CREDIT_CARD,
                "order:" + orderId
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to process inventory event for payment trigger", e);
        }
    }
}
