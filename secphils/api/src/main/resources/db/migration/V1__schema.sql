-- V1__schema.sql
-- SECPhils Client Portal — initial schema
-- 21 tables, snake_case plural, BIGSERIAL PKs, created_at/updated_at everywhere.

BEGIN;

-- ============================================================
-- Trigger: keep updated_at fresh
-- ============================================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- Core Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
  id            BIGSERIAL PRIMARY KEY,
  email         VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255),
  first_name    VARCHAR(100) NOT NULL,
  last_name     VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL,
  is_active     BOOLEAN DEFAULT TRUE,
  created_at    TIMESTAMP DEFAULT NOW(),
  updated_at    TIMESTAMP DEFAULT NOW(),
  last_login    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS companies (
  id                       BIGSERIAL PRIMARY KEY,
  name                     VARCHAR(255) NOT NULL,
  location                 VARCHAR(255),
  owner                    VARCHAR(255),
  description              TEXT,
  authorized_rep_user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  created_at               TIMESTAMP DEFAULT NOW(),
  updated_at               TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS roles (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(100) UNIQUE NOT NULL,
  description TEXT,
  is_system   BOOLEAN DEFAULT FALSE,
  created_at  TIMESTAMP DEFAULT NOW(),
  updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS permissions (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(100) UNIQUE NOT NULL,
  description TEXT,
  created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS role_permissions (
  role_id       BIGINT REFERENCES roles(id) ON DELETE CASCADE,
  permission_id BIGINT REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS user_roles (
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  role_id BIGINT REFERENCES roles(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, role_id)
);

-- ============================================================
-- Service & Configuration Tables (before projects: FK target)
-- ============================================================

CREATE TABLE IF NOT EXISTS services (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(255) NOT NULL,
  description TEXT,
  category    VARCHAR(50) DEFAULT 'ENGINEERING',
  is_active   BOOLEAN DEFAULT TRUE,
  created_at  TIMESTAMP DEFAULT NOW(),
  updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dropdown_categories (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(100) UNIQUE NOT NULL,
  description TEXT,
  created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dropdown_values (
  id            BIGSERIAL PRIMARY KEY,
  category_id   BIGINT NOT NULL REFERENCES dropdown_categories(id) ON DELETE CASCADE,
  value         VARCHAR(100) NOT NULL,
  display_label VARCHAR(100) NOT NULL,
  sort_order    INTEGER DEFAULT 0,
  created_at    TIMESTAMP DEFAULT NOW(),
  UNIQUE (category_id, value)
);

-- ============================================================
-- Project Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS projects (
  id                      BIGSERIAL PRIMARY KEY,
  company_id              BIGINT NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  service_id              BIGINT REFERENCES services(id) ON DELETE SET NULL,
  name                    VARCHAR(255) NOT NULL,
  scope                   TEXT,
  objectives              TEXT,
  deliverables            TEXT,
  status                  VARCHAR(30) DEFAULT 'NOT_STARTED',
  total_cost              DECIMAL(15,2),
  raw_materials           JSONB,
  production_output       JSONB,
  waste_management        TEXT,
  waste_materials         JSONB,
  manufacturing_procedure TEXT,
  production_flowchart_url VARCHAR(500),
  created_at              TIMESTAMP DEFAULT NOW(),
  updated_at              TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS project_team_members (
  project_id      BIGINT REFERENCES projects(id) ON DELETE CASCADE,
  user_id         BIGINT REFERENCES users(id) ON DELETE CASCADE,
  role_on_project VARCHAR(50) NOT NULL,
  assigned_at     TIMESTAMP DEFAULT NOW(),
  PRIMARY KEY (project_id, user_id)
);

-- ============================================================
-- Task & Document Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS tasks (
  id          BIGSERIAL PRIMARY KEY,
  project_id  BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  assignee_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  title       VARCHAR(255) NOT NULL,
  description TEXT,
  status      VARCHAR(30) DEFAULT 'TO_DO',
  priority    VARCHAR(20) DEFAULT 'MEDIUM',
  due_date    DATE,
  created_at  TIMESTAMP DEFAULT NOW(),
  updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS documents (
  id          BIGSERIAL PRIMARY KEY,
  project_id  BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  uploader_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  title       VARCHAR(255) NOT NULL,
  description TEXT,
  category    VARCHAR(50) DEFAULT 'OTHER',
  file_url    VARCHAR(500) NOT NULL,
  file_size   BIGINT,
  version     INTEGER DEFAULT 1,
  is_latest   BOOLEAN DEFAULT TRUE,
  uploaded_at TIMESTAMP DEFAULT NOW(),
  updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS document_comments (
  id          BIGSERIAL PRIMARY KEY,
  document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  comment     TEXT NOT NULL,
  created_at  TIMESTAMP DEFAULT NOW()
);

-- ============================================================
-- Communication Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS messages (
  id         BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  sender_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  body       TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS announcements (
  id           BIGSERIAL PRIMARY KEY,
  company_id   BIGINT REFERENCES companies(id) ON DELETE SET NULL,
  project_id   BIGINT REFERENCES projects(id) ON DELETE SET NULL,
  title        VARCHAR(255) NOT NULL,
  body         TEXT NOT NULL,
  category     VARCHAR(30) DEFAULT 'PROJECT_UPDATE',
  audience     VARCHAR(20) DEFAULT 'COMPANY',
  is_published BOOLEAN DEFAULT TRUE,
  created_by   BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  created_at   TIMESTAMP DEFAULT NOW(),
  updated_at   TIMESTAMP DEFAULT NOW()
);

-- ============================================================
-- Review & Rating Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS reviews (
  id                BIGSERIAL PRIMARY KEY,
  customer_user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  project_id        BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  rating            INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
  title             VARCHAR(255) NOT NULL,
  body              TEXT NOT NULL,
  status            VARCHAR(20) DEFAULT 'PENDING',
  created_at        TIMESTAMP DEFAULT NOW(),
  updated_at        TIMESTAMP DEFAULT NOW()
);

-- ============================================================
-- System Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS notifications (
  id           BIGSERIAL PRIMARY KEY,
  recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title        VARCHAR(255) NOT NULL,
  body         TEXT NOT NULL,
  type         VARCHAR(30) NOT NULL,
  entity_type  VARCHAR(50),
  entity_id    BIGINT,
  is_read      BOOLEAN DEFAULT FALSE,
  created_at   TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notification_preferences (
  user_id          BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  task_assigned    BOOLEAN DEFAULT TRUE,
  project_created  BOOLEAN DEFAULT TRUE,
  new_message      BOOLEAN DEFAULT TRUE,
  document_request BOOLEAN DEFAULT TRUE,
  review_submitted BOOLEAN DEFAULT TRUE,
  announcement     BOOLEAN DEFAULT TRUE,
  status_change    BOOLEAN DEFAULT TRUE,
  updated_at       TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
  action      VARCHAR(100) NOT NULL,
  entity_type VARCHAR(50) NOT NULL,
  entity_id   BIGINT,
  ip_address  VARCHAR(45),
  details     JSONB,
  created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS system_settings (
  id               BIGSERIAL PRIMARY KEY,
  portal_name      VARCHAR(255) DEFAULT 'Client Portal',
  email_templates  JSONB,
  integrations     JSONB,
  security_policies JSONB,
  maintenance_mode BOOLEAN DEFAULT FALSE,
  updated_at       TIMESTAMP DEFAULT NOW()
);

-- ============================================================
-- Indexes
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_users_email              ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role               ON users(role);
CREATE INDEX IF NOT EXISTS idx_projects_company         ON projects(company_id);
CREATE INDEX IF NOT EXISTS idx_projects_status          ON projects(status);
CREATE INDEX IF NOT EXISTS idx_projects_service         ON projects(service_id);
CREATE INDEX IF NOT EXISTS idx_tasks_project            ON tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_tasks_assignee           ON tasks(assignee_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status             ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_documents_project        ON documents(project_id);
CREATE INDEX IF NOT EXISTS idx_messages_project         ON messages(project_id);
CREATE INDEX IF NOT EXISTS idx_messages_created         ON messages(created_at);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient  ON notifications(recipient_id);
CREATE INDEX IF NOT EXISTS idx_notifications_unread     ON notifications(recipient_id, is_read);
CREATE INDEX IF NOT EXISTS idx_audit_user               ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_created            ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_reviews_project          ON reviews(project_id);
CREATE INDEX IF NOT EXISTS idx_reviews_status           ON reviews(status);
CREATE INDEX IF NOT EXISTS idx_dropdown_values_category ON dropdown_values(category_id);

-- ============================================================
-- updated_at triggers
-- ============================================================

DROP TRIGGER IF EXISTS trg_users_updated               ON users;
CREATE TRIGGER trg_users_updated               BEFORE UPDATE ON users               FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_companies_updated           ON companies;
CREATE TRIGGER trg_companies_updated           BEFORE UPDATE ON companies           FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_roles_updated               ON roles;
CREATE TRIGGER trg_roles_updated               BEFORE UPDATE ON roles               FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_services_updated            ON services;
CREATE TRIGGER trg_services_updated            BEFORE UPDATE ON services            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_projects_updated            ON projects;
CREATE TRIGGER trg_projects_updated            BEFORE UPDATE ON projects            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_tasks_updated               ON tasks;
CREATE TRIGGER trg_tasks_updated               BEFORE UPDATE ON tasks               FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_documents_updated           ON documents;
CREATE TRIGGER trg_documents_updated           BEFORE UPDATE ON documents           FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_announcements_updated       ON announcements;
CREATE TRIGGER trg_announcements_updated       BEFORE UPDATE ON announcements       FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_reviews_updated             ON reviews;
CREATE TRIGGER trg_reviews_updated             BEFORE UPDATE ON reviews             FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_notification_prefs_updated  ON notification_preferences;
CREATE TRIGGER trg_notification_prefs_updated  BEFORE UPDATE ON notification_preferences FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_system_settings_updated     ON system_settings;
CREATE TRIGGER trg_system_settings_updated     BEFORE UPDATE ON system_settings     FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMIT;
