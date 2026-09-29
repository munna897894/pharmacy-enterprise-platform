-- V2 seeded rows use non-hex UUID strings (e.g. 'o1234567-...') that Hibernate cannot map to java.util.UUID,
-- and an unpublished outbox event for a non-existent order. Remove them; applied migrations are immutable.
DELETE FROM outbox
WHERE id = 'e1234567-1234-1234-1234-123456789001';

DELETE FROM payment
WHERE id IN (
    'a1234567-1234-1234-1234-123456789001',
    'a2234567-1234-1234-1234-123456789002',
    'a3234567-1234-1234-1234-123456789003'
);
