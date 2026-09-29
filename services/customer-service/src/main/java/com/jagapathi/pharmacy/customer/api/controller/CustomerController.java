package com.jagapathi.pharmacy.customer.api.controller;

import com.jagapathi.pharmacy.customer.api.request.CreateCustomerAddressRequest;
import com.jagapathi.pharmacy.customer.api.request.CreateCustomerRequest;
import com.jagapathi.pharmacy.customer.api.request.UpdateCustomerRequest;
import com.jagapathi.pharmacy.customer.api.response.CustomerAddressResponse;
import com.jagapathi.pharmacy.customer.api.response.CustomerResponse;
import com.jagapathi.pharmacy.customer.application.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Customer profile management")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @Operation(summary = "Create customer profile")
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CreateCustomerRequest request,
            Authentication authentication) {
        String userId = extractUserId(authentication);
        CustomerResponse response = customerService.createCustomer(
                userId,
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone(),
                request.dateOfBirth()
        );
        return ResponseEntity
                .created(URI.create("/api/v1/customers/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get customer profile")
    public ResponseEntity<CustomerResponse> getProfile(
            @PathVariable String id,
            Authentication authentication) {
        String userId = extractUserId(authentication);
        List<String> roles = extractRoles(authentication);
        CustomerResponse response = customerService.getCustomer(id, userId, roles);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer profile")
    public ResponseEntity<CustomerResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateCustomerRequest request,
            Authentication authentication) {
        String userId = extractUserId(authentication);
        List<String> roles = extractRoles(authentication);
        CustomerResponse response = customerService.updateCustomer(
                id,
                userId,
                roles,
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone(),
                request.dateOfBirth()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/addresses")
    @Operation(summary = "Get customer addresses")
    public ResponseEntity<List<CustomerAddressResponse>> getAddresses(
            @PathVariable String id,
            Authentication authentication) {
        String userId = extractUserId(authentication);
        List<String> roles = extractRoles(authentication);
        List<CustomerAddressResponse> addresses = customerService.getAddresses(id, userId, roles);
        return ResponseEntity.ok(addresses);
    }

    @PostMapping("/{id}/addresses")
    @Operation(summary = "Add customer address")
    public ResponseEntity<CustomerAddressResponse> addAddress(
            @PathVariable String id,
            @Valid @RequestBody CreateCustomerAddressRequest request,
            Authentication authentication) {
        String userId = extractUserId(authentication);
        List<String> roles = extractRoles(authentication);
        CustomerAddressResponse response = customerService.addAddress(
                id,
                userId,
                roles,
                request.type(),
                request.line1(),
                request.line2(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.country()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    private String extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("sub");
        }
        return null;
    }

    private List<String> extractRoles(Authentication authentication) {
        if (authentication != null) {
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
        }
        return List.of();
    }
}
