package com.jagapathi.pharmacy.notification.infrastructure.persistence;

import com.jagapathi.pharmacy.notification.domain.NotificationOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEvent, UUID> {

    List<NotificationOutboxEvent> findByPublishedFalseOrderByCreatedAtAsc();
}
