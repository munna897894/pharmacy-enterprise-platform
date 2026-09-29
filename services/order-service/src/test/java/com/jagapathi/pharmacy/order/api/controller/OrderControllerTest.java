package com.jagapathi.pharmacy.order.api.controller;

import com.jagapathi.pharmacy.order.api.request.CancelOrderRequest;
import com.jagapathi.pharmacy.order.api.request.CreateOrderRequest;
import com.jagapathi.pharmacy.order.api.response.OrderResponse;
import com.jagapathi.pharmacy.order.application.service.OrderService;
import com.jagapathi.pharmacy.order.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController Tests")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should create order successfully")
    @WithMockUser(roles = "CUSTOMER")
    void testCreateOrder() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();

        CreateOrderRequest request = new CreateOrderRequest(
            customerId, prescriptionId, pharmacyId, "USD", BigDecimal.valueOf(55.00),
            List.of(new CreateOrderRequest.OrderItemRequest(UUID.randomUUID(), BigDecimal.valueOf(2), BigDecimal.valueOf(25.00)))
        );

        Order order = new Order(UUID.randomUUID(), customerId, prescriptionId, pharmacyId,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(55.00), "USD");
        OrderResponse response = new OrderResponse(order);

        when(orderService.createOrder(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                .with(csrf())
                .header("Idempotency-Key", "test-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        verify(orderService).createOrder(any(), any());
    }

    @Test
    @DisplayName("Should return 400 when creating order with empty items")
    @WithMockUser(roles = "CUSTOMER")
    void testCreateOrderWithEmptyItems() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "USD", BigDecimal.valueOf(55.00),
            List.of()
        );

        when(orderService.createOrder(any(), any()))
            .thenThrow(new IllegalArgumentException("Order must have at least one item"));

        mockMvc.perform(post("/api/v1/orders")
                .with(csrf())
                .header("Idempotency-Key", "test-key-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should get order by ID")
    @WithMockUser(roles = "CUSTOMER")
    void testGetOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(55.00), "USD");
        OrderResponse response = new OrderResponse(order);

        when(orderService.getOrderById(orderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(orderId.toString()));

        verify(orderService).getOrderById(orderId);
    }

    @Test
    @DisplayName("Should return 404 when order not found")
    @WithMockUser(roles = "CUSTOMER")
    void testGetOrderNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrderById(orderId))
            .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should get order status")
    @WithMockUser(roles = "CUSTOMER")
    void testGetOrderStatus() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(55.00), "USD");
        OrderResponse response = new OrderResponse(order);

        when(orderService.getOrderById(orderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/{id}/status", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(OrderStatus.CREATED.toString()));
    }

    @Test
    @DisplayName("Should cancel order")
    @WithMockUser(roles = "CUSTOMER")
    void testCancelOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        CancelOrderRequest request = new CancelOrderRequest(orderId, "Customer request");

        doNothing().when(orderService).cancelOrder(orderId, "Customer request");

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        verify(orderService).cancelOrder(orderId, "Customer request");
    }

    @Test
    @DisplayName("Should return 409 when trying to cancel completed order")
    @WithMockUser(roles = "CUSTOMER")
    void testCancelCompletedOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        CancelOrderRequest request = new CancelOrderRequest(orderId, "Test");

        doThrow(new InvalidOrderStateException("Cannot cancel completed order"))
            .when(orderService).cancelOrder(orderId, "Test");

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should list customer orders")
    @WithMockUser(roles = "CUSTOMER")
    void testListCustomerOrders() throws Exception {
        UUID customerId = UUID.randomUUID();
        Order order = new Order(UUID.randomUUID(), customerId, UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(55.00), "USD");
        OrderResponse response = new OrderResponse(order);

        when(orderService.getCustomerOrders(eq(customerId), any()))
            .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/orders")
                .param("customerId", customerId.toString())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }
}
