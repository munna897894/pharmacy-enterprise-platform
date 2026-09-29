package com.jagapathi.pharmacy.notification.infrastructure.persistence;

import com.jagapathi.pharmacy.notification.domain.OrderCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderCustomerRepository extends JpaRepository<OrderCustomer, UUID> {
}
