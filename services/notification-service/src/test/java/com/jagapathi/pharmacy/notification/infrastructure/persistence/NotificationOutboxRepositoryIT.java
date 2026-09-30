package com.jagapathi.pharmacy.notification.infrastructure.persistence;

import com.jagapathi.pharmacy.notification.domain.NotificationOutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NotificationOutboxRepositoryIT {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.3");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired
    private NotificationOutboxRepository outboxRepository;

    @Test
    void returnsUnpublishedEventsOldestFirstAndExcludesPublishedOnes() {
        Instant base = Instant.parse("2026-01-15T10:00:00Z");
        NotificationOutboxEvent older = event("NotificationSent", base);
        NotificationOutboxEvent newer = event("NotificationFailed", base.plusSeconds(60));
        NotificationOutboxEvent alreadyPublished = event("NotificationSent", base.minusSeconds(60));
        alreadyPublished.recordPublishSuccess(base);

        outboxRepository.saveAllAndFlush(java.util.List.of(newer, older, alreadyPublished));

        assertThat(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc())
            .extracting(NotificationOutboxEvent::getId)
            .containsExactly(older.getId(), newer.getId());
    }

    @Test
    void persistsPublishFailureBookkeeping() {
        NotificationOutboxEvent event = event("NotificationFailed", Instant.parse("2026-01-15T10:00:00Z"));
        event.recordPublishFailure("broker unavailable");

        outboxRepository.saveAndFlush(event);

        assertThat(outboxRepository.findById(event.getId())).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.isPublished()).isFalse();
            assertThat(reloaded.getAttempts()).isEqualTo(1);
            assertThat(reloaded.getLastError()).isEqualTo("broker unavailable");
            assertThat(reloaded.getEventType()).isEqualTo("NotificationFailed");
        });
    }

    private NotificationOutboxEvent event(String eventType, Instant occurredAt) {
        UUID notificationId = UUID.randomUUID();
        return new NotificationOutboxEvent(UUID.randomUUID(), notificationId, eventType,
            "pharmacy.notification.events.v1", notificationId.toString(),
            "{\"eventType\":\"" + eventType + "\"}", "{}", occurredAt);
    }
}
