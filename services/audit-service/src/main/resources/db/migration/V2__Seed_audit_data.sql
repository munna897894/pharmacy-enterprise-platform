-- V2__Seed_audit_data.sql
INSERT INTO audit_logs (id, aggregate_id, service, action, resource_type, user_id, username, timestamp, status, details, ip_address, created_at)
VALUES
-- Product service events
('123e4567-e89b-12d3-a456-426614174001', '223e4567-e89b-12d3-a456-426614174001', 'product-service', 'CREATE', 'PRODUCT', '323e4567-e89b-12d3-a456-426614174001', 'admin_user', DATE_SUB(NOW(), INTERVAL 180 DAY), 'SUCCESS', '{"sku":"PROD001","name":"Aspirin","quantity":1000}', '192.168.1.1', NOW()),
('123e4567-e89b-12d3-a456-426614174002', '223e4567-e89b-12d3-a456-426614174001', 'product-service', 'UPDATE', 'PRODUCT', '323e4567-e89b-12d3-a456-426614174001', 'admin_user', DATE_SUB(NOW(), INTERVAL 175 DAY), 'SUCCESS', '{"quantity":["1000","950"]}', '192.168.1.1', NOW()),
('123e4567-e89b-12d3-a456-426614174003', '223e4567-e89b-12d3-a456-426614174002', 'product-service', 'CREATE', 'PRODUCT', '323e4567-e89b-12d3-a456-426614174001', 'admin_user', DATE_SUB(NOW(), INTERVAL 170 DAY), 'SUCCESS', '{"sku":"PROD002","name":"Ibuprofen","quantity":500}', '192.168.1.1', NOW()),

-- Customer service events
('123e4567-e89b-12d3-a456-426614174004', '223e4567-e89b-12d3-a456-426614174003', 'customer-service', 'CREATE', 'CUSTOMER', '323e4567-e89b-12d3-a456-426614174002', 'john_customer', DATE_SUB(NOW(), INTERVAL 160 DAY), 'SUCCESS', '{"name":"John Doe","email":"john@example.com"}', '192.168.1.2', NOW()),
('123e4567-e89b-12d3-a456-426614174005', '223e4567-e89b-12d3-a456-426614174003', 'customer-service', 'UPDATE', 'CUSTOMER', '323e4567-e89b-12d3-a456-426614174001', 'admin_user', DATE_SUB(NOW(), INTERVAL 155 DAY), 'SUCCESS', '{"email":["john@example.com","john.doe@example.com"]}', '192.168.1.1', NOW()),
('123e4567-e89b-12d3-a456-426614174006', '223e4567-e89b-12d3-a456-426614174004', 'customer-service', 'CREATE', 'CUSTOMER', '323e4567-e89b-12d3-a456-426614174003', 'jane_customer', DATE_SUB(NOW(), INTERVAL 150 DAY), 'SUCCESS', '{"name":"Jane Smith","email":"jane@example.com"}', '192.168.1.3', NOW()),

-- Order service events
('123e4567-e89b-12d3-a456-426614174007', '223e4567-e89b-12d3-a456-426614174005', 'order-service', 'CREATE', 'ORDER', '323e4567-e89b-12d3-a456-426614174002', 'john_customer', DATE_SUB(NOW(), INTERVAL 140 DAY), 'SUCCESS', '{"orderId":"ORD001","total":99.99,"status":"CREATED"}', '192.168.1.2', NOW()),
('123e4567-e89b-12d3-a456-426614174008', '223e4567-e89b-12d3-a456-426614174005', 'order-service', 'UPDATE', 'ORDER', '323e4567-e89b-12d3-a456-426614174002', 'john_customer', DATE_SUB(NOW(), INTERVAL 135 DAY), 'SUCCESS', '{"status":["CREATED","CONFIRMED"],"confirmedAt":"2024-04-05T10:30:00Z"}', '192.168.1.2', NOW()),
('123e4567-e89b-12d3-a456-426614174009', '223e4567-e89b-12d3-a456-426614174006', 'order-service', 'CREATE', 'ORDER', '323e4567-e89b-12d3-a456-426614174003', 'jane_customer', DATE_SUB(NOW(), INTERVAL 130 DAY), 'SUCCESS', '{"orderId":"ORD002","total":149.99,"status":"CREATED"}', '192.168.1.3', NOW()),
('123e4567-e89b-12d3-a456-426614174010', '223e4567-e89b-12d3-a456-426614174006', 'order-service', 'DELETE', 'ORDER', '323e4567-e89b-12d3-a456-426614174003', 'jane_customer', DATE_SUB(NOW(), INTERVAL 125 DAY), 'SUCCESS', '{"reason":"Customer requested cancellation"}', '192.168.1.3', NOW()),

