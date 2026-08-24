-- File attachments for project messages (S3-backed, mirrors V14 document storage).
ALTER TABLE messages
    ADD COLUMN attachment_url      TEXT,
    ADD COLUMN attachment_file_name VARCHAR(512),
    ADD COLUMN attachment_file_size BIGINT,
    ADD COLUMN attachment_content_type VARCHAR(255);
