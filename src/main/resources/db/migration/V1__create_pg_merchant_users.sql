-- V1: Create pg_merchant_users replica table
-- This table stores replicated user data from tms-user for fast reads.
-- Source of truth is tms-user; data is synced via Kafka events.

CREATE TABLE pg_merchant_users (
    id BIGINT PRIMARY KEY,  -- matches tms-user users.id (not auto-generated)
    merchant_id VARCHAR(20) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    business_name VARCHAR(255),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    email_verified_at TIMESTAMPTZ,
    blocked_at TIMESTAMPTZ,
    blocked_reason VARCHAR(500),
    suspended_at TIMESTAMPTZ,
    suspended_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),  -- tracks replication timestamp
    
    CONSTRAINT uk_pg_merchant_users_merchant_id UNIQUE (merchant_id),
    CONSTRAINT uk_pg_merchant_users_email UNIQUE (email)
);

-- Indexes for common queries
CREATE INDEX idx_pg_merchant_users_email ON pg_merchant_users(email);
CREATE INDEX idx_pg_merchant_users_merchant_id ON pg_merchant_users(merchant_id);
CREATE INDEX idx_pg_merchant_users_phone_number ON pg_merchant_users(phone_number);

COMMENT ON TABLE pg_merchant_users IS 'Replica of pg_merchant users from tms-user, synced via Kafka for fast reads';
COMMENT ON COLUMN pg_merchant_users.synced_at IS 'Timestamp when this record was last synced from tms-user';
