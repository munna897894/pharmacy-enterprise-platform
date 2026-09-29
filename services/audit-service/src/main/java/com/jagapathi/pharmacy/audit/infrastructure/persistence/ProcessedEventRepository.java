package com.jagapathi.pharmacy.audit.infrastructure.persistence;

import com.jagapathi.pharmacy.audit.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
    Optional<ProcessedEvent> findByEventId(String eventId);
}
