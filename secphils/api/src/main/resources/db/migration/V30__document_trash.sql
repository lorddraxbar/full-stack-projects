-- V30: Document trash (soft delete)
--
-- "Delete" on a live document now moves it to the trash (deleted_at stamped)
-- instead of hard-deleting row + S3 object immediately. Trashed documents:
--   * stay hidden from the live list, the project-detail Documents tab, and
--     the all-files view, but remain restorable,
--   * are automatically purged 7 days after trashing (scheduled sweep, no
--     password — the window is what bounds that),
--   * can be emptied earlier by any provider role with their account
--     password (POST /documents/trash/empty).
--
-- S3 objects are NEVER deleted by a move-to-trash; only restore/empty/purge
-- touch them (and never while a live message attachment shares the same
-- object). The trash applies uniformly, including to documents of archived
-- projects: if a project's own 7-day archive window expires first, its
-- hard-delete sweeps the documents (rows + objects) with it, as it always
-- did.

BEGIN;

ALTER TABLE documents ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS deleted_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL;

COMMIT;
