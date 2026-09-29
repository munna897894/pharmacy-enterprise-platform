package com.jagapathi.pharmacy.product.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "medication")
public class Medication {

    @Id
    private String id;

    @Column(name = "ndc_code", nullable = false, unique = true)
    private String ndcCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "generic_name")
    private String genericName;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "dosage_form")
    private String dosageForm;

    @Column(name = "strength")
    private String strength;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Medication() {
    }

    public Medication(String ndcCode, String name, String genericName, String manufacturer,
                      String dosageForm, String strength, BigDecimal unitPrice, String currency) {
        this.id = UUID.randomUUID().toString();
        this.ndcCode = ndcCode;
        this.name = name;
        this.genericName = genericName;
        this.manufacturer = manufacturer;
        this.dosageForm = dosageForm;
        this.strength = strength;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void update(String name, String genericName, String manufacturer,
                       String dosageForm, String strength, BigDecimal unitPrice) {
        this.name = name;
        this.genericName = genericName;
        this.manufacturer = manufacturer;
        this.dosageForm = dosageForm;
        this.strength = strength;
        this.unitPrice = unitPrice;
        this.updatedAt = Instant.now();
    }

    public void setActive(Boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getNdcCode() {
        return ndcCode;
    }

    public String getName() {
        return name;
    }

    public String getGenericName() {
        return genericName;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getDosageForm() {
        return dosageForm;
    }

    public String getStrength() {
        return strength;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public Boolean getActive() {
        return active;
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
}
