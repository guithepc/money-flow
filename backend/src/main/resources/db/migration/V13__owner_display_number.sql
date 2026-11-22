-- Per-owner numbers for accounts and transactions (PCT-76).
-- The bot used to show the global primary key ("ID: 789" for someone's first account) and asked users to type it.
-- display_number is what users see and type: 1, 2, 3... per owner. The primary key stays global and internal
-- (foreign keys, dashboard API). Numbers are never reused: deleting account 2 leaves 1 and 3, so an old
-- "/gasto ... 3" can never land on a different account.
--
-- Additive on purpose: the previous release keeps working against this schema (it never sets display_number,
-- the trigger fills it), so rolling back the application does not require rolling back this migration.

-- Last number handed out per owner and table. The upsert row lock serialises concurrent inserts of one owner
-- without touching the owner row, which other flows lock.
CREATE TABLE owner_sequence (
    owner_id   INTEGER     NOT NULL REFERENCES owner(owner_id) ON DELETE CASCADE,
    table_name VARCHAR(30) NOT NULL,
    last_value INTEGER     NOT NULL,
    PRIMARY KEY (owner_id, table_name)
);

CREATE FUNCTION assign_display_number() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO owner_sequence (owner_id, table_name, last_value) VALUES (NEW.owner_id, TG_TABLE_NAME, 1)
    ON CONFLICT (owner_id, table_name) DO UPDATE SET last_value = owner_sequence.last_value + 1
    RETURNING last_value INTO NEW.display_number;
    RETURN NEW;
END
$$;

ALTER TABLE account ADD COLUMN display_number INTEGER;
ALTER TABLE transactions ADD COLUMN display_number INTEGER;

-- Backfill in creation order, per owner.
UPDATE account a SET display_number = n.rn
FROM (SELECT account_id, ROW_NUMBER() OVER (PARTITION BY owner_id ORDER BY account_id) AS rn FROM account) n
WHERE a.account_id = n.account_id;

UPDATE transactions t SET display_number = n.rn
FROM (SELECT transactions_id, ROW_NUMBER() OVER (PARTITION BY owner_id ORDER BY transactions_id) AS rn FROM transactions) n
WHERE t.transactions_id = n.transactions_id;

INSERT INTO owner_sequence (owner_id, table_name, last_value)
SELECT owner_id, 'account', MAX(display_number) FROM account GROUP BY owner_id;

INSERT INTO owner_sequence (owner_id, table_name, last_value)
SELECT owner_id, 'transactions', MAX(display_number) FROM transactions GROUP BY owner_id;

ALTER TABLE account ALTER COLUMN display_number SET NOT NULL;
ALTER TABLE account ADD CONSTRAINT account_owner_display_number_key UNIQUE (owner_id, display_number);
ALTER TABLE transactions ALTER COLUMN display_number SET NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT transactions_owner_display_number_key UNIQUE (owner_id, display_number);

CREATE TRIGGER account_display_number BEFORE INSERT ON account
    FOR EACH ROW EXECUTE FUNCTION assign_display_number();
CREATE TRIGGER transactions_display_number BEFORE INSERT ON transactions
    FOR EACH ROW EXECUTE FUNCTION assign_display_number();
