-- V21: R2-A 灰度期旧 workflow 推断 vs 检查点投影差异日志（只记不覆盖）
CREATE TABLE IF NOT EXISTS stage_truth_diff_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    trigger_source VARCHAR(32) NOT NULL,
    legacy_current_stage VARCHAR(64) NULL,
    truth_current_stage VARCHAR(64) NULL,
    legacy_progress INT NULL,
    truth_progress INT NULL,
    legacy_snapshot_json MEDIUMTEXT NOT NULL,
    truth_snapshot_json MEDIUMTEXT NOT NULL,
    diff_summary_json TEXT NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_stdl_project_created (project_id, created_at),
    KEY idx_stdl_project_fingerprint (project_id, fingerprint)
);
