-- V7: One-time password-set tokens for the admin-invite flow.
-- New users are created inactive; the invite email carries a token that lets
-- them set their own password, which activates the account.
ALTER TABLE users ADD COLUMN password_reset_token VARCHAR(128);
ALTER TABLE users ADD COLUMN password_reset_expires_at TIMESTAMP;
ALTER TABLE users ADD COLUMN password_reset_requested_at TIMESTAMP;

CREATE INDEX idx_users_password_reset_token ON users (password_reset_token);
