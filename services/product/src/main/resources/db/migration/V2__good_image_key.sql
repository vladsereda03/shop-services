-- Object storage for images (Р5): a good's image bytes move to a MinIO/S3 bucket, addressed by
-- this key. The legacy `image` bytea column stays until existing rows are backfilled into the
-- bucket (data migration) and is dropped in a later migration.

ALTER TABLE good ADD COLUMN image_key text;
