-- V15: 质量报告与交付清单 — 质量维度、分级策略、交付清单、打包任务

CREATE TABLE IF NOT EXISTS canvas_quality_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    candidate_id BIGINT NOT NULL COMMENT '关联候选',
    overall_status VARCHAR(16) NOT NULL DEFAULT 'PASS' COMMENT 'PASS|WARN|BLOCK',
    policy_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    created_by BIGINT COMMENT '自动=null，人工=操作人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_canvas_quality_candidate_policy (candidate_id, policy_version),
    INDEX idx_quality_candidate (candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候选质量报告';

CREATE TABLE IF NOT EXISTS canvas_quality_issues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    report_id BIGINT NOT NULL,
    dimension VARCHAR(32) NOT NULL COMMENT 'IDENTITY|COMPOSITION|ACTION|CAMERA|PHYSICS|AUDIO_TIMING|CONTINUITY',
    severity VARCHAR(16) NOT NULL DEFAULT 'WARN' COMMENT 'INFO|WARN|ERROR',
    start_ms INT NOT NULL COMMENT '问题起始时间(ms)',
    end_ms INT NOT NULL COMMENT '问题结束时间(ms)',
    expected_value VARCHAR(500) COMMENT '期望值',
    observed_value VARCHAR(500) COMMENT '实际值',
    source_node_id BIGINT COMMENT '来源节点',
    source_track_id VARCHAR(64) COMMENT '来源轨道',
    suggested_action VARCHAR(64) COMMENT '建议操作',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_issue_report (report_id),
    INDEX idx_issue_node (source_node_id),
    CONSTRAINT chk_issue_range CHECK (end_ms > start_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量问题详情';

CREATE TABLE IF NOT EXISTS delivery_manifests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL COMMENT '所属画布项目',
    revision INT NOT NULL COMMENT '交付版本号',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|FINALIZED|PACKAGING|READY|FAILED',
    manifest_hash VARCHAR(64) COMMENT 'SHA-256',
    idempotency_key VARCHAR(128),
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_delivery_manifest_revision (project_id, revision),
    UNIQUE KEY uk_delivery_idempotency (project_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交付清单';

CREATE TABLE IF NOT EXISTS delivery_manifest_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    manifest_id BIGINT NOT NULL,
    shot_unit_id BIGINT NOT NULL,
    adoption_id BIGINT NOT NULL COMMENT '正式采用ID',
    asset_version_id BIGINT NOT NULL COMMENT '采用的资产版本',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_manifest_item (manifest_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交付清单条目';

-- 扩展 canvas_shot_units：软删除支持
ALTER TABLE canvas_shot_units ADD COLUMN IF NOT EXISTS deleted_at DATETIME COMMENT '软删除时间';
ALTER TABLE generation_candidates ADD COLUMN IF NOT EXISTS deleted_at DATETIME COMMENT '软删除时间';
ALTER TABLE delivery_manifests ADD COLUMN IF NOT EXISTS deleted_at DATETIME COMMENT '软删除时间';
