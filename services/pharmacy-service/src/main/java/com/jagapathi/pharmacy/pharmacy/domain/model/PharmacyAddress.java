package com.jagapathi.pharmacy.pharmacy.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pharmacy_address", indexes = {
        @Index(name = "idx_pharmacy_id", columnList = "pharmacy_id")
})
public class PharmacyAddress {

    @Id
    private String id;

    @Column(name = "pharmacy_id", nullable = false)
    private String pharmacyId;

    @Column(name = "line1", nullable = false, length = 255)
    private String line1;

    @Column(name = "line2", length = 255)
    private String line2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 50)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "latitude", nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false)
    private BigDecimal longitude;

    protected PharmacyAddress() {}

    public PharmacyAddress(String pharmacyId, String line1, String line2, String city, 
                          String state, String postalCode, BigDecimal latitude, BigDecimal longitude) {
        this.id = UUID.randomUUID().toString();
        this.pharmacyId = pharmacyId;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() { return id; }
    public String getPharmacyId() { return pharmacyId; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
}
