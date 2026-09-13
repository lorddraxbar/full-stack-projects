-- V39: client-company lifecycle (pause/erasure). Companies gain the same
-- deactivated_at window users/services/projects already carry (V31 RetentionPolicy).
-- Announcements detach instead of blocking company erasure (V38 doctrine: the
-- RESTRICT on projects is untouched — a company with projects is deleted
-- THROUGH the service cascade, never through the DB behind its back).
ALTER TABLE companies ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMP;
ALTER TABLE announcements DROP CONSTRAINT IF EXISTS announcements_company_id_fkey;
ALTER TABLE announcements ADD CONSTRAINT announcements_company_id_fkey
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL;
