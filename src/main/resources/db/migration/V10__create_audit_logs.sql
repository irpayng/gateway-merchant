-- ============================================================================
-- Merchant audit log
-- One row per state-changing request (POST/PUT/PATCH/DELETE) made against the
-- merchant dashboard API. Read-only (fetch) requests are not recorded.
-- ============================================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id                  BIGSERIAL PRIMARY KEY,

    -- Who performed the action
    user_id             BIGINT,
    merchant_id         VARCHAR(50),
    actor_email         VARCHAR(255),
    actor_name          VARCHAR(255),

    -- What was done
    action              VARCHAR(150) NOT NULL,
    module              VARCHAR(100) NOT NULL,
    http_method         VARCHAR(10)  NOT NULL,
    endpoint            VARCHAR(255) NOT NULL,
    request_path        VARCHAR(500) NOT NULL,
    resource_id         VARCHAR(255),

    -- Result of the action
    status              VARCHAR(20)  NOT NULL,
    status_code         INTEGER      NOT NULL,
    result_message      TEXT,
    error_message       TEXT,
    request_payload     JSONB,
    response_payload    JSONB,

    -- Request context
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(500),
    duration_ms         BIGINT,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_merchant_created ON audit_logs(merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_created ON audit_logs(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_module ON audit_logs(module);
CREATE INDEX IF NOT EXISTS idx_audit_logs_status ON audit_logs(status);

COMMENT ON TABLE audit_logs IS 'Merchant activity trail; fetch (GET) requests are excluded';
COMMENT ON COLUMN audit_logs.request_payload IS 'Request body with sensitive fields redacted';
COMMENT ON COLUMN audit_logs.response_payload IS 'Response body with sensitive fields redacted';
