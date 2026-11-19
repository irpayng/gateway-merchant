-- V4: Make merchant_id nullable for legacy users without merchantId
-- Some users registered before merchantId generation was added need to be supported.

-- Drop the unique constraint first
ALTER TABLE pg_merchant_users DROP CONSTRAINT IF EXISTS uk_pg_merchant_users_merchant_id;

-- Make merchant_id nullable
ALTER TABLE pg_merchant_users ALTER COLUMN merchant_id DROP NOT NULL;

-- Re-add unique constraint that allows nulls (PostgreSQL UNIQUE allows multiple NULLs)
ALTER TABLE pg_merchant_users ADD CONSTRAINT uk_pg_merchant_users_merchant_id UNIQUE (merchant_id);

COMMENT ON COLUMN pg_merchant_users.merchant_id IS 'MID from tms-user. Nullable for legacy users registered before merchantId generation.';
