package com.jagapathi.pharmacy.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_order")
public class Order {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "customer_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID customerId;

    @Column(name = "prescription_id", columnDefinition = "CHAR(36)")
    private UUID prescriptionId;

    @Column(name = "pharmacy_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID pharmacyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(length = 3, nullable = false)
    private String currency;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Integer version;

    public Order() {
    }

    public Order(UUID id, UUID customerId, UUID prescriptionId, UUID pharmacyId, 
                 BigDecimal subtotal, BigDecimal tax, BigDecimal total, String currency) {
        this.id = id;
        this.customerId = customerId;
        this.prescriptionId = prescriptionId;
        this.pharmacyId = pharmacyId;
        this.subtotal = subtotal;
        this.tax = tax;
        this.total = total;
        this.currency = currency;
        this.status = OrderStatus.CREATED;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        // version intentionally left null: Spring Data's isNew() check relies on a null
        // @Version field to route save() through persist() rather than merge(). Hibernate
        // assigns the initial version (0) itself once the entity is actually persisted.
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    public void transitionTo(OrderStatus newStatus) {
        if (!isValidTransition(this.status, newStatus)) {
            throw new InvalidOrderStateException("Cannot transition from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    private boolean isValidTransition(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case CREATED -> to == OrderStatus.INVENTORY_PENDING || to == OrderStatus.CANCELLED_INVENTORY;
            case INVENTORY_PENDING -> to == OrderStatus.INVENTORY_RESERVED || to == OrderStatus.CANCELLED_INVENTORY;
            case INVENTORY_RESERVED -> to == OrderStatus.PAYMENT_PENDING || to == OrderStatus.CANCELLED_INVENTORY;
            case PAYMENT_PENDING -> to == OrderStatus.CONFIRMED || to == OrderStatus.CANCELLED_PAYMENT;
            case CONFIRMED -> to == OrderStatus.READY_FOR_PICKUP;
            case READY_FOR_PICKUP -> to == OrderStatus.COMPLETED;
            default -> false;
        };
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getPrescriptionId() {
        return prescriptionId;
    }

    public UUID getPharmacyId() {
        return pharmacyId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrency() {
        return currency;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Integer getVersion() {
        return version;
    }
}
