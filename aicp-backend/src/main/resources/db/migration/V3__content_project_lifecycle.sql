-- V3: Add lifecycle_status and adopted_version_id to content_projects
-- Executes on the configured production database (H2/MySQL)

ALTER TABLE content_projects
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'active';

ALTER TABLE content_projects
    ADD COLUMN IF NOT EXISTS adopted_version_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_cp_owner_lifecycle_updated
    ON content_projects(owner_user_id, lifecycle_status, updated_at);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cp_legacy_script
    ON content_projects(legacy_script_id);
