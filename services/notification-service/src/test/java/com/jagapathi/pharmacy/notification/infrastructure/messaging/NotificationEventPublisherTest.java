package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.Notification;
import com.jagapathi.pharmacy.notification.domain.NotificationOutboxEvent;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.NotificationOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:30:00Z");
    private static final UUID NOTIFICATION_ID = UUID.fromString("1c0f2b9a-3a5e-4d3b-9f21-0d44b58a9f01");
    private static final UUID ORDER_ID = UUID.fromString("2d1e3c8b-4b6f-4e4c-8a32-1e55c69b0a12");
    private static final UUID CUSTOMER_ID = UUID.fromString("3e2f4d7c-5c7a-4f5d-9b43-2f66d7ac1b23");

    @Mock
    private NotificationOutboxRepository outboxRepository;

    private NotificationEventPublisher publisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new NotificationEventPublisher(outboxRepository, objectMapper,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void writesNotificationSentRowMatchingTheEventContract() throws Exception {
        Notification notification = sentNotification();

        publisher.notificationSent(notification);

        NotificationOutboxEvent saved = captureSaved();
        assertThat(saved.getTopic()).isEqualTo("pharmacy.notification.events.v1");
        assertThat(saved.getEventKey()).isEqualTo(NOTIFICATION_ID.toString());
        assertThat(saved.getAggregateId()).isEqualTo(NOTIFICATION_ID);
        assertThat(saved.getEventType()).isEqualTo("NotificationSent");
        assertThat(saved.isPublished()).isFalse();

        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("eventType").asText()).isEqualTo("NotificationSent");
        assertThat(UUID.fromString(payload.get("eventId").asText())).isNotNull();
        assertThat(payload.get("notificationId").asText()).isEqualTo(NOTIFICATION_ID.toString());
        assertThat(payload.get("orderId").asText()).isEqualTo(ORDER_ID.toString());
        assertThat(payload.get("customerId").asText()).isEqualTo(CUSTOMER_ID.toString());
        assertThat(payload.get("channel").asText()).isEqualTo("EMAIL");
        assertThat(payload.get("templateCode").asText()).isEqualTo("ORDER_CONFIRMATION");
        assertThat(payload.get("status").asText()).isEqualTo("SENT");
        assertThat(payload.get("occurredAt").asText()).isEqualTo(NOW.toString());
        assertThat(payload.has("failureCode")).isFalse();
    }

    @Test
    void writesNotificationFailedRowWithSanitizedFailureCode() throws Exception {
        Notification notification = sentNotification();
        notification.markAsFailed();

        publisher.notificationFailed(notification, "MailSendException");

        JsonNode payload = objectMapper.readTree(captureSaved().getPayload());
        assertThat(payload.get("eventType").asText()).isEqualTo("NotificationFailed");
        assertThat(payload.get("status").asText()).isEqualTo("FAILED");
        assertThat(payload.get("failureCode").asText()).isEqualTo("MailSendException");
    }

    @Test
    void defaultsMissingFailureCodeAndNeverLeaksRecipientOrMessageBody() throws Exception {
        Notification notification = sentNotification();
        notification.markAsFailed();

        publisher.notificationFailed(notification, null);

        String payload = captureSaved().getPayload();
        assertThat(objectMapper.readTree(payload).get("failureCode").asText()).isEqualTo("DELIVERY_FAILED");
        assertThat(payload).doesNotContain("patient@example.com").doesNotContain("Your order is confirmed");
    }

    @Test
    void emitsNullOrderIdForNotificationsWithoutAnOrder() throws Exception {
        Notification notification = new Notification(NOTIFICATION_ID, CUSTOMER_ID, null,
            NotificationType.PRESCRIPTION_VERIFIED, Channel.IN_APP, "IN_APP", "Verified", "Prescription verified");
        notification.markAsSent();

        publisher.notificationSent(notification);

        assertThat(objectMapper.readTree(captureSaved().getPayload()).get("orderId").isNull()).isTrue();
    }

    private Notification sentNotification() {
        Notification notification = new Notification(NOTIFICATION_ID, CUSTOMER_ID, ORDER_ID,
            NotificationType.ORDER_CONFIRMATION, Channel.EMAIL, "patient@example.com",
            "Order confirmed", "Your order is confirmed");
        notification.markAsSent();
        return notification;
    }

    private NotificationOutboxEvent captureSaved() {
        ArgumentCaptor<NotificationOutboxEvent> captor = ArgumentCaptor.forClass(NotificationOutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
