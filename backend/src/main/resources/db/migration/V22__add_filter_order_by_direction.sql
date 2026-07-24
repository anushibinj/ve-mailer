ALTER TABLE filters
    ADD COLUMN IF NOT EXISTS order_by_direction VARCHAR(4);

UPDATE filters
SET order_by_direction = 'ASC'
WHERE order_by IS NOT NULL
  AND order_by_direction IS NULL;
