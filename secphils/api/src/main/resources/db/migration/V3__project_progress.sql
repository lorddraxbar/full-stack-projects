-- V3: add due_date + progress to projects (needed by portal list/dashboard views)
ALTER TABLE projects ADD COLUMN IF NOT EXISTS due_date DATE;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS progress INTEGER NOT NULL DEFAULT 0;
