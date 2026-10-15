-- Telegram update_ids already handled by the webhook. Telegram retries an update when the webhook
-- is slow or fails, so a retried update must not register a transaction twice.
CREATE TABLE telegram_processed_update (
    update_id    BIGINT    PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_telegram_processed_update_processed_at ON telegram_processed_update (processed_at);
