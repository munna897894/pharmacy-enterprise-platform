CREATE TABLE notification_outbox (
    id BINARY(16) NOT NULL PRIMARY KEY,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    event_key VARCHAR(100) NOT NULL,
    payload LONGTEXT NOT NULL,
    headers LONGTEXT,
    occurred_at TIMESTAMP(6) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_notification_outbox_unpublished ON notification_outbox (published, created_at);
