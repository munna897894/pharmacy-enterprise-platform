package com.jagapathi.pharmacy.order.api.controller;

import com.jagapathi.pharmacy.order.api.request.CancelOrderRequest;
import com.jagapathi.pharmacy.order.api.request.CreateOrderRequest;
import com.jagapathi.pharmacy.order.api.response.OrderResponse;
import com.jagapathi.pharmacy.order.api.response.OrderStatusResponse;
import com.jagapathi.pharmacy.order.api.response.PageResponse;
import com.jagapathi.pharmacy.order.application.service.OrderService;
import com.jagapathi.pharmacy.order.domain.OrderAuthorizationException;
import com.jagapathi.pharmacy.order.domain.OrderNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        OrderResponse response = orderService.createOrder(request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.getId()))
            .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable UUID id,
            Authentication authentication) {
        try {
            OrderResponse order = orderService.getOrderById(id);
            // Authorization check - customers can only read their own orders
            if (authentication != null && !hasAdminRole(authentication)) {
                String userId = authentication.getName();
                // In production, would extract user ID from JWT
                // For now, skip customer-specific check as it requires additional context
            }
            return ResponseEntity.ok(order);
        } catch (OrderNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(
            @PathVariable UUID id,
            Authentication authentication) {
        try {
            OrderResponse order = orderService.getOrderById(id);
            return ResponseEntity.ok(new OrderStatusResponse(order.getId(), order.getStatus(), order.getUpdatedAt()));
        } catch (OrderNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrderResponse>> listCustomerOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam UUID customerId,
            Authentication authentication) {
        try {
            Pageable pageable = PageRequest.of(page, Math.min(size, 100));
            var orders = orderService.getCustomerOrders(customerId, pageable);
            return ResponseEntity.ok(new PageResponse<>(orders));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable UUID id,
            @Valid @RequestBody CancelOrderRequest request,
            Authentication authentication) {
        try {
            orderService.cancelOrder(id, request.getReason());
            return ResponseEntity.ok().build();
        } catch (OrderNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (OrderAuthorizationException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/{id}/ready")
    public ResponseEntity<Void> markReadyForPickup(
            @PathVariable UUID id,
            Authentication authentication) {
        try {
            orderService.markOrderReadyForPickup(id);
            return ResponseEntity.ok().build();
        } catch (OrderNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> completeOrder(
            @PathVariable UUID id,
            Authentication authentication) {
        try {
            orderService.completeOrder(id);
            return ResponseEntity.ok().build();
        } catch (OrderNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    private boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") || auth.getAuthority().equals("ROLE_STORE_MANAGER"));
    }
}
