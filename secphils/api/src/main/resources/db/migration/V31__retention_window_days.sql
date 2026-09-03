-- V31: Admin-configurable retention window — the number of days a deactivated
-- entity (archived project, deactivated user/service, trashed document) stays
-- recoverable before its hard delete becomes passwordless / auto-purges.
-- Previously hard-coded to 7 in four places (ProjectArchiveService,
-- DocumentTrashService, UserController, ServiceController).
-- Seeded to 7 so existing behaviour is unchanged until an admin edits it.
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS retention_window_days integer;
UPDATE system_settings SET retention_window_days = 7 WHERE retention_window_days IS NULL;
