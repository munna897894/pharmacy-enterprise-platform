package com.jagapathi.pharmacy.notification.application;

import com.jagapathi.pharmacy.notification.api.TemplateNotFoundException;
import com.jagapathi.pharmacy.notification.domain.*;
import com.jagapathi.pharmacy.notification.infrastructure.channel.EmailSender;
import com.jagapathi.pharmacy.notification.infrastructure.channel.InAppSender;
import com.jagapathi.pharmacy.notification.infrastructure.channel.NotificationSender;
import com.jagapathi.pharmacy.notification.infrastructure.messaging.NotificationEventPublisher;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.NotificationRepository;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private EmailSender emailSender;

    @Mock
    private InAppSender inAppSender;

    @Mock
    private NotificationEventPublisher eventPublisher;

    @Mock
    private NotificationDeliveryFailureRecorder failureRecorder;

    private NotificationService notificationService;
    private List<NotificationSender> senders;

    @BeforeEach
    void setUp() {
        senders = Arrays.asList(emailSender, inAppSender);
        notificationService = new NotificationService(notificationRepository, templateRepository, senders,
            eventPublisher, failureRecorder);
    }

    @Test
    void testSendNotificationWithMultipleChannels() {
        UUID customerId = UUID.randomUUID();
        NotificationType type = NotificationType.ORDER_CONFIRMATION;
        List<Channel> channels = Arrays.asList(Channel.EMAIL, Channel.IN_APP);

        NotificationTemplate emailTemplate = new NotificationTemplate(
                UUID.randomUUID(), type, Channel.EMAIL,
                "Order Confirmed", "Your order for {amount} has been confirmed."
        );
        NotificationTemplate inAppTemplate = new NotificationTemplate(
                UUID.randomUUID(), type, Channel.IN_APP,
                "Order Confirmed", "Your order for {amount} has been confirmed."
        );

        when(templateRepository.findByTypeAndChannelAndIsActiveTrue(type, Channel.EMAIL))
                .thenReturn(Optional.of(emailTemplate));
        when(templateRepository.findByTypeAndChannelAndIsActiveTrue(type, Channel.IN_APP))
                .thenReturn(Optional.of(inAppTemplate));
        when(emailSender.supports(Channel.EMAIL)).thenReturn(true);
        when(inAppSender.supports(Channel.IN_APP)).thenReturn(true);
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, String> variables = new HashMap<>();
        variables.put("amount", "99.99");
        variables.put("email", "test@example.com");

        notificationService.sendNotification(customerId, type, channels, variables);

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    void testSendNotificationWithTemplateNotFound() {
        UUID customerId = UUID.randomUUID();
        NotificationType type = NotificationType.ORDER_CONFIRMATION;
        List<Channel> channels = Arrays.asList(Channel.EMAIL);

        when(templateRepository.findByTypeAndChannelAndIsActiveTrue(type, Channel.EMAIL))
                .thenReturn(Optional.empty());

        Map<String, String> variables = new HashMap<>();
        variables.put("amount", "99.99");

        assertThatThrownBy(() ->
                notificationService.sendNotification(customerId, type, channels, variables)
        ).isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void testMarkNotificationAsRead() {
        UUID notificationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Notification notification = new Notification(
                notificationId, customerId, NotificationType.ORDER_CONFIRMATION,
                Channel.EMAIL, "test@example.com", "Subject", "Message"
        );

        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification));

        notificationService.markAsRead(notificationId);

        verify(notificationRepository).save(notification);
        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    void testTemplateVariableSubstitution() {
        UUID templateId = UUID.randomUUID();
        NotificationTemplate template = new NotificationTemplate(
                templateId, NotificationType.ORDER_CONFIRMATION, Channel.EMAIL,
                "Order #{orderId} Confirmed", "Amount: {amount}, Currency: {currency}"
        );

        Map<String, String> variables = new HashMap<>();
        variables.put("orderId", "ORD-12345");
        variables.put("amount", "150.00");
        variables.put("currency", "USD");

        String subject = template.renderSubject(variables);
        String message = template.renderMessage(variables);

        assertThat(subject).isEqualTo("Order #ORD-12345 Confirmed");
        assertThat(message).isEqualTo("Amount: 150.00, Currency: USD");
    }

    @Test
    void testGetAllTemplates() {
        NotificationTemplate template1 = new NotificationTemplate(
                UUID.randomUUID(), NotificationType.ORDER_CONFIRMATION, Channel.EMAIL,
                "Order Confirmed", "Message 1"
        );
        NotificationTemplate template2 = new NotificationTemplate(
                UUID.randomUUID(), NotificationType.PRESCRIPTION_FILLED, Channel.SMS,
                null, "Message 2"
        );

        when(templateRepository.findByIsActiveTrue())
                .thenReturn(Arrays.asList(template1, template2));

        var templates = notificationService.getAllTemplates();

        assertThat(templates).hasSize(2);
        verify(templateRepository).findByIsActiveTrue();
    }

    @Test
    void publishesNotificationSentEventAfterSuccessfulDelivery() {
        Notification notification = new Notification(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.ORDER_CONFIRMATION, Channel.EMAIL, "patient@example.com", "Confirmed", "Body");
        when(emailSender.supports(Channel.EMAIL)).thenReturn(true);
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.publishNotification(notification, Channel.EMAIL);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(eventPublisher).notificationSent(notification);
        verifyNoInteractions(failureRecorder);
    }

    @Test
    void recordsDeliveryFailureOutsideTheRollingBackTransactionAndRethrows() throws Exception {
        Notification notification = new Notification(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.ORDER_CONFIRMATION, Channel.EMAIL, "patient@example.com", "Confirmed", "Body");
        when(emailSender.supports(Channel.EMAIL)).thenReturn(true);
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(notification);

        assertThatThrownBy(() -> notificationService.publishNotification(notification, Channel.EMAIL))
                .isInstanceOf(NotificationDeliveryException.class);

        verify(failureRecorder).recordDeliveryFailure(notification, "IllegalStateException");
        verify(eventPublisher, never()).notificationSent(any());
        verifyNoInteractions(notificationRepository);
    }
}
