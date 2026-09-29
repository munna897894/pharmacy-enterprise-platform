package com.jagapathi.pharmacy.order.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.order.application.service.OrderService;
import com.jagapathi.pharmacy.order.infrastructure.event.InventoryReservedEvent;
import com.jagapathi.pharmacy.order.infrastructure.event.InventoryRejectedEvent;
import com.jagapathi.pharmacy.order.infrastructure.event.PaymentProcessedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OrderEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "pharmacy.inventory.events.v1",
        groupId = "order-inventory-result-v1",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryEvent(String message) {
        try {
            var jsonNode = objectMapper.readTree(message);
            String eventType = jsonNode.get("eventType").asText();

            if ("InventoryReserved".equals(eventType)) {
                var event = new InventoryReservedEvent(
                    UUID.fromString(jsonNode.get("eventId").asText()),
                    UUID.fromString(jsonNode.get("orderId").asText()),
                    UUID.fromString(jsonNode.get("reservationId").asText()),
                    Instant.parse(jsonNode.get("occurredAt").asText())
                );
                orderService.processInventoryReserved(event);
            } else if ("InventoryRejected".equals(eventType)) {
                var event = new InventoryRejectedEvent(
                    UUID.fromString(jsonNode.get("eventId").asText()),
                    UUID.fromString(jsonNode.get("orderId").asText()),
                    jsonNode.get("reasonCode").asText(),
                    Instant.parse(jsonNode.get("occurredAt").asText())
                );
                orderService.processInventoryRejected(event);
            }
        } catch (Exception e) {
            // Log and handle error appropriately
            throw new RuntimeException("Failed to process inventory event", e);
        }
    }

    @KafkaListener(
        topics = "pharmacy.payment.events.v1",
        groupId = "order-payment-result-v1",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentEvent(String message) {
        try {
            var jsonNode = objectMapper.readTree(message);
            String eventType = jsonNode.get("eventType").asText();

            if ("PaymentCompleted".equals(eventType) || "PaymentFailed".equals(eventType)) {
                String status = "PaymentCompleted".equals(eventType) ? "SUCCESS" : "FAILED";
                // Both fields exist on the wire (Jackson serializes record fields even when null),
                // so check for a non-null value rather than mere key presence.
                var completedAtNode = jsonNode.get("completedAt");
                var failedAtNode = jsonNode.get("failedAt");
                String timestamp = (completedAtNode != null && !completedAtNode.isNull())
                    ? completedAtNode.asText()
                    : failedAtNode.asText();
                var event = new PaymentProcessedEvent(
                    UUID.fromString(jsonNode.get("eventId").asText()),
                    UUID.fromString(jsonNode.get("orderId").asText()),
                    status,
                    Instant.parse(timestamp)
                );
                orderService.processPaymentCompleted(event);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process payment event", e);
        }
    }
}
