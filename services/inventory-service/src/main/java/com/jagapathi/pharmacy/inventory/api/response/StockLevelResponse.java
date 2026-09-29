package com.jagapathi.pharmacy.inventory.api.response;

import com.jagapathi.pharmacy.inventory.domain.model.StockLevel;
import java.math.BigDecimal;
import java.time.Instant;

public record StockLevelResponse(
        String id,
        String pharmacyId,
        String productId,
        BigDecimal quantityOnHand,
        BigDecimal reorderLevel,
        BigDecimal reorderQuantity,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static StockLevelResponse from(StockLevel stockLevel) {
        return new StockLevelResponse(
                stockLevel.getId(),
                stockLevel.getPharmacyId(),
                stockLevel.getProductId(),
                stockLevel.getQuantityOnHand(),
                stockLevel.getReorderLevel(),
                stockLevel.getReorderQuantity(),
                stockLevel.getStatus(),
                stockLevel.getCreatedAt(),
                stockLevel.getUpdatedAt()
        );
    }
}
