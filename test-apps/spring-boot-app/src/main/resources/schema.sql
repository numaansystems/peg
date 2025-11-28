-- User authorities table
-- Stores the mapping between user email and their granted authorities (roles/permissions)
CREATE TABLE IF NOT EXISTS USER_AUTHORITIES (
    ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    USER_EMAIL VARCHAR(255) NOT NULL,
    AUTHORITY VARCHAR(100) NOT NULL
);

-- Create index for faster lookups by email
CREATE INDEX IF NOT EXISTS IDX_USER_EMAIL ON USER_AUTHORITIES(USER_EMAIL);
