INSERT INTO prescriptions (id, customer_id, prescriber_id, prescribed_at, expires_at, status, created_at, updated_at) VALUES
(
    '550e8400-e29b-41d4-a716-446655440001',
    '123e4567-e89b-12d3-a456-426614174000',
    '223e4567-e89b-12d3-a456-426614174000',
    DATE_ADD(NOW(), INTERVAL -1 DAY),
    DATE_ADD(NOW(), INTERVAL 30 DAY),
    'PENDING',
    NOW(),
    NOW()
),
(
    '550e8400-e29b-41d4-a716-446655440002',
    '123e4567-e89b-12d3-a456-426614174000',
    '223e4567-e89b-12d3-a456-426614174000',
    DATE_ADD(NOW(), INTERVAL -7 DAY),
    DATE_ADD(NOW(), INTERVAL 25 DAY),
    'ACTIVE',
    DATE_ADD(NOW(), INTERVAL -7 DAY),
    DATE_ADD(NOW(), INTERVAL -7 DAY)
),
(
    '550e8400-e29b-41d4-a716-446655440003',
    '123e4567-e89b-12d3-a456-426614174000',
    '223e4567-e89b-12d3-a456-426614174000',
    DATE_ADD(NOW(), INTERVAL -14 DAY),
    DATE_ADD(NOW(), INTERVAL 18 DAY),
    'FILLED',
    DATE_ADD(NOW(), INTERVAL -14 DAY),
    DATE_ADD(NOW(), INTERVAL -3 DAY)
);

INSERT INTO prescription_lines (id, prescription_id, product_id, quantity, instructions, dispensed_quantity, filled_at, created_at) VALUES
(
    '660e8400-e29b-41d4-a716-446655440001',
    '550e8400-e29b-41d4-a716-446655440001',
    '323e4567-e89b-12d3-a456-426614174000',
    '2.0000',
    'Take once daily with food',
    NULL,
    NULL,
    NOW()
),
(
    '660e8400-e29b-41d4-a716-446655440002',
    '550e8400-e29b-41d4-a716-446655440001',
    '423e4567-e89b-12d3-a456-426614174000',
    '1.5000',
    'Take as needed for headache',
    NULL,
    NULL,
    NOW()
),
(
    '660e8400-e29b-41d4-a716-446655440003',
    '550e8400-e29b-41d4-a716-446655440002',
    '323e4567-e89b-12d3-a456-426614174000',
    '3.0000',
    'Take twice daily',
    '2.5000',
    DATE_ADD(NOW(), INTERVAL -2 DAY),
    DATE_ADD(NOW(), INTERVAL -7 DAY)
),
(
    '660e8400-e29b-41d4-a716-446655440004',
    '550e8400-e29b-41d4-a716-446655440002',
    '423e4567-e89b-12d3-a456-426614174000',
    '2.0000',
    'Apply to affected area twice daily',
    '1.8000',
    DATE_ADD(NOW(), INTERVAL -1 DAY),
    DATE_ADD(NOW(), INTERVAL -7 DAY)
),
(
    '660e8400-e29b-41d4-a716-446655440005',
    '550e8400-e29b-41d4-a716-446655440003',
    '323e4567-e89b-12d3-a456-426614174000',
    '2.5000',
    'Take once daily',
    '2.5000',
    DATE_ADD(NOW(), INTERVAL -3 DAY),
    DATE_ADD(NOW(), INTERVAL -14 DAY)
),
(
    '660e8400-e29b-41d4-a716-446655440006',
    '550e8400-e29b-41d4-a716-446655440003',
    '523e4567-e89b-12d3-a456-426614174000',
    '1.0000',
    'Use once at night',
    '1.0000',
    DATE_ADD(NOW(), INTERVAL -3 DAY),
    DATE_ADD(NOW(), INTERVAL -14 DAY)
);
