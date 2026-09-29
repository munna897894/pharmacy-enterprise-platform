INSERT INTO customer (id, auth_user_id, first_name, last_name, email, phone, date_of_birth, status)
VALUES
('c1000000-0000-0000-0000-000000000001', 'u1000000-0000-0000-0000-000000000001', 'Alice', 'Johnson', 'alice.johnson@example.com', '555-0101', '1990-03-15', 'ACTIVE'),
('c1000000-0000-0000-0000-000000000002', 'u1000000-0000-0000-0000-000000000002', 'Bob', 'Smith', 'bob.smith@example.com', '555-0102', '1985-07-22', 'ACTIVE'),
('c1000000-0000-0000-0000-000000000003', 'u1000000-0000-0000-0000-000000000003', 'Carol', 'Williams', 'carol.williams@example.com', '555-0103', '1992-11-08', 'ACTIVE');

INSERT INTO customer_address (id, customer_id, type, line1, line2, city, state, postal_code, country)
VALUES
('a1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001', 'HOME', '123 Main St', 'Apt 4B', 'Springfield', 'IL', '62701', 'USA'),
('a1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000002', 'WORK', '456 Oak Ave', NULL, 'Chicago', 'IL', '60601', 'USA'),
('a1000000-0000-0000-0000-000000000003', 'c1000000-0000-0000-0000-000000000003', 'BILLING', '789 Elm Rd', 'Suite 100', 'Naperville', 'IL', '60540', 'USA');
