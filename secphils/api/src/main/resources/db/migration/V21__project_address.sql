-- V21: project-level address. The company address lives on companies.location
-- (renamed to "Company Address" in the UI); a project may operate somewhere
-- different. projects.address is NULL when the project address equals the
-- company's — UI falls back to the company address in that case.
ALTER TABLE projects ADD COLUMN IF NOT EXISTS address VARCHAR(500);
COMMENT ON COLUMN projects.address IS
  'Full project (site) address; NULL = same as the company address.';
