-- V23: drop documents.category.
-- The category was a free-text label (default 'OTHER') that added no signal —
-- message attachments all landed as 'OTHER' anyway. Documents are now
-- classified by their file type, which is derived at read time from the
-- stored file name / s3:// object key (both preserve the original extension).
-- Destructive by design: this is the dev portal and the column carried no
-- user-meaningful data (values: DELIVERABLE/REQUESTED/CLIENT_SUBMITTED/OTHER).
ALTER TABLE documents DROP COLUMN IF EXISTS category;
