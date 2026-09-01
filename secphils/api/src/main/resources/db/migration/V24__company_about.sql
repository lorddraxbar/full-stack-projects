-- Dedicated marketing-page "About" text. The Company Settings tab writes this;
-- the public landing page's About section reads it (falling back to the
-- business description until the provider fills it in).
ALTER TABLE companies ADD COLUMN IF NOT EXISTS about TEXT;
