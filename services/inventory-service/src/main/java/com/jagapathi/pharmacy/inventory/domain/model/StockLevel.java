package com.jagapathi.pharmacy.inventory.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_level", indexes = {
        @Index(name = "idx_pharmacy_id", columnList = "pharmacy_id"),
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_pharmacy_product", columnList = "pharmacy_id,product_id", unique = true)
})
public class StockLevel {

    @Id
    private String id;

    @Column(name = "pharmacy_id", nullable = false)
    private String pharmacyId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "quantity_on_hand", nullable = false)
    private BigDecimal quantityOnHand;

    @Column(name = "reorder_level", nullable = false)
    private BigDecimal reorderLevel;

    @Column(name = "reorder_quantity", nullable = false)
    private BigDecimal reorderQuantity;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Integer version;

    protected StockLevel() {}

    public StockLevel(String pharmacyId, String productId, BigDecimal quantityOnHand,
                     BigDecimal reorderLevel, BigDecimal reorderQuantity) {
        this.id = UUID.randomUUID().toString();
        this.pharmacyId = pharmacyId;
        this.productId = productId;
        this.quantityOnHand = quantityOnHand;
        this.reorderLevel = reorderLevel;
        this.reorderQuantity = reorderQuantity;
        this.status = "IN_STOCK";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        // Leave version null: Spring Data JPA's isNew() check relies on version == null
        // to route new entities through persist() instead of merge().
    }

    public void addStock(BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.quantityOnHand = this.quantityOnHand.add(quantity);
        this.updatedAt = Instant.now();
        updateStatus();
    }

    public void removeStock(BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (this.quantityOnHand.compareTo(quantity) < 0) {
            throw new IllegalArgumentException("Insufficient stock");
        }
        this.quantityOnHand = this.quantityOnHand.subtract(quantity);
        this.updatedAt = Instant.now();
        updateStatus();
    }

    private void updateStatus() {
        if (this.quantityOnHand.compareTo(BigDecimal.ZERO) == 0) {
            this.status = "OUT_OF_STOCK";
        } else if (this.quantityOnHand.compareTo(this.reorderLevel) <= 0) {
            this.status = "LOW_STOCK";
        } else {
            this.status = "IN_STOCK";
        }
    }

    public String getId() { return id; }
    public String getPharmacyId() { return pharmacyId; }
    public String getProductId() { return productId; }
    public BigDecimal getQuantityOnHand() { return quantityOnHand; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public BigDecimal getReorderQuantity() { return reorderQuantity; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
}
