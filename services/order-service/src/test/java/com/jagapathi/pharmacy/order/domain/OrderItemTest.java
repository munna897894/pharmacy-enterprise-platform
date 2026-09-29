package com.jagapathi.pharmacy.order.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderItem Domain Tests")
class OrderItemTest {

    @Test
    @DisplayName("Should create order item with PENDING status")
    void testCreateOrderItem() {
        UUID itemId = UUID.randomUUID();
        UUID medicationId = UUID.randomUUID();
        BigDecimal quantity = BigDecimal.valueOf(2.5);
        BigDecimal unitPrice = BigDecimal.valueOf(25.00);
        BigDecimal lineTotal = BigDecimal.valueOf(62.50);

        OrderItem item = new OrderItem(itemId, medicationId, quantity, unitPrice, lineTotal);

        assertThat(item.getId()).isEqualTo(itemId);
        assertThat(item.getMedicationId()).isEqualTo(medicationId);
        assertThat(item.getQuantity()).isEqualByComparingTo(quantity);
        assertThat(item.getUnitPrice()).isEqualByComparingTo(unitPrice);
        assertThat(item.getLineTotal()).isEqualByComparingTo(lineTotal);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PENDING);
    }

    @Test
    @DisplayName("Should transition item status from PENDING to RESERVED")
    void testItemStatusTransition() {
        OrderItem item = new OrderItem(UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.valueOf(2), BigDecimal.valueOf(25), BigDecimal.valueOf(50));

        item.transitionTo(OrderItemStatus.RESERVED);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.RESERVED);

        item.transitionTo(OrderItemStatus.PICKED);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PICKED);
    }

    @Test
    @DisplayName("Should reject invalid item status transition")
    void testInvalidItemStatusTransition() {
        OrderItem item = new OrderItem(UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.valueOf(2), BigDecimal.valueOf(25), BigDecimal.valueOf(50));

        assertThatThrownBy(() -> item.transitionTo(OrderItemStatus.SHIPPED))
            .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    @DisplayName("Should set fulfilledAt when transitioning to DELIVERED")
    void testSetFulfilledAtOnDelivery() {
        OrderItem item = new OrderItem(UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.valueOf(2), BigDecimal.valueOf(25), BigDecimal.valueOf(50));

        item.transitionTo(OrderItemStatus.RESERVED);
        item.transitionTo(OrderItemStatus.PICKED);
        item.transitionTo(OrderItemStatus.SHIPPED);
        item.transitionTo(OrderItemStatus.DELIVERED);

        assertThat(item.getFulfilledAt()).isNotNull();
    }
}
