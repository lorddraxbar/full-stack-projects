-- V36: complete the shipped project_status vocabulary in the DB.
--
-- V2 seeded four statuses; ARCHIVED existed only in the frontend's hardcoded
-- label map, because archive/restore was driven by buttons, not the dropdown.
-- V35 wired the forms to read this category live, so the missing row would
-- have silently dropped "Archived" from the Projects filter and the
-- Administration status editor (1 live project already has that status).
-- Guarded insert keeps this safe on any DB where an admin already added it.
INSERT INTO dropdown_values (category_id, value, display_label, sort_order)
SELECT c.id, 'ARCHIVED', 'Archived', 4
  FROM dropdown_categories c
 WHERE c.name = 'project_status'
   AND NOT EXISTS (SELECT 1 FROM dropdown_values v
                    WHERE v.category_id = c.id AND v.value = 'ARCHIVED');
