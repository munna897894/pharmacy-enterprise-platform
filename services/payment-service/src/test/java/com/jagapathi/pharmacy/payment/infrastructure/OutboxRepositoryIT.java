package com.jagapathi.pharmacy.payment.infrastructure;

import com.jagapathi.pharmacy.payment.domain.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OutboxRepositoryIT extends AbstractMySqlRepositoryIT {

    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void shouldPersistTraceHeaders() {
        String headers = "{\"traceparent\":\"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01\","
            + "\"X-Correlation-ID\":\"order-123\"}";
        OutboxEvent event = new OutboxEvent(
            UUID.randomUUID(),
            "PaymentProcessedEvent",
            UUID.randomUUID(),
            "{\"status\":\"SUCCESS\"}",
            headers
        );

        entityManager.persistAndFlush(event);
        entityManager.clear();

        OutboxEvent persisted = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(persisted.getHeaders()).isEqualTo(headers);
    }
    
    @Test
    void shouldFindUnpublishedEvents() {
        OutboxEvent event1 = new OutboxEvent(
            UUID.randomUUID(),
            "PaymentProcessedEvent",
            UUID.randomUUID(),
            "{\"status\":\"SUCCESS\"}"
        );
        event1.setPublished(false);
        
        OutboxEvent event2 = new OutboxEvent(
            UUID.randomUUID(),
            "PaymentProcessedEvent",
            UUID.randomUUID(),
            "{\"status\":\"FAILED\"}"
        );
        event2.setPublished(false);
        
        OutboxEvent event3 = new OutboxEvent(
            UUID.randomUUID(),
            "PaymentProcessedEvent",
            UUID.randomUUID(),
            "{\"status\":\"REFUNDED\"}"
        );
        event3.setPublished(true);
        
        entityManager.persistAndFlush(event1);
        entityManager.persistAndFlush(event2);
        entityManager.persistAndFlush(event3);
        
        List<OutboxEvent> unpublished = outboxRepository.findUnpublished();
        
        assertThat(unpublished).hasSize(2);
        assertThat(unpublished).noneMatch(OutboxEvent::isPublished);
    }
    
    @Test
    void shouldMarkEventAsPublished() {
        OutboxEvent event = new OutboxEvent(
            UUID.randomUUID(),
            "PaymentProcessedEvent",
            UUID.randomUUID(),
            "{\"status\":\"SUCCESS\"}"
        );
        event.setPublished(false);
        
        entityManager.persistAndFlush(event);
        
        OutboxEvent found = outboxRepository.findById(event.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.isPublished()).isFalse();
        
        found.setPublished(true);
        entityManager.persistAndFlush(found);
        
        OutboxEvent updated = outboxRepository.findById(event.getId()).orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.isPublished()).isTrue();
    }
}
