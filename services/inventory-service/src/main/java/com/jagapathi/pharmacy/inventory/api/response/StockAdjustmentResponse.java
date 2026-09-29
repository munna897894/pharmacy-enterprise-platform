package com.jagapathi.pharmacy.inventory.api.response;

import com.jagapathi.pharmacy.inventory.domain.model.StockAdjustment;
import java.math.BigDecimal;
import java.time.Instant;

public record StockAdjustmentResponse(
        String id,
        String stockLevelId,
        String adjustmentType,
        BigDecimal quantityAdjusted,
        String reason,
        String adjustedBy,
        Instant createdAt
) {
    public static StockAdjustmentResponse from(StockAdjustment adjustment) {
        return new StockAdjustmentResponse(
                adjustment.getId(),
                adjustment.getStockLevelId(),
                adjustment.getAdjustmentType(),
                adjustment.getQuantityAdjusted(),
                adjustment.getReason(),
                adjustment.getAdjustedBy(),
                adjustment.getCreatedAt()
        );
    }
}
