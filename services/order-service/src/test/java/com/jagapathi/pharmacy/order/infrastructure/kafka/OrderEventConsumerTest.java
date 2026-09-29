package com.jagapathi.pharmacy.order.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.order.application.service.OrderService;
import com.jagapathi.pharmacy.order.infrastructure.event.PaymentProcessedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Guards against the wire-format pitfall: PaymentProcessedEvent always serializes
 * both completedAt and failedAt keys (Jackson emits nulls for record fields), so
 * the consumer must check for a null JSON value, not mere key presence.
 */
@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private OrderService orderService;

    @Test
    void parsesPaymentCompletedEventWithNullFailedAt() {
        OrderEventConsumer consumer = new OrderEventConsumer(orderService, new ObjectMapper());
        String message = """
            {"eventType":"PaymentCompleted","eventId":"11111111-1111-1111-1111-111111111111",
             "orderId":"22222222-2222-2222-2222-222222222222","paymentId":"33333333-3333-3333-3333-333333333333",
             "amount":8.99,"currency":"USD","providerReference":"ref-1","failureCode":null,
             "completedAt":"2026-01-01T00:00:00Z","failedAt":null}
            """;

        consumer.onPaymentEvent(message);

        ArgumentCaptor<PaymentProcessedEvent> captor = ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(orderService).processPaymentCompleted(captor.capture());
        assertThat(captor.getValue().status).isEqualTo("SUCCESS");
    }

    @Test
    void parsesPaymentFailedEventWithNullCompletedAt() {
        OrderEventConsumer consumer = new OrderEventConsumer(orderService, new ObjectMapper());
        String message = """
            {"eventType":"PaymentFailed","eventId":"11111111-1111-1111-1111-111111111111",
             "orderId":"22222222-2222-2222-2222-222222222222","paymentId":"33333333-3333-3333-3333-333333333333",
             "amount":8.99,"currency":"USD","providerReference":null,"failureCode":"DECLINED",
             "completedAt":null,"failedAt":"2026-01-01T00:00:05Z"}
            """;

        consumer.onPaymentEvent(message);

        ArgumentCaptor<PaymentProcessedEvent> captor = ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(orderService).processPaymentCompleted(captor.capture());
        assertThat(captor.getValue().status).isEqualTo("FAILED");
    }
}
