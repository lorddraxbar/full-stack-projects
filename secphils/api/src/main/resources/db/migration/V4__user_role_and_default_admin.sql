-- V4: Rename PROVIDER role to USER (convention: CLIENT / USER / ADMIN)
-- and ensure a default admin account exists (idempotent — safe on fresh
-- and existing databases alike; Flyway only runs this once per DB).

-- 1) Existing users holding the old role value
UPDATE users SET role = 'USER' WHERE role = 'PROVIDER';

-- 2) System roles table
UPDATE roles
SET name = 'USER',
    description = 'User — manages projects, tasks, team, documents, reviews, announcements',
    updated_at = now()
WHERE name = 'PROVIDER';

-- 3) user_role dropdown values
UPDATE dropdown_values dv
SET value = 'USER', display_label = 'User'
FROM dropdown_categories dc
WHERE dv.category_id = dc.id
  AND dc.name = 'user_role'
  AND dv.value = 'PROVIDER';

-- 4) Default admin user (created only if it does not already exist)
INSERT INTO users (email, password_hash, first_name, last_name, role, is_active)
SELECT 'admin@secphils.com',
       '$2b$10$whwMJx0psc.rMzYt.nONAeH18UGed0erCf8fxCTWxLfWlInxpoqFC',
       'Jayson', 'Barroga', 'ADMIN', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@secphils.com');
