-- V2__seed_data.sql
-- SECPhils Client Portal — default dropdowns, system roles, permissions, settings

BEGIN;

-- ============================================================
-- Dropdown categories
-- ============================================================

INSERT INTO dropdown_categories (name, description) VALUES
  ('project_status',      'Project lifecycle statuses'),
  ('task_status',         'Task lifecycle statuses'),
  ('priority',            'Task priority levels'),
  ('document_category',   'Document classification'),
  ('announcement_category','Announcement categories'),
  ('audience',            'Announcement audience scope'),
  ('service_category',    'Service catalog categories'),
  ('user_role',           'Portal user roles'),
  ('report_type',         'Report types'),
  ('status',              'General entity status')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- Dropdown values
-- ============================================================

INSERT INTO dropdown_values (category_id, value, display_label, sort_order)
SELECT c.id, v.value, v.display_label, v.sort_order
FROM dropdown_categories c
JOIN (VALUES
  -- project_status
  ('project_status', 'NOT_STARTED', 'Not Started', 1),
  ('project_status', 'IN_PROGRESS', 'In Progress', 2),
  ('project_status', 'ON_HOLD', 'On Hold', 3),
  ('project_status', 'COMPLETED', 'Completed', 4),
  -- task_status
  ('task_status', 'TO_DO', 'To Do', 1),
  ('task_status', 'IN_PROGRESS', 'In Progress', 2),
  ('task_status', 'DONE', 'Done', 3),
  -- priority
  ('priority', 'LOW', 'Low', 1),
  ('priority', 'MEDIUM', 'Medium', 2),
  ('priority', 'HIGH', 'High', 3),
  -- document_category
  ('document_category', 'CLIENT_SUBMITTED', 'Client-Submitted', 1),
  ('document_category', 'REQUESTED', 'Requested', 2),
  ('document_category', 'DELIVERABLE', 'Deliverable', 3),
  -- announcement_category
  ('announcement_category', 'PROJECT_UPDATE', 'Project Update', 1),
  ('announcement_category', 'COMPANY_NEWS', 'Company News', 2),
  ('announcement_category', 'MAINTENANCE', 'Maintenance', 3),
  -- audience
  ('audience', 'PROJECT', 'Project', 1),
  ('audience', 'COMPANY', 'Company', 2),
  -- service_category
  ('service_category', 'FEASIBILITY', 'Feasibility Study', 1),
  ('service_category', 'OPTIMIZATION', 'Process Optimization', 2),
  ('service_category', 'DESIGN', 'Engineering Design', 3),
  ('service_category', 'AUDIT', 'Compliance Audit', 4),
  -- user_role
  ('user_role', 'CLIENT', 'Client', 1),
  ('user_role', 'PROVIDER', 'Provider', 2),
  ('user_role', 'ADMIN', 'Admin', 3),
  -- report_type
  ('report_type', 'PERFORMANCE', 'Performance', 1),
  ('report_type', 'SATISFACTION', 'Satisfaction', 2),
  ('report_type', 'RESOURCES', 'Resources', 3),
  ('report_type', 'REVENUE', 'Revenue', 4),
  -- status (general)
  ('status', 'ACTIVE', 'Active', 1),
  ('status', 'DEACTIVATED', 'Deactivated', 2),
  ('status', 'ARCHIVED', 'Archived', 3)
) AS v(category, value, display_label, sort_order)
  ON v.category = c.name
ON CONFLICT (category_id, value) DO NOTHING;

-- ============================================================
-- Default system roles
-- ============================================================

INSERT INTO roles (name, description, is_system) VALUES
  ('CLIENT',   'Client company user — views own projects, documents, messages; submits reviews', TRUE),
  ('PROVIDER', 'Provider staff — manages projects, tasks, team, documents, reviews, announcements', TRUE),
  ('ADMIN',    'System administrator — full access to all portal features', TRUE)
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- Default permissions
-- ============================================================

INSERT INTO permissions (name, description) VALUES
  ('project.view',     'View projects'),
  ('project.create',   'Create projects'),
  ('project.update',   'Update projects'),
  ('project.delete',   'Archive/delete projects'),
  ('task.view',        'View tasks'),
  ('task.create',      'Create tasks'),
  ('task.update',      'Update tasks'),
  ('task.delete',      'Delete tasks'),
  ('document.view',    'View documents'),
  ('document.upload',  'Upload documents'),
  ('document.delete',  'Delete documents'),
  ('message.send',     'Send project messages'),
  ('review.submit',    'Submit project reviews'),
  ('review.approve',   'Approve or reject reviews'),
  ('user.view',        'View users'),
  ('user.manage',      'Create/update/deactivate users'),
  ('role.manage',      'Manage roles and permissions'),
  ('settings.manage',  'Manage system settings'),
  ('audit.view',       'View audit logs'),
  ('announcement.create','Create announcements')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- Role → permission mapping
-- ============================================================

-- ADMIN: everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- PROVIDER: project/task/document/message/review/announcement/user.view/audit
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'PROVIDER'
  AND p.name IN (
    'project.view', 'project.create', 'project.update', 'project.delete',
    'task.view', 'task.create', 'task.update', 'task.delete',
    'document.view', 'document.upload', 'document.delete',
    'message.send',
    'review.approve',
    'user.view',
    'announcement.create',
    'audit.view'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- CLIENT: read own + upload + message + review
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'CLIENT'
  AND p.name IN (
    'project.view',
    'task.view',
    'document.view', 'document.upload',
    'message.send',
    'review.submit'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================
-- Default system settings (single row)
-- ============================================================

INSERT INTO system_settings (portal_name, maintenance_mode)
SELECT 'SECPhils Client Portal', FALSE
WHERE NOT EXISTS (SELECT 1 FROM system_settings)
ON CONFLICT DO NOTHING;

COMMIT;