-- Payment service events (NO PAYMENT DETAILS LOGGED - SANITIZED)
('123e4567-e89b-12d3-a456-426614174011', '223e4567-e89b-12d3-a456-426614174007', 'payment-service', 'CREATE', 'PAYMENT', '323e4567-e89b-12d3-a456-426614174002', 'john_customer', DATE_SUB(NOW(), INTERVAL 120 DAY), 'SUCCESS', '{"paymentId":"PAY001","amount":"99.99","currency":"USD","status":"PROCESSED"}', '192.168.1.2', NOW()),
('123e4567-e89b-12d3-a456-426614174012', '223e4567-e89b-12d3-a456-426614174008', 'payment-service', 'CREATE', 'PAYMENT', '323e4567-e89b-12d3-a456-426614174003', 'jane_customer', DATE_SUB(NOW(), INTERVAL 115 DAY), 'SUCCESS', '{"paymentId":"PAY002","amount":"149.99","currency":"USD","status":"PROCESSED"}', '192.168.1.3', NOW()),
('123e4567-e89b-12d3-a456-426614174013', '223e4567-e89b-12d3-a456-426614174008', 'payment-service', 'UPDATE', 'PAYMENT', '323e4567-e89b-12d3-a456-426614174001', 'admin_user', DATE_SUB(NOW(), INTERVAL 110 DAY), 'SUCCESS', '{"paymentId":"PAY002","status":["PROCESSED","REFUNDED"]}', '192.168.1.1', NOW()),

-- Prescription service events
('123e4567-e89b-12d3-a456-426614174014', '223e4567-e89b-12d3-a456-426614174009', 'prescription-service', 'CREATE', 'PRESCRIPTION', '323e4567-e89b-12d3-a456-426614174002', 'john_customer', DATE_SUB(NOW(), INTERVAL 100 DAY), 'SUCCESS', '{"prescriptionId":"RX001","medication":"Aspirin 500mg","quantity":30}', '192.168.1.2', NOW()),
('123e4567-e89b-12d3-a456-426614174015', '223e4567-e89b-12d3-a456-426614174009', 'prescription-service', 'UPDATE', 'PRESCRIPTION', '323e4567-e89b-12d3-a456-426614174001', 'pharmacist', DATE_SUB(NOW(), INTERVAL 95 DAY), 'SUCCESS', '{"prescriptionId":"RX001","status":["CREATED","ACTIVATED"]}', '192.168.1.4', NOW()),
('123e4567-e89b-12d3-a456-426614174016', '223e4567-e89b-12d3-a456-426614174009', 'prescription-service', 'UPDATE', 'PRESCRIPTION', '323e4567-e89b-12d3-a456-426614174001', 'pharmacist', DATE_SUB(NOW(), INTERVAL 90 DAY), 'SUCCESS', '{"prescriptionId":"RX001","status":["ACTIVATED","FILLED"],"filledAt":"2024-06-01T14:20:00Z"}', '192.168.1.4', NOW()),

-- Inventory service events
('123e4567-e89b-12d3-a456-426614174017', '223e4567-e89b-12d3-a456-426614174010', 'inventory-service', 'CREATE', 'INVENTORY', '323e4567-e89b-12d3-a456-426614174001', 'admin_user', DATE_SUB(NOW(), INTERVAL 80 DAY), 'SUCCESS', '{"productId":"223e4567-e89b-12d3-a456-426614174001","level":1000,"reorderLevel":100}', '192.168.1.1', NOW()),
('123e4567-e89b-12d3-a456-426614174018', '223e4567-e89b-12d3-a456-426614174010', 'inventory-service', 'UPDATE', 'INVENTORY', '323e4567-e89b-12d3-a456-426614174001', 'admin_user', DATE_SUB(NOW(), INTERVAL 75 DAY), 'SUCCESS', '{"productId":"223e4567-e89b-12d3-a456-426614174001","level":["1000","950"],"adjustmentReason":"sales"}', '192.168.1.1', NOW()),
('123e4567-e89b-12d3-a456-426614174019', '223e4567-e89b-12d3-a456-426614174011', 'inventory-service', 'CREATE', 'INVENTORY', '323e4567-e89b-12d3-a456-426614174001', 'admin_user', DATE_SUB(NOW(), INTERVAL 70 DAY), 'SUCCESS', '{"productId":"223e4567-e89b-12d3-a456-426614174002","level":500,"reorderLevel":50}', '192.168.1.1', NOW()),

