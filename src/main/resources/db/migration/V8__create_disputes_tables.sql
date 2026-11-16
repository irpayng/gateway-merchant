-- ============================================================================
-- Disputes and Conversations Replica Tables
-- Replicated from tms-dispute via Kafka for fast read queries.
-- ============================================================================

-- Disputes table
CREATE TABLE IF NOT EXISTS disputes (
    id                      BIGINT PRIMARY KEY,
    user_id                 BIGINT NOT NULL,
    transaction_reference   VARCHAR(100),
    subject                 VARCHAR(500) NOT NULL,
    message                 TEXT,
    status_code             VARCHAR(50) NOT NULL DEFAULT 'open',
    status_description      VARCHAR(255),
    attachment_url          TEXT,
    resolved_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_disputes_user_id ON disputes(user_id);
CREATE INDEX IF NOT EXISTS idx_disputes_status_code ON disputes(status_code);
CREATE INDEX IF NOT EXISTS idx_disputes_created_at ON disputes(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_disputes_user_status ON disputes(user_id, status_code);

-- Conversations table (dispute messages)
CREATE TABLE IF NOT EXISTS conversations (
    id              BIGINT PRIMARY KEY,
    dispute_id      BIGINT NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
    user_id         BIGINT,
    sender_type     VARCHAR(50) NOT NULL,
    sender_name     VARCHAR(255),
    message         TEXT NOT NULL,
    attachment_key  VARCHAR(500),
    attachment_name VARCHAR(255),
    attachment_type VARCHAR(100),
    attachment_size BIGINT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conversations_dispute_id ON conversations(dispute_id);
CREATE INDEX IF NOT EXISTS idx_conversations_created_at ON conversations(dispute_id, created_at);

-- Dispute read tracking (for unread counts per user)
CREATE TABLE IF NOT EXISTS dispute_reads (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    dispute_id          BIGINT NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
    last_read_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, dispute_id)
);

CREATE INDEX IF NOT EXISTS idx_dispute_reads_user ON dispute_reads(user_id);
