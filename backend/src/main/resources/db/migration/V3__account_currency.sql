CREATE TYPE currency AS ENUM ('BRL', 'EUR', 'USD');

ALTER TABLE account ADD COLUMN currency currency NOT NULL DEFAULT 'EUR';

-- Local test account. Guarded by description so it can't hit an unrelated account in prod.
UPDATE account SET currency = 'BRL' WHERE account_id = 3 AND description = 'Teste';
