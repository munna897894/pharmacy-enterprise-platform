package com.jagapathi.pharmacy.inventory.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.inventory.application.service.InventorySagaService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Consumes {@code pharmacy.payment.events.v1} to commit or release a stock reservation. */
@Component
public class PaymentEventConsumer {

    private final InventorySagaService sagaService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(InventorySagaService sagaService, ObjectMapper objectMapper) {
        this.sagaService = sagaService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "pharmacy.payment.events.v1",
        groupId = "inventory-payment-result-v1",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.get("eventType").asText();
            UUID eventId = UUID.fromString(node.get("eventId").asText());
            UUID orderId = UUID.fromString(node.get("orderId").asText());

            if ("PaymentCompleted".equals(eventType)) {
                sagaService.handlePaymentCompleted(eventId, orderId);
            } else if ("PaymentFailed".equals(eventType)) {
                sagaService.handlePaymentFailed(eventId, orderId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process payment event", e);
        }
    }
}
