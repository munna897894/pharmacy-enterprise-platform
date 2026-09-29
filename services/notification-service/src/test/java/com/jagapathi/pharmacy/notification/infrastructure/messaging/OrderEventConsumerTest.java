package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import com.jagapathi.pharmacy.notification.application.NotificationService;
import com.jagapathi.pharmacy.notification.domain.ProcessedEvent;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.ProcessedEventRepository;
import com.jagapathi.pharmacy.platform.events.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private OrderEventConsumer orderEventConsumer;

    @BeforeEach
    void setUp() {
        orderEventConsumer = new OrderEventConsumer(notificationService, processedEventRepository);
    }

    @Test
    void testHandleOrderCreatedEvent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();

        OrderCreatedPayload payload = new OrderCreatedPayload(
                orderId, customerId, prescriptionId, pharmacyId,
                "USD", new BigDecimal("99.99"),
                java.util.List.of()
        );

        DomainEvent<OrderCreatedPayload> event = new DomainEvent<>(
                eventId, "OrderCreated", 1, "Order", orderId,
                Instant.now(), "order-service", UUID.randomUUID(),
                null, "system", payload
        );

        when(processedEventRepository.existsById(eventId.toString())).thenReturn(false);
        doNothing().when(notificationService).sendNotification(any(), any(), any(), any());
        when(processedEventRepository.save(any())).thenReturn(new ProcessedEvent(eventId.toString()));

        orderEventConsumer.handleOrderCreated(event);

        verify(notificationService).sendNotification(any(), any(), any(), any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void testHandleOrderCreatedEventIdempotency() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();

        OrderCreatedPayload payload = new OrderCreatedPayload(
                orderId, customerId, prescriptionId, pharmacyId,
                "USD", new BigDecimal("99.99"),
                java.util.List.of()
        );

        DomainEvent<OrderCreatedPayload> event = new DomainEvent<>(
                eventId, "OrderCreated", 1, "Order", orderId,
                Instant.now(), "order-service", UUID.randomUUID(),
                null, "system", payload
        );

        when(processedEventRepository.existsById(eventId.toString())).thenReturn(true);

        orderEventConsumer.handleOrderCreated(event);

        verify(notificationService, never()).sendNotification(any(), any(), any(), any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void testHandleOrderShippedEvent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();

        OrderShippedPayload payload = new OrderShippedPayload(
                orderId, customerId, pharmacyId, Instant.now()
        );

        DomainEvent<OrderShippedPayload> event = new DomainEvent<>(
                eventId, "OrderShipped", 1, "Order", orderId,
                Instant.now(), "order-service", UUID.randomUUID(),
                null, "system", payload
        );

        when(processedEventRepository.existsById(eventId.toString())).thenReturn(false);
        doNothing().when(notificationService).sendNotification(any(), any(), any(), any());
        when(processedEventRepository.save(any())).thenReturn(new ProcessedEvent(eventId.toString()));

        orderEventConsumer.handleOrderShipped(event);

        verify(notificationService).sendNotification(any(), any(), any(), any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void testHandleEventFailure() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();

        OrderCreatedPayload payload = new OrderCreatedPayload(
                orderId, customerId, prescriptionId, pharmacyId,
                "USD", new BigDecimal("99.99"),
                java.util.List.of()
        );

        DomainEvent<OrderCreatedPayload> event = new DomainEvent<>(
                eventId, "OrderCreated", 1, "Order", orderId,
                Instant.now(), "order-service", UUID.randomUUID(),
                null, "system", payload
        );

        when(processedEventRepository.existsById(eventId.toString())).thenReturn(false);
        doThrow(new RuntimeException("Service unavailable"))
                .when(notificationService).sendNotification(any(), any(), any(), any());

        assertThatThrownBy(() -> orderEventConsumer.handleOrderCreated(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to handle OrderCreated event");
    }
}
