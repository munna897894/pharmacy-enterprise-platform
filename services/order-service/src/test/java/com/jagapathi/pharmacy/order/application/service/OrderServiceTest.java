package com.jagapathi.pharmacy.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jagapathi.pharmacy.order.api.request.CreateOrderRequest;
import com.jagapathi.pharmacy.order.api.response.OrderResponse;
import com.jagapathi.pharmacy.order.domain.*;
import com.jagapathi.pharmacy.order.infrastructure.*;
import com.jagapathi.pharmacy.order.infrastructure.client.InventoryAvailabilityClient;
import com.jagapathi.pharmacy.order.infrastructure.client.InventoryAvailabilityResponse;
import com.jagapathi.pharmacy.order.infrastructure.event.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OrderService Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private InventoryAvailabilityClient inventoryAvailabilityClient;
    @Mock
    private com.jagapathi.pharmacy.order.infrastructure.IdempotencyRecordRepository idempotencyRecordRepository;

    private OrderService orderService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        when(inventoryAvailabilityClient.checkAvailability(any(), any(), any()))
            .thenReturn(new InventoryAvailabilityResponse(true, "available"));
        orderService = new OrderService(orderRepository, orderItemRepository, outboxEventRepository,
            processedEventRepository, statusHistoryRepository, kafkaTemplate, objectMapper, inventoryAvailabilityClient,
            idempotencyRecordRepository);
    }

    @Test
    @DisplayName("Should create order with validation")
    void testCreateOrderWithValidation() {
        UUID customerId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();

        BigDecimal quantity = BigDecimal.valueOf(2);
        BigDecimal unitPrice = BigDecimal.valueOf(27.50);
        BigDecimal lineTotal = quantity.multiply(unitPrice); // 55.00

        CreateOrderRequest request = new CreateOrderRequest(
            customerId, prescriptionId, pharmacyId, "USD", lineTotal,
            List.of(new CreateOrderRequest.OrderItemRequest(UUID.randomUUID(), quantity, unitPrice))
        );

        UUID orderId = UUID.randomUUID();
        Order savedOrder = new Order(orderId, customerId, prescriptionId, pharmacyId,
            BigDecimal.ZERO, BigDecimal.ZERO, lineTotal, "USD");

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            // Return with appropriate status based on what was saved
            return order;
        });
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should reject order with empty items")
    void testCreateOrderWithEmptyItems() {
        CreateOrderRequest request = new CreateOrderRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "USD", BigDecimal.valueOf(55.00),
            List.of()
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one item");
    }

    @Test
    @DisplayName("Should reject order with mismatched total")
    void testCreateOrderWithMismatchedTotal() {
        CreateOrderRequest request = new CreateOrderRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "USD", BigDecimal.valueOf(100.00),
            List.of(new CreateOrderRequest.OrderItemRequest(UUID.randomUUID(), BigDecimal.valueOf(2), BigDecimal.valueOf(25.00)))
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("total does not match");
    }

    @Test
    @DisplayName("Should replay same order for same Idempotency-Key and same request body")
    void testCreateOrderIdempotentReplay() {
        UUID customerId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        UUID medicationId = UUID.randomUUID();
        BigDecimal quantity = BigDecimal.valueOf(2);
        BigDecimal unitPrice = BigDecimal.valueOf(27.50);
        BigDecimal lineTotal = quantity.multiply(unitPrice);

        CreateOrderRequest request = new CreateOrderRequest(
            customerId, prescriptionId, pharmacyId, "USD", lineTotal,
            List.of(new CreateOrderRequest.OrderItemRequest(medicationId, quantity, unitPrice))
        );

        UUID existingOrderId = UUID.randomUUID();
        Order existingOrder = new Order(existingOrderId, customerId, prescriptionId, pharmacyId,
            BigDecimal.ZERO, BigDecimal.ZERO, lineTotal, "USD");
        String idempotencyKey = "same-key";

        // First call: no prior record, creates a new order and stores the record.
        when(idempotencyRecordRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey))
            .thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());

        OrderResponse first = orderService.createOrder(request, idempotencyKey);
        assertThat(first).isNotNull();
        var captor = org.mockito.ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecordRepository).save(captor.capture());
        String requestHash = captor.getValue().getRequestHash();

        // Second call: same key + same body must replay the original order without re-creating it.
        when(idempotencyRecordRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey))
            .thenReturn(Optional.of(new IdempotencyRecord(idempotencyKey, customerId, requestHash, existingOrderId)));
        when(orderRepository.findById(existingOrderId)).thenReturn(Optional.of(existingOrder));

        OrderResponse second = orderService.createOrder(request, idempotencyKey);

        assertThat(second.getId()).isEqualTo(existingOrderId);
        verify(orderRepository, times(2)).save(any(Order.class)); // still just the first call's two saves
    }

    @Test
    @DisplayName("Should reject same Idempotency-Key reused with a different request body")
    void testCreateOrderIdempotentConflict() {
        UUID customerId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        String idempotencyKey = "conflicting-key";

        CreateOrderRequest request = new CreateOrderRequest(
            customerId, prescriptionId, pharmacyId, "USD", BigDecimal.valueOf(55.00),
            List.of(new CreateOrderRequest.OrderItemRequest(UUID.randomUUID(), BigDecimal.valueOf(2), BigDecimal.valueOf(27.50)))
        );

        when(idempotencyRecordRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey))
            .thenReturn(Optional.of(new IdempotencyRecord(idempotencyKey, customerId, "different-hash", UUID.randomUUID())));

        assertThatThrownBy(() -> orderService.createOrder(request, idempotencyKey))
            .isInstanceOf(IdempotencyConflictException.class)
            .hasMessageContaining(idempotencyKey);
    }

    @Test
    @DisplayName("Should process inventory reserved event idempotently")
    void testProcessInventoryReservedIdempotent() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        when(processedEventRepository.existsByOrderIdAndEventId(orderId, eventId)).thenReturn(true);

        Order order = new Order(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(55.00), "USD");
        InventoryReservedEvent event = new InventoryReservedEvent(eventId, orderId, UUID.randomUUID(), Instant.now());

        orderService.processInventoryReserved(event);

        verify(orderRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should process payment completed event")
    void testProcessPaymentCompleted() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Order order = new Order(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(55.00), "USD");
        order.transitionTo(OrderStatus.INVENTORY_PENDING);
        order.transitionTo(OrderStatus.INVENTORY_RESERVED);
        order.transitionTo(OrderStatus.PAYMENT_PENDING);

        when(processedEventRepository.existsByOrderIdAndEventId(orderId, eventId)).thenReturn(false);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        PaymentProcessedEvent event = new PaymentProcessedEvent(eventId, orderId, "SUCCESS", Instant.now());
        orderService.processPaymentCompleted(event);

        verify(orderRepository).findById(orderId);
        verify(orderRepository).save(any(Order.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Should mark order ready for pickup")
    void testMarkOrderReadyForPickup() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(55.00), "USD");
        order.transitionTo(OrderStatus.INVENTORY_PENDING);
        order.transitionTo(OrderStatus.INVENTORY_RESERVED);
        order.transitionTo(OrderStatus.PAYMENT_PENDING);
        order.transitionTo(OrderStatus.CONFIRMED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.markOrderReadyForPickup(orderId);

        verify(orderRepository).findById(orderId);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("Should cancel order")
    void testCancelOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(55.00), "USD");
        order.transitionTo(OrderStatus.INVENTORY_PENDING);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.cancelOrder(orderId, "Test cancellation");

        verify(orderRepository).findById(orderId);
        verify(orderRepository).save(any(Order.class));
        verify(statusHistoryRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("Should get order by ID")
    void testGetOrderById() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(55.00), "USD");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("Should throw OrderNotFoundException when order not found")
    void testGetOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(orderId))
            .isInstanceOf(OrderNotFoundException.class);
    }
}
