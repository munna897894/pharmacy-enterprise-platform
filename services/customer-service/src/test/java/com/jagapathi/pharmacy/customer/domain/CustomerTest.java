package com.jagapathi.pharmacy.customer.domain;

import com.jagapathi.pharmacy.customer.domain.model.Customer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerTest {

    @Test
    void should_create_customer() {
        Customer customer = new Customer(
                "user-123",
                "John",
                "Doe",
                "john@example.com",
                "555-1234",
                LocalDate.of(1990, 1, 15)
        );

        assertThat(customer.getId()).isNotNull();
        assertThat(customer.getAuthUserId()).isEqualTo("user-123");
        assertThat(customer.getFirstName()).isEqualTo("John");
        assertThat(customer.getStatus()).isEqualTo("ACTIVE");
        // New entities keep version == null until first persist; Hibernate then
        // initializes @Version to 0 on insert (see optimistic-locking fix).
        assertThat(customer.getVersion()).isNull();
    }

    @Test
    void should_update_customer() {
        Customer customer = new Customer(
                "user-123",
                "John",
                "Doe",
                "john@example.com",
                "555-1234",
                LocalDate.of(1990, 1, 15)
        );

        customer.update("Jane", "Smith", "jane@example.com", "555-5678", LocalDate.of(1991, 6, 20));

        assertThat(customer.getFirstName()).isEqualTo("Jane");
        assertThat(customer.getLastName()).isEqualTo("Smith");
        assertThat(customer.getEmail()).isEqualTo("jane@example.com");
    }
}
