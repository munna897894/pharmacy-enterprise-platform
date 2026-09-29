package com.jagapathi.pharmacy.audit.infrastructure.persistence;

import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditLog;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByAggregateIdOrderByTimestampDesc(UUID aggregateId, Pageable pageable);

    Page<AuditLog> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);

    Page<AuditLog> findByTimestampBetweenOrderByTimestampDesc(Instant startTime, Instant endTime, Pageable pageable);

    Page<AuditLog> findByResourceTypeOrderByTimestampDesc(AuditResourceType resourceType, Pageable pageable);

    Page<AuditLog> findByActionOrderByTimestampDesc(AuditAction action, Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:resourceType IS NULL OR a.resourceType = :resourceType)
        AND (:action IS NULL OR a.action = :action)
        AND (:userId IS NULL OR a.userId = :userId)
        AND (:service IS NULL OR a.service = :service)
        AND a.timestamp >= :startTime
        AND a.timestamp <= :endTime
        ORDER BY a.timestamp DESC
    """)
    Page<AuditLog> searchAuditLogs(
        @Param("resourceType") AuditResourceType resourceType,
        @Param("action") AuditAction action,
        @Param("userId") UUID userId,
        @Param("service") String service,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        Pageable pageable
    );

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.userId = :userId
        AND a.timestamp >= :startTime
        AND a.timestamp <= :endTime
        ORDER BY a.timestamp DESC
    """)
    Page<AuditLog> findUserActivityHistory(
        @Param("userId") UUID userId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        Pageable pageable
    );

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.resourceType = :resourceType
        AND a.timestamp >= :startTime
        AND a.timestamp <= :endTime
        ORDER BY a.action, a.timestamp DESC
    """)
    List<AuditLog> findComplianceReport(
        @Param("resourceType") AuditResourceType resourceType,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );
}
