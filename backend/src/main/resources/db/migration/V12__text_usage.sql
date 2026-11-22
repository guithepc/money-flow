-- Typed messages without a command are parsed by GPT like voice messages (PCT-66), so they get their own daily cap.
-- Additive: existing rows get 0, and code that does not know the column keeps working.
ALTER TABLE voice_usage ADD COLUMN text_count INTEGER NOT NULL DEFAULT 0;
