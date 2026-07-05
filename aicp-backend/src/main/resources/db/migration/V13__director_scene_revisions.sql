-- V13: 导演台 — 不可变 revision、可变草稿、资产关联
-- 每个 ShotWorkUnit 最多一个逻辑 DirectorScene

CREATE TABLE IF NOT EXISTS director_scenes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    shot_unit_id BIGINT NOT NULL COMMENT '所属 ShotWorkUnit',
    current_draft_id BIGINT COMMENT '当前活跃草稿ID',
    current_revision_id BIGINT COMMENT '最新冻结 revision ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_director_scene_unit (shot_unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导演场景';

CREATE TABLE IF NOT EXISTS director_drafts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    scene_id BIGINT NOT NULL COMMENT '所属场景',
    document_json JSON NOT NULL COMMENT '完整 DirectorDocument JSON',
    row_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_draft_scene (scene_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导演草稿（可变，最多一个活跃）';

CREATE TABLE IF NOT EXISTS director_revisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    scene_id BIGINT NOT NULL COMMENT '所属场景',
    revision INT NOT NULL COMMENT '版本号（自增）',
    document_json JSON NOT NULL COMMENT '冻结时规范化的 DirectorDocument',
    document_hash VARCHAR(64) NOT NULL COMMENT 'SHA-256',
    idempotency_key VARCHAR(128) COMMENT '冻结幂等键',
    frozen_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_director_scene_revision (scene_id, revision),
    UNIQUE KEY uk_revision_idempotency (scene_id, idempotency_key),
    INDEX idx_revision_scene (scene_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变导演 revision';

CREATE TABLE IF NOT EXISTS director_revision_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    revision_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL COMMENT '资产ID',
    asset_version_id BIGINT NOT NULL COMMENT '资产版本ID',
    role VARCHAR(64) COMMENT '在 revision 中的角色',
    INDEX idx_revision_asset_rev (revision_id),
    INDEX idx_revision_asset_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='revision 关联的资产版本快照';
