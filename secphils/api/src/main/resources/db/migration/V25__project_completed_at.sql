-- V25: stamp when a project was completed (first transition to COMPLETED).
-- The list page shows "Completed" on each project; it is never cleared on
-- status changes away from COMPLETED (a re-completion overwrites it).
BEGIN;

ALTER TABLE projects ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

-- Backfill: already-completed projects get the update timestamp (best guess).
UPDATE projects
   SET completed_at = updated_at
 WHERE status = 'COMPLETED'
   AND completed_at IS NULL;

COMMIT;
