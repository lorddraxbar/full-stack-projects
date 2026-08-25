-- V19: Google SSO (OAuth 2.0 authorization-code flow) settings.
-- The full config (enabled, clientId, clientSecret, redirectUri,
-- domainRestriction) is stored as JSONB on system_settings, the same
-- pattern as the storage / integrations / security_policies columns.
-- The client secret is redacted to "********" on read by the admin
-- settings endpoint; saving the mask (or blank) keeps the stored secret.
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS google_sso JSONB;
COMMENT ON COLUMN system_settings.google_sso IS
  'Google SSO OAuth config (JSON: enabled, clientId, clientSecret, redirectUri, domainRestriction).';
