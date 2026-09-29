-- Inventory Service Schema

CREATE TABLE stock_level (
    id VARCHAR(36) PRIMARY KEY,
    pharmacy_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    quantity_on_hand DECIMAL(19, 4) NOT NULL,
    reorder_level DECIMAL(19, 4) NOT NULL,
    reorder_quantity DECIMAL(19, 4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT DEFAULT 0,
    UNIQUE KEY uk_pharmacy_product (pharmacy_id, product_id),
    INDEX idx_pharmacy_id (pharmacy_id),
    INDEX idx_product_id (product_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE stock_adjustment (
    id VARCHAR(36) PRIMARY KEY,
    stock_level_id VARCHAR(36) NOT NULL,
    adjustment_type VARCHAR(50) NOT NULL,
    quantity_adjusted DECIMAL(19, 4) NOT NULL,
    reason VARCHAR(255),
    adjusted_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_adjustment FOREIGN KEY (stock_level_id) REFERENCES stock_level(id) ON DELETE CASCADE,
    INDEX idx_stock_level_id (stock_level_id),
    INDEX idx_adjustment_type (adjustment_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
