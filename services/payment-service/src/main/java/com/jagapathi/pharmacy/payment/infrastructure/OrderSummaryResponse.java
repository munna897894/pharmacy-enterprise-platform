package com.jagapathi.pharmacy.payment.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderSummaryResponse(UUID id, UUID customerId, BigDecimal total, String currency, String status) {
}
