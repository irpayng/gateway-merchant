-- V2: Create compliance replica tables for local reads
-- These tables are populated via Kafka events from tms-kyc

-- Master compliance workflow record (replica)
CREATE TABLE pg_merchant_compliance (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,  -- Original ID from tms-kyc
    user_id BIGINT NOT NULL,
    merchant_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    rejection_reason VARCHAR(1000),
    current_step INT NOT NULL DEFAULT 0,
    profile_complete BOOLEAN NOT NULL DEFAULT FALSE,
    contact_complete BOOLEAN NOT NULL DEFAULT FALSE,
    owners_complete BOOLEAN NOT NULL DEFAULT FALSE,
    bank_accounts_complete BOOLEAN NOT NULL DEFAULT FALSE,
    agreement_complete BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_at TIMESTAMPTZ,
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_compliance_source UNIQUE (source_id),
    CONSTRAINT uk_compliance_user UNIQUE (user_id),
    CONSTRAINT uk_compliance_merchant UNIQUE (merchant_id)
);

CREATE INDEX idx_compliance_status ON pg_merchant_compliance(status);

-- Step 1: Profile details (replica)
CREATE TABLE pg_merchant_profiles (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    compliance_id BIGINT NOT NULL REFERENCES pg_merchant_compliance(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    country VARCHAR(3),
    industry VARCHAR(100),
    category VARCHAR(100),
    trading_name VARCHAR(255),
    sales_volume VARCHAR(50),
    business_type VARCHAR(30),
    business_name VARCHAR(255),
    rc_number VARCHAR(100),
    cac_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_profile_compliance UNIQUE (compliance_id)
);

CREATE INDEX idx_profile_user ON pg_merchant_profiles(user_id);

-- Step 2: Contact information (replica)
CREATE TABLE pg_merchant_contacts (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    compliance_id BIGINT NOT NULL REFERENCES pg_merchant_compliance(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(30),
    address VARCHAR(500),
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(3) DEFAULT 'NG',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_contact_compliance UNIQUE (compliance_id)
);

CREATE INDEX idx_contact_user ON pg_merchant_contacts(user_id);

-- Step 3: Owners/directors (replica) - multiple per compliance
CREATE TABLE pg_merchant_owners (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    compliance_id BIGINT NOT NULL REFERENCES pg_merchant_compliance(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    role VARCHAR(50),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    email VARCHAR(255),
    phone VARCHAR(30),
    bvn VARCHAR(20),
    nin VARCHAR(20),
    date_of_birth VARCHAR(10),
    address VARCHAR(500),
    ownership_percentage INT,
    id_document_url VARCHAR(500),
    id_document_type VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_owner_compliance ON pg_merchant_owners(compliance_id);
CREATE INDEX idx_owner_user ON pg_merchant_owners(user_id);

-- Step 4: Bank accounts (replica) - multiple per compliance
CREATE TABLE pg_merchant_bank_accounts (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    compliance_id BIGINT NOT NULL REFERENCES pg_merchant_compliance(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    bank_code VARCHAR(10),
    bank_name VARCHAR(100),
    account_number VARCHAR(20),
    account_name VARCHAR(255),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    currency VARCHAR(3) DEFAULT 'NGN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bank_compliance ON pg_merchant_bank_accounts(compliance_id);
CREATE INDEX idx_bank_user ON pg_merchant_bank_accounts(user_id);

-- Step 5: Agreement (replica)
CREATE TABLE pg_merchant_agreements (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    compliance_id BIGINT NOT NULL REFERENCES pg_merchant_compliance(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    terms_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    terms_version VARCHAR(50),
    privacy_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agreement_compliance UNIQUE (compliance_id)
);

CREATE INDEX idx_agreement_user ON pg_merchant_agreements(user_id);
