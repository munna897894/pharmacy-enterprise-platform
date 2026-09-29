package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import com.jagapathi.pharmacy.observability.CorrelationIdContext;
import com.jagapathi.pharmacy.notification.application.NotificationService;
import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import com.jagapathi.pharmacy.notification.domain.ProcessedEvent;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.ProcessedEventRepository;
import com.jagapathi.pharmacy.platform.events.DomainEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class PaymentEventConsumer {
    
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    public PaymentEventConsumer(NotificationService notificationService,
                               ProcessedEventRepository processedEventRepository) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
    }

    @Bean
    public Consumer<Message<DomainEvent<PaymentProcessedPayload>>> paymentProcessedConsumer() {
        return message -> CorrelationIdContext.runWithCorrelationId(
            message.getHeaders().get(CorrelationIdContext.HEADER_NAME, String.class),
            () -> handlePaymentProcessed(message.getPayload())
        );
    }

    @Transactional
    public void handlePaymentProcessed(DomainEvent<PaymentProcessedPayload> event) {
        String eventId = event.eventId().toString();
        
        if (isAlreadyProcessed(eventId)) {
            return;
        }

        try {
            PaymentProcessedPayload payload = event.payload();
            Map<String, String> variables = new HashMap<>();
            variables.put("orderId", payload.orderId().toString());
            variables.put("amount", payload.amount().toPlainString());
            variables.put("currency", payload.currency());
            variables.put("reference", payload.providerReference());
            variables.put("email", "customer@example.com");

            notificationService.sendNotification(
                payload.customerId(),
                NotificationType.REFUND_PROCESSED,
                Arrays.asList(Channel.EMAIL),
                variables
            );

            markAsProcessed(eventId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle PaymentProcessed event", e);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEventRepository.existsById(eventId);
    }

    private void markAsProcessed(String eventId) {
        try {
            processedEventRepository.save(new ProcessedEvent(eventId));
        } catch (DataIntegrityViolationException e) {
            // Already processed by another instance
        }
    }
}
