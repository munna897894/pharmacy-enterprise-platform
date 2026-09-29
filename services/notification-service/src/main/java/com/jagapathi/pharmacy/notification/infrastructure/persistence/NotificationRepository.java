package com.jagapathi.pharmacy.notification.infrastructure.persistence;

import com.jagapathi.pharmacy.notification.domain.Notification;
import com.jagapathi.pharmacy.notification.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByCustomerId(UUID customerId, Pageable pageable);
    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);
    Page<Notification> findByCustomerIdAndReadAtNull(UUID customerId, Pageable pageable);
    Page<Notification> findByCreatedAtBetween(Instant start, Instant end, Pageable pageable);
}
