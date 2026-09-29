-- V1__Initial_payment_schema.sql
-- Payment Service Initial Schema

CREATE TABLE payment (
    id CHAR(36) NOT NULL PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    customer_id CHAR(36) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    reference_number VARCHAR(100),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version INT NOT NULL DEFAULT 0,
    
    INDEX idx_payment_order_id (order_id),
    INDEX idx_payment_customer_id (customer_id),
    INDEX idx_payment_status (status),
    INDEX idx_payment_idempotency_key (idempotency_key)
);

CREATE TABLE outbox (
    id CHAR(36) NOT NULL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
    payload LONGTEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    
    INDEX idx_outbox_published (published),
    INDEX idx_outbox_event_type (event_type),
    INDEX idx_outbox_created_at (created_at)
);
