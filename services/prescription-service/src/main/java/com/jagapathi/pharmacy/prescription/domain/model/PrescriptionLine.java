package com.jagapathi.pharmacy.prescription.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prescription_lines")
public class PrescriptionLine {

    @Id
    private String id;

    @Column(name = "prescription_id", nullable = false)
    private String prescriptionId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "dispensed_quantity", precision = 19, scale = 4)
    private BigDecimal dispensedQuantity;

    @Column(name = "filled_at")
    private Instant filledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PrescriptionLine() {
    }

    public PrescriptionLine(String prescriptionId, String productId, BigDecimal quantity, String instructions) {
        this.id = UUID.randomUUID().toString();
        this.prescriptionId = prescriptionId;
        this.productId = productId;
        this.quantity = quantity;
        this.instructions = instructions;
        this.dispensedQuantity = BigDecimal.ZERO;
        this.createdAt = Instant.now();
    }

    public void fill(BigDecimal dispensedQty) {
        if (dispensedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Dispensed quantity must be greater than 0");
        }
        if (dispensedQty.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("Cannot dispense more than prescribed amount");
        }
        this.dispensedQuantity = dispensedQty;
        this.filledAt = Instant.now();
    }

    public boolean isFullyFilled() {
        return dispensedQuantity != null && dispensedQuantity.compareTo(quantity) >= 0;
    }

    public String getId() {
        return id;
    }

    public String getPrescriptionId() {
        return prescriptionId;
    }

    public String getProductId() {
        return productId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getInstructions() {
        return instructions;
    }

    public BigDecimal getDispensedQuantity() {
        return dispensedQuantity;
    }

    public Instant getFilledAt() {
        return filledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
