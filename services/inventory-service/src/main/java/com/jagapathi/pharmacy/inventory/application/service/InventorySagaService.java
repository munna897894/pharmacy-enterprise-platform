package com.jagapathi.pharmacy.inventory.application.service;

import com.jagapathi.pharmacy.observability.BusinessMetric;
import com.jagapathi.pharmacy.observability.OutboxMetricsSnapshot;
import com.jagapathi.pharmacy.observability.RecordBusinessMetric;
import com.jagapathi.pharmacy.observability.TraceContextHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.inventory.domain.model.InventoryReservation;
import com.jagapathi.pharmacy.inventory.domain.model.ReservationItem;
import com.jagapathi.pharmacy.inventory.domain.model.StockLevel;
import com.jagapathi.pharmacy.inventory.domain.repository.InventoryReservationRepository;
import com.jagapathi.pharmacy.inventory.domain.repository.StockLevelRepository;
import com.jagapathi.pharmacy.inventory.infrastructure.OutboxEvent;
import com.jagapathi.pharmacy.inventory.infrastructure.OutboxEventRepository;
import com.jagapathi.pharmacy.inventory.infrastructure.ProcessedEvent;
import com.jagapathi.pharmacy.inventory.infrastructure.ProcessedEventRepository;
import com.jagapathi.pharmacy.inventory.infrastructure.event.InventoryRejectedEvent;
import com.jagapathi.pharmacy.inventory.infrastructure.event.InventoryReleasedEvent;
import com.jagapathi.pharmacy.inventory.infrastructure.event.InventoryReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Choreography-saga participant: reserves stock on {@code OrderCreated}, then
 * commits or releases the reservation based on the payment outcome. Consumers
 * (Kafka listeners) hand off parsed events here; this class owns all
 * stock/reservation/outbox persistence for the saga.
 */
@Service
public class InventorySagaService {

    private static final long KAFKA_SEND_TIMEOUT_MS = 10_000L;
    private static final Logger log = LoggerFactory.getLogger(InventorySagaService.class);

    private static final String INVENTORY_EVENTS_TOPIC = "pharmacy.inventory.events.v1";
    private static final String INVENTORY_ORDER_CREATED_CONSUMER = "inventory-order-created-v1";
    private static final String INVENTORY_PAYMENT_RESULT_CONSUMER = "inventory-payment-result-v1";

