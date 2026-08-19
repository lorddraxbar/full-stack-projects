-- V5: Change the default admin email from admin@secphils.com to jayson@secphils.com.
-- Fresh DBs: V4 creates the admin at the old address, this renames it.
-- Existing DBs: renames the row V4 already created. No-op if it does not exist.
UPDATE users SET email = 'jayson@secphils.com' WHERE email = 'admin@secphils.com';
