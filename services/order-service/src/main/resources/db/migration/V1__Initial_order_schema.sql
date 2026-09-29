-- V1__Initial_order_schema.sql

CREATE TABLE IF NOT EXISTS customer_order (
    id CHAR(36) NOT NULL PRIMARY KEY,
    customer_id CHAR(36) NOT NULL,
    prescription_id CHAR(36),
    pharmacy_id CHAR(36) NOT NULL,
    status VARCHAR(50) NOT NULL,
    subtotal DECIMAL(19, 2) NOT NULL,
    tax DECIMAL(19, 2) NOT NULL,
    total DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    INDEX idx_pharmacy_id (pharmacy_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_item (
    id CHAR(36) NOT NULL PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    medication_id CHAR(36) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    unit_price DECIMAL(19, 2) NOT NULL,
    line_total DECIMAL(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    fulfilled_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES customer_order(id) ON DELETE CASCADE,
    INDEX idx_order_id (order_id),
    INDEX idx_medication_id (medication_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_status_history (
    id CHAR(36) NOT NULL PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    reason VARCHAR(500),
    event_id CHAR(36),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES customer_order(id) ON DELETE CASCADE,
    INDEX idx_order_id (order_id),
    INDEX idx_changed_at (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS outbox_event (
    id CHAR(36) NOT NULL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(50) NOT NULL,
    event_key CHAR(36),
    payload LONGTEXT NOT NULL,
    headers LONGTEXT,
    occurred_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_published (published),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS processed_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    event_id CHAR(36) NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_event (order_id, event_id),
    INDEX idx_order_id (order_id),
    INDEX idx_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
