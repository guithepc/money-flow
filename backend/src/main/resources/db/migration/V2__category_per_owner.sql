CREATE TYPE category_type AS ENUM ('EXPENSE', 'INCOME');

ALTER TABLE category ADD COLUMN owner_id INT REFERENCES owner(owner_id);
ALTER TABLE category ADD COLUMN type category_type;

-- Categories were global until now; they all belonged to the first user.
UPDATE category SET owner_id = 1;

-- Merge duplicated descriptions into the lowest id so the unique constraint can be added.
WITH dup AS (
    SELECT category_id,
           MIN(category_id) OVER (PARTITION BY owner_id, description) AS keep_id
    FROM category
)
UPDATE transactions t SET category_id = dup.keep_id
FROM dup WHERE t.category_id = dup.category_id AND dup.category_id <> dup.keep_id;

WITH dup AS (
    SELECT category_id,
           MIN(category_id) OVER (PARTITION BY owner_id, description) AS keep_id
    FROM category
)
UPDATE recurring_payment r SET category_id = dup.keep_id
FROM dup WHERE r.category_id = dup.category_id AND dup.category_id <> dup.keep_id;

DELETE FROM category c
USING category k
WHERE c.owner_id = k.owner_id AND c.description = k.description AND c.category_id > k.category_id;

-- Backfill type from usage: INCOME when the category is used at least as much for income as for expense.
-- Review by hand after running.
UPDATE category c SET type = CASE
    WHEN (SELECT COUNT(*) FROM transactions t WHERE t.category_id = c.category_id AND t.operation_type = 'INCOME') > 0
     AND (SELECT COUNT(*) FROM transactions t WHERE t.category_id = c.category_id AND t.operation_type = 'INCOME')
      >= (SELECT COUNT(*) FROM transactions t WHERE t.category_id = c.category_id AND t.operation_type = 'EXPENSE')
    THEN 'INCOME'::category_type
    ELSE 'EXPENSE'::category_type
END;

ALTER TABLE category ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE category ALTER COLUMN type SET NOT NULL;
ALTER TABLE category ADD CONSTRAINT category_owner_description_key UNIQUE (owner_id, description);
