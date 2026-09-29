package com.jagapathi.pharmacy.payment.api;

import com.jagapathi.pharmacy.payment.application.PaymentService;
import com.jagapathi.pharmacy.payment.domain.PaymentMethod;
import com.jagapathi.pharmacy.payment.domain.PaymentNotFoundException;
import com.jagapathi.pharmacy.payment.domain.InvalidPaymentStateException;
import com.jagapathi.pharmacy.payment.domain.DuplicatePaymentException;
import com.jagapathi.pharmacy.payment.infrastructure.CustomerOwnershipClient;
import com.jagapathi.pharmacy.payment.infrastructure.CustomerOwnershipLookupException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {
    
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentController paymentController;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private CustomerOwnershipClient customerOwnershipClient;
    
    private UUID paymentId = UUID.randomUUID();
    private UUID orderId = UUID.randomUUID();
    private UUID customerId = UUID.randomUUID();
    private String idempotencyKey = UUID.randomUUID().toString();
    
    @Test
    @WithMockUser(roles = "PHARMACIST")
    void shouldProcessPaymentSuccessfully() throws Exception {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
            orderId.toString(),
            customerId.toString(),
            BigDecimal.valueOf(100.00),
            "USD",
            PaymentMethod.CREDIT_CARD,
            idempotencyKey
        );
        
        PaymentResponse response = new PaymentResponse(
            paymentId,
            orderId,
            customerId,
            BigDecimal.valueOf(100.00),
            "USD",
            "CREDIT_CARD",
            "SUCCESS",
            "TXN-123",
            Instant.now(),
            Instant.now()
        );
        
        when(paymentService.processPayment(
            eq(orderId), eq(customerId), any(), eq("USD"), eq(PaymentMethod.CREDIT_CARD), eq(idempotencyKey)
        )).thenReturn(response);
        
        mockMvc.perform(post("/api/v1/payments")
            .principal(staffAuthentication())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(paymentId.toString()))
            .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldForbidCustomerFromCreatingPaymentForAnotherCustomer() throws Exception {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
            orderId.toString(), customerId.toString(), BigDecimal.valueOf(100.00), "USD",
            PaymentMethod.CREDIT_CARD, idempotencyKey
        );
        when(customerOwnershipClient.isOwner(eq(customerId), any())).thenReturn(false);

        assertThat(paymentController.processPayment(request, customerAuthentication()).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        verifyNoInteractions(paymentService);
    }

    @Test
    @WithMockUser(roles = "PHARMACIST")
    void shouldReturnConflictWhenIdempotencyKeyIsReusedForDifferentPayment() throws Exception {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
            orderId.toString(), customerId.toString(), BigDecimal.valueOf(100.00), "USD",
            PaymentMethod.CREDIT_CARD, idempotencyKey
        );
        when(paymentService.processPayment(
            eq(orderId), eq(customerId), any(), eq("USD"), eq(PaymentMethod.CREDIT_CARD), eq(idempotencyKey)
        )).thenThrow(new DuplicatePaymentException("Idempotency key conflict"));

        mockMvc.perform(post("/api/v1/payments")
            .principal(staffAuthentication())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Idempotency conflict"));
    }
    
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldReturnBadRequestForInvalidOrderId() throws Exception {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
            "invalid-uuid",
            customerId.toString(),
            BigDecimal.valueOf(100.00),
            "USD",
            PaymentMethod.CREDIT_CARD,
            idempotencyKey
        );
        
        mockMvc.perform(post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldReturnBadRequestForMissingAmount() throws Exception {
        String payload = """
            {
                "orderId": "%s",
                "customerId": "%s",
                "currency": "USD",
                "paymentMethod": "CREDIT_CARD",
                "idempotencyKey": "%s"
            }
            """.formatted(orderId, customerId, idempotencyKey);
        
        mockMvc.perform(post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldReturnBadRequestForInvalidCurrency() throws Exception {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
            orderId.toString(),
            customerId.toString(),
            BigDecimal.valueOf(100.00),
            "USDA",
            PaymentMethod.CREDIT_CARD,
            idempotencyKey
        );
        
        mockMvc.perform(post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc = mockMvc;
    }
    
    @Test
    @WithMockUser(roles = "PHARMACIST", username = "pharmacist1")
    void shouldGetPaymentSuccessfully() throws Exception {
        PaymentResponse response = new PaymentResponse(
            paymentId,
            orderId,
            customerId,
            BigDecimal.valueOf(100.00),
            "USD",
            "CREDIT_CARD",
            "SUCCESS",
            "TXN-123",
            Instant.now(),
            Instant.now()
        );
        
        when(paymentService.getPaymentStatus(paymentId)).thenReturn(response);
        
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId)
            .principal(staffAuthentication())
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(paymentId.toString()));
    }
    
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldReturnNotFoundWhenPaymentDoesNotExist() throws Exception {
        when(paymentService.getPaymentStatus(paymentId))
            .thenThrow(new PaymentNotFoundException("Payment not found"));
        
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldForbidCustomerFromReadingAnotherCustomersPayment() {
        PaymentResponse response = new PaymentResponse(
            paymentId, orderId, customerId, BigDecimal.valueOf(100.00), "USD",
            "CREDIT_CARD", "SUCCESS", "TXN-123", Instant.now(), Instant.now()
        );
        when(paymentService.getPaymentStatus(paymentId)).thenReturn(response);
        when(customerOwnershipClient.isOwner(eq(customerId), any())).thenReturn(false);

        assertThat(paymentController.getPayment(paymentId, customerAuthentication()).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldAllowCustomerToReadPaymentWhenCustomerServiceConfirmsOwnership() {
        PaymentResponse response = new PaymentResponse(
            paymentId, orderId, customerId, BigDecimal.valueOf(100.00), "USD",
            "CREDIT_CARD", "SUCCESS", "TXN-123", Instant.now(), Instant.now()
        );
        when(paymentService.getPaymentStatus(paymentId)).thenReturn(response);
        when(customerOwnershipClient.isOwner(eq(customerId), any())).thenReturn(true);

        assertThat(paymentController.getPayment(paymentId, customerAuthentication()).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldReturnServiceUnavailableWhenOwnershipCannotBeVerified() {
        PaymentResponse response = new PaymentResponse(
            paymentId, orderId, customerId, BigDecimal.valueOf(100.00), "USD",
            "CREDIT_CARD", "SUCCESS", "TXN-123", Instant.now(), Instant.now()
        );
        when(paymentService.getPaymentStatus(paymentId)).thenReturn(response);
        when(customerOwnershipClient.isOwner(eq(customerId), any()))
            .thenThrow(new CustomerOwnershipLookupException(new IllegalStateException("dependency unavailable")));

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> paymentController.getPayment(paymentId, customerAuthentication())
        ).isInstanceOf(CustomerOwnershipLookupException.class);
        var problem = paymentController.handleCustomerOwnershipLookupFailure();
        assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(problem.getBody().getDetail()).isEqualTo("Payment access cannot be verified right now.");
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRefundPaymentSuccessfully() throws Exception {
        RefundRequest refundRequest = new RefundRequest("Customer request");
        
        PaymentResponse refundedResponse = new PaymentResponse(
            paymentId,
            orderId,
            customerId,
            BigDecimal.valueOf(100.00),
            "USD",
            "CREDIT_CARD",
            "REFUNDED",
            "TXN-123",
            Instant.now(),
            Instant.now()
        );
        
        when(paymentService.refundPayment(paymentId, "Customer request")).thenReturn(refundedResponse);
        
        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(refundRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnConflictWhenRefundingFailedPayment() throws Exception {
        RefundRequest refundRequest = new RefundRequest("Customer request");
        
        when(paymentService.refundPayment(paymentId, "Customer request"))
            .thenThrow(new InvalidPaymentStateException("Cannot refund failed payment"));
        
        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(refundRequest)))
            .andExpect(status().isConflict());
    }
    
    @Test
    @WithMockUser(roles = "PHARMACIST")
    void shouldGetPaymentByOrderId() throws Exception {
        PaymentResponse response = new PaymentResponse(
            paymentId,
            orderId,
            customerId,
            BigDecimal.valueOf(100.00),
            "USD",
            "CREDIT_CARD",
            "SUCCESS",
            "TXN-123",
            Instant.now(),
            Instant.now()
        );
        
        when(paymentService.getPaymentByOrderId(orderId)).thenReturn(response);
        
        mockMvc.perform(get("/api/v1/payments/by-order/{orderId}", orderId)
            .principal(staffAuthentication())
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    private JwtAuthenticationToken customerAuthentication() {
        Jwt jwt = Jwt.withTokenValue("caller-token")
            .header("alg", "RS256")
            .subject(UUID.randomUUID().toString())
            .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private Authentication staffAuthentication() {
        return new UsernamePasswordAuthenticationToken(
            "pharmacist", "not-used", List.of(new SimpleGrantedAuthority("ROLE_PHARMACIST"))
        );
    }
}
