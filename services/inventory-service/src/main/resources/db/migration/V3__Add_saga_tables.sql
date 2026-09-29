-- Saga wiring: outbox/processed-event tables for Kafka reliability, plus
-- inventory_reservation to track per-order stock holds through the choreography saga.

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
    INDEX idx_outbox_published (published)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS processed_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    event_id CHAR(36) NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_event (order_id, event_id),
    INDEX idx_processed_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_reservation (
    id CHAR(36) NOT NULL PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    pharmacy_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    items_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reservation_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fix pre-existing seed data bug: V2 seeded stock_level.product_id values (m1000000-...)
-- that never matched product-service's real medication IDs (550e8400-e29b-41d4-a716-...),
-- so no availability check against real seeded medications could ever succeed.
UPDATE stock_level SET product_id = '550e8400-e29b-41d4-a716-446655440001' WHERE product_id = 'm1000000-0000-0000-0000-000000000001';
UPDATE stock_level SET product_id = '550e8400-e29b-41d4-a716-446655440002' WHERE product_id = 'm2000000-0000-0000-0000-000000000001';
UPDATE stock_level SET product_id = '550e8400-e29b-41d4-a716-446655440003' WHERE product_id = 'm3000000-0000-0000-0000-000000000001';
