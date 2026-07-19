-- Fix admin password hash and ensure all three seeded users exist.
-- The correct bcrypt hash for 'password' (cost 12) is the same for all test users.
-- Using INSERT ... ON CONFLICT to be idempotent.
INSERT INTO users (tenant_id, username, password_hash, role)
VALUES
    (1, 'operator',  '$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq', 'OPERATOR'),
    (1, 'inspector', '$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq', 'INSPECTOR'),
    (1, 'admin',     '$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq', 'ADMIN')
ON CONFLICT (username) DO UPDATE
    SET password_hash = EXCLUDED.password_hash;
