-- V29: Remove the Tasks feature (feature deleted from app; data preserved)
--
-- The Tasks page, /api/v1/tasks endpoints, and all related UI were removed
-- (current processes have no need for them). This migration only cleans up
-- task-adjacent METADATA so the app doesn't surface task references:
--   * permission rows  task.*  (and their role_permissions links)
--   * dropdown categories  task_status, priority  (+ their values)
--   * the old per-type boolean preference columns (superseded by the JSONB
--     in_app/email columns; task_assigned is the only task-typed key)
--   * the seeded USER role description (visible in Roles UI) mentions tasks
--
-- The `tasks` table itself is intentionally KEPT: its rows are preserved so
-- the feature can be restored later without data loss.

BEGIN;

-- Permissions (role_permissions cascade via FK ON DELETE CASCADE).
DELETE FROM permissions WHERE name IN ('task.view', 'task.create', 'task.update', 'task.delete');

-- Dropdown categories: values cascade via FK ON DELETE CASCADE.
-- (Categories are only shown in the admin Dropdowns editor; values are
-- fetched on demand by name, so nothing else references them.)
DELETE FROM dropdown_categories WHERE name IN ('task_status', 'priority');

-- Legacy per-type boolean preference columns. The NotificationPreference
-- entity now stores per-channel JSONB (in_app/email); these booleans are
-- unread. task_assigned is the only task-typed one — drop just that.
ALTER TABLE notification_preferences DROP COLUMN IF EXISTS task_assigned;

-- USER role description (surfaced in the admin Roles UI).
UPDATE roles
   SET description = 'User — manages projects, team, documents, reviews, announcements'
 WHERE name = 'USER';

COMMIT;
