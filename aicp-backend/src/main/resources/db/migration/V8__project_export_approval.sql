-- ============================================================
-- V8: Project Export Approval State Machine
-- ============================================================
CREATE TABLE IF NOT EXISTS project_export_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) DEFAULT '',
    project_id BIGINT NOT NULL,
    project_version_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL,

    export_scope_json TEXT,          -- which episodes/chapters
    export_format VARCHAR(16) DEFAULT 'PDF',
    watermark_policy VARCHAR(32),
    delivery_target VARCHAR(200),

    compliance_evidence_ref VARCHAR(200),
    content_snapshot_summary VARCHAR(2000),

    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED
    approver_user_id BIGINT,
    approver_comment VARCHAR(2000),
    approved_at TIMESTAMP NULL,

    export_task_id BIGINT NULL,       -- linked async export task

    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_project_export UNIQUE (project_id, project_version_id, requester_user_id, status)
);

CREATE INDEX IF NOT EXISTS idx_export_workspace ON project_export_requests(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_export_project ON project_export_requests(project_id);
