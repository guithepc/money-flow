-- Signup timestamp for volumetry. Not mapped in OwnerEntity: ddl-auto=validate tolerates extra
-- columns, new inserts get the DEFAULT now(), and metrics read it with native queries.
ALTER TABLE owner ADD COLUMN created_at TIMESTAMP;

-- Existing rows have no signup record. Best approximation, newest-signal first.
UPDATE owner SET created_at = COALESCE(terms_accepted_at, password_changed_at, now()) WHERE created_at IS NULL;

ALTER TABLE owner ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE owner ALTER COLUMN created_at SET NOT NULL;
