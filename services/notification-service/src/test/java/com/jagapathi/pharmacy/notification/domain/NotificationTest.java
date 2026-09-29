package com.jagapathi.pharmacy.notification.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class NotificationTest {

    @Test
    void testNotificationCreation() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String recipient = "test@example.com";
        String subject = "Test Subject";
        String message = "Test Message";

        Notification notification = new Notification(id, customerId, NotificationType.ORDER_CONFIRMATION,
                Channel.EMAIL, recipient, subject, message);

        assertThat(notification.getId()).isEqualTo(id);
        assertThat(notification.getCustomerId()).isEqualTo(customerId);
        assertThat(notification.getType()).isEqualTo(NotificationType.ORDER_CONFIRMATION);
        assertThat(notification.getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRecipient()).isEqualTo(recipient);
        assertThat(notification.getRetryCount()).isEqualTo(0);
    }

    @Test
    void testStatusTransitions() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Notification notification = new Notification(id, customerId, NotificationType.ORDER_CONFIRMATION,
                Channel.EMAIL, "test@example.com", "Subject", "Message");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);

        notification.markAsSent();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();

        notification.markAsDelivered();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DELIVERED);

        notification.markAsRead();
        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    void testRetryLogic() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Notification notification = new Notification(id, customerId, NotificationType.ORDER_CONFIRMATION,
                Channel.EMAIL, "test@example.com", "Subject", "Message");

        assertThat(notification.canRetry()).isFalse();

        notification.markAsFailed();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.canRetry()).isTrue();
        assertThat(notification.getRetryCount()).isEqualTo(1);

        notification.markAsFailed();
        notification.markAsFailed();
        assertThat(notification.getRetryCount()).isEqualTo(3);
        assertThat(notification.canRetry()).isFalse();
    }

    @Test
    void testTemplateRendering() {
        UUID id = UUID.randomUUID();
        NotificationTemplate template = new NotificationTemplate(
                id, NotificationType.ORDER_CONFIRMATION, Channel.EMAIL,
                "Order #{orderId} Confirmed", "Your order for {amount} has been confirmed."
        );

        java.util.Map<String, String> variables = new java.util.HashMap<>();
        variables.put("orderId", "12345");
        variables.put("amount", "99.99");

        String renderedSubject = template.renderSubject(variables);
        String renderedMessage = template.renderMessage(variables);

        assertThat(renderedSubject).isEqualTo("Order #12345 Confirmed");
        assertThat(renderedMessage).isEqualTo("Your order for 99.99 has been confirmed.");
    }
}
