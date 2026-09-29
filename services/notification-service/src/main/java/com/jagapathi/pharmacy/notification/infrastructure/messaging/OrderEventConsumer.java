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
public class OrderEventConsumer {
    
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    public OrderEventConsumer(NotificationService notificationService,
                             ProcessedEventRepository processedEventRepository) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
    }

    @Bean
    public Consumer<Message<DomainEvent<OrderCreatedPayload>>> orderCreatedConsumer() {
        return message -> CorrelationIdContext.runWithCorrelationId(
            message.getHeaders().get(CorrelationIdContext.HEADER_NAME, String.class),
            () -> handleOrderCreated(message.getPayload())
        );
    }

    @Bean
    public Consumer<Message<DomainEvent<OrderShippedPayload>>> orderShippedConsumer() {
        return message -> CorrelationIdContext.runWithCorrelationId(
            message.getHeaders().get(CorrelationIdContext.HEADER_NAME, String.class),
            () -> handleOrderShipped(message.getPayload())
        );
    }

    @Transactional
    public void handleOrderCreated(DomainEvent<OrderCreatedPayload> event) {
        String eventId = event.eventId().toString();
        
        if (isAlreadyProcessed(eventId)) {
            return;
        }

        try {
            OrderCreatedPayload payload = event.payload();
            Map<String, String> variables = new HashMap<>();
            variables.put("orderId", payload.orderId().toString());
            variables.put("amount", payload.total().toPlainString());
            variables.put("currency", payload.currency());
            variables.put("pharmacyName", "Default Pharmacy");
            variables.put("email", "customer@example.com");

            notificationService.sendNotification(
                payload.customerId(),
                NotificationType.ORDER_CONFIRMATION,
                Arrays.asList(Channel.EMAIL, Channel.IN_APP),
                variables
            );

            markAsProcessed(eventId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle OrderCreated event", e);
        }
    }

    @Transactional
    public void handleOrderShipped(DomainEvent<OrderShippedPayload> event) {
        String eventId = event.eventId().toString();
        
        if (isAlreadyProcessed(eventId)) {
            return;
        }

        try {
            OrderShippedPayload payload = event.payload();
            Map<String, String> variables = new HashMap<>();
            variables.put("orderId", payload.orderId().toString());
            variables.put("pharmacyName", "Default Pharmacy");
            variables.put("email", "customer@example.com");
            variables.put("phone", "+1234567890");

            notificationService.sendNotification(
                payload.customerId(),
                NotificationType.ORDER_SHIPPED,
                Arrays.asList(Channel.EMAIL, Channel.SMS),
                variables
            );

            markAsProcessed(eventId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle OrderShipped event", e);
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
