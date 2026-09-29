package com.jagapathi.pharmacy.payment.api;

import com.jagapathi.pharmacy.payment.domain.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProcessPaymentRequest(
    @NotNull(message = "orderId must not be null")
    @NotBlank(message = "orderId must not be blank")
    String orderId,
    
    @NotNull(message = "customerId must not be null")
    @NotBlank(message = "customerId must not be blank")
    String customerId,
    
    @NotNull(message = "amount must not be null")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @DecimalMax(value = "99999.99", message = "amount must not exceed 99999.99")
    BigDecimal amount,
    
    @NotBlank(message = "currency must not be blank")
    @Size(min = 3, max = 3, message = "currency must be 3-letter code")
    String currency,
    
    @NotNull(message = "paymentMethod must not be null")
    PaymentMethod paymentMethod,
    
    @NotNull(message = "idempotencyKey must not be null")
    @NotBlank(message = "idempotencyKey must not be blank")
    String idempotencyKey
) {}

