-- V8: link users to a company + make the invite link base URL admin-configurable
BEGIN;

-- Users belong to a company (clients/consultants). NULL for admins / unassigned.
ALTER TABLE users ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id);
CREATE INDEX IF NOT EXISTS idx_users_company ON users(company_id);

-- Admin-settable base URL for "set your password" invite links.
-- NULL = fall back to the INVITE_BASE_URL environment variable.
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS invite_base_url VARCHAR(255);

COMMIT;
