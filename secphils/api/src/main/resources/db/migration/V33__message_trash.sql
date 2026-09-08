-- V33: Message trash (provider-only soft delete)
--
-- Supported erasure path for legal/erasure requests: a provider staff member
-- (never a client) can move a message to the trash. Trashed messages:
--   * disappear from the conversation thread, the conversation-list previews
--     and unread counts for EVERYONE (including staff — the trash pane is the
--     only place they stay visible),
--   * lose their in-app bell rows immediately (the bell body carries the
--     message text, so leaving it behind would keep the content readable),
--   * remain restorable by any staff member until the retention window passes,
--   * are automatically purged after the retention window (scheduled sweep,
--     no password — the window is what bounds that),
--   * can be emptied earlier by any provider role with their account password.
--
-- The audit log (MESSAGE_DELETE / MESSAGE_RESTORE / MESSAGE_PERMANENT_DELETE /
-- MESSAGE_TRASH_PURGED / MESSAGE_TRASH_EMPTY) is the durable accountability
-- record: who removed what, from which project, and the stated reason — the
-- body text is deliberately NOT copied into audit details (the point of an
-- erasure is that the text stops circulating). S3 attachment objects are kept
-- whenever a live document row shares the same object (message uploads store
-- one object in both rows); otherwise a hard purge deletes the object.

BEGIN;

ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_messages_deleted_at ON messages(deleted_at) WHERE deleted_at IS NOT NULL;

COMMIT;
