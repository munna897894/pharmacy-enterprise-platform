package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.notification.application.NotificationEventHandler;
import com.jagapathi.pharmacy.observability.CorrelationIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * One consumer (and consumer group) per source topic; routing by eventType happens here so
 * no event type is silently lost to a sibling consumer sharing a group.
 */
@Configuration
public class NotificationEventConsumers {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumers.class);

    private final ObjectMapper objectMapper;
    private final NotificationEventHandler handler;

    public NotificationEventConsumers(ObjectMapper objectMapper, NotificationEventHandler handler) {
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    @Bean
    public Consumer<Message<String>> orderEventConsumer() {
        return message -> withCorrelation(message, () -> handleOrderEvent(message.getPayload()));
    }

    @Bean
    public Consumer<Message<String>> paymentEventConsumer() {
        return message -> withCorrelation(message, () -> handlePaymentEvent(message.getPayload()));
    }

    @Bean
    public Consumer<Message<String>> prescriptionEventConsumer() {
        return message -> withCorrelation(message, () -> handlePrescriptionEvent(message.getPayload()));
    }

    void handleOrderEvent(String json) {
        InboundEvent event = InboundEvent.parse(objectMapper, json);
        if (InboundEvent.ORDER_CREATED.equals(event.eventType())) {
            handler.onOrderCreated(event.eventId(), event.uuid("orderId"), event.uuid("customerId"));
        } else {
            ignore("order", event);
        }
    }

    void handlePaymentEvent(String json) {
        InboundEvent event = InboundEvent.parse(objectMapper, json);
        switch (event.eventType() == null ? "" : event.eventType()) {
            case "PaymentCompleted" -> handler.onPaymentCompleted(event.eventId(), event.uuid("orderId"),
                event.optionalDecimal("amount"), event.optionalText("currency"));
            case "PaymentFailed" -> handler.onPaymentFailed(event.eventId(), event.uuid("orderId"),
                event.optionalText("failureCode"));
            case "PaymentRefundedEvent", "PaymentRefunded" -> handler.onPaymentRefunded(event.eventId(),
                event.uuid("orderId"), event.optionalDecimal("refundAmount"));
            default -> ignore("payment", event);
        }
    }

    void handlePrescriptionEvent(String json) {
        InboundEvent event = InboundEvent.parse(objectMapper, json);
        switch (event.eventType() == null ? "" : event.eventType()) {
            case "PrescriptionVerified" -> handler.onPrescriptionVerified(event.eventId(),
                event.uuid("prescriptionId"), event.uuid("customerId"));
            case "PrescriptionRejected" -> handler.onPrescriptionRejected(event.eventId(),
                event.uuid("prescriptionId"), event.uuid("customerId"), event.optionalText("reasonCode"));
            default -> ignore("prescription", event);
        }
    }

    private static void withCorrelation(Message<String> message, Runnable action) {
        CorrelationIdContext.runWithCorrelationId(
            CorrelationIdContext.fromHeader(message.getHeaders().get(CorrelationIdContext.HEADER_NAME)),
            action
        );
    }

    private static void ignore(String topic, InboundEvent event) {
        log.debug("Ignoring {} event type={} eventId={}", topic, event.eventType(), event.eventId());
    }
}
