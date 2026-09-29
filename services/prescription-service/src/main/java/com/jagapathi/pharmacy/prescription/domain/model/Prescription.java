package com.jagapathi.pharmacy.prescription.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "prescriptions", indexes = {
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_customer_id", columnList = "customer_id"),
    @Index(name = "idx_prescriber_id", columnList = "prescriber_id")
})
public class Prescription {

    @Id
    private String id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "prescriber_id", nullable = false)
    private String prescriberId;

    @Column(name = "prescribed_at", nullable = false)
    private Instant prescribedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PrescriptionStatus status;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "prescription_id")
    private List<PrescriptionLine> lines = new ArrayList<>();

    protected Prescription() {
    }

    public Prescription(String customerId, String prescriberId, Instant prescribedAt, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.prescriberId = prescriberId;
        this.prescribedAt = prescribedAt;
        this.expiresAt = expiresAt;
        this.status = PrescriptionStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addLine(PrescriptionLine line) {
        lines.add(line);
    }

    public void activate() {
        if (status != PrescriptionStatus.PENDING) {
            throw new IllegalStateException("Can only activate PENDING prescriptions");
        }
        this.status = PrescriptionStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void expire() {
        if (status == PrescriptionStatus.FILLED || status == PrescriptionStatus.EXPIRED) {
            return;
        }
        this.status = PrescriptionStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public void checkAndUpdateStatus() {
        if (status != PrescriptionStatus.ACTIVE) {
            return;
        }
        if (allLinesFilled()) {
            this.status = PrescriptionStatus.FILLED;
            this.updatedAt = Instant.now();
        }
    }

    public boolean allLinesFilled() {
        if (lines.isEmpty()) {
            return false;
        }
        return lines.stream().allMatch(PrescriptionLine::isFullyFilled);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isPending() {
        return status == PrescriptionStatus.PENDING;
    }

    public boolean isActive() {
        return status == PrescriptionStatus.ACTIVE;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getPrescriberId() {
        return prescriberId;
    }

    public Instant getPrescribedAt() {
        return prescribedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }

    public Integer getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PrescriptionLine> getLines() {
        return new ArrayList<>(lines);
    }
}
