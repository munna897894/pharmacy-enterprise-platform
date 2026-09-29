package com.jagapathi.pharmacy.pharmacy.domain.model;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "business_hour", indexes = {
        @Index(name = "idx_business_hour_pharmacy_id", columnList = "pharmacy_id")
})
public class BusinessHour {

    @Id
    private String id;

    @Column(name = "pharmacy_id", nullable = false)
    private String pharmacyId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "closed")
    private Boolean closed;

    protected BusinessHour() {}

    public BusinessHour(String pharmacyId, Integer dayOfWeek, LocalTime openTime, LocalTime closeTime) {
        this.id = UUID.randomUUID().toString();
        this.pharmacyId = pharmacyId;
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.closed = false;
    }

    public String getId() { return id; }
    public String getPharmacyId() { return pharmacyId; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public LocalTime getOpenTime() { return openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public Boolean getClosed() { return closed; }
}
