-- Add a per-message visibility flag so the provider team can have an internal
-- thread inside a project conversation that clients never see.
--
--   CLIENT   (default) — visible to everyone who can see the project
--                        (company members + admin), i.e. today's behaviour.
--   INTERNAL — visible only to provider staff (USER/ADMIN roles) and never to
--              a CLIENT-role user, regardless of company membership.
--
-- The filter is enforced in the API (MessageController.list/download and the
-- projects-list "latest update" preview), never only in the UI.
ALTER TABLE messages
  ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'CLIENT';

-- Defensive: no row may ever carry a value outside the known set.
ALTER TABLE messages
  ADD CONSTRAINT messages_visibility_check
  CHECK (visibility IN ('CLIENT', 'INTERNAL'));
