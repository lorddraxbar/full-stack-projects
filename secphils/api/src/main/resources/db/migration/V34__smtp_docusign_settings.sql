-- V34: honest integrations — SMTP + DocuSign as real configuration; drop the fiction.
--
-- The old system_settings.integrations JSONB was decorative: it stored labels
-- like "Connected" for Slack/Teams/DocuSign that no backend ever read (SMTP
-- came from env, DocuSign had zero code). Replaced by two REAL settings:
--   * smtp     — host/port/username/password/from; MailService prefers this
--                over the env-configured sender, live (no restart).
--   * docusign — enabled/oauth host/integration key/account/user + RSA private
--                key; DocuSignService.testConnection does a live JWT-bearer
--                exchange. Seeded DISABLED (no feature ships on it yet).
-- Seeding SMTP from the deploy-time env happens here via Flyway placeholders
-- (spring.flyway.placeholders.* in application.yml) so a fresh prod DB boots
-- with the relay the deploy already used. CAVEAT: substitution is textual —
-- an SMTP password containing an apostrophe would break this statement; the
-- build fails loudly rather than silently corrupting, and the Admin panel
-- remains the fallback path.
--
-- Also renames companies.industry_sectors -> companies.business_type. The UI
-- label has been "Business Type" on every surface for ages; the column name
-- was pure legacy. No API consumers outside the SPA (verified by grep).

BEGIN;

-- 1) Cosmetic rename: DB column
ALTER TABLE companies RENAME COLUMN industry_sectors TO business_type;

-- 2) Real integration settings
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS smtp     JSONB;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS docusign JSONB;

-- Seed SMTP from the deployment env (Flyway placeholders; single quotes in a
-- password will break this statement — see header caveat).
UPDATE system_settings
   SET smtp = jsonb_build_object(
         'host',     '${smtphost}',
         'port',     '${smtpport}'::int,
         'username', '${smtpusername}',
         'password', '${smtppassword}',
         'from',     '${mailfrom}')
 WHERE smtp IS NULL;

-- DocuSign ships disabled until real credentials arrive.
UPDATE system_settings
   SET docusign = jsonb_build_object(
         'enabled', false,
         'oauthBaseUrl', 'https://account-docusign.com',
         'integrationKey', '',
         'accountId', '',
         'userId', '',
         'privateKey', '')
 WHERE docusign IS NULL;

-- 3) Retire the fiction (UI no longer renders it; nothing else read it).
ALTER TABLE system_settings DROP COLUMN IF EXISTS integrations;

COMMIT;
