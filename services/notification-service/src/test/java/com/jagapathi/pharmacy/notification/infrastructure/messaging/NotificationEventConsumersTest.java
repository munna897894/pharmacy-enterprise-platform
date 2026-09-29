package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.notification.application.NotificationEventHandler;
import com.jagapathi.pharmacy.observability.CorrelationIdContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationEventConsumersTest {

    private static final String EVENT_ID = "88772f7c-1014-4314-8ca7-1331b2459ace";
    private static final UUID ORDER_ID = UUID.fromString("feb836ca-e0d4-454b-88eb-74f231b90470");
    private static final UUID CUSTOMER_ID = UUID.fromString("0f93fc97-c263-493e-bcd7-a07e7f9d2119");
    private static final UUID PRESCRIPTION_ID = UUID.fromString("1af95469-fc0b-48f4-952f-7fc861e759e4");

    private NotificationEventHandler handler;
    private NotificationEventConsumers consumers;

    @BeforeEach
    void setUp() {
        handler = mock(NotificationEventHandler.class);
        consumers = new NotificationEventConsumers(new ObjectMapper(), handler);
    }

    @Test
    void flatOrderCreatedWithoutEventTypeIsRecognised() {
        consumers.handleOrderEvent("""
            {"eventId":"%s","orderId":"%s","customerId":"%s","currency":"USD","total":25.0,
             "items":[{"medicationId":"550e8400-e29b-41d4-a716-446655440001","quantity":2,"unitPrice":12.5}]}
            """.formatted(EVENT_ID, ORDER_ID, CUSTOMER_ID));

        verify(handler).onOrderCreated(EVENT_ID, ORDER_ID, CUSTOMER_ID);
    }

    @Test
    void envelopeOrderCreatedReadsPayload() {
        consumers.handleOrderEvent("""
            {"eventId":"%s","eventType":"OrderCreated","aggregateType":"Order",
             "payload":{"orderId":"%s","customerId":"%s","items":[]}}
            """.formatted(EVENT_ID, ORDER_ID, CUSTOMER_ID));

        verify(handler).onOrderCreated(EVENT_ID, ORDER_ID, CUSTOMER_ID);
    }

    @Test
    void unrelatedOrderEventsAreIgnored() {
        consumers.handleOrderEvent("""
            {"eventId":"%s","eventType":"OrderConfirmed","payload":{"orderId":"%s"}}
            """.formatted(EVENT_ID, ORDER_ID));

        verifyNoInteractions(handler);
    }

    @Test
    void paymentEventsAreRoutedByType() {
        consumers.handlePaymentEvent("""
            {"eventId":"%s","eventType":"PaymentCompleted","orderId":"%s","amount":8.99,"currency":"USD"}
            """.formatted(EVENT_ID, ORDER_ID));
        verify(handler).onPaymentCompleted(EVENT_ID, ORDER_ID, new BigDecimal("8.99"), "USD");

        consumers.handlePaymentEvent("""
            {"eventId":"e2","eventType":"PaymentFailed","orderId":"%s","failureCode":"GATEWAY_DECLINED"}
            """.formatted(ORDER_ID));
        verify(handler).onPaymentFailed("e2", ORDER_ID, "GATEWAY_DECLINED");

        consumers.handlePaymentEvent("""
            {"eventId":"e3","eventType":"PaymentRefundedEvent","orderId":"%s","refundAmount":25.00}
            """.formatted(ORDER_ID));
        verify(handler).onPaymentRefunded("e3", ORDER_ID, new BigDecimal("25.00"));
    }

    @Test
    void prescriptionEventsAreRoutedByType() {
        consumers.handlePrescriptionEvent("""
            {"eventId":"%s","eventType":"PrescriptionVerified",
             "payload":{"prescriptionId":"%s","customerId":"%s"}}
            """.formatted(EVENT_ID, PRESCRIPTION_ID, CUSTOMER_ID));
        verify(handler).onPrescriptionVerified(EVENT_ID, PRESCRIPTION_ID, CUSTOMER_ID);

        consumers.handlePrescriptionEvent("""
            {"eventId":"e2","eventType":"PrescriptionRejected",
             "payload":{"prescriptionId":"%s","customerId":"%s","reasonCode":"EXPIRED"}}
            """.formatted(PRESCRIPTION_ID, CUSTOMER_ID));
        verify(handler).onPrescriptionRejected("e2", PRESCRIPTION_ID, CUSTOMER_ID, "EXPIRED");
    }

    @Test
    void malformedEventsFailSoTheyAreRetriedThenDeadLettered() {
        assertThatThrownBy(() -> consumers.handlePaymentEvent("not json"))
            .isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> consumers.handlePaymentEvent("{\"eventType\":\"PaymentCompleted\"}"))
            .isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> consumers.handlePaymentEvent(
            "{\"eventId\":\"e1\",\"eventType\":\"PaymentCompleted\",\"orderId\":\"not-a-uuid\"}"))
            .isInstanceOf(MalformedEventException.class);

        verifyNoInteractions(handler);
    }

    @Test
    void consumerBeanAcceptsByteArrayCorrelationHeader() {
        consumers.orderEventConsumer().accept(MessageBuilder
            .withPayload("""
                {"eventId":"%s","orderId":"%s","customerId":"%s","items":[]}
                """.formatted(EVENT_ID, ORDER_ID, CUSTOMER_ID))
            .setHeader(CorrelationIdContext.HEADER_NAME,
                "6c3925df-133d-4217-9fb6-2c2a03402ada".getBytes(StandardCharsets.UTF_8))
            .build());

        verify(handler).onOrderCreated(any(), any(), any());
    }
}
