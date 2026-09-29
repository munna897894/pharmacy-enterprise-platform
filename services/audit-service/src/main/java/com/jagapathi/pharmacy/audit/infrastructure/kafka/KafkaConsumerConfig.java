package com.jagapathi.pharmacy.audit.infrastructure.kafka;

import com.jagapathi.pharmacy.audit.application.AuditEventProcessor;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import com.jagapathi.pharmacy.observability.CorrelationIdContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration
public class KafkaConsumerConfig {
    private final AuditEventProcessor auditEventProcessor;

    public KafkaConsumerConfig(AuditEventProcessor auditEventProcessor) {
        this.auditEventProcessor = auditEventProcessor;
    }

    @Bean
    @ConditionalOnMissingBean(name = "inventoryEventConsumer")
    public Consumer<Message<String>> inventoryEventConsumer() {
        return consumer(AuditResourceType.INVENTORY);
    }

    @Bean
    @ConditionalOnMissingBean(name = "orderEventConsumer")
    public Consumer<Message<String>> orderEventConsumer() {
        return consumer(AuditResourceType.ORDER);
    }

    @Bean
    @ConditionalOnMissingBean(name = "paymentEventConsumer")
    public Consumer<Message<String>> paymentEventConsumer() {
        return consumer(AuditResourceType.PAYMENT);
    }

    @Bean
    @ConditionalOnMissingBean(name = "notificationEventConsumer")
    public Consumer<Message<String>> notificationEventConsumer() {
        return consumer(AuditResourceType.NOTIFICATION);
    }

    @Bean
    @ConditionalOnMissingBean(name = "prescriptionEventConsumer")
    public Consumer<Message<String>> prescriptionEventConsumer() {
        return consumer(AuditResourceType.PRESCRIPTION);
    }

    private Consumer<Message<String>> consumer(AuditResourceType resourceType) {
        return message -> CorrelationIdContext.runWithCorrelationId(
            message.getHeaders().get(CorrelationIdContext.HEADER_NAME, String.class),
            () -> auditEventProcessor.process(message.getPayload(), resourceType)
        );
    }
}
