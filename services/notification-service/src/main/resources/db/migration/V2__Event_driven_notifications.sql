ALTER TABLE notifications ADD COLUMN order_id BINARY(16) NULL;

CREATE INDEX idx_notifications_order_id ON notifications (order_id);

CREATE TABLE order_customers (
    order_id BINARY(16) NOT NULL PRIMARY KEY,
    customer_id BINARY(16) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL
);

INSERT INTO notification_templates (id, type, channel, subject_template, message_template, is_active) VALUES
    (X'6e0a7d1c2b3a4f5e8d9c0b1a2f3e4d01', 'ORDER_CONFIRMATION', 'EMAIL', 'Order {orderId} confirmed',
     'Your order {orderId} is confirmed. Payment of {amount} {currency} was received.', TRUE),
    (X'6e0a7d1c2b3a4f5e8d9c0b1a2f3e4d02', 'ORDER_CONFIRMATION', 'IN_APP', 'Order confirmed',
     'Order {orderId} is confirmed and is being prepared.', TRUE),
    (X'6e0a7d1c2b3a4f5e8d9c0b1a2f3e4d03', 'PAYMENT_FAILED', 'EMAIL', 'Payment for order {orderId} failed',
     'We could not process the payment for order {orderId} ({failureCode}). The order has been cancelled.', TRUE),
    (X'6e0a7d1c2b3a4f5e8d9c0b1a2f3e4d04', 'PAYMENT_FAILED', 'IN_APP', 'Payment failed',
     'Payment for order {orderId} failed. The order has been cancelled.', TRUE),
    (X'6e0a7d1c2b3a4f5e8d9c0b1a2f3e4d05', 'REFUND_PROCESSED', 'EMAIL', 'Refund for order {orderId}',
     'A refund of {amount} for order {orderId} has been processed.', TRUE),
    (X'6e0a7d1c2b3a4f5e8d9c0b1a2f3e4d06', 'PRESCRIPTION_VERIFIED', 'IN_APP', 'Prescription verified',
     'Your prescription {prescriptionId} has been verified.', TRUE),
    (X'6e0a7d1c2b3a4f5e8d9c0b1a2f3e4d07', 'PRESCRIPTION_REJECTED', 'IN_APP', 'Prescription rejected',
     'Your prescription {prescriptionId} could not be verified ({reasonCode}).', TRUE);
