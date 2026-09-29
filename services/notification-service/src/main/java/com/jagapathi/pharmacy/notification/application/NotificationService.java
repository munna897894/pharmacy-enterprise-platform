package com.jagapathi.pharmacy.notification.application;

import com.jagapathi.pharmacy.observability.BusinessMetric;
import com.jagapathi.pharmacy.observability.RecordBusinessMetric;
import com.jagapathi.pharmacy.notification.api.NotificationResponse;
import com.jagapathi.pharmacy.notification.api.TemplateResponse;
import com.jagapathi.pharmacy.notification.api.NotificationNotFoundException;
import com.jagapathi.pharmacy.notification.api.TemplateNotFoundException;
import com.jagapathi.pharmacy.notification.domain.*;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.NotificationRepository;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.NotificationTemplateRepository;
import com.jagapathi.pharmacy.notification.infrastructure.channel.NotificationSender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final List<NotificationSender> senders;

    public NotificationService(NotificationRepository notificationRepository,
                              NotificationTemplateRepository templateRepository,
                              List<NotificationSender> senders) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.senders = senders;
    }

    @RecordBusinessMetric(BusinessMetric.NOTIFICATION_DELIVERY)
    public void sendNotification(UUID customerId, NotificationType type, List<Channel> channels,
                                Map<String, String> variables) {
        for (Channel channel : channels) {
            NotificationTemplate template = templateRepository
                .findByTypeAndChannelAndIsActiveTrue(type, channel)
                .orElseThrow(() -> new TemplateNotFoundException(
                    "Template not found for type=" + type + ", channel=" + channel));

            Notification notification = createNotification(customerId, type, channel, template, variables);
            publishNotification(notification, channel);
        }
    }

    public void publishNotification(Notification notification, Channel channel) {
        try {
            NotificationSender sender = senders.stream()
                .filter(s -> s.supports(channel))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No sender for channel: " + channel));

            sender.send(notification);
            notification.markAsSent();
            notificationRepository.save(notification);
        } catch (Exception e) {
            notification.markAsFailed();
            notificationRepository.save(notification);
            throw new RuntimeException("Failed to send notification", e);
        }
    }

    public void handleFailedNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + notificationId));

        if (notification.canRetry()) {
            publishNotification(notification, notification.getChannel());
        } else {
            notification.setStatus(NotificationStatus.FAILED);
            notificationRepository.save(notification);
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotificationHistory(UUID customerId, Pageable pageable) {
        return notificationRepository.findByCustomerId(customerId, pageable)
            .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UUID getNotificationOwner(UUID notificationId) {
        return findNotification(notificationId).getCustomerId();
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotification(UUID notificationId) {
        return toResponse(findNotification(notificationId));
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        Notification notification = findNotification(notificationId);
        notification.markAsRead();
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> getAllTemplates() {
        return templateRepository.findByIsActiveTrue()
            .stream()
            .map(this::toTemplateResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(NotificationType type, Channel channel) {
        NotificationTemplate template = templateRepository
            .findByTypeAndChannelAndIsActiveTrue(type, channel)
            .orElseThrow(() -> new TemplateNotFoundException(
                "Template not found for type=" + type + ", channel=" + channel));

        return toTemplateResponse(template);
    }

    private Notification createNotification(UUID customerId, NotificationType type, Channel channel,
                                           NotificationTemplate template, Map<String, String> variables) {
        UUID id = UUID.randomUUID();
        String recipient = getRecipientFromVariables(channel, variables);
        String subject = template.renderSubject(variables);
        String message = template.renderMessage(variables);

        return new Notification(id, customerId, type, channel, recipient, subject, message);
    }

    private String getRecipientFromVariables(Channel channel, Map<String, String> variables) {
        return switch (channel) {
            case EMAIL -> variables.getOrDefault("email", "unknown@example.com");
            case SMS -> variables.getOrDefault("phone", "+1234567890");
            case IN_APP -> "IN_APP";
        };
    }

    private Notification findNotification(UUID notificationId) {
        return notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + notificationId));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getCustomerId(),
            notification.getType(),
            notification.getChannel(),
            notification.getRecipient(),
            notification.getSubject(),
            notification.getMessage(),
            notification.getStatus(),
            notification.getSentAt(),
            notification.getReadAt(),
            notification.getCreatedAt(),
            notification.getUpdatedAt()
        );
    }

    private TemplateResponse toTemplateResponse(NotificationTemplate template) {
        return new TemplateResponse(
            template.getId(),
            template.getType(),
            template.getChannel(),
            template.getSubjectTemplate(),
            template.getMessageTemplate(),
            template.isActive(),
            template.getCreatedAt(),
            template.getUpdatedAt()
        );
    }
}