    private final StockLevelRepository stockLevelRepository;
    private final InventoryReservationRepository reservationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    public InventorySagaService(StockLevelRepository stockLevelRepository,
                                 InventoryReservationRepository reservationRepository,
                                 OutboxEventRepository outboxEventRepository,
                                 ProcessedEventRepository processedEventRepository,
                                 ObjectMapper objectMapper,
                                 org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate) {
        this.stockLevelRepository = stockLevelRepository;
        this.reservationRepository = reservationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    @RecordBusinessMetric(BusinessMetric.INVENTORY_RESERVATION)
    public void handleOrderCreated(UUID eventId, UUID orderId, String pharmacyId, List<ReservationItem> items, Instant occurredAt) {
        if (processedEventRepository.existsByOrderIdAndEventId(orderId, eventId)) {
            return;
        }
        if (reservationRepository.findByOrderId(orderId).isPresent()) {
            markProcessed(orderId, eventId, INVENTORY_ORDER_CREATED_CONSUMER);
            return;
        }

        // Phase 1: read-only availability check across all items before mutating anything.
        for (ReservationItem item : items) {
            StockLevel stock = stockLevelRepository.findByPharmacyIdAndProductId(pharmacyId, item.productId()).orElse(null);
            if (stock == null || stock.getQuantityOnHand().compareTo(item.quantity()) < 0) {
                UUID resultEventId = publishRejected(orderId, "INSUFFICIENT_STOCK", occurredAt);
                markProcessed(orderId, eventId, INVENTORY_ORDER_CREATED_CONSUMER);
                log.atWarn()
                    .addKeyValue("eventName", "inventory.reservation.rejected")
                    .addKeyValue("orderId", orderId)
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("resultEventId", resultEventId)
                    .addKeyValue("outcome", "rejected")
                    .addKeyValue("failureCode", "INSUFFICIENT_STOCK")
                    .log("Inventory reservation lifecycle event");
                return;
            }
        }

        // Phase 2: apply stock decrements now that every line is known to be available.
        for (ReservationItem item : items) {
            StockLevel stock = stockLevelRepository.findByPharmacyIdAndProductId(pharmacyId, item.productId()).orElseThrow();
            stock.removeStock(item.quantity());
            stockLevelRepository.save(stock);
        }

        String itemsJson = writeItemsJson(items);
        InventoryReservation reservation = new InventoryReservation(orderId, pharmacyId, itemsJson);
        reservationRepository.save(reservation);

        UUID resultEventId = publishReserved(orderId, reservation.getId(), pharmacyId, occurredAt);
        markProcessed(orderId, eventId, INVENTORY_ORDER_CREATED_CONSUMER);
        log.atInfo()
            .addKeyValue("eventName", "inventory.reservation.completed")
            .addKeyValue("orderId", orderId)
            .addKeyValue("eventId", eventId)
            .addKeyValue("resultEventId", resultEventId)
            .addKeyValue("reservationId", reservation.getId())
            .addKeyValue("outcome", "reserved")
            .log("Inventory reservation lifecycle event");
    }

    @Transactional
    public void handlePaymentCompleted(UUID eventId, UUID orderId) {
        if (processedEventRepository.existsByOrderIdAndEventId(orderId, eventId)) {
            return;
        }
        boolean committed = reservationRepository.findByOrderId(orderId)
            .map(reservation -> {
                if (!"RESERVED".equals(reservation.getStatus())) {
                    return false;
                }
                reservation.commit();
                reservationRepository.save(reservation);
                return true;
            })
            .orElse(false);
        markProcessed(orderId, eventId, INVENTORY_PAYMENT_RESULT_CONSUMER);
        var lifecycleLog = log.atLevel(committed ? org.slf4j.event.Level.INFO : org.slf4j.event.Level.WARN)
            .addKeyValue("eventName", committed
                ? "inventory.reservation.committed" : "inventory.reservation.commit_skipped")
            .addKeyValue("orderId", orderId)
            .addKeyValue("eventId", eventId)
            .addKeyValue("outcome", committed ? "committed" : "unchanged");
        lifecycleLog.log("Inventory reservation lifecycle event");
    }

    @Transactional
    public void handlePaymentFailed(UUID eventId, UUID orderId) {
        if (processedEventRepository.existsByOrderIdAndEventId(orderId, eventId)) {
            return;
        }
        boolean released = reservationRepository.findByOrderId(orderId)
            .map(reservation -> {
                if (!"RESERVED".equals(reservation.getStatus())) {
                    return false;
                }
                releaseStock(reservation);
                reservation.release();
                reservationRepository.save(reservation);
                publishReleased(orderId, reservation.getId(), "PAYMENT_FAILED", Instant.now());
                return true;
            })
            .orElse(false);
        markProcessed(orderId, eventId, INVENTORY_PAYMENT_RESULT_CONSUMER);
        var lifecycleLog = log.atLevel(released ? org.slf4j.event.Level.INFO : org.slf4j.event.Level.WARN)
            .addKeyValue("eventName", released
                ? "inventory.reservation.released" : "inventory.reservation.release_skipped")
            .addKeyValue("orderId", orderId)
            .addKeyValue("eventId", eventId)
            .addKeyValue("outcome", released ? "released" : "unchanged");
        if (released) {
            lifecycleLog.addKeyValue("failureCode", "PAYMENT_FAILED");
        }
        lifecycleLog.log("Inventory reservation lifecycle event");
    }

    private void releaseStock(InventoryReservation reservation) {
        try {
            List<ReservationItem> items = readItemsJson(reservation.getItemsJson());
            for (ReservationItem item : items) {
                stockLevelRepository.findByPharmacyIdAndProductId(reservation.getPharmacyId(), item.productId())
                    .ifPresent(stock -> {
                        stock.addStock(item.quantity());
                        stockLevelRepository.save(stock);
                    });
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to release reserved stock for order " + reservation.getOrderId(), e);
        }
    }

    private UUID publishReserved(UUID orderId, UUID reservationId, String pharmacyId, Instant occurredAt) {
        try {
            UUID eventId = UUID.randomUUID();
            var payload = new InventoryReservedEvent(eventId, orderId, reservationId, pharmacyId, Instant.now());
            saveOutbox(orderId, "InventoryReserved", objectMapper.writeValueAsString(payload));
            return eventId;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish InventoryReserved for order " + orderId, e);
        }
    }

    private UUID publishRejected(UUID orderId, String reasonCode, Instant occurredAt) {
        try {
            UUID eventId = UUID.randomUUID();
            var payload = new InventoryRejectedEvent(eventId, orderId, reasonCode, Instant.now());
            saveOutbox(orderId, "InventoryRejected", objectMapper.writeValueAsString(payload));
            return eventId;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish InventoryRejected for order " + orderId, e);
        }
    }

    private UUID publishReleased(UUID orderId, UUID reservationId, String reasonCode, Instant occurredAt) {
        try {
            UUID eventId = UUID.randomUUID();
            var payload = new InventoryReleasedEvent(eventId, orderId, reservationId, reasonCode, occurredAt);
            saveOutbox(orderId, "InventoryReleased", objectMapper.writeValueAsString(payload));
            log.atInfo()
                .addKeyValue("eventName", "inventory.reservation.release_event.created")
                .addKeyValue("orderId", orderId)
                .addKeyValue("eventId", eventId)
                .addKeyValue("outcome", "queued")
                .log("Inventory reservation lifecycle event");
            return eventId;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish InventoryReleased for order " + orderId, e);
        }
    }

    private void saveOutbox(UUID orderId, String eventType, String payload) throws Exception {
        OutboxEvent outboxEvent = new OutboxEvent(
            UUID.randomUUID(),
            "InventoryReservation",
            orderId,
            eventType,
            INVENTORY_EVENTS_TOPIC,
            orderId.toString(),
            payload,
            objectMapper.writeValueAsString(TraceContextHeaders.capture())
        );
        outboxEventRepository.save(outboxEvent);
    }

    private void markProcessed(UUID orderId, UUID eventId, String consumerName) {
        processedEventRepository.save(new ProcessedEvent(orderId, eventId, consumerName));
    }

    @Transactional
    public OutboxMetricsSnapshot publishOutboxEvents() {
        var unpublished = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        long publishedCount = 0;
        long failedCount = 0;
        for (OutboxEvent event : unpublished) {
            try {
                var record = new org.apache.kafka.clients.producer.ProducerRecord<String, String>(
                    event.getTopic(), event.getEventKey(), event.getPayload());
                var capturedHeaders = readOutboxHeaders(event.getHeaders());
                capturedHeaders.forEach((name, value) ->
                    record.headers().add(name, value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                TraceContextHeaders.callInCapturedContext(capturedHeaders, () -> kafkaTemplate.send(record))
                    .get(KAFKA_SEND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                event.recordPublishSuccess();
                publishedCount++;
            } catch (Exception e) {
                event.recordPublishFailure(e.getMessage());
                failedCount++;
            }
            outboxEventRepository.save(event);
        }
        var pendingEvents = unpublished.stream()
            .filter(event -> !Boolean.TRUE.equals(event.getPublished()))
            .toList();
        long oldestAgeSeconds = pendingEvents.stream()
            .map(OutboxEvent::getCreatedAt)
            .min(Instant::compareTo)
            .map(createdAt -> Math.max(0, Duration.between(createdAt, Instant.now()).getSeconds()))
            .orElse(0L);
        return new OutboxMetricsSnapshot(pendingEvents.size(), oldestAgeSeconds, publishedCount, failedCount);
    }

    private java.util.Map<String, String> readOutboxHeaders(String serializedHeaders) throws Exception {
        var allowed = new java.util.LinkedHashMap<String, String>();
        if (serializedHeaders == null || serializedHeaders.isBlank()) {
            return allowed;
        }
        var fields = objectMapper.readTree(serializedHeaders).fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (TraceContextHeaders.isAllowedHeader(entry.getKey()) && entry.getValue().isTextual()) {
                allowed.put(entry.getKey(), entry.getValue().asText());
            }
        }
        return allowed;
    }

    private String writeItemsJson(List<ReservationItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize reservation items", e);
        }
    }

    private List<ReservationItem> readItemsJson(String json) throws Exception {
        return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, ReservationItem.class));
    }
}
