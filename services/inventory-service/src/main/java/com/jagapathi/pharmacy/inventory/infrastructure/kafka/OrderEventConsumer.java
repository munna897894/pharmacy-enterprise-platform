package com.jagapathi.pharmacy.inventory.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.inventory.application.service.InventorySagaService;
import com.jagapathi.pharmacy.inventory.domain.model.ReservationItem;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consumes {@code pharmacy.order.events.v1}. The wire format for OrderCreated
 * (order-service's OrderCreatedEvent) has no eventType discriminator, so every
 * message on this topic is treated as OrderCreated by structure.
 */
@Component
public class OrderEventConsumer {

    private final InventorySagaService sagaService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(InventorySagaService sagaService, ObjectMapper objectMapper) {
        this.sagaService = sagaService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "pharmacy.order.events.v1",
        groupId = "inventory-order-created-v1",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            UUID eventId = UUID.fromString(node.get("eventId").asText());
            UUID orderId = UUID.fromString(node.get("orderId").asText());
            String pharmacyId = node.get("pharmacyId").asText();
            Instant occurredAt = Instant.parse(node.get("occurredAt").asText());

            List<ReservationItem> items = new java.util.ArrayList<>();
            for (JsonNode itemNode : node.get("items")) {
                items.add(new ReservationItem(
                    itemNode.get("medicationId").asText(),
                    new BigDecimal(itemNode.get("quantity").asText())
                ));
            }

            sagaService.handleOrderCreated(eventId, orderId, pharmacyId, items, occurredAt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process order event", e);
        }
    }
}
