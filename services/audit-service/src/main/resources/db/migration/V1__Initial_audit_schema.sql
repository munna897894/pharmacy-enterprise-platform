-- V1__Initial_audit_schema.sql
CREATE TABLE audit_logs (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    aggregate_id VARCHAR(36) NOT NULL,
    service VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    user_id VARCHAR(36),
    username VARCHAR(100),
    timestamp TIMESTAMP(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    details JSON,
    ip_address VARCHAR(50),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_aggregate_id_timestamp ON audit_logs(aggregate_id, timestamp);
CREATE INDEX idx_user_id_timestamp ON audit_logs(user_id, timestamp);
CREATE INDEX idx_resource_type_timestamp_action ON audit_logs(resource_type, timestamp, action);
CREATE INDEX idx_service ON audit_logs(service);
CREATE INDEX idx_timestamp ON audit_logs(timestamp);

CREATE TABLE processed_events (
    event_id VARCHAR(100) NOT NULL PRIMARY KEY,
    processed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_processed_at ON processed_events(processed_at);
