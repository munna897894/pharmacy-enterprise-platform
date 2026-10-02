package com.jagapathi.pharmacy.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.order.api.request.CreateOrderRequest;
import com.jagapathi.pharmacy.order.infrastructure.OrderItemRepository;
import com.jagapathi.pharmacy.order.infrastructure.OrderRepository;
import com.jagapathi.pharmacy.order.infrastructure.OrderStatusHistoryRepository;
import com.jagapathi.pharmacy.order.infrastructure.OutboxEventRepository;
import com.jagapathi.pharmacy.order.infrastructure.ProcessedEventRepository;
import com.jagapathi.pharmacy.order.infrastructure.client.InventoryAvailabilityClient;
import com.jagapathi.pharmacy.order.infrastructure.client.InventoryAvailabilityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import io.micrometer.observation.ObservationRegistry;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceInventoryCheckTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private OrderStatusHistoryRepository statusHistoryRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private InventoryAvailabilityClient inventoryAvailabilityClient;
    @Mock private com.jagapathi.pharmacy.order.infrastructure.IdempotencyRecordRepository idempotencyRecordRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
            orderRepository,
            orderItemRepository,
            outboxEventRepository,
            processedEventRepository,
            statusHistoryRepository,
            kafkaTemplate,
            objectMapper,
            inventoryAvailabilityClient,
            idempotencyRecordRepository,
            ObservationRegistry.create()
        );
    }

    @Test
    void createOrderRejectsWhenInventoryUnavailable() {
        var request = new CreateOrderRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "USD",
            new BigDecimal("20.00"),
            List.of(new CreateOrderRequest.OrderItemRequest(
                UUID.randomUUID(),
                new BigDecimal("2"),
                new BigDecimal("10.00")
            ))
        );

        when(inventoryAvailabilityClient.checkAvailability(any(), any(), any()))
            .thenReturn(new InventoryAvailabilityResponse(false, "Insufficient stock"));

        assertThatThrownBy(() -> orderService.createOrder(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Insufficient stock");
    }
}
