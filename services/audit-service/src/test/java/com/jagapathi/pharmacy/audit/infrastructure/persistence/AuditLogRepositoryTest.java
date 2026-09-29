package com.jagapathi.pharmacy.audit.infrastructure.persistence;

import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditLog;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import com.jagapathi.pharmacy.audit.domain.AuditStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AuditLogRepositoryTest {
    @Autowired
    private AuditLogRepository repository;

    private UUID aggregateId;
    private UUID userId;
    private AuditLog auditLog;

    @BeforeEach
    void setUp() {
        aggregateId = UUID.randomUUID();
        userId = UUID.randomUUID();
        auditLog = new AuditLog(
            aggregateId, "product-service", AuditAction.CREATE, AuditResourceType.PRODUCT,
            userId, "testuser", Instant.now(), AuditStatus.SUCCESS, "{}", "192.168.1.1"
        );
    }

    @Test
    void testSaveAndFindAuditLog() {
        AuditLog saved = repository.save(auditLog);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAggregateId()).isEqualTo(aggregateId);
    }

    @Test
    void testFindByAggregateId() {
        repository.save(auditLog);
        Pageable pageable = PageRequest.of(0, 10);

        Page<AuditLog> result = repository.findByAggregateIdOrderByTimestampDesc(aggregateId, pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void testFindByUserId() {
        repository.save(auditLog);
        Pageable pageable = PageRequest.of(0, 10);

        Page<AuditLog> result = repository.findByUserIdOrderByTimestampDesc(userId, pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void testFindByResourceType() {
        repository.save(auditLog);
        Pageable pageable = PageRequest.of(0, 10);

        Page<AuditLog> result = repository.findByResourceTypeOrderByTimestampDesc(AuditResourceType.PRODUCT, pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void testFindByTimestampBetween() {
        repository.save(auditLog);
        Instant startTime = Instant.now().minus(java.time.Duration.ofHours(1));
        Instant endTime = Instant.now().plus(java.time.Duration.ofHours(1));
        Pageable pageable = PageRequest.of(0, 10);

        Page<AuditLog> result = repository.findByTimestampBetweenOrderByTimestampDesc(startTime, endTime, pageable);

        assertThat(result).isNotEmpty();
    }

    @Test
    void testSearchAuditLogs() {
        repository.save(auditLog);
        Instant startTime = Instant.now().minus(java.time.Duration.ofHours(1));
        Instant endTime = Instant.now().plus(java.time.Duration.ofHours(1));
        Pageable pageable = PageRequest.of(0, 10);

        Page<AuditLog> result = repository.searchAuditLogs(
            AuditResourceType.PRODUCT, AuditAction.CREATE, userId, "product-service",
            startTime, endTime, pageable
        );

        assertThat(result).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void testFindUserActivityHistory() {
        repository.save(auditLog);
        Instant startTime = Instant.now().minus(java.time.Duration.ofHours(1));
        Instant endTime = Instant.now().plus(java.time.Duration.ofHours(1));
        Pageable pageable = PageRequest.of(0, 10);

        Page<AuditLog> result = repository.findUserActivityHistory(userId, startTime, endTime, pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void testFindComplianceReport() {
        repository.save(auditLog);
        Instant startTime = Instant.now().minus(java.time.Duration.ofHours(1));
        Instant endTime = Instant.now().plus(java.time.Duration.ofHours(1));

        List<AuditLog> result = repository.findComplianceReport(
            AuditResourceType.PRODUCT, startTime, endTime
        );

        assertThat(result).isNotEmpty();
        assertThat(result.size()).isEqualTo(1);
    }

    @Test
    void testIndexPerformanceForAggregateIdQuery() {
        for (int i = 0; i < 100; i++) {
            AuditLog log = new AuditLog(
                aggregateId, "service-" + i, AuditAction.CREATE, AuditResourceType.PRODUCT,
                userId, "user-" + i, Instant.now(), AuditStatus.SUCCESS, "{}", null
            );
            repository.save(log);
        }

        Pageable pageable = PageRequest.of(0, 10);
        long startTime = System.currentTimeMillis();

        Page<AuditLog> result = repository.findByAggregateIdOrderByTimestampDesc(aggregateId, pageable);

        long duration = System.currentTimeMillis() - startTime;
        assertThat(result.getTotalElements()).isEqualTo(100);
        assertThat(duration).isLessThan(500);
    }
}
