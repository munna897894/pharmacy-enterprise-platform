package com.jagapathi.pharmacy.customer.domain.repository;

import com.jagapathi.pharmacy.customer.domain.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, String> {
    Optional<Customer> findByAuthUserId(String authUserId);
    Optional<Customer> findByEmail(String email);
}
