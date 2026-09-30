package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.notification.domain.Notification;
import com.jagapathi.pharmacy.notification.domain.NotificationOutboxEvent;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.NotificationOutboxRepository;
import com.jagapathi.pharmacy.observability.TraceContextHeaders;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes NotificationSent / NotificationFailed outbox rows. Always called inside the transaction
 * that persists the notification state change, so the event and the state commit together.
 */
@Component
public class NotificationEventPublisher {

    public static final String TOPIC = "pharmacy.notification.events.v1";

    private static final String SENT = "NotificationSent";
    private static final String FAILED = "NotificationFailed";

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationEventPublisher(NotificationOutboxRepository outboxRepository,
                                      ObjectMapper objectMapper,
                                      Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void notificationSent(Notification notification) {
        append(notification, SENT, null);
    }

    public void notificationFailed(Notification notification, String failureCode) {
        append(notification, FAILED, failureCode == null ? "DELIVERY_FAILED" : failureCode);
    }

    private void append(Notification notification, String eventType, String failureCode) {
        Instant occurredAt = clock.instant();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", eventType);
        payload.put("notificationId", notification.getId().toString());
        payload.put("orderId", notification.getOrderId() == null ? null : notification.getOrderId().toString());
        payload.put("customerId", notification.getCustomerId().toString());
        payload.put("channel", notification.getChannel().name());
        payload.put("templateCode", notification.getType().name());
        payload.put("status", notification.getStatus().name());
        payload.put("occurredAt", occurredAt.toString());
        if (failureCode != null) {
            payload.put("failureCode", failureCode);
        }

        outboxRepository.save(new NotificationOutboxEvent(
            UUID.randomUUID(),
            notification.getId(),
            eventType,
            TOPIC,
            notification.getId().toString(),
            write(payload),
            write(TraceContextHeaders.capture()),
            occurredAt
        ));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize notification outbox record", exception);
        }
    }
}
