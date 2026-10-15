-- Guided Telegram onboarding (#7): chosen language, current step and the account being drafted.
CREATE TYPE onboarding_step AS ENUM ('LANGUAGE', 'ACCOUNT', 'ACCOUNT_CONFIRM', 'MORE_ACCOUNTS', 'TUTORIAL', 'DONE');

ALTER TABLE owner ADD COLUMN language VARCHAR(2);
ALTER TABLE owner ADD CONSTRAINT owner_language_check CHECK (language IN ('pt', 'en'));

-- New owners start at LANGUAGE; the flow begins once /start links the chat.
ALTER TABLE owner ADD COLUMN onboarding_step onboarding_step NOT NULL DEFAULT 'LANGUAGE';

-- JSON draft of the account being created (name, currency, balance, field being asked). Cleared on save/cancel.
ALTER TABLE owner ADD COLUMN onboarding_draft JSONB;

-- Owners already using the app (Telegram linked or at least one account) skip onboarding.
-- Everyone else keeps LANGUAGE and gets the guided flow when they link Telegram.
-- language stays NULL for skipped owners, so the bot keeps following their Telegram language.
UPDATE owner o SET onboarding_step = 'DONE'
WHERE o.telegram_chat_id IS NOT NULL
   OR EXISTS (SELECT 1 FROM account a WHERE a.owner_id = o.owner_id);

-- Account names are unique per owner, case-insensitive ("Nubank" and "nubank" are the same account).
CREATE UNIQUE INDEX account_owner_lower_description_key ON account (owner_id, lower(description));
