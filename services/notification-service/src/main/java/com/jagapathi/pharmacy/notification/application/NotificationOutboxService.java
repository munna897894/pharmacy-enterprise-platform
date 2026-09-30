package com.jagapathi.pharmacy.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.notification.domain.NotificationOutboxEvent;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.NotificationOutboxRepository;
import com.jagapathi.pharmacy.observability.OutboxMetricsSnapshot;
import com.jagapathi.pharmacy.observability.TraceContextHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Relays notification outbox rows to Kafka. A row is only marked published once the broker has
 * acknowledged the send, so a broker outage leaves the row pending for the next sweep.
 */
@Service
public class NotificationOutboxService {

    private static final long KAFKA_SEND_TIMEOUT_MS = 10_000L;

    private final NotificationOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationOutboxService(NotificationOutboxRepository outboxRepository,
                                     KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public OutboxMetricsSnapshot publishPendingEvents() {
        List<NotificationOutboxEvent> pending = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
        long publishedCount = 0;
        long failedCount = 0;

        for (NotificationOutboxEvent event : pending) {
            try {
                var record = new ProducerRecord<String, String>(
                    event.getTopic(), event.getEventKey(), event.getPayload());
                Map<String, String> capturedHeaders = readOutboxHeaders(event.getHeaders());
                capturedHeaders.forEach((name, value) ->
                    record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
                TraceContextHeaders.callInCapturedContext(capturedHeaders, () -> kafkaTemplate.send(record))
                    .get(KAFKA_SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                event.recordPublishSuccess(clock.instant());
                publishedCount++;
            } catch (Exception exception) {
                event.recordPublishFailure(exception.getMessage());
                failedCount++;
            }
            outboxRepository.save(event);
        }
        return snapshot(pending, publishedCount, failedCount);
    }

    private OutboxMetricsSnapshot snapshot(List<NotificationOutboxEvent> events,
                                           long publishedCount, long failedCount) {
        List<NotificationOutboxEvent> stillPending = events.stream()
            .filter(event -> !event.isPublished())
            .toList();
        long oldestAgeSeconds = stillPending.stream()
            .map(NotificationOutboxEvent::getCreatedAt)
            .min(Instant::compareTo)
            .map(createdAt -> Math.max(0, Duration.between(createdAt, clock.instant()).getSeconds()))
            .orElse(0L);
        return new OutboxMetricsSnapshot(stillPending.size(), oldestAgeSeconds, publishedCount, failedCount);
    }

    private Map<String, String> readOutboxHeaders(String serializedHeaders) throws Exception {
        Map<String, String> allowed = new LinkedHashMap<>();
        if (serializedHeaders == null || serializedHeaders.isBlank()) {
            return allowed;
        }
        var fields = objectMapper.readTree(serializedHeaders).fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (TraceContextHeaders.isAllowedHeader(entry.getKey()) && entry.getValue().isTextual()) {
                allowed.put(entry.getKey(), entry.getValue().asText());
            }
        }
        return allowed;
    }
}
