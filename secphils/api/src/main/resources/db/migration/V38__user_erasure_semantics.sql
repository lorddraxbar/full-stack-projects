-- V38: user-erasure semantics. Hard-deleting an account must never be blocked
-- by the history that person wrote (the old RESTRICTs surfaced as a 409 whose
-- copy guessed "audit history" while the real blocker was messages/reviews/
-- announcements/rep-pointer). Those records ARE the company's record — they
-- stay; only the author link is cleared (SET NULL), and the portal renders a
-- deleted account (every DTO already null-guards; UI shows an em dash).
--
-- companies.authorized_rep_user_id deliberately stays RESTRICT: rep handoff is
-- an explicit admin action (Project Detail -> Company / Admin -> Company
-- Settings), never a silent side effect of account erasure — the API refuses
-- with an actionable "reassign the representative first" 409 instead.
ALTER TABLE messages ALTER COLUMN sender_id DROP NOT NULL;
ALTER TABLE messages DROP CONSTRAINT messages_sender_id_fkey;
ALTER TABLE messages ADD CONSTRAINT messages_sender_id_fkey
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE reviews ALTER COLUMN customer_user_id DROP NOT NULL;
ALTER TABLE reviews DROP CONSTRAINT reviews_customer_user_id_fkey;
ALTER TABLE reviews ADD CONSTRAINT reviews_customer_user_id_fkey
    FOREIGN KEY (customer_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE announcements ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE announcements DROP CONSTRAINT announcements_created_by_fkey;
ALTER TABLE announcements ADD CONSTRAINT announcements_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;
