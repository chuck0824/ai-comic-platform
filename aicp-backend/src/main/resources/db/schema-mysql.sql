-- ============================================================
-- AICP V1.5 Database Schema (MySQL 8.0)
-- AI漫剧与视频内容工业化生产工作台
-- ============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- === 1. 用户与账户 (user-svc) ===
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    phone VARCHAR(20) UNIQUE, email VARCHAR(255) UNIQUE,
    wechat_openid VARCHAR(128) UNIQUE, password_hash VARCHAR(255),
    nickname VARCHAR(100) NOT NULL, avatar_url VARCHAR(500),
    account_type ENUM('personal','enterprise') DEFAULT 'personal',
    real_name_status ENUM('unverified','pending','verified') DEFAULT 'unverified',
    member_level ENUM('free','creator','enterprise') DEFAULT 'free',
    member_expire_at DATETIME,
    status ENUM('active','disabled','deleted') DEFAULT 'active',
    last_login_at DATETIME, last_login_ip VARCHAR(45),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS enterprises (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL, name VARCHAR(200) NOT NULL,
    license_number VARCHAR(100), license_image_url VARCHAR(500),
    verify_status ENUM('unverified','pending','verified','rejected') DEFAULT 'unverified',
    member_limit INT DEFAULT 10,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业表';

CREATE TABLE IF NOT EXISTS enterprise_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    role VARCHAR(50) DEFAULT 'writer', permissions JSON,
    department VARCHAR(100),
    purchase_budget_monthly DECIMAL(10,2) DEFAULT 0,
    purchase_budget_single DECIMAL(10,2) DEFAULT 0,
    status ENUM('pending','active','disabled') DEFAULT 'active',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (enterprise_id) REFERENCES enterprises(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uk_ent_user (enterprise_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业成员表';

CREATE TABLE IF NOT EXISTS api_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, enterprise_id BIGINT,
    key_hash VARCHAR(255) NOT NULL, name VARCHAR(100) NOT NULL,
    key_prefix VARCHAR(10), scopes JSON,
    rate_limit INT DEFAULT 1000,
    status ENUM('active','disabled') DEFAULT 'active',
    last_used_at DATETIME, expires_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key表';

-- === 2. 剧本生成 (script-gen-svc) ===
CREATE TABLE IF NOT EXISTS gen_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, project_id VARCHAR(50),
    gen_type ENUM('topic','synopsis','outline','episode','adaptation','storyboard','promotion','quick') NOT NULL,
    storyboard_tier ENUM('A','B','C'),
    input_params JSON, output_data JSON, prompt_used TEXT,
    model_used VARCHAR(100),
    status ENUM('pending','processing','completed','failed','cancelled') DEFAULT 'pending',
    tokens_used INT DEFAULT 0, duration_ms INT DEFAULT 0,
    error_msg TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成任务表';

-- === 3. 剧本仓库 (script-repo-svc) ===
CREATE TABLE IF NOT EXISTS scripts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE, project_id VARCHAR(50),
    title VARCHAR(200) NOT NULL,
    author_user_id BIGINT, owner_user_id BIGINT,
    owner_type ENUM('personal','enterprise') DEFAULT 'personal',
    enterprise_id BIGINT,
    episode_count INT DEFAULT 0, completed_episodes INT DEFAULT 0,
    total_words INT DEFAULT 0, cover_image_url VARCHAR(500),
    synopsis TEXT,
    genre_tag VARCHAR(50), plot_tags JSON, tone_tags JSON, setting_tag VARCHAR(50),
    source ENUM('ai_generated','purchased','uploaded') DEFAULT 'ai_generated',
    status ENUM('draft','pending_review','listed','sold','delisted','archived') DEFAULT 'draft',
    current_version VARCHAR(20) DEFAULT 'v0.1',
    maturity_level ENUM('L0','L1','L2','L3','L4') DEFAULT 'L0',
    plugin_pack JSON,
    rating DECIMAL(2,1) DEFAULT 0, review_count INT DEFAULT 0, sales_count INT DEFAULT 0,
    is_deleted TINYINT(1) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧本表';

-- 高频查询索引
CREATE INDEX IF NOT EXISTS idx_scripts_owner ON scripts(owner_user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_scripts_market ON scripts(status, genre_tag, updated_at);

CREATE TABLE IF NOT EXISTS script_episodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL, episode_number INT NOT NULL,
    title VARCHAR(200), content LONGTEXT,
    storyboard_tier ENUM('A','B','C'), word_count INT DEFAULT 0,
    opening_hook TEXT,
    closing_hook TEXT,
    hook_score_avg DECIMAL(3,2),
    hook_count INT DEFAULT 0,
    status ENUM('draft','completed') DEFAULT 'draft',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    UNIQUE KEY uk_script_ep (script_id, episode_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧本分集表';

CREATE TABLE IF NOT EXISTS episode_review_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT,
    episode_id BIGINT,
    episode_number INT,
    overall_status ENUM('pass','needs_revision','approved') DEFAULT 'needs_revision',
    overall_score DECIMAL(4,2),
    hook_score DECIMAL(4,2),
    showrunner_score DECIMAL(4,2),
    director_score DECIMAL(4,2),
    report_json JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_episode_review (episode_id, created_at),
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    FOREIGN KEY (episode_id) REFERENCES script_episodes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每集联合审核报告表';

CREATE TABLE IF NOT EXISTS script_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL, version VARCHAR(20) NOT NULL,
    content LONGTEXT, change_summary VARCHAR(500),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧本版本表';

CREATE TABLE IF NOT EXISTS chapter_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT,
    episode_id BIGINT,
    chapter_number INT,
    title VARCHAR(200),
    content LONGTEXT,
    content_format VARCHAR(30) DEFAULT 'novel',
    version_no VARCHAR(30),
    change_summary VARCHAR(500),
    source VARCHAR(30) DEFAULT 'manual_edit',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chapter_version (episode_id, created_at),
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    FOREIGN KEY (episode_id) REFERENCES script_episodes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单章正文版本表';

CREATE TABLE IF NOT EXISTS adaptation_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT,
    source_chapter_version_id BIGINT,
    source_project_version_id BIGINT,
    target_type ENUM('ai_comic','short_drama','web_drama','tvc') DEFAULT 'ai_comic',
    version_no VARCHAR(30),
    title VARCHAR(200),
    content LONGTEXT,
    hook_strategy_json JSON,
    status ENUM('draft','needs_sync','reviewing','locked') DEFAULT 'draft',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_adaptation_script (script_id, target_type, created_at),
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    FOREIGN KEY (source_chapter_version_id) REFERENCES chapter_versions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='源头文本改编脚本版本表';

CREATE TABLE IF NOT EXISTS repo_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id VARCHAR(50) NOT NULL UNIQUE,
    asset_type ENUM('character','scene','prop','voice','style') NOT NULL,
    name VARCHAR(200) NOT NULL,
    script_id BIGINT, project_id VARCHAR(50),
    owner_user_id BIGINT, enterprise_id BIGINT,
    description TEXT,
    face_id VARCHAR(50), costume_id VARCHAR(50), voice_id VARCHAR(50),
    location_id VARCHAR(50),
    maturity_level ENUM('L0','L1','L2','L3','L4') DEFAULT 'L0',
    is_locked TINYINT(1) DEFAULT 0,
    short_anchor TEXT, long_anchor TEXT,
    reference_image_urls JSON, consistency_prompt TEXT,
    seed_value BIGINT, metadata JSON,
    is_public TINYINT(1) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库资产表';

CREATE TABLE IF NOT EXISTS continuity_states (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL, script_id BIGINT,
    episode_id VARCHAR(20),
    state_type ENUM('character','relation','prop','foreshadow','info','voice','scene','asset') NOT NULL,
    target_id VARCHAR(100),
    start_state TEXT, end_state TEXT,
    must_inherit TINYINT(1) DEFAULT 0, risk VARCHAR(200),
    data JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='连续性状态表';

-- === 4. 画布工作台 (canvas-svc) — V1.5 核心 ===
CREATE TABLE IF NOT EXISTS canvas_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL, enterprise_id BIGINT,
    workspace_id VARCHAR(64),
    name VARCHAR(200) NOT NULL DEFAULT '未命名画布项目',
    script_id BIGINT COMMENT 'legacy: replaced by content_project_id + production_unit_id',
    episode_index INT DEFAULT 1 COMMENT 'legacy: replaced by production_unit_id',
    style_config JSON,
    applied_asset_ids JSON DEFAULT ('[]'),
    status ENUM('editing','generating','composing','exporting','completed','archived') DEFAULT 'editing',
    canvas_version INT DEFAULT 1,
    -- New ownership columns (2026-07-01)
    content_project_id BIGINT COMMENT 'FK to content_projects.id',
    production_unit_type VARCHAR(32) COMMENT 'episode / chapter / tvc_variant',
    production_unit_id BIGINT COMMENT 'FK to content_units.id',
    source_content_version_id BIGINT COMMENT 'FK to content_versions.id',
    source_storyboard_version_id BIGINT COMMENT 'FK to cp_storyboard_masters.id',
    production_snapshot JSON COMMENT 'Immutable source snapshot',
    purpose VARCHAR(32) DEFAULT 'official' COMMENT 'official / alternative / experiment',
    owner_id BIGINT COMMENT 'Canvas owner user ID',
    thumbnail_url VARCHAR(500),
    idempotency_key VARCHAR(200) COMMENT 'Idempotent creation key',
    archived_at DATETIME COMMENT 'Archived timestamp',
    revision INT DEFAULT 0 COMMENT 'Optimistic lock',
    is_deleted TINYINT DEFAULT 0 COMMENT 'Soft delete',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uk_canvas_idempotency (user_id, idempotency_key),
    INDEX idx_canvas_owner_status (user_id, status, updated_at),
    INDEX idx_canvas_content_unit (content_project_id, production_unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='画布项目表';

CREATE TABLE IF NOT EXISTS canvas_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    type ENUM('text','image','video','audio','script','storyboard','character','scene','prompt','reference','workflow') NOT NULL,
    name VARCHAR(200),
    x INT NOT NULL DEFAULT 0, y INT NOT NULL DEFAULT 0,
    width INT DEFAULT 200, height INT DEFAULT 180,
    input_data JSON, output_data JSON,
    status ENUM('ready','processing','completed','failed','locked') DEFAULT 'ready',
    group_id BIGINT, locked_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES canvas_projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='画布节点表';

CREATE TABLE IF NOT EXISTS canvas_edges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    source_node_id BIGINT NOT NULL, source_port VARCHAR(50) DEFAULT 'out',
    target_node_id BIGINT NOT NULL, target_port VARCHAR(50) DEFAULT 'in',
    edge_type ENUM('data','flow','reference') DEFAULT 'data',
    metadata JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES canvas_projects(id) ON DELETE CASCADE,
    FOREIGN KEY (source_node_id) REFERENCES canvas_nodes(id) ON DELETE CASCADE,
    FOREIGN KEY (target_node_id) REFERENCES canvas_nodes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='画布连线表';

CREATE TABLE IF NOT EXISTS canvas_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL DEFAULT '未命名分组',
    node_ids JSON, workflow_template_id BIGINT, color VARCHAR(20),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES canvas_projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='画布节点分组表';

CREATE TABLE IF NOT EXISTS storyboard_shots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    storyboard_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
    shot_no INT NOT NULL, scene_no INT NOT NULL,
    duration INT DEFAULT 3000,
    shot_size VARCHAR(50), camera_motion VARCHAR(50),
    visual_description TEXT, characters JSON, scene_asset_id VARCHAR(50),
    dialogue JSON,
    image_prompt TEXT, video_prompt TEXT,
    keyframe_start JSON, keyframe_end JSON,
    image_status ENUM('pending','generating','completed','failed') DEFAULT 'pending',
    video_status ENUM('pending','generating','completed','failed') DEFAULT 'pending',
    metadata JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (storyboard_id) REFERENCES canvas_nodes(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES canvas_projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分镜表';

CREATE TABLE IF NOT EXISTS canvas_timelines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    data JSON, duration_ms INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES canvas_projects(id) ON DELETE CASCADE,
    UNIQUE KEY uk_timeline_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='画布时间线表';

CREATE TABLE IF NOT EXISTS canvas_workflows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL DEFAULT '未命名工作流',
    description TEXT, node_ids JSON, config JSON,
    status ENUM('draft','published','archived') DEFAULT 'draft',
    template_version VARCHAR(20),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES canvas_projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='画布工作流表';

CREATE TABLE IF NOT EXISTS workflow_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    owner_id BIGINT, name VARCHAR(200) NOT NULL, description TEXT,
    category VARCHAR(50), config JSON, variables JSON,
    thumbnail_url VARCHAR(500),
    usage_count INT DEFAULT 0, rating DECIMAL(2,1) DEFAULT 0,
    visibility ENUM('private','team','public') DEFAULT 'private',
    status ENUM('draft','published','archived') DEFAULT 'draft',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流模板表';

-- === 5. 生成任务 (generation-svc) ===
CREATE TABLE IF NOT EXISTS generation_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT, node_id BIGINT, shot_id BIGINT,
    type VARCHAR(30) NOT NULL,
    sub_type VARCHAR(50),
    provider VARCHAR(50), model_id VARCHAR(100),
    parameters JSON,
    status VARCHAR(20) DEFAULT 'pending',
    progress INT DEFAULT 0, credit_cost INT DEFAULT 0,
    error_code VARCHAR(50), error_message TEXT,
    output_assets JSON,
    workspace_id VARCHAR(64) NOT NULL DEFAULT 'personal_1',
    created_by BIGINT NOT NULL DEFAULT 0,
    content_project_id BIGINT,
    asset_type VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    retry_of_task_id BIGINT,
    idempotency_key VARCHAR(64),
    request_id VARCHAR(64),
    started_at DATETIME, completed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成任务表';

-- 高频查询索引
CREATE INDEX IF NOT EXISTS idx_gen_task_project ON generation_tasks(project_id, created_at);
CREATE INDEX IF NOT EXISTS idx_gen_task_status ON generation_tasks(status, created_at);
CREATE INDEX IF NOT EXISTS idx_gen_task_workspace ON generation_tasks(workspace_id, created_at);
CREATE INDEX IF NOT EXISTS idx_gen_task_retry ON generation_tasks(retry_of_task_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_gen_task_idempotency ON generation_tasks(workspace_id, idempotency_key);

CREATE TABLE IF NOT EXISTS generation_variants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_task_id BIGINT NOT NULL, variant_index INT NOT NULL,
    parameters JSON, output_data JSON,
    quality_score DECIMAL(3,2), selected TINYINT(1) DEFAULT 0,
    task_uuid VARCHAR(36),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_task_id) REFERENCES generation_tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多副本变体表';

CREATE TABLE IF NOT EXISTS credit_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, enterprise_id BIGINT, task_id BIGINT,
    amount INT NOT NULL,
    type ENUM('charge','consumption','refund','bonus','gift') NOT NULL,
    balance_before INT NOT NULL, balance_after INT NOT NULL,
    description VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分交易表';

-- === 6. 平台资产库 ===
CREATE TABLE IF NOT EXISTS platform_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT, source_node_id BIGINT, source_task_id BIGINT,
    type ENUM('image','video','audio','model','other') NOT NULL,
    name VARCHAR(200) NOT NULL,
    file_url VARCHAR(500), thumbnail_url VARCHAR(500),
    prompt TEXT, model_id VARCHAR(100), parameters JSON,
    file_size BIGINT DEFAULT 0, duration_ms INT,
    width INT, height INT, metadata JSON, tags JSON,
    favorite TINYINT(1) DEFAULT 0,
    owner_user_id BIGINT NOT NULL, enterprise_id BIGINT,
    visibility ENUM('private','team','public') DEFAULT 'private',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES canvas_projects(id) ON DELETE SET NULL,
    FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台资产库表';

-- ============================================================
-- 7. Script Trading Market (V6 — unified trade domain)
-- ============================================================

CREATE TABLE IF NOT EXISTS script_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    seller_user_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    script_version_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    synopsis VARCHAR(5000),
    cover_url VARCHAR(500),
    tags_json JSON,
    characters_json JSON,
    episode_count INT DEFAULT 0,
    author_display_name VARCHAR(100),
    preview_episode_count INT NOT NULL DEFAULT 1,
    preview_episodes_json JSON,
    review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    review_reason VARCHAR(2000),
    reviewed_by BIGINT,
    reviewed_at DATETIME NULL,
    listing_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    exclusive_license_type VARCHAR(10) NULL,
    historical_normal_count INT NOT NULL DEFAULT 0,
    reserved_order_no VARCHAR(32) NULL,
    reservation_expires_at DATETIME NULL,
    row_version INT NOT NULL DEFAULT 0,
    listed_at DATETIME NULL,
    delisted_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_listings_status (listing_status),
    INDEX idx_listings_workspace (workspace_id),
    INDEX idx_listings_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧本上架表';

CREATE TABLE IF NOT EXISTS listing_license_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT NOT NULL,
    license_type VARCHAR(10) NOT NULL,
    price_cents BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    term_json JSON,
    agreement_text TEXT,
    agreement_version VARCHAR(20),
    agreement_hash VARCHAR(64),
    enabled TINYINT NOT NULL DEFAULT 1,
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_license_listing_type (listing_id, license_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授权选项表';

CREATE TABLE IF NOT EXISTS trade_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
    buyer_user_id BIGINT NOT NULL,
    buyer_workspace_id VARCHAR(64) NOT NULL,
    buyer_workspace_type VARCHAR(16) NOT NULL DEFAULT 'PERSONAL',
    seller_user_id BIGINT NOT NULL,
    seller_workspace_id VARCHAR(64) NOT NULL,
    total_amount_cents BIGINT NOT NULL DEFAULT 0,
    platform_fee_cents BIGINT NOT NULL DEFAULT 0,
    seller_income_cents BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    wallet_transfer_no VARCHAR(64) NULL,
    wallet_status VARCHAR(20) NULL,
    create_idempotency_key VARCHAR(128) NOT NULL,
    expires_at DATETIME NULL,
    paid_at DATETIME NULL,
    fulfilled_at DATETIME NULL,
    completed_at DATETIME NULL,
    refunded_at DATETIME NULL,
    row_version INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(2000),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_trade_order_no (order_no),
    UNIQUE KEY uk_trade_order_idempotent (buyer_workspace_id, create_idempotency_key),
    INDEX idx_trade_orders_buyer (buyer_user_id, buyer_workspace_id),
    INDEX idx_trade_orders_seller (seller_user_id),
    INDEX idx_trade_orders_status (status),
    INDEX idx_trade_orders_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易订单表';

CREATE TABLE IF NOT EXISTS trade_order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    script_version_id BIGINT NOT NULL,
    license_type VARCHAR(10) NOT NULL,
    price_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    title_snapshot VARCHAR(200),
    author_snapshot VARCHAR(100),
    tags_snapshot JSON,
    agreement_text TEXT,
    agreement_version VARCHAR(20),
    agreement_hash VARCHAR(64),
    historical_normal_count INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_item (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单项表';

CREATE TABLE IF NOT EXISTS script_entitlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    beneficiary_workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL,
    script_version_id BIGINT NOT NULL,
    license_type VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATETIME DEFAULT CURRENT_TIMESTAMP,
    effective_until DATETIME NULL,
    max_accounts INT NULL,
    allow_commercial TINYINT NOT NULL DEFAULT 0,
    allow_adaptation TINYINT NOT NULL DEFAULT 0,
    allow_sublicense TINYINT NOT NULL DEFAULT 0,
    territory_restriction VARCHAR(200),
    revoked_at DATETIME NULL,
    revoke_reason VARCHAR(2000),
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_entitlement_order_item (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授权凭证表';

CREATE TABLE IF NOT EXISTS purchased_script_copies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL,
    source_version_id BIGINT NOT NULL,
    content_json JSON,
    title VARCHAR(200),
    created_by_user_id BIGINT NOT NULL,
    source_listing_id BIGINT,
    source_author_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_copy_order_item (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已购剧本副本表';

CREATE TABLE IF NOT EXISTS purchase_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    license_type VARCHAR(10) NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    reason VARCHAR(2000),
    approver_user_id BIGINT NULL,
    approval_comment VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    order_no VARCHAR(32) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pr_workspace_status (workspace_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业采购申请表';

CREATE TABLE IF NOT EXISTS refund_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    reason_code VARCHAR(30),
    reason_text VARCHAR(2000),
    evidence_json JSON,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reviewer_user_id BIGINT NULL,
    review_comment VARCHAR(2000),
    reviewed_at DATETIME NULL,
    refund_amount_cents BIGINT,
    wallet_reversal_no VARCHAR(64),
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_rf_order (order_no),
    INDEX idx_rf_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款申请表';

CREATE TABLE IF NOT EXISTS trade_outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSON,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 10,
    next_retry_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(2000),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_outbox_idempotent (aggregate_type, aggregate_id, event_type, idempotency_key),
    INDEX idx_outbox_dispatch (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易Outbox事件表';

CREATE TABLE IF NOT EXISTS trade_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT,
    workspace_id VARCHAR(64),
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    before_summary VARCHAR(2000),
    after_summary VARCHAR(2000),
    correlation_id VARCHAR(64),
    client_ip VARCHAR(45),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_target (target_type, target_id),
    INDEX idx_audit_actor (actor_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易审计日志表';

-- (withdrawals table retained from legacy; unused by new trade module)

-- === 8. AI资产市场 (asset-market-svc) ===
-- ============================================================
-- AI 资产市场 统一模型 (V2 — 替换旧 market_assets/asset_favorites/asset_downloads)
-- ============================================================

CREATE TABLE IF NOT EXISTS workspace_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    workspace_id VARCHAR(64) NOT NULL,
    workspace_type VARCHAR(16) NOT NULL,
    creator_user_id BIGINT NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    tags JSON DEFAULT ('[]'),
    access_scope VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    source_type VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    source_listing_id BIGINT,
    source_version_id BIGINT,
    current_version_id BIGINT,
    content_project_id BIGINT,
    source_canvas_project_id BIGINT,
    source_node_id BIGINT,
    source_task_id BIGINT,
    media_type VARCHAR(16) NOT NULL DEFAULT 'OTHER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    deleted_at DATETIME NULL,
    deleted_by BIGINT,
    purge_at DATETIME NULL,
    purge_blocked_reason VARCHAR(64),
    legacy_platform_asset_id BIGINT,
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    INDEX idx_ws_asset_workspace (workspace_id),
    INDEX idx_ws_asset_project (workspace_id, content_project_id, status),
    INDEX idx_ws_asset_creator (workspace_id, creator_user_id, status),
    INDEX idx_ws_asset_type (asset_type),
    INDEX idx_ws_asset_source_task (workspace_id, source_task_id),
    UNIQUE INDEX uk_ws_legacy_platform (legacy_platform_asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workspace资产本体';

CREATE TABLE IF NOT EXISTS asset_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    version_number INT NOT NULL DEFAULT 1,
    source_task_id BIGINT,
    storage_provider VARCHAR(24),
    storage_bucket VARCHAR(128),
    storage_key VARCHAR(768),
    mime_type VARCHAR(128),
    file_size BIGINT DEFAULT 0,
    width INT,
    height INT,
    duration_ms INT,
    metadata JSON,
    preview_url VARCHAR(500),
    content_ref VARCHAR(500),
    checksum VARCHAR(128),
    generation_snapshot JSON,
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_av_asset (asset_id),
    UNIQUE INDEX uk_av_asset_version (asset_id, version_number),
    INDEX idx_av_source_task (source_task_id),
    INDEX idx_av_checksum (checksum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产版本(不可变)';

CREATE TABLE IF NOT EXISTS market_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    publisher_workspace_id VARCHAR(64) NOT NULL,
    publisher_user_id BIGINT NOT NULL,
    source_asset_id BIGINT NOT NULL,
    source_version_id BIGINT NOT NULL,
    asset_type ENUM('CHECKPOINT','LORA','STYLE_PACK','CHARACTER','SCENE','PROMPT') NOT NULL,
    public_snapshot JSON NOT NULL,
    license_type ENUM('FREE','PAID','SUBSCRIPTION') NOT NULL DEFAULT 'FREE',
    price DECIMAL(10,2) DEFAULT 0,
    status ENUM('LISTED','UNLISTED','REMOVED') NOT NULL DEFAULT 'LISTED',
    use_count INT DEFAULT 0,
    rating DECIMAL(2,1) DEFAULT 0,
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ml_status_type (status, asset_type),
    INDEX idx_ml_publisher (publisher_workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共市场上架记录';

CREATE TABLE IF NOT EXISTS asset_entitlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    beneficiary_workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL,
    source_version_id BIGINT NOT NULL,
    grant_type ENUM('FREE_CLAIM','PAID_PURCHASE','GIFTED') NOT NULL DEFAULT 'FREE_CLAIM',
    claimed_by BIGINT,
    claimed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_entitlement_workspace_listing (beneficiary_workspace_id, listing_id),
    INDEX idx_ae_workspace (beneficiary_workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产使用授权';

CREATE TABLE IF NOT EXISTS asset_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_favorite_user_workspace_listing (user_id, workspace_id, listing_id),
    INDEX idx_af_user_workspace (user_id, workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产收藏';

CREATE TABLE IF NOT EXISTS asset_publish_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    reviewer_id BIGINT,
    status ENUM('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(500),
    review_comment VARCHAR(500),
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_apr_workspace_status (workspace_id, status),
    UNIQUE KEY uk_pending_publish_asset_version (workspace_id, asset_id, version_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业资产发布申请';

CREATE TABLE IF NOT EXISTS asset_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    asset_version_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    target_type VARCHAR(20),
    target_id BIGINT,
    target_key VARCHAR(128),
    change_summary VARCHAR(500),
    previous_state JSON,
    undo_token_hash VARCHAR(64),
    applied_by BIGINT,
    idempotency_key VARCHAR(64) NOT NULL,
    status ENUM('APPLIED','UNDONE') NOT NULL DEFAULT 'APPLIED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_application_workspace_key (workspace_id, idempotency_key),
    INDEX idx_aa_workspace (workspace_id),
    INDEX idx_aa_project (project_id),
    INDEX idx_aa_target_key (target_type, target_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产应用记录';

-- ── Asset workbench tables ──────────────────────────────────────────

CREATE TABLE IF NOT EXISTS workspace_asset_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_waf_user_workspace_asset (user_id, workspace_id, asset_id),
    INDEX idx_waf_workspace (workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workspace资产个人收藏';

CREATE TABLE IF NOT EXISTS asset_activity_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    before_data JSON,
    after_data JSON,
    request_id VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_aal_workspace (workspace_id, created_at),
    INDEX idx_aal_asset (workspace_id, asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产操作日志(只追加)';

CREATE TABLE IF NOT EXISTS canvas_asset_placements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    asset_version_id BIGINT NOT NULL,
    canvas_project_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    placed_by BIGINT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    released_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cap_workspace_idem_key (workspace_id, idempotency_key),
    INDEX idx_cap_asset (workspace_id, asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产到画布节点的放置引用';

CREATE TABLE IF NOT EXISTS asset_command_idempotencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_code INT,
    response_body JSON,
    expires_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_aci_workspace_user_key (workspace_id, user_id, idempotency_key),
    INDEX idx_aci_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='命令幂等记录';

CREATE TABLE IF NOT EXISTS generation_settlement_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    payload JSON,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME,
    last_error TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gso_task_stage (task_id, stage),
    INDEX idx_gso_status_next_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成结算补偿事件表';

-- === 9. Agent与Skill (agent-svc) — V1.5 新增 ===
CREATE TABLE IF NOT EXISTS agent_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL, project_id BIGINT,
    title VARCHAR(200) DEFAULT '新会话', agent_config JSON,
    status ENUM('active','completed','failed','canceled') DEFAULT 'active',
    estimated_seconds INT, total_credit_cost INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, completed_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话表';

CREATE TABLE IF NOT EXISTS agent_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role ENUM('user','assistant','system','tool') NOT NULL,
    content TEXT, tool_calls JSON, tool_results JSON,
    confidence DECIMAL(3,2), tokens_used INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES agent_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息表';

CREATE TABLE IF NOT EXISTS agent_executions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    session_id BIGINT NOT NULL, skill_id BIGINT,
    tool_name VARCHAR(100),
    status ENUM('pending','running','succeeded','failed') DEFAULT 'pending',
    inputs JSON, outputs JSON, logs JSON,
    duration_ms INT DEFAULT 0, credit_cost INT DEFAULT 0,
    error_message TEXT,
    started_at DATETIME, completed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES agent_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent执行记录表';

CREATE TABLE IF NOT EXISTS skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL UNIQUE, description TEXT,
    content TEXT NOT NULL,
    type ENUM('script','storyboard','image','video','audio','compose','quality','meta') NOT NULL,
    version VARCHAR(20) DEFAULT '1.0.0', variables JSON,
    visibility ENUM('private','team','public') DEFAULT 'private',
    owner_id BIGINT,
    usage_count INT DEFAULT 0, rating DECIMAL(2,1) DEFAULT 0,
    status ENUM('draft','published','archived') DEFAULT 'draft',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill配置表';

CREATE TABLE IF NOT EXISTS skill_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id BIGINT NOT NULL, version VARCHAR(20) NOT NULL,
    content TEXT NOT NULL, change_summary VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE,
    UNIQUE KEY uk_skill_version (skill_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill版本表';

-- === 10. SOP与通知 ===
CREATE TABLE IF NOT EXISTS sop_audits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL, canvas_project_id BIGINT,
    shot_id VARCHAR(50), check_item VARCHAR(200) NOT NULL,
    issue_type VARCHAR(100),
    severity ENUM('P0','P1','P2','P3') NOT NULL DEFAULT 'P2',
    quality_grade ENUM('S','A','B','C','D','E'),
    description TEXT, fix_suggestion TEXT, responsible_role VARCHAR(50),
    status ENUM('open','fixing','fixed','verified','ignored') DEFAULT 'open',
    fixed_by BIGINT, verified_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SOP审计表';

CREATE INDEX IF NOT EXISTS idx_sop_audit_project ON sop_audits(project_id);

CREATE TABLE IF NOT EXISTS sop_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL,
    from_version VARCHAR(20), to_version VARCHAR(20) NOT NULL,
    comment TEXT, promoted_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产版本记录表';

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type ENUM('script_generated','script_listed','order_paid','export_completed','audit_failed','asset_published','agent_completed','system') NOT NULL,
    title VARCHAR(200) NOT NULL, content TEXT,
    is_read TINYINT(1) DEFAULT 0, action_url VARCHAR(500),
    source_type VARCHAR(50), source_id VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知表';

CREATE INDEX IF NOT EXISTS idx_notif_user_read ON notifications(user_id, is_read, created_at);

-- ============================================================
-- V7.1 Content Project Foundation (M0)
-- ============================================================

CREATE TABLE IF NOT EXISTS content_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    tenant_type VARCHAR(20) NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    creation_mode VARCHAR(30) NOT NULL,
    source_mode VARCHAR(30) NOT NULL,
    storyboard_intent_status VARCHAR(20) NOT NULL DEFAULT 'not_decided',
    content_status VARCHAR(20) NOT NULL DEFAULT 'draft',
    production_status VARCHAR(20) NOT NULL DEFAULT 'not_started',
    market_status VARCHAR(20) NOT NULL DEFAULT 'private',
    last_stage_key VARCHAR(50),
    last_task_key VARCHAR(50),
    last_content_unit_id BIGINT,
    current_parameter_version_id BIGINT,
    legacy_script_id BIGINT,
    converted_from_project_id BIGINT,
    copied_from_project_id BIGINT,
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'active',
    adopted_version_id BIGINT,
    revision INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cp_tenant_updated (tenant_type, tenant_id, updated_at),
    INDEX idx_cp_owner_updated (owner_user_id, updated_at),
    INDEX idx_cp_owner_lifecycle_updated (owner_user_id, lifecycle_status, updated_at),
    UNIQUE INDEX uk_cp_legacy_script (legacy_script_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容项目表';

CREATE TABLE IF NOT EXISTS project_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_member UNIQUE (project_id, user_id),
    INDEX idx_pm_user_project (user_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成员表';

CREATE TABLE IF NOT EXISTS project_parameter_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    payload_json TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_parameter_version UNIQUE (project_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目参数版本表';

CREATE TABLE IF NOT EXISTS content_units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stable_key VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    unit_type VARCHAR(20) NOT NULL,
    display_no INT NOT NULL,
    title VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    current_version_id BIGINT,
    revision INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_unit_display UNIQUE (project_id, unit_type, display_no),
    INDEX idx_cu_project_display (project_id, display_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容单元表';

CREATE TABLE IF NOT EXISTS content_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    content_unit_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    content_json TEXT NOT NULL,
    plain_text TEXT,
    source VARCHAR(30) NOT NULL,
    generation_job_id BIGINT,
    content_hash VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_content_unit_version UNIQUE (content_unit_id, version_no),
    INDEX idx_cv_project_created (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容版本表';

CREATE TABLE IF NOT EXISTS artifact_dependencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_version_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_version_id BIGINT NOT NULL,
    dependency_type VARCHAR(30) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'current',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_artifact_dependency UNIQUE (source_version_id, target_version_id, dependency_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产物依赖关系表';

CREATE TABLE IF NOT EXISTS content_generation_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    job_type VARCHAR(40) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    input_snapshot_json TEXT NOT NULL,
    input_snapshot_hash VARCHAR(64) NOT NULL,
    schema_version VARCHAR(30) NOT NULL,
    model VARCHAR(100),
    prompt_version VARCHAR(50),
    estimated_credits INT NOT NULL DEFAULT 0,
    actual_credits INT NOT NULL DEFAULT 0,
    error_code VARCHAR(50),
    retry_of_job_id BIGINT,
    idempotency_key VARCHAR(120) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME,
    CONSTRAINT uk_project_job_idempotency UNIQUE (project_id, idempotency_key),
    INDEX idx_cgj_project_status (project_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容生成任务表';

CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(30) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    aggregate_revision INT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME,
    occurred_at DATETIME NOT NULL,
    published_at DATETIME,
    INDEX idx_oe_status_next (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发件箱事件表';

-- ============================================================
-- V7.1 M1: cp_storyboard_* (content-project storyboard, 避免与canvas冲突)
-- ============================================================

CREATE TABLE IF NOT EXISTS cp_storyboard_masters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    content_unit_id BIGINT NOT NULL,
    tier VARCHAR(10) NOT NULL DEFAULT 'A',
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    total_shots INT DEFAULT 0,
    estimated_duration_sec INT DEFAULT 0,
    source_version_id BIGINT NOT NULL,
    locked_by BIGINT,
    locked_at DATETIME,
    revision INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cp_sbm_project (project_id, tier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分镜Master表(V7)';

CREATE TABLE IF NOT EXISTS cp_storyboard_scenes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL,
    scene_no INT NOT NULL,
    dramatic_goal TEXT,
    beat_description TEXT,
    location_id BIGINT,
    character_ids TEXT,
    duration_sec INT DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cp_sb_scene UNIQUE (master_id, scene_no),
    INDEX idx_cp_sbs_master (master_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分镜场景表(V7)';

CREATE TABLE IF NOT EXISTS cp_storyboard_shots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    scene_id BIGINT NOT NULL,
    master_id BIGINT NOT NULL,
    shot_no INT NOT NULL,
    shot_type VARCHAR(30),
    duration_sec INT DEFAULT 0,
    description TEXT,
    camera_action TEXT,
    dialogue_ref TEXT,
    visual_ref_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'draft',
    sort_order INT NOT NULL DEFAULT 0,
    director_intention TEXT COMMENT 'B-tier: 导演意图',
    action_motivation TEXT COMMENT 'B-tier: 动作动机',
    relationship_blocking TEXT COMMENT 'B-tier: 关系调度',
    information_gap TEXT COMMENT 'B-tier: 信息差设计',
    edit_point TEXT COMMENT 'B-tier: 剪辑点',
    image_prompt TEXT COMMENT 'C-tier: 图片生成提示词',
    video_prompt TEXT COMMENT 'C-tier: 视频生成提示词',
    dub_text TEXT COMMENT 'C-tier: 配音文本',
    subtitle TEXT COMMENT 'C-tier: 字幕文本',
    failure_strategy VARCHAR(50) COMMENT 'C-tier: 失败策略(skip|retry|fallback)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cp_sb_shot UNIQUE (master_id, shot_no),
    INDEX idx_cp_sbsh_master (master_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分镜镜头表(V7)';

-- ============================================================
-- Storyboard Professional Domain (V2)
-- 独立分镜专业领域：Master、Version、Scene、Shot、6类专业模块、Job、Audit、Snapshot
-- ============================================================

CREATE TABLE IF NOT EXISTS storyboards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    content_unit_id BIGINT NOT NULL,
    source_content_version_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    purpose VARCHAR(30) NOT NULL DEFAULT 'default',
    current_draft_version_id BIGINT,
    current_locked_version_id BIGINT,
    production_status VARCHAR(30) NOT NULL DEFAULT 'not_ready',
    created_by BIGINT NOT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_source UNIQUE(project_id, content_unit_id, source_content_version_id, purpose),
    INDEX idx_sb_project (project_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分镜资产根表(V2)';

CREATE TABLE IF NOT EXISTS storyboard_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    storyboard_id BIGINT NOT NULL,
    parent_version_id BIGINT,
    source_content_version_id BIGINT NOT NULL,
    tier VARCHAR(1) NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    revision INT NOT NULL DEFAULT 0,
    schema_version INT NOT NULL DEFAULT 1,
    total_scenes INT NOT NULL DEFAULT 0,
    total_shots INT NOT NULL DEFAULT 0,
    total_duration_ms BIGINT NOT NULL DEFAULT 0,
    created_from VARCHAR(20) NOT NULL,
    locked_by BIGINT,
    locked_at DATETIME,
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_version UNIQUE(storyboard_id, tier, version_no),
    INDEX idx_sbv_master (storyboard_id, tier, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分镜版本表(V2)';

CREATE TABLE IF NOT EXISTS storyboard_version_scenes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    scene_key VARCHAR(36) NOT NULL,
    scene_no INT NOT NULL,
    title VARCHAR(255),
    dramatic_goal TEXT,
    beat_description TEXT,
    location_ref_id BIGINT,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    emotion_label VARCHAR(100),
    emotion_intensity INT,
    sort_order INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_scene_key UNIQUE(version_id, scene_key),
    CONSTRAINT uk_sb_scene_no UNIQUE(version_id, scene_no),
    INDEX idx_sbscene_version (version_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分镜场景表(V2)';

CREATE TABLE IF NOT EXISTS storyboard_version_shots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    version_id BIGINT NOT NULL,
    scene_id BIGINT NOT NULL,
    shot_key VARCHAR(36) NOT NULL,
    shot_code VARCHAR(30) NOT NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    shot_size VARCHAR(50),
    visual_description TEXT,
    lighting_atmosphere TEXT,
    character_action TEXT,
    emotion_description TEXT,
    dialogue_text TEXT,
    scene_tags_json JSON,
    sound_effect TEXT,
    reference_text TEXT,
    image_prompt LONGTEXT,
    video_motion_prompt LONGTEXT,
    scene_asset_id BIGINT,
    scene_asset_version_id BIGINT,
    scene_variant_id VARCHAR(64),
    scene_variant_version INT,
    scene_asset_snapshot JSON,
    director_intention TEXT COMMENT 'B-tier: 导演意图',
    action_motivation TEXT COMMENT 'B-tier: 动作动机',
    relationship_blocking TEXT COMMENT 'B-tier: 关系调度',
    information_gap TEXT COMMENT 'B-tier: 信息差设计',
    audio_visual_relation TEXT COMMENT 'B-tier: 声画关系',
    edit_point TEXT COMMENT 'B-tier: 剪辑点',
    dub_text TEXT COMMENT 'C-tier: 配音文本',
    subtitle_text TEXT COMMENT 'C-tier: 字幕文本',
    failure_strategy VARCHAR(30) COMMENT 'C-tier: 失败策略',
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    sort_order INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_shot_key UNIQUE(version_id, shot_key),
    CONSTRAINT uk_sb_shot_code UNIQUE(version_id, shot_code),
    INDEX idx_sbshot_version (version_id, scene_id, sort_order),
    INDEX idx_sbshot_scene_asset (scene_asset_id),
    INDEX idx_sbshot_scene_asset_version (scene_asset_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分镜镜头表(V2)';

-- 6类专业辅助表

CREATE TABLE IF NOT EXISTS storyboard_emotion_segments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    emotion_type VARCHAR(100) NOT NULL,
    shot_range VARCHAR(255) NOT NULL,
    intensity INT NOT NULL,
    core_expression TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sbemotion_version (version_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情绪节奏段表';

CREATE TABLE IF NOT EXISTS storyboard_prompt_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    template_code VARCHAR(50) NOT NULL,
    emotion_name VARCHAR(100),
    shot_refs_json JSON,
    image_prompt LONGTEXT,
    video_motion_prompt LONGTEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbprompt_code UNIQUE(version_id, template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词模板表';

CREATE TABLE IF NOT EXISTS storyboard_creative_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    rule_type VARCHAR(30) NOT NULL,
    dimension_name VARCHAR(100) NOT NULL,
    principle TEXT,
    implementation_text TEXT,
    target_refs_json JSON,
    effect_text TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sbrule_version (version_id, rule_type, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创意规则表';

CREATE TABLE IF NOT EXISTS storyboard_character_visuals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    character_ref_id BIGINT,
    character_name VARCHAR(100) NOT NULL,
    core_identity TEXT,
    daily_look TEXT,
    task_look TEXT,
    performance_anchor TEXT,
    prompt_lock LONGTEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbvisual_character UNIQUE(version_id, character_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人物视觉规范表';

CREATE TABLE IF NOT EXISTS storyboard_shot_visual_bindings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    shot_id BIGINT NOT NULL,
    character_visual_id BIGINT NOT NULL,
    application_note TEXT,
    anti_drift_requirement TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbbinding UNIQUE(version_id, shot_id, character_visual_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='镜头-人物视觉绑定表';

CREATE TABLE IF NOT EXISTS storyboard_review_issues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    issue_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    shot_id BIGINT,
    message TEXT NOT NULL,
    evidence TEXT,
    suggestion TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    resolution_note TEXT,
    resolved_by BIGINT,
    resolved_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbissue_fingerprint UNIQUE(version_id, fingerprint),
    INDEX idx_sbissue_status (version_id, status, severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核问题表';

CREATE TABLE IF NOT EXISTS storyboard_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    storyboard_id BIGINT NOT NULL,
    version_id BIGINT,
    job_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    current_stage VARCHAR(100),
    request_json LONGTEXT,
    result_json LONGTEXT,
    error_code VARCHAR(100),
    error_message TEXT,
    created_by BIGINT NOT NULL,
    started_at DATETIME,
    finished_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbjob_idem UNIQUE(project_id, job_type, idempotency_key),
    INDEX idx_sbjob_status (project_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分镜异步任务表';

CREATE TABLE IF NOT EXISTS storyboard_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id BIGINT,
    operation_id VARCHAR(100),
    before_json LONGTEXT,
    after_json LONGTEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sbaudit_version (version_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分镜审计日志表';

CREATE TABLE IF NOT EXISTS storyboard_canvas_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    storyboard_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    snapshot_type VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    parameter_version_id BIGINT,
    source_content_version_id BIGINT NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    gate_report_json LONGTEXT,
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbsnapshot_idem UNIQUE(project_id, idempotency_key),
    INDEX idx_sbsnapshot_version (version_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='画布生产快照表';

-- M1: Upload files
CREATE TABLE IF NOT EXISTS content_upload_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    parsed_text LONGTEXT,
    parse_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    error_message VARCHAR(500),
    storage_uri VARCHAR(600),
    storage_provider VARCHAR(20),
    storage_bucket VARCHAR(100),
    storage_key VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传文件表';

-- M2: Content unit hooks
CREATE TABLE IF NOT EXISTS content_unit_hooks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_unit_id BIGINT NOT NULL,
    content_version_id BIGINT,
    previous_promise TEXT,
    promise_payoff TEXT,
    opening_hook TEXT,
    mid_escalation TEXT,
    payoff_or_reversal TEXT,
    closing_hook TEXT,
    next_promise TEXT,
    hook_score DECIMAL(4,2),
    locked_fields TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_unit_hook UNIQUE (content_unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单元钩子表';

-- M2: Continuity snapshots
CREATE TABLE IF NOT EXISTS continuity_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    content_unit_id BIGINT NOT NULL,
    snapshot_json TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_unit_snapshot UNIQUE (content_unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='连续性快照表';


-- M3: Long-form worldbuilding
CREATE TABLE IF NOT EXISTS character_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL, role VARCHAR(50), archetype VARCHAR(100),
    appearance TEXT, personality TEXT, motivation TEXT, long_term_goal TEXT,
    knowledge_boundary TEXT, dialogue_style TEXT, backstory TEXT,
    relationships_json TEXT, status VARCHAR(20) DEFAULT 'draft',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色档案表';

CREATE TABLE IF NOT EXISTS plot_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    task_type VARCHAR(30) NOT NULL, title VARCHAR(200) NOT NULL,
    description TEXT, stage_goals TEXT, obstacles TEXT, cost TEXT,
    character_ids TEXT, parent_task_id BIGINT,
    status VARCHAR(20) DEFAULT 'planned', sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情节任务表';

CREATE TABLE IF NOT EXISTS volume_outlines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    volume_no INT NOT NULL, title VARCHAR(200), goal TEXT, turns TEXT,
    volume_end_hook TEXT, character_changes TEXT, chapter_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'draft', sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_volume UNIQUE (project_id, volume_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卷大纲表';

CREATE TABLE IF NOT EXISTS world_locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL, tier VARCHAR(5) NOT NULL DEFAULT 'L0',
    description TEXT, parent_location_id BIGINT, area_type VARCHAR(30),
    distance_from_origin VARCHAR(50), transportation TEXT,
    faction_territory VARCHAR(100), visual_reference TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='世界地点表';

CREATE TABLE IF NOT EXISTS story_timeline (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    event_name VARCHAR(200) NOT NULL, description TEXT,
    relative_time VARCHAR(100), involved_characters TEXT,
    location_id BIGINT, foreshadowing_ids TEXT, sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故事时间线表';

CREATE TABLE IF NOT EXISTS foreshadowing_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    description TEXT NOT NULL, planted_in_unit_id BIGINT, payoff_in_unit_id BIGINT,
    status VARCHAR(20) DEFAULT 'planted', category VARCHAR(30),
    character_ids TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='伏笔项表';

-- M4: TVC Commercial Script
CREATE TABLE IF NOT EXISTS tvc_briefs (id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT NOT NULL,brand_name VARCHAR(200),product_name VARCHAR(200),target_audience VARCHAR(500),budget VARCHAR(100),platforms VARCHAR(200),duration VARCHAR(50),additional_notes TEXT,status VARCHAR(20) DEFAULT 'draft',created_at DATETIME DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TVC简报表';
CREATE TABLE IF NOT EXISTS brand_facts (id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT NOT NULL,fact_type VARCHAR(30),content TEXT,evidence_status VARCHAR(20) DEFAULT 'unverified',evidence_url VARCHAR(500),is_must_express VARCHAR(10) DEFAULT 'yes',is_must_not_express VARCHAR(10) DEFAULT 'no',created_at DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌事实表';
CREATE TABLE IF NOT EXISTS creative_strategies (id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT NOT NULL,angle_no INT NOT NULL,angle_name VARCHAR(200),opening_hook TEXT,value_proposition TEXT,brand_memory_point TEXT,platform VARCHAR(50),status VARCHAR(20) DEFAULT 'draft',created_at DATETIME DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创意策略表';
CREATE TABLE IF NOT EXISTS tvc_scripts (id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT NOT NULL,source_unit_id BIGINT,version_name VARCHAR(100),content_json TEXT,plain_text TEXT,duration_sec INT DEFAULT 0,platforms VARCHAR(200),status VARCHAR(20) DEFAULT 'draft',source_version_id BIGINT,content_hash VARCHAR(64),created_at DATETIME DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TVC脚本表');
SET FOREIGN_KEY_CHECKS = 1;

-- M5: Quality reports
CREATE TABLE IF NOT EXISTS quality_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    canvas_project_id VARCHAR(36),
    node_uuid VARCHAR(36),
    asset_version_id BIGINT,
    correctness_score INT DEFAULT 0,
    security_score INT DEFAULT 0,
    performance_score INT DEFAULT 0,
    cost_score INT DEFAULT 0,
    consistency_score INT DEFAULT 0,
    issues_json TEXT,
    summary TEXT,
    status VARCHAR(20) DEFAULT 'open',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量审核报告表';

-- M5: Plugin packs
CREATE TABLE IF NOT EXISTS plugin_packs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    storyboard_master_id BIGINT NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    name VARCHAR(200),
    manifest_json TEXT,
    asset_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'draft',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_plugin_pack_version UNIQUE (storyboard_master_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='插件包表';

-- ============================================================
-- M6: Work editor — profiles, tag dictionary, settings, extraction
-- ============================================================

CREATE TABLE IF NOT EXISTS content_project_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE,
    genre_tag VARCHAR(50),
    plot_tags JSON,
    tone_tags JSON,
    setting_tag VARCHAR(50),
    synopsis TEXT,
    outline TEXT,
    revision INT DEFAULT 0,
    updated_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品资料表';
CREATE INDEX idx_cpp_genre ON content_project_profiles(genre_tag);
CREATE INDEX idx_cpp_setting ON content_project_profiles(setting_tag);

CREATE TABLE IF NOT EXISTS tag_dictionary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    axis VARCHAR(20) NOT NULL,
    tag_value VARCHAR(50) NOT NULL,
    tag_label VARCHAR(50) NOT NULL,
    sort_order INT DEFAULT 0,
    is_active TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_tag_dict_axis_value UNIQUE (axis, tag_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签字典表';
CREATE INDEX idx_td_axis ON tag_dictionary(axis);

CREATE TABLE IF NOT EXISTS project_setting_entities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    setting_type VARCHAR(20) NOT NULL,
    canonical_name VARCHAR(200) NOT NULL,
    aliases_json JSON,
    summary TEXT,
    details_json JSON,
    relationships_json JSON,
    status VARCHAR(20) DEFAULT 'draft',
    source_type VARCHAR(20) DEFAULT 'manual',
    current_version_no INT DEFAULT 0,
    revision INT DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    archived_at DATETIME NULL,
    archived_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_setting_entity UNIQUE (project_id, setting_type, canonical_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目设定实体表';
CREATE INDEX idx_pse_project_type ON project_setting_entities(project_id, setting_type);
CREATE INDEX idx_pse_status ON project_setting_entities(status);

CREATE TABLE IF NOT EXISTS project_setting_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    snapshot_json JSON NOT NULL,
    field_changes_json JSON,
    source_type VARCHAR(20),
    operated_by BIGINT,
    evidence_json JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_setting_version UNIQUE (entity_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目设定版本表';
CREATE INDEX idx_psv_entity ON project_setting_versions(entity_id);

CREATE TABLE IF NOT EXISTS setting_extraction_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_version_id BIGINT,
    chapter_version_ids_json JSON,
    target_setting_types JSON NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(20) DEFAULT 'queued',
    model_id VARCHAR(50),
    prompt_version VARCHAR(20),
    extraction_config_json JSON,
    error_message TEXT,
    applied_at DATETIME NULL,
    applied_by BIGINT,
    revision INT DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_extraction_idempotent UNIQUE (project_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设定提取批次表';
CREATE INDEX idx_seb_project ON setting_extraction_batches(project_id);

CREATE TABLE IF NOT EXISTS setting_extraction_candidates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    setting_type VARCHAR(20) NOT NULL,
    canonical_name VARCHAR(200) NOT NULL,
    aliases_json JSON,
    field_values_json JSON NOT NULL,
    evidence_text TEXT,
    evidence_position_json JSON,
    confidence DECIMAL(3,2),
    matched_entity_id BIGINT,
    match_reason TEXT,
    match_status VARCHAR(20) DEFAULT 'new',
    field_decisions_json JSON,
    review_status VARCHAR(20) DEFAULT 'pending',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (batch_id) REFERENCES setting_extraction_batches(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设定提取候选项表';
CREATE INDEX idx_sec_batch ON setting_extraction_candidates(batch_id);

-- ============================================================
-- P0 创作圣经基础表
-- ============================================================

CREATE TABLE IF NOT EXISTS creative_bible_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    source_version_id BIGINT NULL,
    summary VARCHAR(500) NULL,
    snapshot_json JSON NOT NULL,
    snapshot_hash VARCHAR(64) NULL,
    confirmed_by BIGINT NULL,
    confirmed_at DATETIME NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cbv_project_version UNIQUE (project_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作圣经版本表';

CREATE TABLE IF NOT EXISTS ecosystem_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    bible_version_id BIGINT NOT NULL,
    rule_type VARCHAR(40) NOT NULL,
    name VARCHAR(200) NOT NULL,
    summary TEXT NULL,
    details_json JSON NULL,
    scope_json JSON NULL,
    exceptions_json JSON NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    source_type VARCHAR(20) NOT NULL DEFAULT 'manual',
    evidence_json JSON NULL,
    revision INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生态规则表';
CREATE INDEX idx_eco_project_bible ON ecosystem_rules(project_id, bible_version_id);

CREATE TABLE IF NOT EXISTS project_writing_guides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    bible_version_id BIGINT NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    scope_id BIGINT NOT NULL DEFAULT 0,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    guide_json JSON NOT NULL,
    parent_guide_id BIGINT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'manual',
    confirmed_by BIGINT NULL,
    confirmed_at DATETIME NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pwg_scope_version UNIQUE
        (project_id, bible_version_id, scope_type, scope_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作口径表';
CREATE INDEX idx_pwg_project_scope ON project_writing_guides(project_id, scope_type, scope_id);

CREATE TABLE IF NOT EXISTS generation_context_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    generation_job_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    bible_version_id BIGINT NOT NULL,
    project_guide_id BIGINT NULL,
    character_guide_ids_json JSON NULL,
    unit_guide_id BIGINT NULL,
    selected_versions_json JSON NOT NULL,
    resolved_guide_json JSON NOT NULL,
    payload_json JSON NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gcs_job UNIQUE (generation_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成上下文快照表';
CREATE INDEX idx_gcs_project ON generation_context_snapshots(project_id);

-- ============================================================
-- V6: Trade market tables (script trading foundation)
-- ============================================================
CREATE TABLE IF NOT EXISTS trade_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
    buyer_user_id BIGINT NOT NULL,
    buyer_workspace_id VARCHAR(64) NOT NULL,
    buyer_workspace_type VARCHAR(16) NOT NULL DEFAULT 'PERSONAL',
    seller_user_id BIGINT NOT NULL,
    seller_workspace_id VARCHAR(64) NOT NULL,
    total_amount_cents BIGINT NOT NULL DEFAULT 0,
    platform_fee_cents BIGINT NOT NULL DEFAULT 0,
    seller_income_cents BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    wallet_transfer_no VARCHAR(64),
    wallet_status VARCHAR(20),
    create_idempotency_key VARCHAR(128) NOT NULL,
    expires_at DATETIME,
    paid_at DATETIME,
    fulfilled_at DATETIME,
    completed_at DATETIME,
    refunded_at DATETIME,
    row_version INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(2000),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_trade_order_no UNIQUE (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易订单表';
CREATE INDEX idx_to_buyer ON trade_orders(buyer_user_id, buyer_workspace_id);

CREATE TABLE IF NOT EXISTS trade_order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    script_version_id BIGINT NOT NULL,
    license_type VARCHAR(10) NOT NULL,
    price_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    title_snapshot VARCHAR(200),
    author_snapshot VARCHAR(100),
    tags_snapshot VARCHAR(2000),
    agreement_text TEXT,
    agreement_version VARCHAR(20),
    agreement_hash VARCHAR(64),
    historical_normal_count INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oi UNIQUE (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单项表';

CREATE TABLE IF NOT EXISTS script_entitlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    beneficiary_workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL,
    script_version_id BIGINT NOT NULL,
    license_type VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATETIME DEFAULT CURRENT_TIMESTAMP,
    effective_until DATETIME,
    max_accounts INT,
    allow_commercial TINYINT NOT NULL DEFAULT 0,
    allow_adaptation TINYINT NOT NULL DEFAULT 0,
    allow_sublicense TINYINT NOT NULL DEFAULT 0,
    territory_restriction VARCHAR(200),
    revoked_at DATETIME,
    revoke_reason VARCHAR(2000),
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_ent UNIQUE (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧本授权表';

CREATE TABLE IF NOT EXISTS purchased_script_copies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL,
    source_version_id BIGINT NOT NULL,
    content_json TEXT,
    title VARCHAR(200),
    created_by_user_id BIGINT NOT NULL,
    source_listing_id BIGINT,
    source_author_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_copy UNIQUE (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购买剧本副本表';

CREATE TABLE IF NOT EXISTS refund_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    reason_code VARCHAR(30),
    reason_text VARCHAR(2000),
    evidence_json TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reviewer_user_id BIGINT,
    review_comment VARCHAR(2000),
    reviewed_at DATETIME,
    refund_amount_cents BIGINT,
    wallet_reversal_no VARCHAR(64),
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款申请表';
CREATE INDEX idx_rf_order ON refund_requests(order_no);

CREATE TABLE IF NOT EXISTS trade_outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 10,
    next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(2000),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易Outbox事件表';
CREATE INDEX idx_toe_dispatch ON trade_outbox_events(status, next_retry_at);

CREATE TABLE IF NOT EXISTS trade_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT,
    workspace_id VARCHAR(64),
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    before_summary VARCHAR(2000),
    after_summary VARCHAR(2000),
    correlation_id VARCHAR(64),
    client_ip VARCHAR(45),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易审计日志表';

-- ============================================================
-- V7: Enterprise budget & approval projection
-- ============================================================
CREATE TABLE IF NOT EXISTS enterprise_purchase_budgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    subject_type VARCHAR(16) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    period_month VARCHAR(7) NOT NULL,
    amount_cents BIGINT NOT NULL DEFAULT 0,
    single_limit_cents BIGINT NOT NULL DEFAULT 0,
    reserved_cents BIGINT NOT NULL DEFAULT 0,
    consumed_cents BIGINT NOT NULL DEFAULT 0,
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_budget_scope UNIQUE (workspace_id, subject_type, subject_id, period_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购预算表';
CREATE INDEX idx_budget_ws ON enterprise_purchase_budgets(workspace_id);

CREATE TABLE IF NOT EXISTS enterprise_purchase_budget_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    entry_type VARCHAR(16) NOT NULL,
    amount_cents BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    wallet_transfer_no VARCHAR(64),
    idempotency_key VARCHAR(128) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_budget_entry_idem UNIQUE (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购预算流水表';
CREATE INDEX idx_budget_entry ON enterprise_purchase_budget_entries(budget_id);

CREATE TABLE IF NOT EXISTS enterprise_approval_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) DEFAULT '',
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    source_version INT NOT NULL DEFAULT 0,
    requester_user_id BIGINT NOT NULL,
    summary VARCHAR(500),
    amount_cents BIGINT,
    currency VARCHAR(3) DEFAULT 'CNY',
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    allowed_actions_json VARCHAR(2000),
    submitted_at DATETIME,
    decided_at DATETIME,
    last_event_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_approval_src UNIQUE (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一审批投影表';
CREATE INDEX idx_approval_ws ON enterprise_approval_items(workspace_id, status);

CREATE TABLE IF NOT EXISTS asset_outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME,
    CONSTRAINT uk_asset_oe_id UNIQUE (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产Outbox事件表';

-- ============================================================
-- V8: Project export approval
-- ============================================================
CREATE TABLE IF NOT EXISTS project_export_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) DEFAULT '',
    project_id BIGINT NOT NULL,
    project_version_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL,
    export_scope_json TEXT,
    export_format VARCHAR(16) DEFAULT 'PDF',
    watermark_policy VARCHAR(32),
    delivery_target VARCHAR(200),
    compliance_evidence_ref VARCHAR(200),
    content_snapshot_summary VARCHAR(2000),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    approver_user_id BIGINT,
    approver_comment VARCHAR(2000),
    approved_at DATETIME,
    export_task_id BIGINT,
    row_version INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目导出审批表';
CREATE INDEX idx_export_ws ON project_export_requests(workspace_id, status);

-- ============================================================
-- V9: Enterprise audit index
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
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_audit_evt_id UNIQUE (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业审计索引表';
CREATE INDEX idx_audit_ws ON enterprise_audit_index(workspace_id, created_at);
CREATE INDEX idx_audit_actor ON enterprise_audit_index(actor_user_id, created_at);

-- ============================================================
-- Canvas 迁移: 回填 workspace_id
-- ============================================================
UPDATE canvas_projects SET workspace_id = CONCAT('ent:', enterprise_id) WHERE enterprise_id IS NOT NULL AND workspace_id IS NULL;
UPDATE canvas_projects SET workspace_id = CONCAT('personal:', user_id) WHERE enterprise_id IS NULL AND workspace_id IS NULL;
UPDATE canvas_projects SET owner_id = user_id WHERE owner_id IS NULL;
UPDATE canvas_projects SET revision = 0 WHERE revision IS NULL;
UPDATE canvas_projects SET is_deleted = 0 WHERE is_deleted IS NULL;

-- ============================================================
-- 种子用户 (幂等: INSERT IGNORE, 密码均为 Abc@123456)
-- ============================================================
INSERT IGNORE INTO users (id, uuid, phone, email, nickname, password_hash, account_type, member_level, real_name_status, status)
VALUES (1, 'dev-admin-001', '13800000001', 'admin@aicp.com', '管理员',
        '$2a$04$YaRTTXEk1gK50gdoa8ZHKuwmTUu8REzBDEkrygu4HRWVz0LH.8agS', 'personal', 'creator', 'verified', 'active');

INSERT IGNORE INTO users (id, uuid, phone, email, nickname, password_hash, account_type, member_level, real_name_status, status)
VALUES (2, 'dev-user-002', '13800000002', 'writer@aicp.com', '编剧小李',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'personal', 'creator', 'verified', 'active');

INSERT IGNORE INTO users (id, uuid, phone, email, nickname, password_hash, account_type, member_level, real_name_status, status)
VALUES (3, 'dev-user-003', '13800000003', 'director@aicp.com', '导演小王',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'personal', 'creator', 'verified', 'active');

-- ============================================================
-- 种子剧本 - 脚本仓库 (幂等: INSERT IGNORE)
-- ============================================================
INSERT IGNORE INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, owner_type, episode_count, completed_episodes, total_words, synopsis, genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version, maturity_level, rating, review_count, sales_count)
VALUES ('scr-demo-001', 'PROJ_DEMO_001', '暗夜追光者', 1, 1, 'personal', 60, 60, 95000,
        '前刑警队长林深为追查妹妹失踪真相，卧底进入神秘组织"暗夜"。在正义与黑暗的边缘，他发现了一个惊天秘密…',
        '悬疑', '["卧底","复仇","悬疑"]', '["紧张","反转","暗黑"]', '现代', 'ai_generated', 'listed', 'v2.0', 'L2', 4.7, 128, 45);

INSERT IGNORE INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, owner_type, episode_count, completed_episodes, total_words, synopsis, genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version, maturity_level, rating, review_count, sales_count)
VALUES ('scr-demo-002', 'PROJ_DEMO_002', '星海迷航', 2, 2, 'personal', 80, 45, 72000,
        '公元2250年，人类星际移民船团在深空遭遇未知文明。年轻的领航员苏瑾被迫在人类存亡与外星文明之间做出抉择…',
        '科幻', '["星际","文明冲突","冒险"]', '["宏大","紧张","感人"]', '未来', 'ai_generated', 'listed', 'v1.5', 'L1', 4.4, 89, 32);

INSERT IGNORE INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, owner_type, episode_count, completed_episodes, total_words, synopsis, genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version, maturity_level, rating, review_count, sales_count)
VALUES ('scr-demo-003', 'PROJ_DEMO_003', '长安十二时辰之幻术师', 1, 1, 'personal', 40, 40, 68000,
        '盛唐长安，天才幻术师白鹤在朱雀大街摆摊卖艺，却卷入了一场涉及皇室秘宝的惊天阴谋。真幻交织，谁才是幕后黑手？',
        '奇幻', '["古装","探案","玄幻"]', '["奇幻","紧张","史诗"]', '古代', 'ai_generated', 'listed', 'v1.0', 'L1', 4.9, 256, 98);

INSERT IGNORE INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, owner_type, episode_count, completed_episodes, total_words, synopsis, genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version, maturity_level, rating, review_count, sales_count)
VALUES ('scr-demo-004', 'PROJ_DEMO_004', '校园奇妙物语', 3, 3, 'personal', 24, 24, 35000,
        '平凡高中生在校园角落发现一扇通往"里世界"的门，与同伴们一起在表里世界之间守护日常的奇妙冒险。',
        '奇幻', '["校园","冒险","青春"]', '["轻松","治愈","搞笑"]', '现代', 'ai_generated', 'draft', 'v0.5', 'L0', 3.8, 15, 0);

INSERT IGNORE INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, owner_type, episode_count, completed_episodes, total_words, synopsis, genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version, maturity_level, rating, review_count, sales_count)
VALUES ('scr-demo-005', 'PROJ_DEMO_005', '锦绣未央之医女倾城', 1, 1, 'personal', 52, 30, 58000,
        '现代女医生穿越古代成为落魄医女，凭借精湛医术与智慧在乱世中立足，收获爱情与事业的双重逆袭。',
        '言情', '["穿越","医术","逆袭"]', '["甜宠","励志","虐心"]', '古代', 'ai_generated', 'pending_review', 'v0.8', 'L1', 4.2, 42, 12);

-- ============================================================
-- AI 资产市场 种子数据 (幂等: INSERT IGNORE)
-- ============================================================

-- 风格模型 1: 韩漫风格 — 都市言情
INSERT IGNORE INTO workspace_assets (id, uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
VALUES (1, 'seed-style-1', 'platform_seed', 'enterprise', 1, 'STYLE_PACK', '韩漫风格 — 都市言情', '经典韩漫都市言情风格模型', '["韩漫","都市","言情"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1);
INSERT IGNORE INTO asset_versions (id, asset_id, version_number, metadata, preview_url, created_by)
VALUES (1, 1, 1, '{"style":"korean_manhwa","genre":"urban_romance","trigger_words":"korean manhwa style"}', '/assets/preview/style-1.jpg', 1);
UPDATE workspace_assets SET current_version_id = 1 WHERE id = 1 AND current_version_id IS NULL;
INSERT IGNORE INTO market_listings (id, publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
VALUES (1, 'platform_seed', 1, 1, 1, 'STYLE_PACK', '{"name":"韩漫风格 — 都市言情","description":"经典韩漫都市言情风格模型，适用于都市恋爱题材的漫画创作","tags":["韩漫","都市","言情"],"previews":["/assets/preview/style-1.jpg"],"author_name":"AI视觉师","recommended_params":{"trigger_words":"korean manhwa style","strength":0.8}}', 'FREE', 'LISTED', 2300, 4.9, 0);

-- 风格模型 2: 日系唯美 — 校园青春
INSERT IGNORE INTO workspace_assets (id, uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
VALUES (2, 'seed-style-2', 'platform_seed', 'enterprise', 1, 'STYLE_PACK', '日系唯美 — 校园青春', '日系唯美校园青春风格模型', '["日系","唯美","校园"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1);
INSERT IGNORE INTO asset_versions (id, asset_id, version_number, metadata, preview_url, created_by)
VALUES (2, 2, 1, '{"style":"japanese_aesthetic","genre":"school_life","trigger_words":"anime style, beautiful, school"}', '/assets/preview/style-2.jpg', 1);
UPDATE workspace_assets SET current_version_id = 2 WHERE id = 2 AND current_version_id IS NULL;
INSERT IGNORE INTO market_listings (id, publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
VALUES (2, 'platform_seed', 1, 2, 2, 'STYLE_PACK', '{"name":"日系唯美 — 校园青春","description":"日系唯美校园青春风格","tags":["日系","唯美","校园"],"previews":["/assets/preview/style-2.jpg"],"author_name":"二次元画师","recommended_params":{"trigger_words":"anime style, beautiful"}}', 'FREE', 'LISTED', 1800, 4.6, 0);

-- 风格模型 3: 美式写实 — 科幻冒险
INSERT IGNORE INTO workspace_assets (id, uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
VALUES (3, 'seed-style-3', 'platform_seed', 'enterprise', 1, 'STYLE_PACK', '美式写实 — 科幻冒险', '美式写实科幻冒险风格模型', '["美式","写实","科幻"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1);
INSERT IGNORE INTO asset_versions (id, asset_id, version_number, metadata, preview_url, created_by)
VALUES (3, 3, 1, '{"style":"american_realistic","genre":"sci_fi","trigger_words":"realistic modern, sci-fi"}', '/assets/preview/style-3.jpg', 1);
UPDATE workspace_assets SET current_version_id = 3 WHERE id = 3 AND current_version_id IS NULL;
INSERT IGNORE INTO market_listings (id, publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
VALUES (3, 'platform_seed', 1, 3, 3, 'STYLE_PACK', '{"name":"美式写实 — 科幻冒险","description":"美式写实科幻冒险风格","tags":["美式","写实","科幻"],"previews":["/assets/preview/style-3.jpg"],"author_name":"写实派","recommended_params":{"trigger_words":"realistic modern, sci-fi"}}', 'FREE', 'LISTED', 5100, 4.7, 0);

-- 风格模型 4: 国风古装 — 仙侠奇幻
INSERT IGNORE INTO workspace_assets (id, uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
VALUES (4, 'seed-style-4', 'platform_seed', 'enterprise', 1, 'STYLE_PACK', '国风古装 — 仙侠奇幻', '国风古装仙侠奇幻风格模型', '["国风","古装","仙侠"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1);
INSERT IGNORE INTO asset_versions (id, asset_id, version_number, metadata, preview_url, created_by)
VALUES (4, 4, 1, '{"style":"chinese_ink","genre":"xianxia","trigger_words":"ink wash painting, chinese ancient style"}', '/assets/preview/style-4.jpg', 1);
UPDATE workspace_assets SET current_version_id = 4 WHERE id = 4 AND current_version_id IS NULL;
INSERT IGNORE INTO market_listings (id, publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
VALUES (4, 'platform_seed', 1, 4, 4, 'STYLE_PACK', '{"name":"国风古装 — 仙侠奇幻","description":"国风古装仙侠奇幻风格","tags":["国风","古装","仙侠"],"previews":["/assets/preview/style-4.jpg"],"author_name":"国风画师","recommended_params":{"trigger_words":"ink wash painting, chinese ancient style"}}', 'FREE', 'LISTED', 890, 4.8, 0);

-- 角色 1
INSERT IGNORE INTO workspace_assets (id, uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
VALUES (5, 'seed-char-1', 'platform_seed', 'enterprise', 1, 'CHARACTER', '都市男主角 — 青年', '现代都市题材青年男性角色', '["角色","男性","青年","都市"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1);
INSERT IGNORE INTO asset_versions (id, asset_id, version_number, metadata, preview_url, created_by)
VALUES (5, 5, 1, '{"character_type":"protagonist","gender":"male","age":"young_adult","setting":"urban"}', '/assets/preview/char-1.jpg', 1);
UPDATE workspace_assets SET current_version_id = 5 WHERE id = 5 AND current_version_id IS NULL;
INSERT IGNORE INTO market_listings (id, publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
VALUES (5, 'platform_seed', 1, 5, 5, 'CHARACTER', '{"name":"都市男主角 — 青年","description":"现代都市题材青年男性角色资产","tags":["角色","男性","青年","都市"],"previews":["/assets/preview/char-1.jpg"],"author_name":"AI视觉师"}', 'FREE', 'LISTED', 420, 4.3, 0);

-- 角色 2
INSERT IGNORE INTO workspace_assets (id, uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
VALUES (6, 'seed-char-2', 'platform_seed', 'enterprise', 1, 'CHARACTER', '校园女主角 — 少女', '校园题材少女角色资产', '["角色","女性","少女","校园"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1);
INSERT IGNORE INTO asset_versions (id, asset_id, version_number, metadata, preview_url, created_by)
VALUES (6, 6, 1, '{"character_type":"heroine","gender":"female","age":"teen","setting":"school"}', '/assets/preview/char-2.jpg', 1);
UPDATE workspace_assets SET current_version_id = 6 WHERE id = 6 AND current_version_id IS NULL;
INSERT IGNORE INTO market_listings (id, publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
VALUES (6, 'platform_seed', 1, 6, 6, 'CHARACTER', '{"name":"校园女主角 — 少女","description":"校园题材少女角色资产","tags":["角色","女性","少女","校园"],"previews":["/assets/preview/char-2.jpg"],"author_name":"二次元画师"}', 'FREE', 'LISTED', 680, 4.5, 0);

-- 场景 1
INSERT IGNORE INTO workspace_assets (id, uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
VALUES (7, 'seed-scene-1', 'platform_seed', 'enterprise', 1, 'SCENE', '现代都市街道', '现代都市街道场景资产', '["场景","现代","都市","室外"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1);
INSERT IGNORE INTO asset_versions (id, asset_id, version_number, metadata, preview_url, created_by)
VALUES (7, 7, 1, '{"scene_type":"exterior","setting":"urban","time_of_day":"day","mood":"busy"}', '/assets/preview/scene-1.jpg', 1);
UPDATE workspace_assets SET current_version_id = 7 WHERE id = 7 AND current_version_id IS NULL;
INSERT IGNORE INTO market_listings (id, publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
VALUES (7, 'platform_seed', 1, 7, 7, 'SCENE', '{"name":"现代都市街道","description":"现代都市街道场景","tags":["场景","现代","都市","室外"],"previews":["/assets/preview/scene-1.jpg"],"author_name":"写实派"}', 'FREE', 'LISTED', 310, 4.1, 0);

-- 场景 2
INSERT IGNORE INTO workspace_assets (id, uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
VALUES (8, 'seed-scene-2', 'platform_seed', 'enterprise', 1, 'SCENE', '教室与走廊', '日系校园教室走廊场景', '["场景","校园","室内","日系"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1);
INSERT IGNORE INTO asset_versions (id, asset_id, version_number, metadata, preview_url, created_by)
VALUES (8, 8, 1, '{"scene_type":"interior","setting":"school","time_of_day":"afternoon","mood":"nostalgic"}', '/assets/preview/scene-2.jpg', 1);
UPDATE workspace_assets SET current_version_id = 8 WHERE id = 8 AND current_version_id IS NULL;
INSERT IGNORE INTO market_listings (id, publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
VALUES (8, 'platform_seed', 1, 8, 8, 'SCENE', '{"name":"教室与走廊","description":"日系校园教室走廊场景","tags":["场景","校园","室内","日系"],"previews":["/assets/preview/scene-2.jpg"],"author_name":"二次元画师"}', 'FREE', 'LISTED', 250, 4.0, 0);

-- 提示词 1
INSERT IGNORE INTO workspace_assets (id, uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
VALUES (9, 'seed-prompt-1', 'platform_seed', 'enterprise', 1, 'PROMPT', '韩漫都市对话提示词模板', '韩漫都市题材对话场景提示词', '["提示词","韩漫","对话"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1);
INSERT IGNORE INTO asset_versions (id, asset_id, version_number, metadata, preview_url, created_by)
VALUES (9, 9, 1, '{"prompt_type":"dialogue","style":"korean_manhwa","setting":"urban","tone":"romantic"}', NULL, 1);
UPDATE workspace_assets SET current_version_id = 9 WHERE id = 9 AND current_version_id IS NULL;
INSERT IGNORE INTO market_listings (id, publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
VALUES (9, 'platform_seed', 1, 9, 9, 'PROMPT', '{"name":"韩漫都市对话提示词模板","description":"韩漫都市题材对话场景提示词模板","tags":["提示词","韩漫","对话"],"previews":[],"author_name":"AI视觉师"}', 'FREE', 'LISTED', 150, 4.2, 0);

-- 提示词 2
INSERT IGNORE INTO workspace_assets (id, uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
VALUES (10, 'seed-prompt-2', 'platform_seed', 'enterprise', 1, 'PROMPT', '日系校园氛围提示词模板', '日系校园氛围场景提示词模板', '["提示词","日系","氛围"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1);
INSERT IGNORE INTO asset_versions (id, asset_id, version_number, metadata, preview_url, created_by)
VALUES (10, 10, 1, '{"prompt_type":"atmosphere","style":"japanese_aesthetic","setting":"school","tone":"nostalgic"}', NULL, 1);
UPDATE workspace_assets SET current_version_id = 10 WHERE id = 10 AND current_version_id IS NULL;
INSERT IGNORE INTO market_listings (id, publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
VALUES (10, 'platform_seed', 1, 10, 10, 'PROMPT', '{"name":"日系校园氛围提示词模板","description":"日系校园氛围场景提示词模板","tags":["提示词","日系","氛围"],"previews":[],"author_name":"二次元画师"}', 'FREE', 'LISTED', 200, 4.4, 0);

-- ============================================================
-- AA. Agent 配置中心 (M1)
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_blueprints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE COMMENT '稳定外部ID',
    role_type ENUM('HOOK','SCREENWRITER','STORYBOARD','DIRECTOR') NOT NULL COMMENT '角色类型',
    name VARCHAR(200) NOT NULL COMMENT 'Blueprint 名称',
    description VARCHAR(1000) COMMENT '能力说明',
    parameter_schema_json JSON NOT NULL COMMENT '结构化参数 JSON Schema',
    default_parameters_json JSON NOT NULL COMMENT '默认参数值',
    locked_system_prompt TEXT NOT NULL COMMENT '平台锁定 System Prompt',
    editable_prompt_template TEXT NOT NULL COMMENT '用户可编辑 Prompt 模板',
    input_schema_json JSON NOT NULL COMMENT '输入 Schema',
    output_schema_json JSON NOT NULL COMMENT '输出 Schema',
    allowed_tools_json JSON NOT NULL COMMENT '工具白名单',
    context_policy_json JSON NOT NULL COMMENT '上下文策略',
    model_policy_json JSON NOT NULL COMMENT '模型策略',
    blueprint_version INT NOT NULL DEFAULT 1 COMMENT 'Blueprint 版本号',
    status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_bp_role_version (role_type, blueprint_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 系统基础框架';

CREATE TABLE IF NOT EXISTS user_agent_definitions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE COMMENT '稳定外部ID',
    blueprint_id BIGINT NOT NULL COMMENT '所属 Blueprint',
    owner_user_id BIGINT NOT NULL COMMENT '所有者',
    current_published_version_id BIGINT COMMENT '当前发布版本',
    name VARCHAR(120) NOT NULL COMMENT 'Agent 名称',
    description VARCHAR(1000) COMMENT '用途描述',
    icon VARCHAR(500) COMMENT '图标 URL',
    applicable_genres_json JSON COMMENT '适用题材',
    platforms_json JSON COMMENT '适用平台',
    visibility ENUM('PRIVATE','TEAM') NOT NULL DEFAULT 'PRIVATE' COMMENT '可见性',
    lifecycle_status ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE' COMMENT '生命周期状态',
    row_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_def_owner_name (owner_user_id, name),
    INDEX idx_def_blueprint (blueprint_id),
    INDEX idx_def_owner (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户 Agent 定义';

CREATE TABLE IF NOT EXISTS agent_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE COMMENT '稳定外部ID',
    user_agent_id BIGINT NOT NULL COMMENT '所属 UserAgent',
    blueprint_id BIGINT NOT NULL COMMENT '所属 Blueprint',
    version_no INT NOT NULL COMMENT '版本序号',
    parameters_json JSON COMMENT '结构化业务参数',
    editable_prompt TEXT COMMENT '用户可编辑 Prompt',
    examples_json JSON COMMENT 'Few-shot 示例',
    model_policy_json JSON COMMENT '模型策略',
    status ENUM('DRAFT','PUBLISHED','ARCHIVED') NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态',
    change_summary VARCHAR(500) COMMENT '版本说明',
    content_hash VARCHAR(64) COMMENT '内容哈希',
    created_by BIGINT COMMENT '创建者',
    published_by BIGINT COMMENT '发布者',
    published_at DATETIME COMMENT '发布时间',
    row_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ver_agent_no (user_agent_id, version_no),
    INDEX idx_ver_user_agent (user_agent_id),
    INDEX idx_ver_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 版本';

CREATE TABLE IF NOT EXISTS agent_bindings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE COMMENT '稳定外部ID',
    scope_type ENUM('USER','PROJECT') NOT NULL COMMENT '作用域类型',
    scope_id VARCHAR(50) NOT NULL COMMENT '作用域 ID',
    role_type ENUM('HOOK','SCREENWRITER','STORYBOARD','DIRECTOR') NOT NULL COMMENT '角色类型',
    user_agent_id BIGINT NOT NULL COMMENT '绑定的 UserAgent',
    agent_version_id BIGINT NOT NULL COMMENT '绑定的发布版本',
    created_by BIGINT COMMENT '创建者',
    updated_by BIGINT COMMENT '更新者',
    row_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_bind_scope_role (scope_type, scope_id, role_type),
    INDEX idx_bind_version (agent_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 绑定';

CREATE TABLE IF NOT EXISTS agent_test_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE COMMENT '稳定外部ID',
    agent_version_id BIGINT NOT NULL COMMENT '测试的版本',
    input_snapshot_json JSON COMMENT '测试输入快照',
    context_snapshot_json JSON COMMENT '上下文快照',
    output_json JSON COMMENT '模型输出',
    output_schema_valid TINYINT COMMENT '输出是否通过 Schema 校验',
    model_id VARCHAR(100) COMMENT '使用的模型',
    prompt_tokens INT COMMENT 'Prompt tokens',
    completion_tokens INT COMMENT 'Completion tokens',
    credit_cost DECIMAL(10,4) COMMENT '费用',
    duration_ms INT COMMENT '耗时（毫秒）',
    status ENUM('PENDING','RUNNING','SUCCEEDED','FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '试跑状态',
    error_code VARCHAR(50) COMMENT '错误码',
    error_message VARCHAR(2000) COMMENT '错误信息',
    created_by BIGINT COMMENT '操作者',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tr_version (agent_version_id),
    INDEX idx_tr_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 试跑记录';

CREATE TABLE IF NOT EXISTS agent_execution_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE COMMENT '稳定外部ID',
    blueprint_id BIGINT NOT NULL COMMENT 'Blueprint ID',
    blueprint_version INT NOT NULL COMMENT 'Blueprint 版本号',
    user_agent_id BIGINT COMMENT 'UserAgent ID',
    agent_version_id BIGINT COMMENT 'AgentVersion ID',
    binding_source ENUM('USER','PROJECT','SYSTEM','TEMPORARY') NOT NULL COMMENT '配置来源',
    resolved_parameters_json JSON COMMENT '解析后的参数',
    temporary_overrides_json JSON COMMENT '临时覆盖参数',
    resolved_prompt TEXT COMMENT '最终编译 Prompt',
    prompt_hash VARCHAR(64) COMMENT 'Prompt 哈希',
    output_schema_version VARCHAR(50) COMMENT '输出 Schema 版本',
    project_id BIGINT COMMENT '关联项目',
    context_hash VARCHAR(64) COMMENT '上下文哈希',
    context_refs_json JSON COMMENT '上下文引用',
    business_task_type VARCHAR(50) COMMENT '业务任务类型',
    business_task_id VARCHAR(100) COMMENT '业务任务 ID',
    model_id VARCHAR(100) COMMENT '使用的模型',
    created_by BIGINT COMMENT '创建者',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_snap_version (agent_version_id),
    INDEX idx_snap_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 执行快照';

-- Blueprint 种子数据 (MySQL: INSERT IGNORE)
INSERT IGNORE INTO agent_blueprints (uuid, role_type, name, description, parameter_schema_json, default_parameters_json, locked_system_prompt, editable_prompt_template, input_schema_json, output_schema_json, allowed_tools_json, context_policy_json, model_policy_json, blueprint_version, status)
VALUES
('bp-hook-v1', 'HOOK', '钩子 Agent', '钩子生成、分析和审核',
 '{"type":"object","additionalProperties":false,"properties":{"opening_seconds":{"type":"integer","minimum":1,"maximum":10,"default":3},"hook_density":{"type":"string","enum":["low","medium","high","extreme"],"enumLabels":["低","中","高","极高"],"default":"medium"},"reversal_strength":{"type":"number","minimum":0,"maximum":1,"default":0.5},"closing_hook_strength":{"type":"string","enum":["weak","moderate","strong"],"enumLabels":["弱","中","强"],"default":"moderate"},"minimum_score":{"type":"integer","minimum":0,"maximum":100,"default":60}}}',
 '{"opening_seconds":3,"hook_density":"medium","reversal_strength":0.5,"closing_hook_strength":"moderate","minimum_score":60}',
 '平台锁定：仅生成钩子结构。你必须严格遵循以下工具权限、安全规则和输出协议。不得输出与钩子无关的内容。',
 '{{user_method}}',
 '{"type":"object","properties":{"script_excerpt":{"type":"string"}}}',
 '{"type":"object","properties":{"score":{"type":"integer"},"hooks":{"type":"array"},"analysis":{"type":"string"}}}',
 '[]',
 '{"max_context_length":16000}',
 '{"default_model":"deepseek-v3","max_tokens":4096,"temperature":{"default":0.7}}',
 1, 'ACTIVE'),

('bp-screenwriter-v1', 'SCREENWRITER', '编剧 Agent', '大纲、分集、正文生成和编剧修订',
 '{"type":"object","additionalProperties":false,"properties":{"revision_mode":{"type":"string","enum":["conservative","balanced","rewrite"],"default":"balanced"},"target_duration_seconds":{"type":"integer","minimum":30,"maximum":600,"default":180},"dialogue_density":{"type":"string","enum":["sparse","normal","dense"],"default":"normal"},"conflict_pace":{"type":"string","enum":["slow","moderate","fast","intense"],"default":"moderate"},"character_consistency":{"type":"string","enum":["loose","normal","strict"],"default":"normal"}}}',
 '{"revision_mode":"balanced","target_duration_seconds":180,"dialogue_density":"normal","conflict_pace":"moderate","character_consistency":"normal"}',
 '平台锁定：仅执行编剧任务。你必须严格遵循创作圣经和项目约束。所有输出必须符合剧本格式规范。',
 '{{user_method}}',
 '{"type":"object","properties":{"task_type":{"type":"string","enum":["outline","episode","body","revise"]},"context":{"type":"string"}}}',
 '{"type":"object","properties":{"content":{"type":"string"},"revision_summary":{"type":"string"}}}',
 '[]',
 '{"max_context_length":32000}',
 '{"default_model":"deepseek-v3","max_tokens":8192,"temperature":{"default":0.7}}',
 1, 'ACTIVE'),

('bp-storyboard-v1', 'STORYBOARD', '分镜 Agent', 'A/B/C 档分镜生成和镜头策略',
 '{"type":"object","additionalProperties":false,"properties":{"tier":{"type":"string","enum":["A","B","C"],"default":"B"},"average_shot_seconds":{"type":"number","minimum":1,"maximum":30,"default":4},"shot_density":{"type":"string","enum":["sparse","normal","dense"],"default":"normal"},"camera_complexity":{"type":"string","enum":["simple","moderate","complex"],"default":"moderate"},"continuity_level":{"type":"string","enum":["basic","standard","strict"],"default":"standard"},"production_cost_mode":{"type":"string","enum":["low","balanced","quality_first"],"default":"balanced"}}}',
 '{"tier":"B","average_shot_seconds":4,"shot_density":"normal","camera_complexity":"moderate","continuity_level":"standard","production_cost_mode":"balanced"}',
 '平台锁定：输出专业分镜结构。你必须输出符合行业标准的分镜脚本格式，包含镜头号、景别、运镜、动作、对白、时长。',
 '{{user_method}}',
 '{"type":"object","properties":{"script_text":{"type":"string"},"bible_context":{"type":"string"},"tier_override":{"type":"string","enum":["A","B","C"]}}}',
 '{"type":"object","properties":{"shots":{"type":"array"},"summary":{"type":"string"},"estimated_duration_seconds":{"type":"number"}}}',
 '[]',
 '{"max_context_length":16000}',
 '{"default_model":"deepseek-v3","max_tokens":8192,"temperature":{"default":0.6}}',
 1, 'ACTIVE'),

('bp-director-v1', 'DIRECTOR', '导演 Agent', '节奏、画面、可拍性和导演审核',
 '{"type":"object","additionalProperties":false,"properties":{"visual_style":{"type":"string","enum":["realistic","stylized","cinematic","minimalist"],"default":"cinematic"},"pacing_mode":{"type":"string","enum":["slow_burn","balanced","fast_paced","rhythmic"],"default":"balanced"},"feasibility_level":{"type":"string","enum":["strict","pragmatic","creative"],"default":"pragmatic"},"budget_mode":{"type":"string","enum":["micro","low","medium","unlimited"],"default":"medium"},"approval_threshold":{"type":"integer","minimum":50,"maximum":100,"default":70},"output_mode":{"type":"string","enum":["review_only","suggestions","patch","full_revision"],"default":"suggestions"}}}',
 '{"visual_style":"cinematic","pacing_mode":"balanced","feasibility_level":"pragmatic","budget_mode":"medium","approval_threshold":70,"output_mode":"suggestions"}',
 '平台锁定：输出导演审核结构。你必须输出评分、问题列表、严重度分级、建议和可执行修订项。不得跳过可拍性检查。',
 '{{user_method}}',
 '{"type":"object","properties":{"script_or_storyboard":{"type":"string"},"bible_context":{"type":"string"},"budget_constraints":{"type":"object"}}}',
 '{"type":"object","properties":{"overall_score":{"type":"integer"},"issues":{"type":"array"},"suggestions":{"type":"array"},"feasibility_report":{"type":"string"}}}',
 '[]',
 '{"max_context_length":32000}',
 '{"default_model":"deepseek-v3","max_tokens":8192,"temperature":{"default":0.4}}',
 1, 'ACTIVE');
