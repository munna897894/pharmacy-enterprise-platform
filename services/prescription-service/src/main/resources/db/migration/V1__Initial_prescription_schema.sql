CREATE TABLE prescriptions (
    id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    prescriber_id VARCHAR(36) NOT NULL,
    prescribed_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    version INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_customer_id (customer_id),
    INDEX idx_prescriber_id (prescriber_id)
);

CREATE TABLE prescription_lines (
    id VARCHAR(36) PRIMARY KEY,
    prescription_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    instructions TEXT,
    dispensed_quantity DECIMAL(19, 4),
    filled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE,
    INDEX idx_prescription_id (prescription_id),
    INDEX idx_product_id (product_id)
);
