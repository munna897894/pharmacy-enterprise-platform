package com.jagapathi.pharmacy.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByOrderIdAndEventId(UUID orderId, UUID eventId);
}
