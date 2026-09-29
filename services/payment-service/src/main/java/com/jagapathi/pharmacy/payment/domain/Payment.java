package com.jagapathi.pharmacy.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment", indexes = {
    @Index(name = "idx_payment_order_id", columnList = "order_id"),
    @Index(name = "idx_payment_customer_id", columnList = "customer_id"),
    @Index(name = "idx_payment_status", columnList = "status"),
    @Index(name = "idx_payment_idempotency_key", columnList = "idempotency_key")
})
public class Payment {
    
    @Id
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    
    @Column(name = "order_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID orderId;
    
    @Column(name = "customer_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID customerId;
    
    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;
    
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;
    
    @Column(name = "idempotency_key", length = 100, nullable = false, unique = true)
    private String idempotencyKey;
    
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant updatedAt;
    
    @Version
    @Column(name = "version")
    private int version;
    
    // Constructors
    public Payment() {}
    
    private Payment(Builder builder) {
        this.id = builder.id;
        this.orderId = builder.orderId;
        this.customerId = builder.customerId;
        this.amount = builder.amount;
        this.currency = builder.currency;
        this.paymentMethod = builder.paymentMethod;
        this.status = builder.status;
        this.referenceNumber = builder.referenceNumber;
        this.idempotencyKey = builder.idempotencyKey;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }
    
    // Builder pattern
    public static class Builder {
        private UUID id;
        private UUID orderId;
        private UUID customerId;
        private BigDecimal amount;
        private String currency;
        private PaymentMethod paymentMethod;
        private PaymentStatus status = PaymentStatus.PENDING;
        private String referenceNumber;
        private String idempotencyKey;
        private Instant createdAt;
        private Instant updatedAt;
        
        public Builder id(UUID id) {
            this.id = id;
            return this;
        }
        
        public Builder orderId(UUID orderId) {
            this.orderId = orderId;
            return this;
        }
        
        public Builder customerId(UUID customerId) {
            this.customerId = customerId;
            return this;
        }
        
        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Builder paymentMethod(PaymentMethod paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }
        
        public Builder status(PaymentStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder referenceNumber(String referenceNumber) {
            this.referenceNumber = referenceNumber;
            return this;
        }
        
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public Payment build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }
            if (this.createdAt == null) {
                this.createdAt = Instant.now();
            }
            if (this.updatedAt == null) {
                this.updatedAt = Instant.now();
            }
            return new Payment(this);
        }
    }
    
    // Getters and setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getOrderId() {
        return orderId;
    }
    
    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }
    
    public UUID getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }
    
    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public PaymentStatus getStatus() {
        return status;
    }
    
    public void setStatus(PaymentStatus status) {
        this.status = status;
    }
    
    public String getReferenceNumber() {
        return referenceNumber;
    }
    
    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }
    
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public int getVersion() {
        return version;
    }
    
    public void setVersion(int version) {
        this.version = version;
    }
}
