package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import com.jagapathi.pharmacy.notification.application.NotificationOutboxService;
import com.jagapathi.pharmacy.observability.OutboxMetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationOutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(NotificationOutboxPublisher.class);

    private final NotificationOutboxService outboxService;
    private final OutboxMetricsRecorder metricsRecorder;

    public NotificationOutboxPublisher(NotificationOutboxService outboxService,
                                       OutboxMetricsRecorder metricsRecorder) {
        this.outboxService = outboxService;
        this.metricsRecorder = metricsRecorder;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        try {
            metricsRecorder.record(outboxService.publishPendingEvents());
        } catch (Exception exception) {
            logger.warn("Notification outbox batch failed; it will be retried. exceptionType={}",
                exception.getClass().getSimpleName());
        }
    }
}
