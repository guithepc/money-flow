-- Password reset via a Telegram bot link (#26).

-- Set when the password is reset. JWTs issued before it are rejected, which ends every session.
ALTER TABLE owner ADD COLUMN password_changed_at TIMESTAMP;

-- One-time reset tokens. Only the SHA-256 (hex) of the token is stored; the raw token only goes out in the link.
CREATE TABLE password_reset_token (
    id         SERIAL    PRIMARY KEY,
    owner_id   INT       NOT NULL REFERENCES owner(owner_id) ON DELETE CASCADE,
    token_hash CHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_token_owner_id ON password_reset_token (owner_id);
