-- Archive lifecycle metadata for projects.
--   archived_at:      set when staff archives (soft delete).
--   previous_status:  the status before archiving, restored on un-archive.
--   delete_at:        7 days after archived_at -> eligible for hard delete.
--   archive_dir:      S3 prefix where the project's objects were relocated
--                     while archived (restoration moves them back).
-- A hard delete is allowed only when (delete_at <= now()) or the admin
-- supplies their account password to force it immediately.
ALTER TABLE projects ADD COLUMN IF NOT EXISTS archived_at     TIMESTAMP;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS delete_at       TIMESTAMP;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS previous_status VARCHAR(30);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS archive_dir     VARCHAR(500);
COMMENT ON COLUMN projects.archived_at    IS 'When the project was archived (soft delete).';
COMMENT ON COLUMN projects.delete_at      IS 'When the project becomes eligible for hard delete (archived_at + 7 days).';
COMMENT ON COLUMN projects.previous_status IS 'Status before archiving; restored on un-archive.';
COMMENT ON COLUMN projects.archive_dir    IS 'S3 prefix holding the project''s archived objects.';
