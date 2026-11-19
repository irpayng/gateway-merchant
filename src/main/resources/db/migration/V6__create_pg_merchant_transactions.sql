-- Transaction replica table for pg_merchant dashboard
-- Source of truth: tms-transaction (replicated via Kafka topic: transaction-result)

CREATE TABLE pg_merchant_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    source_id           BIGINT,
    reference           VARCHAR(64) NOT NULL,
    user_id             BIGINT NOT NULL,
    product_code        VARCHAR(50),
    amount              DECIMAL(19,2) NOT NULL,
    service_fee         DECIMAL(19,2),
    status_code         VARCHAR(30) NOT NULL,
    status_message      VARCHAR(500),
    channel             VARCHAR(50),
    payment_method      VARCHAR(50),
    customer_email      VARCHAR(255),
    customer_name       VARCHAR(255),
    bank_name           VARCHAR(100),
    account_number      VARCHAR(20),
    provider_code       VARCHAR(50),
    provider_reference  VARCHAR(100),
    metadata            JSONB,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    synced_at           TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_pg_merchant_transactions_reference UNIQUE (reference)
);

-- Index for user dashboard queries (list transactions by user, recent first)
CREATE INDEX idx_pg_merchant_transactions_user_created 
    ON pg_merchant_transactions (user_id, created_at DESC);

-- Index for filtering by status
CREATE INDEX idx_pg_merchant_transactions_user_status 
    ON pg_merchant_transactions (user_id, status_code);

-- Index for reference lookups
CREATE INDEX idx_pg_merchant_transactions_reference 
    ON pg_merchant_transactions (reference);

-- Index for filtering by payment method
CREATE INDEX idx_pg_merchant_transactions_user_payment_method 
    ON pg_merchant_transactions (user_id, payment_method);

COMMENT ON TABLE pg_merchant_transactions IS 'Replica of transactions for pg_merchant users from tms-transaction';
COMMENT ON COLUMN pg_merchant_transactions.source_id IS 'Original transaction ID from tms-transaction (if available)';
COMMENT ON COLUMN pg_merchant_transactions.status_code IS 'Transaction status: pending, completed, failed, reversed';
COMMENT ON COLUMN pg_merchant_transactions.payment_method IS 'Payment method: card, bank_transfer, wallet';
COMMENT ON COLUMN pg_merchant_transactions.metadata IS 'Full transaction metadata as JSONB for flexible data storage';
