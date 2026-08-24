-- V17: allow documents without a file (metadata-only entries, e.g. S3 upload pending or manual notes)
-- V1 created file_url as NOT NULL; V14's S3 storage and the Documents UI both treat it as optional,
-- which made POST /documents 500 with a not-null violation.
ALTER TABLE documents ALTER COLUMN file_url DROP NOT NULL;
