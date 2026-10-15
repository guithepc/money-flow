-- Prod was created before the Telegram invite feature, so baselining it as V1 skips these columns.
-- IF NOT EXISTS keeps this a no-op on databases that already have them.
ALTER TABLE owner ADD COLUMN IF NOT EXISTS invite_expiration TIMESTAMP;
ALTER TABLE owner ADD COLUMN IF NOT EXISTS invite_uuid UUID;
