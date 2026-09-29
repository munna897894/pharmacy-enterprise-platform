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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private PaymentEventConsumer paymentEventConsumer;

    @BeforeEach
    void setUp() {
        paymentEventConsumer = new PaymentEventConsumer(notificationService, processedEventRepository);
    }

    @Test
    void testHandlePaymentProcessedEvent() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        PaymentProcessedPayload payload = new PaymentProcessedPayload(
                paymentId, orderId, customerId, new BigDecimal("99.99"),
                "USD", "TXN-12345", Instant.now()
        );

        DomainEvent<PaymentProcessedPayload> event = new DomainEvent<>(
                eventId, "PaymentProcessed", 1, "Payment", paymentId,
                Instant.now(), "payment-service", UUID.randomUUID(),
                null, "system", payload
        );

        when(processedEventRepository.existsById(eventId.toString())).thenReturn(false);
        doNothing().when(notificationService).sendNotification(any(), any(), any(), any());
        when(processedEventRepository.save(any())).thenReturn(new ProcessedEvent(eventId.toString()));

        paymentEventConsumer.handlePaymentProcessed(event);

        verify(notificationService).sendNotification(any(), any(), any(), any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void testPaymentEventIdempotency() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        PaymentProcessedPayload payload = new PaymentProcessedPayload(
                paymentId, orderId, customerId, new BigDecimal("99.99"),
                "USD", "TXN-12345", Instant.now()
        );

        DomainEvent<PaymentProcessedPayload> event = new DomainEvent<>(
                eventId, "PaymentProcessed", 1, "Payment", paymentId,
                Instant.now(), "payment-service", UUID.randomUUID(),
                null, "system", payload
        );

        when(processedEventRepository.existsById(eventId.toString())).thenReturn(true);

        paymentEventConsumer.handlePaymentProcessed(event);

        verify(notificationService, never()).sendNotification(any(), any(), any(), any());
        verify(processedEventRepository, never()).save(any());
    }
}
