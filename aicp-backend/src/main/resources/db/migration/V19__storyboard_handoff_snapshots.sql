-- V19: R2-A Storyboard handoff snapshot for R2-B
CREATE TABLE IF NOT EXISTS storyboard_handoff_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    checkpoint_id BIGINT NULL,
    reviewed_script_body_version_id BIGINT NOT NULL,
    continuity_check_result VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    scene_count INT NOT NULL DEFAULT 0,
    payload_json TEXT NULL,
    content_hash CHAR(64) NULL,
    created_by BIGINT NULL,
    captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_shs_project (project_id),
    KEY idx_shs_reviewed (reviewed_script_body_version_id)
);
