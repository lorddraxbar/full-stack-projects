-- V6: Track when a user was deactivated so hard delete can enforce a 7-day window.
ALTER TABLE users ADD COLUMN deactivated_at TIMESTAMP;
