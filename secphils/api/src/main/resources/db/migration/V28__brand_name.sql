-- V28: Admin-configurable brand name — the short name that replaces a
-- provider's identity on client-facing surfaces (collapsed sender name on
-- client-visible messages, announcements, documents, tasks, reviews) and the
-- portal drawer wordmark. Distinct from portal_name (the app title).
-- Seed to the historical value so existing behaviour is unchanged.
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS brand_name varchar(255);
UPDATE system_settings SET brand_name = 'SECPhils' WHERE brand_name IS NULL;
