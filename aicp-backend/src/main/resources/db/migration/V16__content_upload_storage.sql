-- Persist original content-project uploads in object storage
ALTER TABLE content_upload_files ADD COLUMN storage_uri VARCHAR(600);
ALTER TABLE content_upload_files ADD COLUMN storage_provider VARCHAR(20);
ALTER TABLE content_upload_files ADD COLUMN storage_bucket VARCHAR(100);
ALTER TABLE content_upload_files ADD COLUMN storage_key VARCHAR(500);
