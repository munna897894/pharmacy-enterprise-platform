package com.jagapathi.pharmacy.payment.api;

import com.jagapathi.pharmacy.payment.application.PaymentService;
import com.jagapathi.pharmacy.payment.domain.PaymentMethod;
import com.jagapathi.pharmacy.payment.domain.PaymentNotFoundException;
import com.jagapathi.pharmacy.payment.domain.InvalidPaymentStateException;
import com.jagapathi.pharmacy.payment.domain.DuplicatePaymentException;
import com.jagapathi.pharmacy.payment.infrastructure.CustomerOwnershipClient;
import com.jagapathi.pharmacy.payment.infrastructure.CustomerOwnershipLookupException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    
    private final PaymentService paymentService;
    private final CustomerOwnershipClient customerOwnershipClient;
    
    public PaymentController(PaymentService paymentService, CustomerOwnershipClient customerOwnershipClient) {
        this.paymentService = paymentService;
        this.customerOwnershipClient = customerOwnershipClient;
    }
    
    /**
     * POST /api/v1/payments
     * Process a new payment with idempotency support.
     * Authenticated: CUSTOMER, PHARMACIST, STORE_MANAGER, ADMIN
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PHARMACIST', 'STORE_MANAGER', 'ADMIN')")
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request,
            Authentication authentication) {
        
        try {
            UUID orderId = UUID.fromString(request.orderId());
            UUID customerId = UUID.fromString(request.customerId());
            if (!isStaff(authentication) && !isCustomerOwner(customerId, authentication)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            PaymentResponse response = paymentService.processPayment(
                orderId,
                customerId,
                request.amount(),
                request.currency(),
                request.paymentMethod(),
                request.idempotencyKey()
            );
            
            return ResponseEntity
                .created(URI.create("/api/v1/payments/" + response.id()))
                .body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .badRequest()
                .build();
        }
    }
    
    /**
     * GET /api/v1/payments/{id}
     * Get payment details. Customers can view their own payments; staff can view all payments.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PHARMACIST', 'STORE_MANAGER', 'ADMIN')")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID id,
            Authentication authentication) {
        
        try {
            PaymentResponse response = paymentService.getPaymentStatus(id);
            
            // Check authorization: customer can only view own payments
            if (!isStaff(authentication) && !isCustomerOwner(response.customerId(), authentication)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            return ResponseEntity.ok(response);
        } catch (PaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * GET /api/v1/orders/{orderId}/payment
     * Get payment for a specific order.
     */
    @GetMapping("/by-order/{orderId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PHARMACIST', 'STORE_MANAGER', 'ADMIN')")
    public ResponseEntity<PaymentResponse> getPaymentByOrder(
            @PathVariable UUID orderId,
            Authentication authentication) {
        
        try {
            PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
            
            // Check authorization
            if (!isStaff(authentication) && !isCustomerOwner(response.customerId(), authentication)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            return ResponseEntity.ok(response);
        } catch (PaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * POST /api/v1/payments/{id}/refund
     * Request a refund for a payment. Admin only.
     */
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable UUID id,
            @Valid @RequestBody RefundRequest request) {
        
        try {
            PaymentResponse response = paymentService.refundPayment(id, request.reason());
            return ResponseEntity.ok(response);
        } catch (PaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (InvalidPaymentStateException e) {
            ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
            problemDetail.setTitle("Invalid payment state");
            problemDetail.setDetail(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidArgument(IllegalArgumentException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Invalid argument");
        problemDetail.setDetail(e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }
    
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Void> handlePaymentNotFound(PaymentNotFoundException e) {
        return ResponseEntity.notFound().build();
    }
    
    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidState(InvalidPaymentStateException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problemDetail.setTitle("Invalid payment state");
        problemDetail.setDetail(e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(DuplicatePaymentException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problemDetail.setTitle("Idempotency conflict");
        problemDetail.setDetail(e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(CustomerOwnershipLookupException.class)
    public ResponseEntity<ProblemDetail> handleCustomerOwnershipLookupFailure() {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problemDetail.setTitle("Customer ownership verification unavailable");
        problemDetail.setDetail("Payment access cannot be verified right now.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problemDetail);
    }
    
    private boolean isStaff(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                || a.getAuthority().equals("ROLE_PHARMACIST")
                || a.getAuthority().equals("ROLE_STORE_MANAGER"));
    }
    
    private boolean isCustomerOwner(UUID customerId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return customerOwnershipClient.isOwner(customerId, jwt);
    }
}
