-- V35: wire Project Config to the forms; cut the orphaned categories.
--
-- Since this migration the portal's forms READ these tables live (composable
-- useDropdownOptions): project_status drives the Projects filter, the status
-- badges and the Administration-tab status editor; announcement_category and
-- audience drive the announcement create/edit selects and badges. Admin edits
-- now take effect immediately instead of persisting into an unconsumed table.
--
-- DELETED (verified 2026-09-09: zero consumers anywhere in api + web):
--   * document_category  - documents have no category surface in the UI at all
--   * report_type        - reports are not a feature
--   * service_category   - DEAD DUPLICATE: the Services pages use the real
--                          service_categories table (V15, editable in Admin ->
--                          Services); two parallel vocabularies for one concept
--   * status             - generic entity-status echo; Users/Services render
--                          their own fixed lifecycle labels
-- dropdown_values cascades (FK ON DELETE CASCADE in V1).
--
-- user_role SURVIVES but is now PROTECTED (column below): roles are the closed
-- privilege set enforced server-side (post-c799c16 escalation fix). The table
-- row remains as documentation, but editing it must never affect auth - the
-- API refuses writes to protected categories so nobody can believe they
-- changed the role vocabulary here. The UI keeps its hardcoded CLIENT/USER/ADMIN
-- select by design.
ALTER TABLE dropdown_categories
  ADD COLUMN IF NOT EXISTS is_protected BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE dropdown_categories SET is_protected = TRUE WHERE name = 'user_role';

-- refresh descriptions so the Project Config panel states the truth per row
UPDATE dropdown_categories SET description =
  'Project statuses. Consumed live: Projects filter, status badges, dashboard donut, Administration status editor.'
  WHERE name = 'project_status';
UPDATE dropdown_categories SET description =
  'Announcement categories. Consumed live: announcement create/edit forms and category badges.'
  WHERE name = 'announcement_category';
UPDATE dropdown_categories SET description =
  'Announcement audiences. Consumed live: announcement create/edit forms and audience badges.'
  WHERE name = 'audience';
UPDATE dropdown_categories SET description =
  'Fixed privilege set enforced by the backend (CLIENT/USER/ADMIN). Protected: not editable here, and the login/role system never reads this table.'
  WHERE name = 'user_role';

DELETE FROM dropdown_categories
 WHERE name IN ('document_category', 'report_type', 'service_category', 'status');
