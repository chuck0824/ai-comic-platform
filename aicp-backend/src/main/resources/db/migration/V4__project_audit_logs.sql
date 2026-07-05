-- V4: Project audit log table for lifecycle action tracing
-- Create in separate migration so V3 (lifecycle column) can run independently

CREATE TABLE IF NOT EXISTS project_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_version_id BIGINT,
    before_status VARCHAR(50),
    after_status VARCHAR(50),
    comment VARCHAR(2000),
    idempotency_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_audit_idempotency UNIQUE (project_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_pal_project_created
    ON project_audit_logs(project_id, created_at);
