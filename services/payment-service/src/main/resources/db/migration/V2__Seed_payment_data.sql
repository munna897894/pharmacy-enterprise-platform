-- V2__Seed_payment_data.sql
-- Sample payment data for testing

INSERT INTO payment (id, order_id, customer_id, amount, currency, payment_method, status, reference_number, idempotency_key, created_at, updated_at)
VALUES 
    (
        'a1234567-1234-1234-1234-123456789001',
        'o1234567-1234-1234-1234-123456789001',
        'c1234567-1234-1234-1234-123456789001',
        150.50,
        'USD',
        'CREDIT_CARD',
        'PENDING',
        NULL,
        'i1234567-1234-1234-1234-123456789001',
        NOW(6),
        NOW(6)
    ),
    (
        'a2234567-1234-1234-1234-123456789002',
        'o2234567-1234-1234-1234-123456789002',
        'c2234567-1234-1234-1234-123456789002',
        200.00,
        'USD',
        'DEBIT_CARD',
        'SUCCESS',
        'TXN-a2234567-1234-1234-1234-123456789002',
        'i2234567-1234-1234-1234-123456789002',
        DATE_SUB(NOW(6), INTERVAL 1 DAY),
        DATE_SUB(NOW(6), INTERVAL 1 DAY)
    ),
    (
        'a3234567-1234-1234-1234-123456789003',
        'o3234567-1234-1234-1234-123456789003',
        'c3234567-1234-1234-1234-123456789003',
        75.25,
        'USD',
        'DIGITAL_WALLET',
        'FAILED',
        'DECLINED',
        'i3234567-1234-1234-1234-123456789003',
        DATE_SUB(NOW(6), INTERVAL 2 DAY),
        DATE_SUB(NOW(6), INTERVAL 2 DAY)
    );

INSERT INTO outbox (id, event_type, aggregate_id, payload, published, created_at)
VALUES 
    (
        'e1234567-1234-1234-1234-123456789001',
        'PaymentProcessedEvent',
        'a2234567-1234-1234-1234-123456789002',
        '{"eventId":"e1234567-1234-1234-1234-123456789001","eventType":"PaymentProcessedEvent","aggregateType":"Payment","aggregateId":"a2234567-1234-1234-1234-123456789002","occurredAt":"2026-09-16T10:00:00Z","producer":"payment-service","correlationId":"c1234567-1234-1234-1234-123456789001","paymentId":"a2234567-1234-1234-1234-123456789002","orderId":"o2234567-1234-1234-1234-123456789002","status":"SUCCESS","amount":200.00,"transactionId":"TXN-a2234567-1234-1234-1234-123456789002"}',
        FALSE,
        NOW(6)
    );
