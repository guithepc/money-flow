-- Persistent browser session (#24): long-lived refresh tokens behind a short-lived access JWT.
-- Only the SHA-256 of the opaque token is stored. Every refresh rotates the token inside the same family;
-- presenting a token that was already rotated (or revoked) revokes the whole family.
CREATE TABLE refresh_token (
    refresh_token_id BIGSERIAL    PRIMARY KEY,
    owner_id         INT          NOT NULL REFERENCES owner(owner_id) ON DELETE CASCADE,
    family_id        UUID         NOT NULL,
    token_hash       CHAR(64)     NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at       TIMESTAMP    NOT NULL,
    revoked_at       TIMESTAMP,
    user_agent       VARCHAR(255),
    CONSTRAINT refresh_token_token_hash_key UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_owner_id ON refresh_token (owner_id);
CREATE INDEX idx_refresh_token_family_id ON refresh_token (family_id);
CREATE INDEX idx_refresh_token_expires_at ON refresh_token (expires_at);
