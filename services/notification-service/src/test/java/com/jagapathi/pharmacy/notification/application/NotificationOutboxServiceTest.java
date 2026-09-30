package com.jagapathi.pharmacy.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.notification.domain.NotificationOutboxEvent;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.NotificationOutboxRepository;
import com.jagapathi.pharmacy.observability.OutboxMetricsSnapshot;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:30:00Z");

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private NotificationOutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new NotificationOutboxService(outboxRepository, kafkaTemplate, new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void marksEventPublishedOnlyAfterTheBrokerAcknowledges() {
        NotificationOutboxEvent event = pendingEvent();
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(null));

        OutboxMetricsSnapshot snapshot = outboxService.publishPendingEvents();

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        assertThat(snapshot.publishedCount()).isEqualTo(1);
        assertThat(snapshot.failedCount()).isZero();
        assertThat(snapshot.unpublishedCount()).isZero();
        verify(outboxRepository).save(event);
    }

    @Test
    void leavesEventPendingAndRecordsFailureWhenTheSendFails() {
        NotificationOutboxEvent event = pendingEvent();
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        OutboxMetricsSnapshot snapshot = outboxService.publishPendingEvents();

        assertThat(event.isPublished()).isFalse();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isNotNull();
        assertThat(snapshot.publishedCount()).isZero();
        assertThat(snapshot.failedCount()).isEqualTo(1);
        assertThat(snapshot.unpublishedCount()).isEqualTo(1);
        verify(outboxRepository).save(event);
    }

    @Test
    void forwardsOnlyAllowedTraceHeadersToKafka() {
        NotificationOutboxEvent event = new NotificationOutboxEvent(UUID.randomUUID(), UUID.randomUUID(),
            "NotificationSent", "pharmacy.notification.events.v1", UUID.randomUUID().toString(), "{}",
            "{\"traceparent\":\"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01\","
                + "\"X-Correlation-ID\":\"c0ffee00-0000-4000-8000-000000000000\","
                + "\"Authorization\":\"Bearer secret\"}",
            NOW);
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(null));

        outboxService.publishPendingEvents();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        var headers = captor.getValue().headers();
        assertThat(headers.lastHeader("Authorization")).isNull();
        assertThat(new String(headers.lastHeader("X-Correlation-ID").value(), StandardCharsets.UTF_8))
            .isEqualTo("c0ffee00-0000-4000-8000-000000000000");
    }

    private NotificationOutboxEvent pendingEvent() {
        return new NotificationOutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "NotificationSent",
            "pharmacy.notification.events.v1", UUID.randomUUID().toString(), "{}", "{}", NOW.minusSeconds(30));
    }
}
