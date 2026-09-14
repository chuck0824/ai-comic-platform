-- V18: R2-A ContentStageCheckpoint — 八阶段事实链
CREATE TABLE IF NOT EXISTS content_stage_checkpoints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    stage_key VARCHAR(50) NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    primary_artifact_type VARCHAR(50) NULL,
    primary_artifact_id BIGINT NULL,
    adopted_content_version_id BIGINT NULL,
    input_snapshot_json TEXT NULL,
    input_snapshot_hash CHAR(64) NULL,
    gate_result_json TEXT NULL,
    stale_reason_json TEXT NULL,
    revision INT NOT NULL DEFAULT 0,
    completed_at TIMESTAMP NULL,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_csc_project_stage (project_id, stage_key),
    KEY idx_csc_project_state (project_id, state)
);
