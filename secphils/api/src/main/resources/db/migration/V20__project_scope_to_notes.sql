-- Projects: the free-text "Scope" field is redundant with the (now required)
-- Service Type, so it is repurposed as an optional "Notes" field. Renaming the
-- column (instead of adding a fresh one) preserves the free-text content that
-- existing projects already carry in `scope`.
ALTER TABLE projects RENAME COLUMN scope TO notes;
