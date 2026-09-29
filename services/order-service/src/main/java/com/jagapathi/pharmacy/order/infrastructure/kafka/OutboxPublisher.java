package com.jagapathi.pharmacy.order.infrastructure.kafka;

import com.jagapathi.pharmacy.order.application.service.OrderService;
import com.jagapathi.pharmacy.observability.OutboxMetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OrderService orderService;
    private final OutboxMetricsRecorder metricsRecorder;

    public OutboxPublisher(OrderService orderService, OutboxMetricsRecorder metricsRecorder) {
        this.orderService = orderService;
        this.metricsRecorder = metricsRecorder;
    }

    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    public void publishPendingEvents() {
        try {
            metricsRecorder.record(orderService.publishOutboxEvents());
        } catch (Exception e) {
            logger.warn("Outbox batch failed; it will be retried. exceptionType={}", e.getClass().getSimpleName());
        }
    }
}
