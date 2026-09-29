package com.jagapathi.pharmacy.order.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_event", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"order_id", "event_id"})
})
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID orderId;

    @Column(name = "event_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID eventId;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent() {
    }

    public ProcessedEvent(UUID orderId, UUID eventId, String consumerName) {
        this.orderId = orderId;
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.processedAt = Instant.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
