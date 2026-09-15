-- V40: Email unsubscribe + SES bounce/complaint suppression
--
-- Two durable pieces behind the "manage email preferences" links in every
-- notification email:
--
--   users.unsubscribe_token        — stable random per-user token (64 hex).
--     Unlike password_reset_token it NEVER expires and is only rotated by an
--     explicit re-issue, so List-Unsubscribe links in old emails keep working.
--     NULL until first needed; minted lazily by UnsubscribeTokenService.
--
--   email_suppressions             — addresses (or address+category pairs) we
--     must not mail. Written by: SES bounce/complaint events (via the SNS
--     webhook), and one-click unsubscribes from notification emails (category
--     = the notification key; category = '' means the WHOLE address — a hard
--     bounce or complaint). Checked by MailService before every send, so it
--     covers even the mandatory emails for a hard-bounced address.
--     user_id cascades on user hard-delete (V38 erasure semantics).

BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS unsubscribe_token VARCHAR(64) UNIQUE;

CREATE TABLE IF NOT EXISTS email_suppressions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL,
    category    VARCHAR(64)  NOT NULL DEFAULT '',
    reason      VARCHAR(32)  NOT NULL,
    detail      VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS email_suppressions_key
    ON email_suppressions (email, category);

COMMIT;
