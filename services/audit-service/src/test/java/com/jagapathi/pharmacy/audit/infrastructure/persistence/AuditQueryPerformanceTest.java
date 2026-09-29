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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AuditQueryPerformanceTest {
    @Autowired
    private AuditLogRepository repository;

    private static final int LARGE_DATASET_SIZE = 10000;
    private UUID testAggregateId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testAggregateId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        createLargeDataset();
    }

    private void createLargeDataset() {
        Instant baseTime = Instant.now().minus(java.time.Duration.ofDays(365));
        for (int i = 0; i < LARGE_DATASET_SIZE; i++) {
            AuditLog log = new AuditLog(
                i % 100 == 0 ? testAggregateId : UUID.randomUUID(),
                "service-" + (i % 10),
                AuditAction.values()[i % AuditAction.values().length],
                AuditResourceType.values()[i % AuditResourceType.values().length],
                i % 50 == 0 ? testUserId : UUID.randomUUID(),
                "user-" + (i % 100),
                baseTime.plus(java.time.Duration.ofSeconds(i)),
                AuditStatus.SUCCESS,
                "{\"data\":\"test\"}",
                "192.168.1." + (i % 256)
            );
            repository.save(log);
        }
        repository.flush();
    }

    @Test
    void testAuditByResourceTypePerformance() {
        Pageable pageable = PageRequest.of(0, 100);
        long startTime = System.currentTimeMillis();

        Page<AuditLog> result = repository.findByResourceTypeOrderByTimestampDesc(
            AuditResourceType.PRODUCT, pageable
        );

        long duration = System.currentTimeMillis() - startTime;
        assertThat(result).isNotEmpty();
        assertThat(duration).isLessThan(2000);
    }

    @Test
    void testUserActivityHistoryPerformance() {
        Instant startDate = Instant.now().minus(java.time.Duration.ofDays(365));
        Instant endDate = Instant.now();
        Pageable pageable = PageRequest.of(0, 100);

        long startTime = System.currentTimeMillis();
        Page<AuditLog> result = repository.findUserActivityHistory(testUserId, startDate, endDate, pageable);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(duration).isLessThan(2000);
    }

    @Test
    void testSearchAuditLogsPerformance() {
        Instant startDate = Instant.now().minus(java.time.Duration.ofDays(365));
        Instant endDate = Instant.now();
        Pageable pageable = PageRequest.of(0, 100);

        long startTime = System.currentTimeMillis();
        Page<AuditLog> result = repository.searchAuditLogs(
            AuditResourceType.PRODUCT, AuditAction.CREATE, testUserId, "service-1",
            startDate, endDate, pageable
        );
        long duration = System.currentTimeMillis() - startTime;

        assertThat(duration).isLessThan(2000);
    }

    @Test
    void testComplianceReportPerformance() {
        Instant startDate = Instant.now().minus(java.time.Duration.ofDays(365));
        Instant endDate = Instant.now();

        long startTime = System.currentTimeMillis();
        var result = repository.findComplianceReport(AuditResourceType.PRODUCT, startDate, endDate);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(duration).isLessThan(2000);
    }

    @Test
    void testAggregateIdQueryPerformance() {
        Pageable pageable = PageRequest.of(0, 50);
        long startTime = System.currentTimeMillis();

        Page<AuditLog> result = repository.findByAggregateIdOrderByTimestampDesc(testAggregateId, pageable);

        long duration = System.currentTimeMillis() - startTime;
        assertThat(result.getTotalElements()).isGreaterThan(0);
        assertThat(duration).isLessThan(500);
    }
}
