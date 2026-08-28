-- Optional phone number for the company owner (free-text name, not a portal
-- user). The authorized rep's phone lives on users.phone (the rep is a real
-- user row). Both fields are optional — NULL when not provided.
ALTER TABLE companies ADD COLUMN IF NOT EXISTS owner_phone VARCHAR(30);
