package com.jagapathi.pharmacy.payment.infrastructure;

import com.jagapathi.pharmacy.payment.application.PaymentService;
import com.jagapathi.pharmacy.observability.OutboxMetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Scheduling enabled for outbox publisher
}

@Component
class OutboxPublisher {
    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);
    private final PaymentService paymentService;
    private final OutboxMetricsRecorder metricsRecorder;
    
    public OutboxPublisher(PaymentService paymentService, OutboxMetricsRecorder metricsRecorder) {
        this.paymentService = paymentService;
        this.metricsRecorder = metricsRecorder;
    }
    
    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void publishPendingEvents() {
        try {
            metricsRecorder.record(paymentService.publishOutboxEvents());
        } catch (Exception e) {
            logger.warn("Payment outbox batch failed; it will be retried. exceptionType={}",
                e.getClass().getSimpleName());
        }
    }
}
