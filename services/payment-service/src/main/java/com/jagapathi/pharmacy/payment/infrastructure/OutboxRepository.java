package com.jagapathi.pharmacy.payment.infrastructure;

import com.jagapathi.pharmacy.payment.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    
    @Query("SELECT e FROM OutboxEvent e WHERE e.published = false ORDER BY e.createdAt ASC")
    List<OutboxEvent> findUnpublished();
}
