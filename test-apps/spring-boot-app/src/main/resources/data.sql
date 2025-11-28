-- Sample user authorities data
-- Add your users and their roles here

-- Example: Admin user with multiple roles
INSERT INTO USER_AUTHORITIES (USER_EMAIL, AUTHORITY) VALUES ('admin@example.com', 'ROLE_USER');
INSERT INTO USER_AUTHORITIES (USER_EMAIL, AUTHORITY) VALUES ('admin@example.com', 'ROLE_ADMIN');

-- Example: Regular user
INSERT INTO USER_AUTHORITIES (USER_EMAIL, AUTHORITY) VALUES ('user@example.com', 'ROLE_USER');

-- Example: User with specific permissions
INSERT INTO USER_AUTHORITIES (USER_EMAIL, AUTHORITY) VALUES ('editor@example.com', 'ROLE_USER');
INSERT INTO USER_AUTHORITIES (USER_EMAIL, AUTHORITY) VALUES ('editor@example.com', 'PERMISSION_EDIT');
INSERT INTO USER_AUTHORITIES (USER_EMAIL, AUTHORITY) VALUES ('editor@example.com', 'PERMISSION_CREATE');
