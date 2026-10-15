-- Guided Telegram onboarding (#7): chosen language, current step and the account being drafted.
CREATE TYPE onboarding_step AS ENUM ('LANGUAGE', 'ACCOUNT', 'ACCOUNT_CONFIRM', 'MORE_ACCOUNTS', 'TUTORIAL', 'DONE');

ALTER TABLE owner ADD COLUMN language VARCHAR(2);
ALTER TABLE owner ADD CONSTRAINT owner_language_check CHECK (language IN ('pt', 'en'));

-- New owners start at LANGUAGE; the flow begins once /start links the chat.
ALTER TABLE owner ADD COLUMN onboarding_step onboarding_step NOT NULL DEFAULT 'LANGUAGE';

-- JSON draft of the account being created (name, currency, balance, field being asked). Cleared on save/cancel.
ALTER TABLE owner ADD COLUMN onboarding_draft JSONB;

-- Owners created before onboarding already have accounts and categories: they skip it.
-- language stays NULL for them, so the bot keeps following their Telegram language.
UPDATE owner SET onboarding_step = 'DONE';
