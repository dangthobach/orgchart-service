-- Insert basic data to avoid foreign key constraint issues

-- Insert default permissions
INSERT INTO permissions (id, name, code, description, type, created_at, updated_at) 
VALUES 
('550e8400-e29b-41d4-a716-446655440001', 'Read Permission', 'READ', 'Permission to read data', 'READ', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440002', 'Write Permission', 'WRITE', 'Permission to write data', 'WRITE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440003', 'Delete Permission', 'DELETE', 'Permission to delete data', 'DELETE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440004', 'Execute Permission', 'EXECUTE', 'Permission to execute operations', 'EXECUTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert default role
INSERT INTO roles (id, name, description, created_at, updated_at) 
VALUES 
('550e8400-e29b-41d4-a716-446655440010', 'ADMIN', 'Administrator role with full access', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440011', 'USER', 'Regular user role', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert default menus
INSERT INTO menus (id, name, path, icon, "order", created_at, updated_at) 
VALUES 
('550e8400-e29b-41d4-a716-446655440020', 'Dashboard', '/dashboard', 'dashboard', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440021', 'Users', '/users', 'users', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440022', 'Settings', '/settings', 'settings', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert default tabs
INSERT INTO tabs (id, name, path, "order", menu_id, created_at, updated_at) 
VALUES 
('550e8400-e29b-41d4-a716-446655440030', 'Overview', '/dashboard/overview', 1, '550e8400-e29b-41d4-a716-446655440020', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440031', 'User List', '/users/list', 1, '550e8400-e29b-41d4-a716-446655440021', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440032', 'User Profile', '/users/profile', 2, '550e8400-e29b-41d4-a716-446655440021', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert default users
INSERT INTO users (id, username, email, first_name, last_name, created_at, updated_at) 
VALUES 
('550e8400-e29b-41d4-a716-446655440040', 'admin', 'admin@learnmore.com', 'Admin', 'User', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440041', 'user1', 'user1@learnmore.com', 'John', 'Doe', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert default API resources
INSERT INTO api_resources (id, name, path, method, description, is_public, resource_type, created_at, updated_at) 
VALUES 
('550e8400-e29b-41d4-a716-446655440050', 'Get Users', '/api/users', 'GET', 'Get all users', false, 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440051', 'Create User', '/api/users', 'POST', 'Create new user', false, 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert default teams
INSERT INTO teams (id, name, description, team_lead_id, created_at, updated_at) 
VALUES 
('550e8400-e29b-41d4-a716-446655440060', 'Development Team', 'Software development team', '550e8400-e29b-41d4-a716-446655440040', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440061', 'QA Team', 'Quality assurance team', '550e8400-e29b-41d4-a716-446655440041', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Associate users with roles
INSERT INTO user_roles (user_id, role_id) 
VALUES 
('550e8400-e29b-41d4-a716-446655440040', '550e8400-e29b-41d4-a716-446655440010'),
('550e8400-e29b-41d4-a716-446655440041', '550e8400-e29b-41d4-a716-446655440011');

-- Associate roles with permissions
INSERT INTO role_permissions (role_id, permission_id) 
VALUES 
('550e8400-e29b-41d4-a716-446655440010', '550e8400-e29b-41d4-a716-446655440001'),
('550e8400-e29b-41d4-a716-446655440010', '550e8400-e29b-41d4-a716-446655440002'),
('550e8400-e29b-41d4-a716-446655440010', '550e8400-e29b-41d4-a716-446655440003'),
('550e8400-e29b-41d4-a716-446655440010', '550e8400-e29b-41d4-a716-446655440004'),
('550e8400-e29b-41d4-a716-446655440011', '550e8400-e29b-41d4-a716-446655440001');