package com.jagapathi.pharmacy.payment.infrastructure;

import com.jagapathi.pharmacy.payment.domain.Payment;
import com.jagapathi.pharmacy.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId")
    Optional<Payment> findByOrderId(@Param("orderId") UUID orderId);
    
    @Query("SELECT p FROM Payment p WHERE p.customerId = :customerId ORDER BY p.createdAt DESC")
    List<Payment> findByCustomerId(@Param("customerId") UUID customerId);
    
    @Query("SELECT p FROM Payment p WHERE p.status = :status ORDER BY p.createdAt DESC")
    List<Payment> findByStatus(@Param("status") PaymentStatus status);
    
    @Query("SELECT p FROM Payment p WHERE p.idempotencyKey = :idempotencyKey")
    Optional<Payment> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
