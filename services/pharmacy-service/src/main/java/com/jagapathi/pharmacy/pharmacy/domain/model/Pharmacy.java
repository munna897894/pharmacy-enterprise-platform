package com.jagapathi.pharmacy.pharmacy.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pharmacy", indexes = {
        @Index(name = "idx_license_number", columnList = "license_number"),
        @Index(name = "idx_status", columnList = "status")
})
public class Pharmacy {

    @Id
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "license_number", nullable = false, length = 100, unique = true)
    private String licenseNumber;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Integer version;

    protected Pharmacy() {}

    public Pharmacy(String name, String licenseNumber, String phone, String timezone) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.licenseNumber = licenseNumber;
        this.phone = phone;
        this.status = "OPEN";
        this.timezone = timezone;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        // Leave version null: Spring Data JPA's isNew() check relies on version == null
        // to route new entities through persist() instead of merge().
    }

    public void update(String name, String phone) {
        this.name = name;
        this.phone = phone;
        this.updatedAt = Instant.now();
    }

    public void setStatus(String status) {
        if (!isValidStatusTransition(this.status, status)) {
            throw new IllegalArgumentException("Invalid status transition from " + this.status + " to " + status);
        }
        this.status = status;
        this.updatedAt = Instant.now();
    }

    private boolean isValidStatusTransition(String from, String to) {
        if (from.equals(to)) return true;
        if ("OPEN".equals(from)) return "CLOSED".equals(to) || "DISABLED".equals(to);
        if ("CLOSED".equals(from)) return "OPEN".equals(to) || "DISABLED".equals(to);
        if ("DISABLED".equals(from)) return false;
        return false;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getLicenseNumber() { return licenseNumber; }
    public String getPhone() { return phone; }
    public String getStatus() { return status; }
    public String getTimezone() { return timezone; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
}
