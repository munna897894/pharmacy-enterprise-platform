-- Pharmacy Service Schema

CREATE TABLE pharmacy (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    license_number VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    timezone VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT DEFAULT 0,
    INDEX idx_license_number (license_number),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pharmacy_address (
    id VARCHAR(36) PRIMARY KEY,
    pharmacy_id VARCHAR(36) NOT NULL,
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    CONSTRAINT fk_pharmacy_address FOREIGN KEY (pharmacy_id) REFERENCES pharmacy(id) ON DELETE CASCADE,
    INDEX idx_pharmacy_id (pharmacy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE business_hour (
    id VARCHAR(36) PRIMARY KEY,
    pharmacy_id VARCHAR(36) NOT NULL,
    day_of_week INT NOT NULL,
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    closed BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_business_hour FOREIGN KEY (pharmacy_id) REFERENCES pharmacy(id) ON DELETE CASCADE,
    INDEX idx_business_hour_pharmacy_id (pharmacy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
