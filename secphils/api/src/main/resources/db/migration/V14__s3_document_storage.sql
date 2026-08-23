-- V14: S3-backed document storage
--   * documents.file_url may hold an S3 URI (s3://bucket/key) in addition to
--     plain http(s) URLs; length 500 -> 1000 for roomy keys
--   * system_settings.storage JSONB: provider, region, bucket, access key,
--     secret key, custom endpoint, public-base-url, folder prefix, max size
BEGIN;

ALTER TABLE documents ALTER COLUMN file_url TYPE VARCHAR(1000);

ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS storage JSONB;

-- Sensible default for fresh/empty rows; admin edits are never overwritten
UPDATE system_settings
SET storage = '{
  "provider": "S3",
  "region": "us-east-1",
  "bucket": "",
  "accessKey": "",
  "secretKey": "",
  "endpoint": "",
  "publicBaseUrl": "",
  "folder": "",
  "maxUploadMb": 25
}'::jsonb
WHERE storage IS NULL;

COMMIT;
