-- V12: Seed the system_settings JSONB columns (email_templates, integrations,
-- security_policies) with portal defaults for rows where they are still NULL
-- (fresh installs and upgrades from V2/V8, which seeded only portal_name and
-- maintenance_mode). Only NULLs are touched, so admin edits survive upgrades.

BEGIN;

UPDATE system_settings
SET email_templates = '[
  {"id":1,"name":"Welcome Email","subject":"Welcome to the SECPhils Portal","body":"Hi {{name}},\n\nYour account is ready. Sign in to view your assigned projects.\n\n- SECPhils Team"},
  {"id":2,"name":"Team Invitation","subject":"You have been invited to {{company}}","body":"Hi {{name}},\n\n{{inviter}} has invited you to join {{company}} on the SECPhils Portal.\n\nSetup link: {{setupLink}}\n\n- SECPhils Team"},
  {"id":3,"name":"Project Update","subject":"Update on {{project}}","body":"Hi {{name}},\n\nNew update on {{project}}: {{updateText}}\n\n- SECPhils Team"}
]'::jsonb
WHERE email_templates IS NULL;

UPDATE system_settings
SET integrations = '[
  {"id":1,"name":"Gmail / Google Workspace","type":"Email","status":"Connected","detail":"notifications@secphils.com"},
  {"id":2,"name":"Slack","type":"Notifications","status":"Disconnected","detail":"-"},
  {"id":3,"name":"Microsoft Teams","type":"Notifications","status":"Disconnected","detail":"-"},
  {"id":4,"name":"DocuSign","type":"Documents","status":"Connected","detail":"secphils@docusign.net"}
]'::jsonb
WHERE integrations IS NULL;

UPDATE system_settings
SET security_policies = '{
  "passwordMinLength": 12,
  "require2fa": false,
  "sessionTimeoutMinutes": 30,
  "maxLoginAttempts": 5
}'::jsonb
WHERE security_policies IS NULL;

COMMIT;
