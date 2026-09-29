-- V3__Idempotency_key.sql
-- Supports the order-creation Idempotency-Key contract documented in docs/06-api-contracts.md:
-- same key + same request body returns the original order; same key + a different
-- body is a 409 conflict.

CREATE TABLE IF NOT EXISTS idempotency_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    customer_id CHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    order_id CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_idempotency_key (customer_id, idempotency_key),
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
