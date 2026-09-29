package com.jagapathi.pharmacy.customer.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.customer.api.request.CreateCustomerRequest;
import com.jagapathi.pharmacy.customer.application.service.CustomerService;
import com.jagapathi.pharmacy.customer.infrastructure.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_validate_customer_request_empty_first_name() throws Exception {
        CreateCustomerRequest invalidRequest = new CreateCustomerRequest(
                "",
                "Doe",
                "john@example.com",
                "555-1234",
                LocalDate.of(1990, 1, 15)
        );

        mockMvc.perform(post("/api/v1/customers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void should_validate_customer_request_invalid_email() throws Exception {
        CreateCustomerRequest invalidRequest = new CreateCustomerRequest(
                "John",
                "Doe",
                "not-an-email",
                "555-1234",
                LocalDate.of(1990, 1, 15)
        );

        mockMvc.perform(post("/api/v1/customers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void should_validate_customer_request_blank_last_name() throws Exception {
        CreateCustomerRequest invalidRequest = new CreateCustomerRequest(
                "John",
                "",
                "john@example.com",
                "555-1234",
                LocalDate.of(1990, 1, 15)
        );

        mockMvc.perform(post("/api/v1/customers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void should_validate_customer_request_missing_email() throws Exception {
        CreateCustomerRequest invalidRequest = new CreateCustomerRequest(
                "John",
                "Doe",
                "",
                "555-1234",
                LocalDate.of(1990, 1, 15)
        );

        mockMvc.perform(post("/api/v1/customers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
