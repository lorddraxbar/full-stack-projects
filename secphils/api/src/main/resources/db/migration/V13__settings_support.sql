-- V13: settings-page support (applies to all roles)
--   * users: phone, avatar (data URI), TOTP 2FA (secret + flag), email_verified, communication_prefs jsonb
--   * companies: email_verified_at, contact_details (client-facing "Contact Details" string)
--   * notification_preferences: flat booleans -> per-channel jsonb (in_app / email)

ALTER TABLE users ADD COLUMN IF NOT EXISTS phone varchar(30) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar text;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_secret varchar(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS communication_prefs jsonb;

ALTER TABLE companies ADD COLUMN IF NOT EXISTS email_verified_at timestamptz;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS contact_details varchar(500);

-- Notification preferences: the old flat booleans had no consumers anywhere in the
-- API. Rebuild as per-channel jsonb so the Settings UI can persist arbitrary keys.
DROP TABLE IF EXISTS notification_preferences;
CREATE TABLE notification_preferences (
    user_id    bigint PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    in_app     jsonb,
    email      jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);