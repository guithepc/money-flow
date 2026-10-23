-- Voice messages per owner per day (#27). Each one costs a transcription plus a GPT parse, so they are capped daily.
-- usage_date is the day in Europe/Lisbon; count is incremented atomically before the audio is downloaded.
CREATE TABLE voice_usage (
    owner_id   INTEGER NOT NULL REFERENCES owner(owner_id) ON DELETE CASCADE,
    usage_date DATE    NOT NULL,
    count      INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (owner_id, usage_date)
);
