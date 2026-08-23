-- V15: Task subtasks
--   * tasks.sub_tasks JSONB: array of {"id": number, "title": string, "completed": boolean}
--     (id is a client-generated client-side key, e.g. Date.now(); server treats the
--     array opaquely and persists it verbatim on create/update)
--   * default '[]' for existing and fresh rows so old clients that omit the field
--     keep working (null is tolerated on the wire and mapped to [])
--   * GIN index on the array for future "has subtasks" / partial queries
BEGIN;

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS sub_tasks JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_tasks_sub_tasks ON tasks USING GIN (sub_tasks);

COMMIT;
