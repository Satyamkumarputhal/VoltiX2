CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(tenant_id),
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed test users with bcrypt hash for 'password' ($2a$10$wN1rB4/1t0P8n6uY5uQj9.wD8i5Z5.W5/uC0Hk3fXf3/w3gD0Z6Yq)
INSERT INTO users (tenant_id, username, password_hash, role) VALUES 
(1, 'operator', '$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq', 'OPERATOR'),
(1, 'inspector', '$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq', 'INSPECTOR'),
(1, 'admin', '$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq', 'ADMIN');
