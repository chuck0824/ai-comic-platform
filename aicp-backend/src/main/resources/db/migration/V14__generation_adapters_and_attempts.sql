-- V14: 生成适配器 — TaskAttempt、Adapter 元数据、重试策略、优先级

CREATE TABLE IF NOT EXISTS generation_task_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    task_id BIGINT NOT NULL COMMENT '关联 GenerationTask',
    attempt_no INT NOT NULL DEFAULT 1 COMMENT '尝试序号',
    provider_request_id VARCHAR(200) COMMENT '供应商请求ID',
    adapter_version VARCHAR(64) COMMENT '使用的适配器版本',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RUNNING|SUCCESS|FAILED|CANCELLED',
    priority VARCHAR(16) NOT NULL DEFAULT 'P1_NORMAL' COMMENT 'P0_HIGH|P1_NORMAL|P2_LOW|P3_BATCH',
    started_at DATETIME,
    completed_at DATETIME,
    error_code VARCHAR(64) COMMENT '失败错误码',
    response_storage_key VARCHAR(500) COMMENT '原始响应存储引用',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retries INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_generation_task_attempt (task_id, attempt_no),
    INDEX idx_attempt_task (task_id),
    INDEX idx_attempt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成任务尝试';

-- 扩展 generation_tasks：请求快照、实际费用、结算引用
ALTER TABLE generation_tasks
    ADD COLUMN IF NOT EXISTS request_snapshot_id BIGINT COMMENT '关联请求快照',
    ADD COLUMN IF NOT EXISTS actual_credit_cost INT COMMENT '实际消耗积分',
    ADD COLUMN IF NOT EXISTS settlement_ref VARCHAR(128) COMMENT '结算引用',
    ADD COLUMN IF NOT EXISTS retry_strategy VARCHAR(32) DEFAULT 'EXPONENTIAL_BACKOFF' COMMENT '重试策略';

-- 模型能力 profile 表（可选持久化）
CREATE TABLE IF NOT EXISTS model_capability_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id VARCHAR(64) NOT NULL UNIQUE COMMENT '如 seedance-2.0',
    adapter_version VARCHAR(64) NOT NULL COMMENT '当前适配器版本',
    production_verified BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否通过生产 Gate',
    limits_json JSON COMMENT '输入限制（max_images/max_videos/max_audio/max_duration_seconds）',
    supported_formats_json JSON COMMENT '支持的格式列表',
    rate_limits_json JSON COMMENT '限流配置',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型能力配置';
