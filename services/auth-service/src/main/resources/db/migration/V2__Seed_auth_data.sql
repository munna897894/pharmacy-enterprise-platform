-- Seed test data with BCrypt hashed passwords
-- Password: password123 (hashed with BCrypt strength 12)
-- Hash: $2a$12$Xb.t5OW/SVXYhzVqhf3mQeEqDz9H7u.H5K4hR5F.NqK.Rl6xzXqPO

INSERT INTO app_user (id, username, email, password_hash, first_name, last_name, roles, is_active, created_at, updated_at)
VALUES 
('550e8400-e29b-41d4-a716-446655440001', 'admin', 'admin@pharmacy.local', '$2a$12$Xb.t5OW/SVXYhzVqhf3mQeEqDz9H7u.H5K4hR5F.NqK.Rl6xzXqPO', 'Admin', 'User', '["ADMIN"]', true, NOW(), NOW()),
('550e8400-e29b-41d4-a716-446655440002', 'pharmacist', 'pharmacist@pharmacy.local', '$2a$12$Xb.t5OW/SVXYhzVqhf3mQeEqDz9H7u.H5K4hR5F.NqK.Rl6xzXqPO', 'John', 'Pharmacist', '["PHARMACIST"]', true, NOW(), NOW()),
('550e8400-e29b-41d4-a716-446655440003', 'store_manager', 'manager@pharmacy.local', '$2a$12$Xb.t5OW/SVXYhzVqhf3mQeEqDz9H7u.H5K4hR5F.NqK.Rl6xzXqPO', 'Jane', 'Manager', '["STORE_MANAGER"]', true, NOW(), NOW()),
('550e8400-e29b-41d4-a716-446655440004', 'customer', 'customer@pharmacy.local', '$2a$12$Xb.t5OW/SVXYhzVqhf3mQeEqDz9H7u.H5K4hR5F.NqK.Rl6xzXqPO', 'Alice', 'Customer', '["CUSTOMER"]', true, NOW(), NOW()),
('550e8400-e29b-41d4-a716-446655440005', 'prescriber', 'prescriber@pharmacy.local', '$2a$12$Xb.t5OW/SVXYhzVqhf3mQeEqDz9H7u.H5K4hR5F.NqK.Rl6xzXqPO', 'Dr.', 'Prescriber', '["PRESCRIBER"]', true, NOW(), NOW());
