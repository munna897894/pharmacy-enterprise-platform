package com.jagapathi.pharmacy.inventory.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_adjustment", indexes = {
        @Index(name = "idx_stock_level_id", columnList = "stock_level_id"),
        @Index(name = "idx_adjustment_type", columnList = "adjustment_type")
})
public class StockAdjustment {

    @Id
    private String id;

    @Column(name = "stock_level_id", nullable = false)
    private String stockLevelId;

    @Column(name = "adjustment_type", nullable = false, length = 50)
    private String adjustmentType;

    @Column(name = "quantity_adjusted", nullable = false)
    private BigDecimal quantityAdjusted;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "adjusted_by", nullable = false, length = 36)
    private String adjustedBy;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    protected StockAdjustment() {}

    public StockAdjustment(String stockLevelId, String adjustmentType, BigDecimal quantityAdjusted,
                          String reason, String adjustedBy) {
        this.id = UUID.randomUUID().toString();
        this.stockLevelId = stockLevelId;
        this.adjustmentType = adjustmentType;
        this.quantityAdjusted = quantityAdjusted;
        this.reason = reason;
        this.adjustedBy = adjustedBy;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getStockLevelId() { return stockLevelId; }
    public String getAdjustmentType() { return adjustmentType; }
    public BigDecimal getQuantityAdjusted() { return quantityAdjusted; }
    public String getReason() { return reason; }
    public String getAdjustedBy() { return adjustedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
