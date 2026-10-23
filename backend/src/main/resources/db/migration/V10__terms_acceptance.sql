-- Consent record (#28): when the owner accepted the terms of use / privacy policy and which version.
-- Existing owners stay NULL and are asked to accept on their next login.
ALTER TABLE owner ADD COLUMN terms_accepted_at TIMESTAMP;
ALTER TABLE owner ADD COLUMN terms_version VARCHAR(20);
