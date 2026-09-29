package com.jagapathi.pharmacy.notification.infrastructure.persistence;

import com.jagapathi.pharmacy.notification.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
