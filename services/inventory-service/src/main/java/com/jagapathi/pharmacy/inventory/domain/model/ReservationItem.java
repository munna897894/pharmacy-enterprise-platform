package com.jagapathi.pharmacy.inventory.domain.model;

import java.math.BigDecimal;

/** One reserved line item: a product/quantity pair from an order's saga payload. */
public record ReservationItem(String productId, BigDecimal quantity) {
}
