-- V9: company profile fields + role user types
BEGIN;

-- Company profile fields for the Admin Panel "Company Profile" section
ALTER TABLE companies ADD COLUMN IF NOT EXISTS tagline VARCHAR(255);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS industry_sectors VARCHAR(500);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS headquarters VARCHAR(500);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS phone VARCHAR(100);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS website VARCHAR(255);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS social_links VARCHAR(500);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS tax_number VARCHAR(100);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS banking_details TEXT;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS operational_fields VARCHAR(500);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS brand_primary VARCHAR(7);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS brand_secondary VARCHAR(7);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500);

-- The provider's own company has no "authorized representative user" — make it optional
ALTER TABLE companies ALTER COLUMN authorized_rep_user_id DROP NOT NULL;

-- Which account type a role is meant for (CLIENT / USER / ADMIN)
ALTER TABLE roles ADD COLUMN IF NOT EXISTS user_type VARCHAR(20);
UPDATE roles SET user_type = 'CLIENT' WHERE user_type IS NULL AND name = 'CLIENT';
UPDATE roles SET user_type = 'USER'   WHERE user_type IS NULL AND name = 'USER';
UPDATE roles SET user_type = 'ADMIN'  WHERE user_type IS NULL AND name = 'ADMIN';
UPDATE roles SET user_type = 'USER'   WHERE user_type IS NULL;

COMMIT;
