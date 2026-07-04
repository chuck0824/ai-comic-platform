-- ============================================================
-- V9: Enterprise Audit Index (rebuildable cross-domain query)
-- ============================================================
CREATE TABLE IF NOT EXISTS enterprise_audit_index (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) DEFAULT '',
    actor_user_id BIGINT,
    action VARCHAR(50) NOT NULL,
    object_type VARCHAR(50) NOT NULL,
    object_id VARCHAR(64) NOT NULL,
    result VARCHAR(16) DEFAULT 'SUCCESS',
    source_domain VARCHAR(32) NOT NULL,
    source_record_id VARCHAR(64),
    request_id VARCHAR(64),
    redacted_summary VARCHAR(2000),
    event_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_audit_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_audit_workspace ON enterprise_audit_index(workspace_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_dept ON enterprise_audit_index(workspace_id, department_id);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON enterprise_audit_index(actor_user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_object ON enterprise_audit_index(object_type, object_id);