-- Pharmacy service events
('123e4567-e89b-12d3-a456-426614174020', '223e4567-e89b-12d3-a456-426614174012', 'pharmacy-service', 'UPDATE', 'PHARMACY', '323e4567-e89b-12d3-a456-426614174001', 'manager', DATE_SUB(NOW(), INTERVAL 60 DAY), 'SUCCESS', '{"pharmacyId":"PH001","hours":"08:00-20:00"}', '192.168.1.5', NOW()),
('123e4567-e89b-12d3-a456-426614174021', '223e4567-e89b-12d3-a456-426614174012', 'pharmacy-service', 'UPDATE', 'PHARMACY', '323e4567-e89b-12d3-a456-426614174001', 'manager', DATE_SUB(NOW(), INTERVAL 55 DAY), 'SUCCESS', '{"pharmacyId":"PH001","status":"CLOSED_FOR_MAINTENANCE"}', '192.168.1.5', NOW()),

-- Failed operations
('123e4567-e89b-12d3-a456-426614174022', '223e4567-e89b-12d3-a456-426614174013', 'order-service', 'CREATE', 'ORDER', '323e4567-e89b-12d3-a456-426614174004', 'invalid_customer', DATE_SUB(NOW(), INTERVAL 50 DAY), 'FAILURE', '{"error":"Customer not found","customerId":"invalid-id"}', '192.168.1.6', NOW()),
('123e4567-e89b-12d3-a456-426614174023', '223e4567-e89b-12d3-a456-426614174014', 'payment-service', 'CREATE', 'PAYMENT', '323e4567-e89b-12d3-a456-426614174004', 'customer', DATE_SUB(NOW(), INTERVAL 45 DAY), 'FAILURE', '{"error":"Insufficient funds"}', '192.168.1.7', NOW()),

-- Additional events for diversity
('123e4567-e89b-12d3-a456-426614174024', '223e4567-e89b-12d3-a456-426614174015', 'product-service', 'UPDATE', 'PRODUCT', '323e4567-e89b-12d3-a456-426614174001', 'admin_user', DATE_SUB(NOW(), INTERVAL 40 DAY), 'SUCCESS', '{"quantity":["950","900"],"reason":"Regular inventory adjustment"}', '192.168.1.1', NOW()),
('123e4567-e89b-12d3-a456-426614174025', '223e4567-e89b-12d3-a456-426614174016', 'customer-service', 'UPDATE', 'CUSTOMER', '323e4567-e89b-12d3-a456-426614174002', 'john_customer', DATE_SUB(NOW(), INTERVAL 35 DAY), 'SUCCESS', '{"address":"123 Main St, City, State"}', '192.168.1.2', NOW()),
('123e4567-e89b-12d3-a456-426614174026', '223e4567-e89b-12d3-a456-426614174017', 'inventory-service', 'UPDATE', 'INVENTORY', '323e4567-e89b-12d3-a456-426614174001', 'admin_user', DATE_SUB(NOW(), INTERVAL 30 DAY), 'SUCCESS', '{"productId":"223e4567-e89b-12d3-a456-426614174002","level":["500","450"]}', '192.168.1.1', NOW()),
('123e4567-e89b-12d3-a456-426614174027', '223e4567-e89b-12d3-a456-426614174018', 'order-service', 'UPDATE', 'ORDER', '323e4567-e89b-12d3-a456-426614174002', 'john_customer', DATE_SUB(NOW(), INTERVAL 25 DAY), 'SUCCESS', '{"status":["CONFIRMED","FULFILLED"],"fulfilledAt":"2024-08-20T16:45:00Z"}', '192.168.1.2', NOW()),
('123e4567-e89b-12d3-a456-426614174028', '223e4567-e89b-12d3-a456-426614174019', 'prescription-service', 'CREATE', 'PRESCRIPTION', '323e4567-e89b-12d3-a456-426614174003', 'jane_customer', DATE_SUB(NOW(), INTERVAL 20 DAY), 'SUCCESS', '{"prescriptionId":"RX002","medication":"Lisinopril 10mg","quantity":30}', '192.168.1.3', NOW()),
('123e4567-e89b-12d3-a456-426614174029', '223e4567-e89b-12d3-a456-426614174019', 'prescription-service', 'UPDATE', 'PRESCRIPTION', '323e4567-e89b-12d3-a456-426614174001', 'pharmacist', DATE_SUB(NOW(), INTERVAL 15 DAY), 'SUCCESS', '{"prescriptionId":"RX002","status":["CREATED","ACTIVATED"]}', '192.168.1.4', NOW()),
('123e4567-e89b-12d3-a456-426614174030', '223e4567-e89b-12d3-a456-426614174020', 'pharmacy-service', 'UPDATE', 'PHARMACY', '323e4567-e89b-12d3-a456-426614174001', 'manager', DATE_SUB(NOW(), INTERVAL 10 DAY), 'SUCCESS', '{"pharmacyId":"PH001","status":"OPEN"}', '192.168.1.5', NOW());
