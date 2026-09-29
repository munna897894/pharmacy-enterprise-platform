package com.jagapathi.pharmacy.order.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Order Domain Tests")
class OrderTest {

    @Test
    @DisplayName("Should create order with CREATED status")
    void testCreateOrder() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();

        Order order = new Order(orderId, customerId, prescriptionId, pharmacyId,
            BigDecimal.valueOf(50), BigDecimal.valueOf(5), BigDecimal.valueOf(55), "USD");

        assertThat(order.getId()).isEqualTo(orderId);
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(55));
    }

    @Test
    @DisplayName("Should add order item to order")
    void testAddOrderItem() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.valueOf(50), BigDecimal.valueOf(5), BigDecimal.valueOf(55), "USD");

        OrderItem item = new OrderItem(UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.valueOf(2), BigDecimal.valueOf(25), BigDecimal.valueOf(50));

        order.addItem(item);

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0)).isEqualTo(item);
    }

    @Test
    @DisplayName("Should transition order status")
    void testOrderStatusTransition() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.valueOf(50), BigDecimal.valueOf(5), BigDecimal.valueOf(55), "USD");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        order.transitionTo(OrderStatus.INVENTORY_PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_PENDING);
    }

    @Test
    @DisplayName("Should reject invalid status transition")
    void testInvalidOrderStatusTransition() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.valueOf(50), BigDecimal.valueOf(5), BigDecimal.valueOf(55), "USD");

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CONFIRMED))
            .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    @DisplayName("Should calculate total amount correctly")
    void testOrderTotalAmount() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.valueOf(100), BigDecimal.valueOf(10), BigDecimal.valueOf(110), "USD");

        assertThat(order.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(order.getTax()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(order.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(110));
    }

    @Test
    @DisplayName("Should not allow cancellation of COMPLETED order")
    void testCannotCancelCompletedOrder() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.valueOf(50), BigDecimal.valueOf(5), BigDecimal.valueOf(55), "USD");

        // Transition to COMPLETED
        order.transitionTo(OrderStatus.INVENTORY_PENDING);
        order.transitionTo(OrderStatus.INVENTORY_RESERVED);
        order.transitionTo(OrderStatus.PAYMENT_PENDING);
        order.transitionTo(OrderStatus.CONFIRMED);
        order.transitionTo(OrderStatus.READY_FOR_PICKUP);
        order.transitionTo(OrderStatus.COMPLETED);

        // Cannot transition from COMPLETED to any state
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CANCELLED_INVENTORY))
            .isInstanceOf(InvalidOrderStateException.class);
    }
}
