package com.jagapathi.pharmacy.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    Optional<ProcessedEvent> findByOrderIdAndEventId(UUID orderId, UUID eventId);
    boolean existsByOrderIdAndEventId(UUID orderId, UUID eventId);
}
