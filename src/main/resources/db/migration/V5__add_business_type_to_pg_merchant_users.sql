-- V5: Add business_type column to pg_merchant_users replica table
-- This mirrors the business_type from tms-user for the local user replica.

ALTER TABLE pg_merchant_users
ADD COLUMN IF NOT EXISTS business_type VARCHAR(20);

COMMENT ON COLUMN pg_merchant_users.business_type IS 'Business registration type: starter (unregistered) or registered (CAC registered)';
