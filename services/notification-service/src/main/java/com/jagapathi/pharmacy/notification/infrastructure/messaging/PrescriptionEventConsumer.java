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
public class PrescriptionEventConsumer {
    
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    public PrescriptionEventConsumer(NotificationService notificationService,
                                    ProcessedEventRepository processedEventRepository) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
    }

    @Bean
    public Consumer<Message<DomainEvent<PrescriptionFilledPayload>>> prescriptionFilledConsumer() {
        return message -> CorrelationIdContext.runWithCorrelationId(
            message.getHeaders().get(CorrelationIdContext.HEADER_NAME, String.class),
            () -> handlePrescriptionFilled(message.getPayload())
        );
    }

    @Bean
    public Consumer<Message<DomainEvent<PrescriptionExpiringPayload>>> prescriptionExpiringConsumer() {
        return message -> CorrelationIdContext.runWithCorrelationId(
            message.getHeaders().get(CorrelationIdContext.HEADER_NAME, String.class),
            () -> handlePrescriptionExpiring(message.getPayload())
        );
    }

    @Transactional
    public void handlePrescriptionFilled(DomainEvent<PrescriptionFilledPayload> event) {
        String eventId = event.eventId().toString();
        
        if (isAlreadyProcessed(eventId)) {
            return;
        }

        try {
            PrescriptionFilledPayload payload = event.payload();
            Map<String, String> variables = new HashMap<>();
            variables.put("prescriptionId", payload.prescriptionId().toString());
            variables.put("medicationId", payload.medicationId().toString());
            variables.put("quantity", payload.quantity().toString());
            variables.put("pharmacyName", "Default Pharmacy");
            variables.put("email", "customer@example.com");

            notificationService.sendNotification(
                payload.customerId(),
                NotificationType.PRESCRIPTION_FILLED,
                Arrays.asList(Channel.EMAIL, Channel.IN_APP),
                variables
            );

            markAsProcessed(eventId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle PrescriptionFilled event", e);
        }
    }

    @Transactional
    public void handlePrescriptionExpiring(DomainEvent<PrescriptionExpiringPayload> event) {
        String eventId = event.eventId().toString();
        
        if (isAlreadyProcessed(eventId)) {
            return;
        }

        try {
            PrescriptionExpiringPayload payload = event.payload();
            Map<String, String> variables = new HashMap<>();
            variables.put("prescriptionId", payload.prescriptionId().toString());
            variables.put("productName", payload.productName());
            variables.put("expiryDate", payload.expiryDate().toString());
            variables.put("phone", "+1234567890");

            notificationService.sendNotification(
                payload.customerId(),
                NotificationType.PRESCRIPTION_EXPIRING,
                Arrays.asList(Channel.SMS, Channel.IN_APP),
                variables
            );

            markAsProcessed(eventId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle PrescriptionExpiring event", e);
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
