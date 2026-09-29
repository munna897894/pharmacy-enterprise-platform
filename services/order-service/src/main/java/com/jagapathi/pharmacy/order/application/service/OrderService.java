package com.jagapathi.pharmacy.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.observability.BusinessMetric;
import com.jagapathi.pharmacy.observability.OutboxMetricsSnapshot;
import com.jagapathi.pharmacy.observability.RecordBusinessMetric;
import com.jagapathi.pharmacy.observability.TraceContextHeaders;
import com.jagapathi.pharmacy.order.api.request.CreateOrderRequest;
import com.jagapathi.pharmacy.order.api.response.OrderResponse;
import com.jagapathi.pharmacy.order.domain.*;
import com.jagapathi.pharmacy.order.infrastructure.*;
import com.jagapathi.pharmacy.order.infrastructure.client.InventoryAvailabilityClient;
import com.jagapathi.pharmacy.order.infrastructure.event.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final InventoryAvailabilityClient inventoryAvailabilityClient;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                       OutboxEventRepository outboxEventRepository, ProcessedEventRepository processedEventRepository,
                       OrderStatusHistoryRepository statusHistoryRepository, KafkaTemplate<String, String> kafkaTemplate,
                       ObjectMapper objectMapper, InventoryAvailabilityClient inventoryAvailabilityClient,
                       IdempotencyRecordRepository idempotencyRecordRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.inventoryAvailabilityClient = inventoryAvailabilityClient;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional
    @RecordBusinessMetric(BusinessMetric.ORDER_CREATED)
    public OrderResponse createOrder(CreateOrderRequest request) {
        return doCreateOrder(request);
    }

    /**
     * Idempotency-Key aware entry point used by the controller. Same key + same
     * request body replays the original order; same key + a different body is a
     * conflict (IdempotencyConflictException -> 409).
     */
    @Transactional
    @RecordBusinessMetric(BusinessMetric.ORDER_CREATED)
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        String requestHash = hashRequest(request);
        var existing = idempotencyRecordRepository.findByCustomerIdAndIdempotencyKey(
            request.getCustomerId(), idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                    "Idempotency-Key '" + idempotencyKey + "' was already used with a different request body");
            }
            Order order = orderRepository.findById(record.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(record.getOrderId()));
            return new OrderResponse(order);
        }

        OrderResponse response = doCreateOrder(request);
        idempotencyRecordRepository.save(new IdempotencyRecord(
            idempotencyKey, request.getCustomerId(), requestHash, response.getId()));
        return response;
    }

    private String hashRequest(CreateOrderRequest request) {
        String canonical = request.getCustomerId() + "|" + request.getPrescriptionId() + "|"
            + request.getPharmacyId() + "|" + request.getCurrency() + "|" + request.getTotal() + "|"
            + request.getItems().stream()
                .map(i -> i.getMedicationId() + ":" + i.getQuantity() + ":" + i.getUnitPrice())
                .sorted()
                .reduce("", (a, b) -> a + ";" + b);
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private OrderResponse doCreateOrder(CreateOrderRequest request) {
        // Validate that at least one item is provided
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }

        // Calculate totals and validate
        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            var availability = inventoryAvailabilityClient.checkAvailability(
                request.getPharmacyId(),
                itemRequest.getMedicationId(),
                itemRequest.getQuantity()
            );
            if (availability == null || !availability.available()) {
                throw new IllegalArgumentException(availability == null || availability.reason() == null
                    ? "Inventory is unavailable for one or more items"
                    : availability.reason());
            }
            BigDecimal lineTotal = itemRequest.getQuantity().multiply(itemRequest.getUnitPrice());
            calculatedTotal = calculatedTotal.add(lineTotal);
        }

        // Validate total with 0.01 tolerance for rounding
        if (calculatedTotal.subtract(request.getTotal()).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new IllegalArgumentException("Order total does not match sum of line items");
        }

        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, request.getCustomerId(), request.getPrescriptionId(),
            request.getPharmacyId(), BigDecimal.ZERO, BigDecimal.ZERO, request.getTotal(), request.getCurrency());

        // Add items to order
        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            BigDecimal lineTotal = itemRequest.getQuantity().multiply(itemRequest.getUnitPrice());
            OrderItem item = new OrderItem(UUID.randomUUID(), itemRequest.getMedicationId(),
                itemRequest.getQuantity(), itemRequest.getUnitPrice(), lineTotal);
            order.addItem(item);
        }

        // Save order
        Order savedOrder = orderRepository.save(order);

        // Transition to INVENTORY_PENDING and create outbox event
        savedOrder.transitionTo(OrderStatus.INVENTORY_PENDING);
        orderRepository.save(savedOrder);

        // Create and save OrderCreatedEvent to outbox
        createOutboxEvent(orderId, savedOrder);

        return new OrderResponse(savedOrder);
    }

    private void createOutboxEvent(UUID orderId, Order order) {
        try {
            UUID eventId = UUID.randomUUID();
            var eventPayload = new OrderCreatedEvent(
                eventId,
                orderId,
                order.getCustomerId(),
                order.getPrescriptionId(),
                order.getPharmacyId(),
                order.getCurrency(),
                order.getTotal(),
                Instant.now(),
                order.getItems().stream()
                    .map(item -> new OrderCreatedEvent.OrderItemPayload(
                        item.getMedicationId(),
                        item.getQuantity(),
                        item.getUnitPrice()
                    ))
                    .toList()
            );

            String payload = objectMapper.writeValueAsString(eventPayload);
            OutboxEvent outboxEvent = new OutboxEvent(
                UUID.randomUUID(),
                "Order",
                orderId,
                "OrderCreated",
                "pharmacy.order.events.v1",
                orderId.toString(),
                payload,
                objectMapper.writeValueAsString(TraceContextHeaders.capture())
            );

            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create outbox event", e);
        }
    }

    @Transactional
    public void processInventoryReserved(InventoryReservedEvent event) {
        // Check for duplicate event
        if (processedEventRepository.existsByOrderIdAndEventId(event.orderId, event.eventId)) {
            return; // Already processed, skip
        }

        Order order = orderRepository.findById(event.orderId)
            .orElseThrow(() -> new OrderNotFoundException(event.orderId));

        // Transition order to INVENTORY_RESERVED
        order.transitionTo(OrderStatus.INVENTORY_RESERVED);
        order.transitionTo(OrderStatus.PAYMENT_PENDING);

        // Save order with status transition
        orderRepository.save(order);

        // Record processed event for idempotency
        ProcessedEvent processedEvent = new ProcessedEvent(event.orderId, event.eventId, "inventory-order-events");
        processedEventRepository.save(processedEvent);

        // Create status history record
        UUID historyId = UUID.randomUUID();
        OrderStatusHistory history = new OrderStatusHistory(
            historyId, event.orderId, OrderStatus.INVENTORY_PENDING, OrderStatus.PAYMENT_PENDING,
            "Inventory reserved", event.eventId
        );
        statusHistoryRepository.save(history);
    }

    @Transactional
    public void processInventoryRejected(InventoryRejectedEvent event) {
        // Check for duplicate event
        if (processedEventRepository.existsByOrderIdAndEventId(event.orderId, event.eventId)) {
            return;
        }

        Order order = orderRepository.findById(event.orderId)
            .orElseThrow(() -> new OrderNotFoundException(event.orderId));

        order.transitionTo(OrderStatus.CANCELLED_INVENTORY);
        orderRepository.save(order);

        ProcessedEvent processedEvent = new ProcessedEvent(event.orderId, event.eventId, "inventory-order-rejected");
        processedEventRepository.save(processedEvent);

        // Create status history
        UUID historyId = UUID.randomUUID();
        OrderStatusHistory history = new OrderStatusHistory(
            historyId, event.orderId, OrderStatus.INVENTORY_PENDING, OrderStatus.CANCELLED_INVENTORY,
            "Inventory rejected: " + event.reasonCode, event.eventId
        );
        statusHistoryRepository.save(history);
    }

    @Transactional
    public void processPaymentCompleted(PaymentProcessedEvent event) {
        if (processedEventRepository.existsByOrderIdAndEventId(event.orderId, event.eventId)) {
            return;
        }

        Order order = orderRepository.findById(event.orderId)
            .orElseThrow(() -> new OrderNotFoundException(event.orderId));

        if ("SUCCESS".equals(event.status)) {
            order.transitionTo(OrderStatus.CONFIRMED);
        } else {
            order.transitionTo(OrderStatus.CANCELLED_PAYMENT);
        }

        orderRepository.save(order);

        ProcessedEvent processedEvent = new ProcessedEvent(event.orderId, event.eventId, "payment-order-events");
        processedEventRepository.save(processedEvent);

        OrderStatus fromStatus = "SUCCESS".equals(event.status) ? OrderStatus.PAYMENT_PENDING : OrderStatus.PAYMENT_PENDING;
        OrderStatus toStatus = "SUCCESS".equals(event.status) ? OrderStatus.CONFIRMED : OrderStatus.CANCELLED_PAYMENT;

        UUID historyId = UUID.randomUUID();
        OrderStatusHistory history = new OrderStatusHistory(
            historyId, event.orderId, fromStatus, toStatus,
            "Payment " + ("SUCCESS".equals(event.status) ? "completed" : "failed"), event.eventId
        );
        statusHistoryRepository.save(history);
    }

    @Transactional
    public void markOrderReadyForPickup(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException("Order must be in CONFIRMED status to mark as ready for pickup");
        }

        order.transitionTo(OrderStatus.READY_FOR_PICKUP);
        orderRepository.save(order);
    }

    @Transactional
    public void completeOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new InvalidOrderStateException("Order must be in READY_FOR_PICKUP status to complete");
        }

        order.transitionTo(OrderStatus.COMPLETED);
        for (OrderItem item : order.getItems()) {
            item.transitionTo(OrderItemStatus.DELIVERED);
        }
        orderRepository.save(order);
    }

    @Transactional
    public void cancelOrder(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus().toString().contains("CANCELLED")) {
            throw new InvalidOrderStateException("Cannot cancel order in status: " + order.getStatus());
        }

        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = currentStatus == OrderStatus.INVENTORY_PENDING || currentStatus == OrderStatus.INVENTORY_RESERVED
            ? OrderStatus.CANCELLED_INVENTORY
            : OrderStatus.CANCELLED_PAYMENT;

        order.transitionTo(newStatus);
        orderRepository.save(order);

        // Create status history
        UUID historyId = UUID.randomUUID();
        OrderStatusHistory history = new OrderStatusHistory(
            historyId, orderId, currentStatus, newStatus, "Cancelled: " + reason, null
        );
        statusHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        return new OrderResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getCustomerOrders(UUID customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable)
            .map(OrderResponse::new);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getPharmacyOrders(UUID pharmacyId, Pageable pageable) {
        return orderRepository.findByPharmacyId(pharmacyId, pageable)
            .map(OrderResponse::new);
    }

    @Transactional
    public OutboxMetricsSnapshot publishOutboxEvents() {
        var unpublishedEvents = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        long publishedCount = 0;
        long failedCount = 0;

        for (OutboxEvent outboxEvent : unpublishedEvents) {
            try {
                var record = new org.apache.kafka.clients.producer.ProducerRecord<String, String>(
                    outboxEvent.getTopic(), outboxEvent.getEventKey(), outboxEvent.getPayload());
                var capturedHeaders = readOutboxHeaders(outboxEvent.getHeaders());
                capturedHeaders.forEach((name, value) ->
                    record.headers().add(name, value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                TraceContextHeaders.runInCapturedContext(capturedHeaders, () -> kafkaTemplate.send(record));
                outboxEvent.recordPublishSuccess();
                outboxEventRepository.save(outboxEvent);
                publishedCount++;
            } catch (Exception e) {
                outboxEvent.recordPublishFailure(e.getMessage());
                outboxEventRepository.save(outboxEvent);
                failedCount++;
            }
        }
        return outboxSnapshot(unpublishedEvents, publishedCount, failedCount);
    }

    private OutboxMetricsSnapshot outboxSnapshot(java.util.List<OutboxEvent> events,
                                                 long publishedCount, long failedCount) {
        var pendingEvents = events.stream()
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
}
