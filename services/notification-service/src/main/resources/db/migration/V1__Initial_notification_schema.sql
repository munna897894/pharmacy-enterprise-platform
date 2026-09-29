CREATE TABLE notifications (
    id BINARY(16) NOT NULL PRIMARY KEY,
    customer_id BINARY(16) NOT NULL,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    message LONGTEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retry_count INT DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status),
    INDEX idx_type (type),
    INDEX idx_created_at (created_at)
);

CREATE TABLE notification_templates (
    id BINARY(16) NOT NULL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    subject_template VARCHAR(255),
    message_template LONGTEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_type_channel (type, channel)
);

CREATE TABLE processed_events (
    event_id VARCHAR(100) NOT NULL PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_event_id (event_id)
);
