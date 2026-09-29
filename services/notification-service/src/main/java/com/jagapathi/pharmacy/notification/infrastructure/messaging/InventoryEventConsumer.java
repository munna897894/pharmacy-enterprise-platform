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
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class InventoryEventConsumer {
    
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    public InventoryEventConsumer(NotificationService notificationService,
                                 ProcessedEventRepository processedEventRepository) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
    }

    @Bean
    public Consumer<Message<DomainEvent<LowStockPayload>>> lowStockConsumer() {
        return message -> CorrelationIdContext.runWithCorrelationId(
            message.getHeaders().get(CorrelationIdContext.HEADER_NAME, String.class),
            () -> handleLowStock(message.getPayload())
        );
    }

    @Transactional
    public void handleLowStock(DomainEvent<LowStockPayload> event) {
        String eventId = event.eventId().toString();
        
        if (isAlreadyProcessed(eventId)) {
            return;
        }

        try {
            LowStockPayload payload = event.payload();
            // For stock alerts, we use a dummy customer ID (pharmacist account)
            UUID pharmacistId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            
            Map<String, String> variables = new HashMap<>();
            variables.put("medicationName", payload.medicationName());
            variables.put("currentStock", payload.currentStock().toString());
            variables.put("reorderLevel", payload.reorderLevel().toString());
            variables.put("pharmacyName", "Default Pharmacy");

            notificationService.sendNotification(
                pharmacistId,
                NotificationType.STOCK_ALERT,
                Arrays.asList(Channel.IN_APP),
                variables
            );

            markAsProcessed(eventId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle LowStock event", e);
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
