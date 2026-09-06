-- V32: Per-section "Not Applicable" flags for the production checklist.
-- A jsonb object keyed by checklist section: {"rawMaterials":true,...}. Lets a
-- client knowingly skip sections that don't apply to their business (e.g. no
-- waste stream). The rep-completion gate considers a section satisfied when it
-- has content OR its flag is true. Total cost is never skippable.
ALTER TABLE projects ADD COLUMN IF NOT EXISTS checklist_na jsonb NOT NULL DEFAULT '{}'::jsonb;