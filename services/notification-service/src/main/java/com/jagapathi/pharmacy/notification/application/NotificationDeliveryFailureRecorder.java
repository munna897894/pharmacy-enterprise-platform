package com.jagapathi.pharmacy.notification.application;

import com.jagapathi.pharmacy.notification.domain.Notification;
import com.jagapathi.pharmacy.notification.infrastructure.messaging.NotificationEventPublisher;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a failed delivery attempt and its NotificationFailed outbox row in an independent
 * transaction. The calling transaction is rolled back so Kafka redelivers the source event, which
 * would otherwise discard every trace of the failed attempt.
 */
@Service
public class NotificationDeliveryFailureRecorder {

    private final NotificationRepository notificationRepository;
    private final NotificationEventPublisher eventPublisher;

    public NotificationDeliveryFailureRecorder(NotificationRepository notificationRepository,
                                               NotificationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeliveryFailure(Notification notification, String failureCode) {
        notification.markAsFailed();
        Notification persisted = notificationRepository.save(notification);
        eventPublisher.notificationFailed(persisted, failureCode);
    }
}
