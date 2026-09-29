package com.jagapathi.pharmacy.inventory.infrastructure.kafka;

import com.jagapathi.pharmacy.inventory.application.service.InventorySagaService;
import com.jagapathi.pharmacy.observability.OutboxMetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    private final InventorySagaService sagaService;
    private final OutboxMetricsRecorder metricsRecorder;

    public OutboxPublisher(InventorySagaService sagaService, OutboxMetricsRecorder metricsRecorder) {
        this.sagaService = sagaService;
        this.metricsRecorder = metricsRecorder;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        try {
            metricsRecorder.record(sagaService.publishOutboxEvents());
        } catch (Exception e) {
            logger.warn("Inventory outbox batch failed; it will be retried. exceptionType={}",
                e.getClass().getSimpleName());
        }
    }
}
