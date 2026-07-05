-- V12: Canvas 生产内核 — ShotWorkUnit、不可变快照、候选、正式采用、迁移记录
-- R1 核心 schema：探索/正式双模式、类型化端口、请求快照、候选和正式采用

-- 1. ShotWorkUnit：画布内镜头生产容器（非新节点类型）
CREATE TABLE IF NOT EXISTS canvas_shot_units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE COMMENT '稳定外部ID',
    project_id BIGINT NOT NULL COMMENT '所属画布项目',
    mode VARCHAR(16) NOT NULL DEFAULT 'EXPLORATION' COMMENT '模式：EXPLORATION | PRODUCTION',
    provisional_shot_id VARCHAR(64) COMMENT '探索模式临时镜头ID（draft_shot_xxx）',
    source_shot_id BIGINT COMMENT '正式模式绑定分镜ID',
    source_shot_revision INT COMMENT '正式模式绑定分镜版本号',
    target_duration_ms INT NOT NULL DEFAULT 5000 COMMENT '目标时长(ms)',
    fps INT NOT NULL DEFAULT 24 COMMENT '帧率',
    aspect_ratio VARCHAR(16) NOT NULL DEFAULT '16:9' COMMENT '画幅比例',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    row_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_shot_unit_project (project_id),
    INDEX idx_shot_unit_source (source_shot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='画布镜头生产单元';

-- 2. 不可变生成请求快照
CREATE TABLE IF NOT EXISTS generation_request_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE COMMENT '稳定外部ID',
    node_id BIGINT NOT NULL COMMENT '触发节点ID',
    shot_unit_id BIGINT NOT NULL COMMENT '所属镜头单元ID',
    payload_json JSON NOT NULL COMMENT '规范化请求JSON',
    payload_hash VARCHAR(64) NOT NULL COMMENT 'SHA-256 哈希',
    resolved_model_id VARCHAR(128) NOT NULL COMMENT '解析后的模型ID',
    resolved_model_version VARCHAR(128) COMMENT '解析后的模型版本',
    adapter_version VARCHAR(64) NOT NULL COMMENT '适配器版本',
    estimated_credits INT NOT NULL COMMENT '预估积分',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_snapshot_node (node_id),
    INDEX idx_snapshot_shot_unit (shot_unit_id),
    INDEX idx_snapshot_hash (payload_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变生成请求快照';

-- 3. 生成候选
CREATE TABLE IF NOT EXISTS generation_candidates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE COMMENT '稳定外部ID',
    request_snapshot_id BIGINT NOT NULL COMMENT '关联请求快照',
    task_id BIGINT COMMENT '关联生成任务',
    attempt_no INT NOT NULL DEFAULT 1 COMMENT '尝试序号',
    asset_version_id BIGINT COMMENT '输出资产版本ID',
    model_id VARCHAR(128) COMMENT '实际使用的模型ID',
    seed BIGINT COMMENT '随机种子',
    actual_credits INT COMMENT '实际消耗积分',
    safety_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '安全审核：PENDING|PASS|FLAGGED|REJECTED',
    is_selected BOOLEAN NOT NULL DEFAULT FALSE COMMENT '节点当前选择的候选',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_candidate_snapshot (request_snapshot_id),
    INDEX idx_candidate_task (task_id),
    INDEX idx_candidate_asset (asset_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成候选';

-- 4. 正式采用（唯一事实源）
CREATE TABLE IF NOT EXISTS shot_adoptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE COMMENT '稳定外部ID',
    shot_unit_id BIGINT NOT NULL COMMENT '所属镜头单元',
    revision INT NOT NULL COMMENT '采用版本号（自增）',
    candidate_id BIGINT NOT NULL COMMENT '采用的候选ID',
    adopted_by BIGINT NOT NULL COMMENT '采用操作人',
    reason VARCHAR(500) COMMENT '采用原因',
    override_reason VARCHAR(500) COMMENT '人工覆盖阻断质检的原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shot_adoption_revision (shot_unit_id, revision),
    INDEX idx_adoption_candidate (candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='镜头正式采用记录';

-- 5. 画布迁移记录（R1 升级时持久化备份和迁移状态）
CREATE TABLE IF NOT EXISTS canvas_migration_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL COMMENT '所属画布项目',
    backup_json JSON NOT NULL COMMENT '升级前完整备份JSON',
    backup_checksum VARCHAR(64) NOT NULL COMMENT '备份校验和',
    node_count INT NOT NULL DEFAULT 0,
    edge_count INT NOT NULL DEFAULT 0,
    ambiguous_items_json JSON COMMENT '需人工确认的歧义项列表',
    status VARCHAR(32) NOT NULL DEFAULT 'UPGRADED' COMMENT '迁移状态：UPGRADED|FAILED|ROLLED_BACK',
    idempotency_key VARCHAR(128) COMMENT '幂等键',
    error_detail VARCHAR(2000) COMMENT '失败原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_migration_idempotency (project_id, idempotency_key),
    INDEX idx_migration_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='画布迁移记录';

-- 6. 扩展 canvas_nodes：增加 ShotWorkUnit 归属和 schema 版本
ALTER TABLE canvas_nodes
    ADD COLUMN IF NOT EXISTS shot_unit_id BIGINT COMMENT '所属镜头单元ID',
    ADD COLUMN IF NOT EXISTS node_schema_version VARCHAR(32) DEFAULT 'legacy' COMMENT '节点 schema 版本';

-- 7. 扩展 canvas_edges：增加端口契约版本、状态和角色
ALTER TABLE canvas_edges
    ADD COLUMN IF NOT EXISTS port_contract_version VARCHAR(32) DEFAULT 'legacy' COMMENT '端口契约版本',
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'NEEDS_CONFIRMATION' COMMENT '连线状态：ACTIVE|NEEDS_CONFIRMATION|LEGACY',
    ADD COLUMN IF NOT EXISTS role VARCHAR(64) COMMENT '端口角色（identity/scene/composition 等）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_edge_target_role
    ON canvas_edges (target_node_id, target_port, role)
    WHERE role IS NOT NULL;

-- 8. 扩展 canvas_projects：增加模式、schema 版本和迁移状态
ALTER TABLE canvas_projects
    ADD COLUMN IF NOT EXISTS canvas_mode VARCHAR(16) DEFAULT 'EXPLORATION' COMMENT '模式：EXPLORATION | PRODUCTION',
    ADD COLUMN IF NOT EXISTS schema_version INT DEFAULT 1 COMMENT '画布 schema 版本（1=旧，2=V2内核）',
    ADD COLUMN IF NOT EXISTS content_project_id BIGINT COMMENT '绑定的内容项目',
    ADD COLUMN IF NOT EXISTS storyboard_revision INT COMMENT '绑定的分镜版本';
