-- V27: Admin-configurable default recipient for landing-page contact-form
-- emails. When the provider company profile has no valid email address(es),
-- the inquiry falls back to this address (was the hardcoded
-- manager@secphils.com). Managed in Admin Settings > Email Templates.
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS landing_contact_email varchar(255);
UPDATE system_settings SET landing_contact_email = 'manager@secphils.com'
WHERE landing_contact_email IS NULL;
