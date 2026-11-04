CREATE TABLE pg_merchant_customers (
    customer_ref VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36),
    merchant_id VARCHAR(50) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone_number VARCHAR(16),
    blacklisted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_pg_merchant_customers_email UNIQUE (merchant_id, normalized_email)
);

CREATE INDEX idx_pg_merchant_customers_merchant_created
    ON pg_merchant_customers (merchant_id, created_at DESC);

CREATE INDEX idx_pg_merchant_transactions_customer_email
    ON pg_merchant_transactions (user_id, LOWER(BTRIM(customer_email)));
