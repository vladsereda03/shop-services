-- Synthetic catalog for load testing (Р5): 50 goods, each carrying a ~50 KB image, so
-- GET /api/products returns a payload dominated by base64 image bytes — the exact anti-pattern
-- the MinIO track removes. Run once against the products DB before the baseline k6 run, and
-- reuse the same data for the re-measure so before/after compare like for like.
--
--   psql "postgresql://<user>:<pass>@localhost:5432/products" -f infra/k6/seed-catalog.sql
--   (compose stack: port 5433 instead of 5432)
--
-- Re-running is a no-op (guarded on the 'load-test' category). Remove the synthetic data with:
--   DELETE FROM good_manufacturer WHERE good_id IN (SELECT id FROM good WHERE category = 'load-test');
--   DELETE FROM good WHERE category = 'load-test';

INSERT INTO good (name, description, category, image, price_kopeck, quantity)
SELECT
    'Load test good ' || g,
    'Synthetic catalog entry for load testing',
    'load-test',
    -- ~50 KB of bytes: the content is irrelevant, only the size reproduces the payload weight
    decode(repeat('ab', 50000), 'hex'),
    (1000 + g)::bigint,
    100
FROM generate_series(1, 50) AS g
WHERE NOT EXISTS (SELECT 1 FROM good WHERE category = 'load-test');

-- link each synthetic good to one of the seeded manufacturers (ids 1..7)
INSERT INTO good_manufacturer (good_id, manufacturer_id)
SELECT id, (id % 7) + 1
FROM good
WHERE category = 'load-test'
  AND id NOT IN (SELECT good_id FROM good_manufacturer);
