package com.jagapathi.pharmacy.prescription.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PrescriptionTest {
    
    @Test
    void testCreatePrescription() {
        String customerId = UUID.randomUUID().toString();
        String prescriberId = UUID.randomUUID().toString();
        Instant prescribedAt = Instant.now();
        Instant expiresAt = Instant.now().plusSeconds(30 * 24 * 3600);
        
        Prescription prescription = new Prescription(customerId, prescriberId, prescribedAt, expiresAt);
        
        assertThat(prescription.getId()).isNotNull();
        assertThat(prescription.getCustomerId()).isEqualTo(customerId);
        assertThat(prescription.getPrescriberId()).isEqualTo(prescriberId);
        assertThat(prescription.getPrescribedAt()).isEqualTo(prescribedAt);
        assertThat(prescription.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.PENDING);
        assertThat(prescription.getCreatedAt()).isNotNull();
        assertThat(prescription.getUpdatedAt()).isNotNull();
    }
    
    @Test
    void testAddLine() {
        Prescription prescription = createTestPrescription();
        String productId = UUID.randomUUID().toString();
        PrescriptionLine line = new PrescriptionLine(prescription.getId(), productId, BigDecimal.valueOf(2), "Test instructions");
        
        prescription.addLine(line);
        
        assertThat(prescription.getLines()).hasSize(1);
        assertThat(prescription.getLines().get(0).getProductId()).isEqualTo(productId);
    }
    
    @Test
    void testActivatePrescription() {
        Prescription prescription = createTestPrescription();
        
        prescription.activate();
        
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
    }
    
    @Test
    void testActivateNonPendingThrowsException() {
        Prescription prescription = createTestPrescription();
        prescription.activate();
        
        assertThatThrownBy(prescription::activate)
            .isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    void testFillLine() {
        Prescription prescription = createTestPrescription();
        PrescriptionLine line = new PrescriptionLine(prescription.getId(), UUID.randomUUID().toString(), BigDecimal.valueOf(5), null);
        
        line.fill(BigDecimal.valueOf(5));
        
        assertThat(line.getDispensedQuantity()).isEqualTo(BigDecimal.valueOf(5));
        assertThat(line.isFullyFilled()).isTrue();
    }
    
    @Test
    void testFillLineWithInvalidQuantity() {
        PrescriptionLine line = new PrescriptionLine(UUID.randomUUID().toString(), UUID.randomUUID().toString(), BigDecimal.valueOf(5), null);
        
        assertThatThrownBy(() -> line.fill(BigDecimal.valueOf(10)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot dispense more than prescribed amount");
    }
    
    @Test
    void testCheckAndUpdateStatusToFilled() {
        Prescription prescription = createTestPrescription();
        prescription.activate();
        
        String productId = UUID.randomUUID().toString();
        PrescriptionLine line = new PrescriptionLine(prescription.getId(), productId, BigDecimal.valueOf(2), null);
        prescription.addLine(line);
        
        line.fill(BigDecimal.valueOf(2));
        prescription.checkAndUpdateStatus();
        
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.FILLED);
    }
    
    @Test
    void testExpirePrescription() {
        Prescription prescription = createTestPrescription();
        prescription.activate();
        
        prescription.expire();
        
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.EXPIRED);
    }
    
    @Test
    void testIsExpired() {
        Instant nowTime = Instant.now();
        Instant expiredTime = nowTime.minusSeconds(3600); // 1 hour ago
        Instant expiresAt = expiredTime.minusSeconds(3600); // 2 hours ago
        
        Prescription prescription = new Prescription(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            expiredTime,
            expiresAt
        );
        
        assertThat(prescription.isExpired(nowTime)).isTrue();
    }
    
    @Test
    void testIsPending() {
        Prescription prescription = createTestPrescription();
        
        assertThat(prescription.isPending()).isTrue();
    }
    
    @Test
    void testIsActive() {
        Prescription prescription = createTestPrescription();
        prescription.activate();
        
        assertThat(prescription.isActive()).isTrue();
    }
    
    private Prescription createTestPrescription() {
        return new Prescription(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(30 * 24 * 3600)
        );
    }
}
