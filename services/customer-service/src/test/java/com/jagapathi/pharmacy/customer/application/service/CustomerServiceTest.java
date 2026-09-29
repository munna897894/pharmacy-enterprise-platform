package com.jagapathi.pharmacy.customer.application.service;

import com.jagapathi.pharmacy.customer.api.response.CustomerResponse;
import com.jagapathi.pharmacy.customer.domain.exception.CustomerAccessDeniedException;
import com.jagapathi.pharmacy.customer.domain.exception.CustomerNotFoundException;
import com.jagapathi.pharmacy.customer.domain.model.Customer;
import com.jagapathi.pharmacy.customer.domain.repository.CustomerRepository;
import com.jagapathi.pharmacy.customer.domain.repository.CustomerAddressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerAddressRepository customerAddressRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void should_create_customer() {
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.createCustomer(
                "user-123",
                "John",
                "Doe",
                "john@example.com",
                "555-1234",
                LocalDate.of(1990, 1, 15)
        );

        assertThat(response).isNotNull();
        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.email()).isEqualTo("john@example.com");
    }

    @Test
    void should_get_customer_as_owner() {
        Customer customer = new Customer(
                "user-123",
                "John",
                "Doe",
                "john@example.com",
                "555-1234",
                LocalDate.of(1990, 1, 15)
        );
        when(customerRepository.findById("c123")).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomer(
                "c123",
                "user-123",
                List.of()
        );

        assertThat(response).isNotNull();
        assertThat(response.firstName()).isEqualTo("John");
    }

    @Test
    void should_deny_access_to_other_users_profile() {
        Customer customer = new Customer(
                "user-123",
                "John",
                "Doe",
                "john@example.com",
                "555-1234",
                LocalDate.of(1990, 1, 15)
        );
        when(customerRepository.findById("c123")).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.getCustomer(
                "c123",
                "user-999",
                List.of()
        )).isInstanceOf(CustomerAccessDeniedException.class);
    }

    @Test
    void should_allow_staff_to_access_customer_profile() {
        Customer customer = new Customer(
                "user-123",
                "John",
                "Doe",
                "john@example.com",
                "555-1234",
                LocalDate.of(1990, 1, 15)
        );
        when(customerRepository.findById("c123")).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomer(
                "c123",
                "staff-user",
                List.of("ROLE_PHARMACIST")
        );

        assertThat(response).isNotNull();
    }

    @Test
    void should_throw_when_customer_not_found() {
        when(customerRepository.findById("c999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(
                "c999",
                "user-123",
                List.of()
        )).isInstanceOf(CustomerNotFoundException.class);
    }
}
