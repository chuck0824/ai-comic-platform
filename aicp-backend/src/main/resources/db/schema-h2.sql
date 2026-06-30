-- ============================================================
-- AICP · H2 数据库 Schema (dev 环境)
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    phone VARCHAR(20),
    email VARCHAR(100),
    wechat_openid VARCHAR(100),
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    avatar_url VARCHAR(500),
    account_type VARCHAR(20) DEFAULT 'free_user',
    real_name_status VARCHAR(20) DEFAULT 'unverified',
    member_level VARCHAR(20) DEFAULT 'free',
    member_expire_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'active',
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS canvas_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    enterprise_id BIGINT,
    workspace_id VARCHAR(64),
    name VARCHAR(200),
    script_id BIGINT,                           -- legacy: replaced by content_project_id + production_unit_id
    episode_index INT DEFAULT 1,                -- legacy: replaced by production_unit_id
    style_config VARCHAR(4000),
    applied_asset_ids VARCHAR(4000) DEFAULT '[]',
    status VARCHAR(20) DEFAULT 'editing',
    canvas_version INT DEFAULT 1,
    -- New ownership columns (2026-07-01)
    content_project_id BIGINT,
    production_unit_type VARCHAR(32),
    production_unit_id BIGINT,
    source_content_version_id BIGINT,
    source_storyboard_version_id BIGINT,
    production_snapshot TEXT,
    purpose VARCHAR(32) DEFAULT 'official',
    owner_id BIGINT,
    thumbnail_url VARCHAR(500),
    idempotency_key VARCHAR(200),
    archived_at TIMESTAMP,
    revision INT DEFAULT 0,
    is_deleted TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_canvas_idempotency ON canvas_projects(user_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_canvas_owner_status ON canvas_projects(user_id, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_canvas_content_unit ON canvas_projects(content_project_id, production_unit_id);

CREATE TABLE IF NOT EXISTS canvas_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    type VARCHAR(30) DEFAULT 'text',
    name VARCHAR(200),
    x INT DEFAULT 80, y INT DEFAULT 80, width INT DEFAULT 200, height INT DEFAULT 180,
    input_data VARCHAR(8000), output_data VARCHAR(8000),
    status VARCHAR(20) DEFAULT 'ready', group_id BIGINT, locked_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS canvas_edges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL, source_node_id BIGINT NOT NULL, target_node_id BIGINT NOT NULL,
    source_port VARCHAR(20) DEFAULT 'out', target_port VARCHAR(20) DEFAULT 'in',
    edge_type VARCHAR(20) DEFAULT 'data', metadata VARCHAR(2000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS storyboard_shots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE, project_id BIGINT NOT NULL, storyboard_id BIGINT,
    shot_no INT DEFAULT 1, scene_no INT DEFAULT 1, duration INT DEFAULT 3000,
    shot_size VARCHAR(20), camera_motion VARCHAR(50),
    visual_description VARCHAR(4000), characters VARCHAR(2000), dialogue VARCHAR(2000),
    image_prompt VARCHAR(4000), video_prompt VARCHAR(4000),
    image_status VARCHAR(20) DEFAULT 'pending', video_status VARCHAR(20) DEFAULT 'pending',
    keyframe_start VARCHAR(2000), keyframe_end VARCHAR(2000),
    image_url VARCHAR(1000), video_url VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS canvas_timelines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    data VARCHAR(16000), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS canvas_workflows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL, name VARCHAR(200), description VARCHAR(500),
    node_ids VARCHAR(4000), config VARCHAR(4000), status VARCHAR(20) DEFAULT 'draft',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workflow_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL, description VARCHAR(500), category VARCHAR(50),
    config VARCHAR(4000), variables VARCHAR(4000), visibility VARCHAR(20) DEFAULT 'private',
    usage_count INT DEFAULT 0, rating DECIMAL(3,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS canvas_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL, name VARCHAR(200), node_ids VARCHAR(4000),
    color VARCHAR(20), workflow_template_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS generation_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT, node_id BIGINT, shot_id BIGINT,
    type VARCHAR(30), sub_type VARCHAR(30), provider VARCHAR(50), model_id VARCHAR(100),
    parameters VARCHAR(8000), status VARCHAR(20) DEFAULT 'pending', progress INT DEFAULT 0,
    credit_cost INT DEFAULT 0, error_code VARCHAR(50), error_message VARCHAR(2000),
    output_assets VARCHAR(4000), started_at TIMESTAMP, completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS generation_variants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    parent_task_id BIGINT NOT NULL, variant_index INT DEFAULT 1,
    parameters VARCHAR(8000), output_url VARCHAR(1000),
    status VARCHAR(20) DEFAULT 'pending', selected TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS platform_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT, source_node_id BIGINT, source_task_id BIGINT,
    type VARCHAR(30), name VARCHAR(200), prompt VARCHAR(4000), model_id VARCHAR(100),
    owner_user_id BIGINT, maturity_level VARCHAR(10) DEFAULT 'L0',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
    type VARCHAR(50), title VARCHAR(200), content VARCHAR(2000), action_url VARCHAR(500),
    is_read TINYINT DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS gen_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id VARCHAR(50),
    gen_type VARCHAR(30),
    storyboard_tier VARCHAR(10),
    input_params VARCHAR(8000),
    output_data VARCHAR(8000),
    prompt_used VARCHAR(4000),
    model_used VARCHAR(100),
    status VARCHAR(20) DEFAULT 'pending',
    progress INT DEFAULT 0,
    tokens_used INT DEFAULT 0,
    duration_ms INT DEFAULT 0,
    error_msg VARCHAR(2000),
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL, description VARCHAR(500), content VARCHAR(16000),
    type VARCHAR(30), version VARCHAR(20) DEFAULT '1.0.0', visibility VARCHAR(20) DEFAULT 'private',
    owner_id BIGINT NOT NULL, usage_count INT DEFAULT 0, rating DECIMAL(3,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'draft',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL, skill_id BIGINT, title VARCHAR(200),
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL,
    role VARCHAR(20), content VARCHAR(16000), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    buyer_id BIGINT NOT NULL, seller_id BIGINT NOT NULL, script_id BIGINT,
    amount DECIMAL(10,2), status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- AI 资产市场 统一模型 (V2 — 替换旧 market_assets)
-- ============================================================

CREATE TABLE IF NOT EXISTS workspace_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    workspace_id VARCHAR(64) NOT NULL,
    workspace_type VARCHAR(16) NOT NULL,
    creator_user_id BIGINT NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    tags VARCHAR(1000) DEFAULT '[]',
    access_scope VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    source_type VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    source_listing_id BIGINT,
    source_version_id BIGINT,
    current_version_id BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

CREATE TABLE IF NOT EXISTS asset_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    version_number INT NOT NULL DEFAULT 1,
    metadata VARCHAR(4000),
    preview_url VARCHAR(500),
    content_ref VARCHAR(500),
    checksum VARCHAR(128),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS market_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    publisher_workspace_id VARCHAR(64) NOT NULL,
    publisher_user_id BIGINT NOT NULL,
    source_asset_id BIGINT NOT NULL,
    source_version_id BIGINT NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    public_snapshot VARCHAR(4000) NOT NULL,
    license_type VARCHAR(16) NOT NULL DEFAULT 'FREE',
    price DECIMAL(10,2) DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'LISTED',
    use_count INT DEFAULT 0,
    rating DECIMAL(2,1) DEFAULT 0,
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS asset_entitlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    beneficiary_workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL,
    source_version_id BIGINT NOT NULL,
    grant_type VARCHAR(16) NOT NULL DEFAULT 'FREE_CLAIM',
    claimed_by BIGINT,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_entitlement_workspace_listing UNIQUE (beneficiary_workspace_id, listing_id)
);

CREATE TABLE IF NOT EXISTS asset_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_favorite_user_workspace_listing UNIQUE (user_id, workspace_id, listing_id)
);

CREATE TABLE IF NOT EXISTS asset_publish_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    reviewer_id BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(500),
    review_comment VARCHAR(500),
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS asset_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    asset_version_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    target_type VARCHAR(20),
    target_id BIGINT,
    change_summary VARCHAR(500),
    previous_state VARCHAR(4000),
    undo_token_hash VARCHAR(64),
    applied_by BIGINT,
    idempotency_key VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'APPLIED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_application_workspace_key UNIQUE (workspace_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS sop_audits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL, auditor_id BIGINT, status VARCHAR(20) DEFAULT 'pending',
    result VARCHAR(4000), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS scripts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id VARCHAR(50), title VARCHAR(200) NOT NULL,
    author_user_id BIGINT NOT NULL, owner_user_id BIGINT NOT NULL,
    owner_type VARCHAR(20) DEFAULT 'personal', enterprise_id BIGINT,
    episode_count INT DEFAULT 0, completed_episodes INT DEFAULT 0,
    total_words INT DEFAULT 0, cover_image_url VARCHAR(500),
    synopsis VARCHAR(4000),
    genre_tag VARCHAR(50), plot_tags VARCHAR(2000), tone_tags VARCHAR(2000),
    setting_tag VARCHAR(50), source VARCHAR(20) DEFAULT 'ai_generated',
    status VARCHAR(20) DEFAULT 'draft', current_version VARCHAR(20) DEFAULT 'v0.1',
    maturity_level VARCHAR(20) DEFAULT 'L0', plugin_pack VARCHAR(2000),
    rating DOUBLE DEFAULT 0, review_count INT DEFAULT 0, sales_count INT DEFAULT 0,
    is_deleted TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS script_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, script_id BIGINT NOT NULL,
    version VARCHAR(20), content VARCHAR(16000), change_summary VARCHAR(500),
    created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chapter_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT,
    episode_id BIGINT,
    chapter_number INT,
    title VARCHAR(200),
    content VARCHAR(16000),
    content_format VARCHAR(30) DEFAULT 'novel',
    version_no VARCHAR(30),
    change_summary VARCHAR(500),
    source VARCHAR(30) DEFAULT 'manual_edit',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS adaptation_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT,
    source_chapter_version_id BIGINT,
    source_project_version_id BIGINT,
    target_type VARCHAR(30) DEFAULT 'ai_comic',
    version_no VARCHAR(30),
    title VARCHAR(200),
    content VARCHAR(16000),
    hook_strategy_json VARCHAR(8000),
    status VARCHAR(30) DEFAULT 'draft',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS script_episodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, script_id BIGINT NOT NULL,
    episode_number INT, title VARCHAR(200), content VARCHAR(16000),
    word_count INT DEFAULT 0, status VARCHAR(20) DEFAULT 'draft',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS repo_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, asset_id VARCHAR(50) NOT NULL UNIQUE,
    asset_type VARCHAR(30), name VARCHAR(200), description VARCHAR(500),
    owner_user_id BIGINT, maturity_level VARCHAR(10) DEFAULT 'L0',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS credit_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
    amount INT NOT NULL, type VARCHAR(30), description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- === DEV 测试数据 (每次重启自动创建) ===
-- 账号: admin  密码: admin123
INSERT INTO users (uuid, nickname, phone, password_hash, account_type, member_level, real_name_status, status)
VALUES ('dev-admin-001', '管理员', 'admin', '$2a$04$YaRTTXEk1gK50gdoa8ZHKuwmTUu8REzBDEkrygu4HRWVz0LH.8agS', 'free_user', 'creator', 'verified', 'active');

-- 脚本上传文件记录表
CREATE TABLE IF NOT EXISTS script_upload_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(500),
    file_type VARCHAR(20),
    file_size BIGINT,
    storage_path VARCHAR(500),
    parse_status VARCHAR(20) DEFAULT 'pending',
    parse_result VARCHAR(8000),
    episode_count INT,
    total_words INT,
    error_msg VARCHAR(2000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 剧本钩子表
CREATE TABLE IF NOT EXISTS episode_hooks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    episode_id BIGINT NOT NULL,
    script_id BIGINT,
    hook_type VARCHAR(20),
    content VARCHAR(4000),
    strength_score DECIMAL(3,2),
    strength_reason VARCHAR(500),
    position INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'draft',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 更新 script_episodes 添加钩子缓存列
ALTER TABLE script_episodes ADD COLUMN IF NOT EXISTS opening_hook VARCHAR(4000);
ALTER TABLE script_episodes ADD COLUMN IF NOT EXISTS closing_hook VARCHAR(4000);
ALTER TABLE script_episodes ADD COLUMN IF NOT EXISTS hook_score_avg DECIMAL(3,2);
ALTER TABLE script_episodes ADD COLUMN IF NOT EXISTS hook_count INT DEFAULT 0;

-- 每集联合审核报告表（钩子 Agent + 编导 Agent + 导演 Agent）
CREATE TABLE IF NOT EXISTS episode_review_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT,
    episode_id BIGINT,
    episode_number INT,
    overall_status VARCHAR(30),
    overall_score DECIMAL(4,2),
    hook_score DECIMAL(4,2),
    showrunner_score DECIMAL(4,2),
    director_score DECIMAL(4,2),
    report_json VARCHAR(16000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Prompt 模板表
CREATE TABLE IF NOT EXISTS prompt_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(200),
    category VARCHAR(50),
    content VARCHAR(16000),
    description VARCHAR(500),
    visibility VARCHAR(20) DEFAULT 'private',
    owner_id BIGINT NOT NULL,
    version INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'draft',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
    revision INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_member UNIQUE (project_id, user_id)
);

CREATE TABLE IF NOT EXISTS project_parameter_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    payload_json TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_parameter_version UNIQUE (project_id, version_no)
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_unit_display UNIQUE (project_id, unit_type, display_no)
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_content_unit_version UNIQUE (content_unit_id, version_no)
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_artifact_dependency UNIQUE (source_version_id, target_version_id, dependency_type)
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    CONSTRAINT uk_project_job_idempotency UNIQUE (project_id, idempotency_key)
);

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
    next_attempt_at TIMESTAMP,
    occurred_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cp_tenant_updated ON content_projects(tenant_type, tenant_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_cp_owner_updated ON content_projects(owner_user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_pm_user_project ON project_members(user_id, project_id);
CREATE INDEX IF NOT EXISTS idx_cu_project_display ON content_units(project_id, display_no);
CREATE INDEX IF NOT EXISTS idx_cv_project_created ON content_versions(project_id, created_at);
CREATE INDEX IF NOT EXISTS idx_cgj_project_status ON content_generation_jobs(project_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_oe_status_next ON outbox_events(status, next_attempt_at);

-- ============================================================
-- V7.1 M1: A-tier 分镜 (content-project storyboard)
-- 使用 cp_ 前缀避免与 canvas storyboard_shots 冲突
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
    locked_at TIMESTAMP,
    revision INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cp_sb_scene UNIQUE (master_id, scene_no)
);

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
    director_intention TEXT,
    action_motivation TEXT,
    relationship_blocking TEXT,
    information_gap TEXT,
    edit_point TEXT,
    image_prompt TEXT,
    video_prompt TEXT,
    dub_text TEXT,
    subtitle TEXT,
    failure_strategy VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cp_sb_shot UNIQUE (master_id, shot_no)
);

CREATE INDEX IF NOT EXISTS idx_cp_sbm_project ON cp_storyboard_masters(project_id, tier);
CREATE INDEX IF NOT EXISTS idx_cp_sbs_master ON cp_storyboard_scenes(master_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_cp_sbsh_master ON cp_storyboard_shots(master_id, sort_order);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_source UNIQUE(project_id, content_unit_id, source_content_version_id, purpose)
);

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
    locked_at TIMESTAMP,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_version UNIQUE(storyboard_id, tier, version_no)
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_scene_key UNIQUE(version_id, scene_key),
    CONSTRAINT uk_sb_scene_no UNIQUE(version_id, scene_no)
);

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
    scene_tags_json TEXT,
    sound_effect TEXT,
    reference_text TEXT,
    image_prompt CLOB,
    video_motion_prompt CLOB,
    director_intention TEXT,
    action_motivation TEXT,
    relationship_blocking TEXT,
    information_gap TEXT,
    audio_visual_relation TEXT,
    edit_point TEXT,
    dub_text TEXT,
    subtitle_text TEXT,
    failure_strategy VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    sort_order INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sb_shot_key UNIQUE(version_id, shot_key),
    CONSTRAINT uk_sb_shot_code UNIQUE(version_id, shot_code)
);

CREATE INDEX IF NOT EXISTS idx_sb_project ON storyboards(project_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_sbv_master ON storyboard_versions(storyboard_id, tier, version_no);
CREATE INDEX IF NOT EXISTS idx_sbscene_version ON storyboard_version_scenes(version_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_sbshot_version ON storyboard_version_shots(version_id, scene_id, sort_order);

-- 6类专业辅助表

CREATE TABLE IF NOT EXISTS storyboard_emotion_segments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    emotion_type VARCHAR(100) NOT NULL,
    shot_range VARCHAR(255) NOT NULL,
    intensity INT NOT NULL,
    core_expression TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sbemotion_version ON storyboard_emotion_segments(version_id, sort_order);

CREATE TABLE IF NOT EXISTS storyboard_prompt_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    template_code VARCHAR(50) NOT NULL,
    emotion_name VARCHAR(100),
    shot_refs_json TEXT,
    image_prompt CLOB,
    video_motion_prompt CLOB,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbprompt_code UNIQUE(version_id, template_code)
);

CREATE TABLE IF NOT EXISTS storyboard_creative_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    rule_type VARCHAR(30) NOT NULL,
    dimension_name VARCHAR(100) NOT NULL,
    principle TEXT,
    implementation_text TEXT,
    target_refs_json TEXT,
    effect_text TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sbrule_version ON storyboard_creative_rules(version_id, rule_type, sort_order);

CREATE TABLE IF NOT EXISTS storyboard_character_visuals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    character_ref_id BIGINT,
    character_name VARCHAR(100) NOT NULL,
    core_identity TEXT,
    daily_look TEXT,
    task_look TEXT,
    performance_anchor TEXT,
    prompt_lock CLOB,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbvisual_character UNIQUE(version_id, character_name)
);

CREATE TABLE IF NOT EXISTS storyboard_shot_visual_bindings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    shot_id BIGINT NOT NULL,
    character_visual_id BIGINT NOT NULL,
    application_note TEXT,
    anti_drift_requirement TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbbinding UNIQUE(version_id, shot_id, character_visual_id)
);

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
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbissue_fingerprint UNIQUE(version_id, fingerprint)
);
CREATE INDEX IF NOT EXISTS idx_sbissue_status ON storyboard_review_issues(version_id, status, severity);

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
    request_json CLOB,
    result_json CLOB,
    error_code VARCHAR(100),
    error_message TEXT,
    created_by BIGINT NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbjob_idem UNIQUE(project_id, job_type, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_sbjob_status ON storyboard_jobs(project_id, status, created_at);

CREATE TABLE IF NOT EXISTS storyboard_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id BIGINT,
    operation_id VARCHAR(100),
    before_json CLOB,
    after_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sbaudit_version ON storyboard_audit_logs(version_id, created_at);

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
    snapshot_json CLOB NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    gate_report_json CLOB,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sbsnapshot_idem UNIQUE(project_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_sbsnapshot_version ON storyboard_canvas_snapshots(version_id, created_at);

-- M2: Content unit hooks (per-unit hook analysis)
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_unit_hook UNIQUE (content_unit_id)
);

-- M2: Continuity snapshots
CREATE TABLE IF NOT EXISTS continuity_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    content_unit_id BIGINT NOT NULL,
    snapshot_json TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_unit_snapshot UNIQUE (content_unit_id)
);

-- M1: Upload files
CREATE TABLE IF NOT EXISTS content_upload_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    parsed_text TEXT,
    parse_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    error_message VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- M3: Long-form worldbuilding
-- ============================================================

CREATE TABLE IF NOT EXISTS character_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    role VARCHAR(50),
    archetype VARCHAR(100),
    appearance TEXT,
    personality TEXT,
    motivation TEXT,
    long_term_goal TEXT,
    knowledge_boundary TEXT,
    dialogue_style TEXT,
    backstory TEXT,
    relationships_json TEXT,
    status VARCHAR(20) DEFAULT 'draft',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plot_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    task_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    stage_goals TEXT,
    obstacles TEXT,
    cost TEXT,
    character_ids TEXT,
    parent_task_id BIGINT,
    status VARCHAR(20) DEFAULT 'planned',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS volume_outlines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    volume_no INT NOT NULL,
    title VARCHAR(200),
    goal TEXT,
    turns TEXT,
    volume_end_hook TEXT,
    character_changes TEXT,
    chapter_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'draft',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_volume UNIQUE (project_id, volume_no)
);

CREATE TABLE IF NOT EXISTS world_locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    tier VARCHAR(5) NOT NULL DEFAULT 'L0',
    description TEXT,
    parent_location_id BIGINT,
    area_type VARCHAR(30),
    distance_from_origin VARCHAR(50),
    transportation TEXT,
    faction_territory VARCHAR(100),
    visual_reference TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS story_timeline (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    event_name VARCHAR(200) NOT NULL,
    description TEXT,
    relative_time VARCHAR(100),
    involved_characters TEXT,
    location_id BIGINT,
    foreshadowing_ids TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS foreshadowing_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    description TEXT NOT NULL,
    planted_in_unit_id BIGINT,
    payoff_in_unit_id BIGINT,
    status VARCHAR(20) DEFAULT 'planted',
    category VARCHAR(30),
    character_ids TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- M4: TVC Commercial Script
-- ============================================================

CREATE TABLE IF NOT EXISTS tvc_briefs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    brand_name VARCHAR(200),
    product_name VARCHAR(200),
    target_audience VARCHAR(500),
    budget VARCHAR(100),
    platforms VARCHAR(200),
    duration VARCHAR(50),
    additional_notes TEXT,
    status VARCHAR(20) DEFAULT 'draft',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS brand_facts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    fact_type VARCHAR(30),
    content TEXT,
    evidence_status VARCHAR(20) DEFAULT 'unverified',
    evidence_url VARCHAR(500),
    is_must_express VARCHAR(10) DEFAULT 'yes',
    is_must_not_express VARCHAR(10) DEFAULT 'no',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS creative_strategies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    angle_no INT NOT NULL,
    angle_name VARCHAR(200),
    opening_hook TEXT,
    value_proposition TEXT,
    brand_memory_point TEXT,
    platform VARCHAR(50),
    status VARCHAR(20) DEFAULT 'draft',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tvc_scripts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_unit_id BIGINT,
    version_name VARCHAR(100),
    content_json TEXT,
    plain_text TEXT,
    duration_sec INT DEFAULT 0,
    platforms VARCHAR(200),
    status VARCHAR(20) DEFAULT 'draft',
    source_version_id BIGINT,
    content_hash VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_plugin_pack_version UNIQUE (storyboard_master_id, version_no)
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cpp_genre ON content_project_profiles(genre_tag);
CREATE INDEX IF NOT EXISTS idx_cpp_setting ON content_project_profiles(setting_tag);

CREATE TABLE IF NOT EXISTS tag_dictionary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    axis VARCHAR(20) NOT NULL,
    tag_value VARCHAR(50) NOT NULL,
    tag_label VARCHAR(50) NOT NULL,
    sort_order INT DEFAULT 0,
    is_active TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tag_dict_axis_value UNIQUE (axis, tag_value)
);
CREATE INDEX IF NOT EXISTS idx_td_axis ON tag_dictionary(axis);

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
    archived_at TIMESTAMP NULL,
    archived_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_setting_entity UNIQUE (project_id, setting_type, canonical_name, status)
);
CREATE INDEX IF NOT EXISTS idx_pse_project_type ON project_setting_entities(project_id, setting_type);
CREATE INDEX IF NOT EXISTS idx_pse_status ON project_setting_entities(status);

CREATE TABLE IF NOT EXISTS project_setting_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    snapshot_json JSON NOT NULL,
    field_changes_json JSON,
    source_type VARCHAR(20),
    operated_by BIGINT,
    evidence_json JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_setting_version UNIQUE (entity_id, version_no)
);
CREATE INDEX IF NOT EXISTS idx_psv_entity ON project_setting_versions(entity_id);

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
    applied_at TIMESTAMP NULL,
    applied_by BIGINT,
    revision INT DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_extraction_idempotent UNIQUE (project_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_seb_project ON setting_extraction_batches(project_id);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (batch_id) REFERENCES setting_extraction_batches(id)
);
CREATE INDEX IF NOT EXISTS idx_sec_batch ON setting_extraction_candidates(batch_id);

-- ============================================================
-- DEV Storyboard 专业编辑器实例数据
-- 重启后自动创建，访问 http://localhost:8080/content-projects/1/storyboards/1
-- ============================================================

INSERT INTO content_projects (uuid, tenant_type, tenant_id, owner_user_id, name, creation_mode, source_mode, storyboard_intent_status, content_status, production_status, market_status)
VALUES ('demo-proj-001', 'personal', 1, 1, '第一章·血月之夜', 'short_drama', 'ai_manual', 'in_progress', 'draft', 'not_started', 'private');

INSERT INTO project_members (project_id, user_id, role) VALUES (1, 1, 'owner');

INSERT INTO content_units (stable_key, project_id, unit_type, display_no, title, status, revision)
VALUES ('unit-001', 1, 'episode', 1, '第一集', 'draft', 0);

INSERT INTO content_versions (project_id, content_unit_id, version_no, status, content_json, plain_text, source, content_hash, created_by)
VALUES (1, 1, 1, 'locked', '{"title":"血月之夜"}', '血月之夜正文...', 'ai_generated', 'hash-001', 1);

-- 分镜 Master
INSERT INTO storyboards (uuid, project_id, content_unit_id, source_content_version_id, title, purpose, current_draft_version_id, production_status, created_by)
VALUES ('sb-demo-001', 1, 1, 1, '第一章分镜', 'default', NULL, 'not_ready', 1);

-- 分镜版本 (A档草稿, 2场景5镜头)
INSERT INTO storyboard_versions (uuid, storyboard_id, source_content_version_id, tier, version_no, status, revision, schema_version, total_scenes, total_shots, total_duration_ms, created_from, created_by)
VALUES ('sv-demo-001', 1, 1, 'A', 1, 'draft', 0, 1, 2, 5, 19500, 'manual', 1);

UPDATE storyboards SET current_draft_version_id = 1 WHERE id = 1;

-- 场景1: 月下古巷
INSERT INTO storyboard_version_scenes (version_id, scene_key, scene_no, title, dramatic_goal, beat_description, duration_ms, emotion_label, emotion_intensity, sort_order)
VALUES (1, 'scene-key-1', 1, '月下古巷', '建立世界观与主角处境', '主角在月夜小巷中独行，暗示危机降临', 10000, '紧张', 7, 0);
-- 场景2: 屋顶对峙
INSERT INTO storyboard_version_scenes (version_id, scene_key, scene_no, title, dramatic_goal, beat_description, duration_ms, emotion_label, emotion_intensity, sort_order)
VALUES (1, 'scene-key-2', 2, '屋顶对峙', '首次冲突，揭示反派动机', '主角跃上屋顶，与蒙面人对峙', 9500, '愤怒', 8, 1);

-- 场景1 镜头1: 全景入场
INSERT INTO storyboard_version_shots (uuid, version_id, scene_id, shot_key, shot_code, duration_ms, shot_size, visual_description, lighting_atmosphere, character_action, emotion_description, dialogue_text, scene_tags_json, sound_effect, reference_text, image_prompt, video_motion_prompt, status, sort_order)
VALUES ('shot-001', 1, 1, 'shot-key-1', 'S01-C01', 4000, '全景', '血月高悬，古巷石板路反射暗红光芒，主角林夜缓步走入画面，风衣下摆被夜风掀起', '顶光+逆光，血月红光为主光源，暗部冷蓝补光', '缓步前行，右手按在腰间刀柄上', '警惕、压抑的紧张感', '', '["血月","古巷","夜景"]', '风声、远处犬吠', '《银翼杀手》开场街景', 'cinematic wide shot, blood moon over ancient alley, cobblestone reflecting crimson light, lone figure in trench coat, volumetric lighting, hyperrealistic, 8k', 'slow dolly forward, wind blowing coat', 'draft', 0);

-- 场景1 镜头2: 中景警觉
INSERT INTO storyboard_version_shots (uuid, version_id, scene_id, shot_key, shot_code, duration_ms, shot_size, visual_description, lighting_atmosphere, character_action, emotion_description, dialogue_text, scene_tags_json, sound_effect, reference_text, image_prompt, video_motion_prompt, status, sort_order)
VALUES ('shot-002', 1, 1, 'shot-key-2', 'S01-C02', 3000, '中景', '主角停下脚步，侧头望向巷口阴影处，手电筒光束扫过墙角', '手电筒冷白光束与血月红光形成冷暖对比', '停下、侧头、举电筒扫视', '警觉、发现异常', '什么人在那儿？', '["血月","古巷","手电筒"]', '手电筒开关声、脚步停止', '《真探》手电筒戏', 'medium shot, detective shining flashlight into dark alley corner, fog particles in beam, cold white light vs warm red moonlight, cinematic noir style', 'static, flashlight beam sweeps slowly across frame', 'draft', 1);

-- 场景1 镜头3: 特写血迹
INSERT INTO storyboard_version_shots (uuid, version_id, scene_id, shot_key, shot_code, duration_ms, shot_size, visual_description, lighting_atmosphere, character_action, emotion_description, dialogue_text, scene_tags_json, sound_effect, reference_text, image_prompt, video_motion_prompt, status, sort_order)
VALUES ('shot-003', 1, 1, 'shot-key-3', 'S01-C03', 3000, '特写', '主角瞳孔收缩，手电光束照亮墙角一抹新鲜血迹', '电筒光束聚焦血迹，周围暗部压低', '瞳孔收缩、握紧刀柄', '震惊、确认危险', '', '["血月","古巷","血迹","特写"]', '心跳声渐强', '《七宗罪》证据发现', 'extreme close-up, eye pupils dilating, flashlight beam revealing fresh blood on stone wall, shallow depth of field, tense atmosphere', 'crash zoom to blood, handheld micro-shake', 'confirmed', 2);

-- 场景2 镜头4: 屋顶跃上
INSERT INTO storyboard_version_shots (uuid, version_id, scene_id, shot_key, shot_code, duration_ms, shot_size, visual_description, lighting_atmosphere, character_action, emotion_description, dialogue_text, scene_tags_json, sound_effect, reference_text, image_prompt, video_motion_prompt, status, sort_order)
VALUES ('shot-004', 1, 2, 'shot-key-4', 'S02-C01', 4500, '中近景', '主角跃上屋顶瓦面，月光勾勒出剪影轮廓，对面站着一个身披斗篷的蒙面人', '逆光剪影，月光银白为主，远处城市灯火星点', '翻身跃上屋顶、站稳、抬头直视对手', '决绝、毫不畏惧', '追了你三条街，该现身了。', '["屋顶","对峙","月光","蒙面人"]', '瓦片滑动声、衣袂破风声', '《卧虎藏龙》屋顶追逐', 'low angle hero shot, figure standing on curved roof tiles against full moon silhouette, cape fluttering, distant city lights, wuxia cinematic', 'camera tilts up from street to rooftop, dramatic reveal', 'draft', 0);

-- 场景2 镜头5: 摘面具
INSERT INTO storyboard_version_shots (uuid, version_id, scene_id, shot_key, shot_code, duration_ms, shot_size, visual_description, lighting_atmosphere, character_action, emotion_description, dialogue_text, scene_tags_json, sound_effect, reference_text, image_prompt, video_motion_prompt, status, sort_order)
VALUES ('shot-005', 1, 2, 'shot-key-5', 'S02-C02', 5000, '全景', '蒙面人缓缓摘下面具，露出半张机械改造的脸，月光在金属表面流动', '月光在机械面部形成高光反射，周围氛围光转为冷蓝', '摘面具、露出机械脸、嘴角微扬', '揭示、亦敌亦友的复杂', '你还记得五年前的那个夜晚吗？', '["屋顶","对峙","机械脸","月光","反转"]', '金属机械音、风声骤停', '《攻壳机动队》赛博格', 'dramatic unmasking, half cyborg face revealed under moonlight, liquid metal surface reflections, cyberpunk meets wuxia aesthetic', 'slow push-in on unmasking, depth of field racks to cyborg face', 'needs_review', 1);

-- ============================================================
-- Canvas 迁移: 回填 workspace_id
-- ============================================================
UPDATE canvas_projects SET workspace_id = CONCAT('ent:', enterprise_id) WHERE enterprise_id IS NOT NULL AND workspace_id IS NULL;
UPDATE canvas_projects SET workspace_id = CONCAT('personal:', user_id) WHERE enterprise_id IS NULL AND workspace_id IS NULL;
UPDATE canvas_projects SET owner_id = user_id WHERE owner_id IS NULL;
UPDATE canvas_projects SET revision = 0 WHERE revision IS NULL;
UPDATE canvas_projects SET is_deleted = 0 WHERE is_deleted IS NULL;

-- ============================================================
-- AI 资产市场 种子数据
-- 平台种子 Workspace: platform_seed
-- ============================================================

-- 风格模型 (4条，来自旧 Mock 数据迁移)
INSERT INTO workspace_assets (uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
SELECT 'seed-style-1', 'platform_seed', 'enterprise', 1, 'STYLE_PACK', '韩漫风格 — 都市言情', '经典韩漫都市言情风格模型', '["韩漫","都市","言情"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM workspace_assets WHERE uuid = 'seed-style-1');

INSERT INTO asset_versions (asset_id, version_number, metadata, preview_url, created_by)
SELECT 1, 1, '{"style":"korean_manhwa","genre":"urban_romance","trigger_words":"korean manhwa style"}', '/assets/preview/style-1.jpg', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_versions WHERE asset_id = 1 AND version_number = 1);

UPDATE workspace_assets SET current_version_id = 1 WHERE uuid = 'seed-style-1' AND current_version_id IS NULL;

INSERT INTO market_listings (publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
SELECT 'platform_seed', 1, 1, 1, 'STYLE_PACK', '{"name":"韩漫风格 — 都市言情","description":"经典韩漫都市言情风格模型，适用于都市恋爱题材的漫画创作","tags":["韩漫","都市","言情"],"previews":["/assets/preview/style-1.jpg"],"author_name":"AI视觉师","recommended_params":{"trigger_words":"korean manhwa style","strength":0.8}}', 'FREE', 'LISTED', 2300, 4.9, 0
WHERE NOT EXISTS (SELECT 1 FROM market_listings WHERE source_asset_id = 1 AND source_version_id = 1);

-- 风格模型 2
INSERT INTO workspace_assets (uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
SELECT 'seed-style-2', 'platform_seed', 'enterprise', 1, 'STYLE_PACK', '日系唯美 — 校园青春', '日系唯美校园风格模型', '["日系","唯美","校园"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM workspace_assets WHERE uuid = 'seed-style-2');

INSERT INTO asset_versions (asset_id, version_number, metadata, preview_url, created_by)
SELECT 2, 1, '{"style":"japanese_aesthetic","genre":"school_life","trigger_words":"anime style, beautiful, school"}', '/assets/preview/style-2.jpg', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_versions WHERE asset_id = 2 AND version_number = 1);

UPDATE workspace_assets SET current_version_id = (SELECT id FROM asset_versions WHERE asset_id = 2 AND version_number = 1) WHERE uuid = 'seed-style-2' AND current_version_id IS NULL;

INSERT INTO market_listings (publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
SELECT 'platform_seed', 1, 2, (SELECT id FROM asset_versions WHERE asset_id = 2 AND version_number = 1), 'STYLE_PACK', '{"name":"日系唯美 — 校园青春","description":"日系唯美校园青春风格","tags":["日系","唯美","校园"],"previews":["/assets/preview/style-2.jpg"],"author_name":"二次元画师","recommended_params":{"trigger_words":"anime style, beautiful"}}', 'FREE', 'LISTED', 1800, 4.6, 0
WHERE NOT EXISTS (SELECT 1 FROM market_listings WHERE source_asset_id = 2);

-- 风格模型 3
INSERT INTO workspace_assets (uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
SELECT 'seed-style-3', 'platform_seed', 'enterprise', 1, 'STYLE_PACK', '美式写实 — 科幻冒险', '美式写实科幻风格模型', '["美式","写实","科幻"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM workspace_assets WHERE uuid = 'seed-style-3');

INSERT INTO asset_versions (asset_id, version_number, metadata, preview_url, created_by)
SELECT 3, 1, '{"style":"american_realistic","genre":"sci_fi","trigger_words":"realistic modern, sci-fi"}', '/assets/preview/style-3.jpg', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_versions WHERE asset_id = 3 AND version_number = 1);

UPDATE workspace_assets SET current_version_id = (SELECT id FROM asset_versions WHERE asset_id = 3 AND version_number = 1) WHERE uuid = 'seed-style-3' AND current_version_id IS NULL;

INSERT INTO market_listings (publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
SELECT 'platform_seed', 1, 3, (SELECT id FROM asset_versions WHERE asset_id = 3 AND version_number = 1), 'STYLE_PACK', '{"name":"美式写实 — 科幻冒险","description":"美式写实科幻冒险风格","tags":["美式","写实","科幻"],"previews":["/assets/preview/style-3.jpg"],"author_name":"写实派","recommended_params":{"trigger_words":"realistic modern, sci-fi"}}', 'FREE', 'LISTED', 5100, 4.7, 0
WHERE NOT EXISTS (SELECT 1 FROM market_listings WHERE source_asset_id = 3);

-- 风格模型 4
INSERT INTO workspace_assets (uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
SELECT 'seed-style-4', 'platform_seed', 'enterprise', 1, 'STYLE_PACK', '国风古装 — 仙侠奇幻', '国风古装仙侠风格模型', '["国风","古装","仙侠"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM workspace_assets WHERE uuid = 'seed-style-4');

INSERT INTO asset_versions (asset_id, version_number, metadata, preview_url, created_by)
SELECT 4, 1, '{"style":"chinese_ink","genre":"xianxia","trigger_words":"ink wash painting, chinese ancient style"}', '/assets/preview/style-4.jpg', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_versions WHERE asset_id = 4 AND version_number = 1);

UPDATE workspace_assets SET current_version_id = (SELECT id FROM asset_versions WHERE asset_id = 4 AND version_number = 1) WHERE uuid = 'seed-style-4' AND current_version_id IS NULL;

INSERT INTO market_listings (publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
SELECT 'platform_seed', 1, 4, (SELECT id FROM asset_versions WHERE asset_id = 4 AND version_number = 1), 'STYLE_PACK', '{"name":"国风古装 — 仙侠奇幻","description":"国风古装仙侠奇幻风格","tags":["国风","古装","仙侠"],"previews":["/assets/preview/style-4.jpg"],"author_name":"国风画师","recommended_params":{"trigger_words":"ink wash painting, chinese ancient style"}}', 'FREE', 'LISTED', 890, 4.8, 0
WHERE NOT EXISTS (SELECT 1 FROM market_listings WHERE source_asset_id = 4);

-- 角色资产 (2条)
INSERT INTO workspace_assets (uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
SELECT 'seed-char-1', 'platform_seed', 'enterprise', 1, 'CHARACTER', '都市男主角 — 青年', '现代都市题材青年男性角色', '["角色","男性","青年","都市"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM workspace_assets WHERE uuid = 'seed-char-1');

INSERT INTO asset_versions (asset_id, version_number, metadata, preview_url, created_by)
SELECT 5, 1, '{"character_type":"protagonist","gender":"male","age":"young_adult","setting":"urban"}', '/assets/preview/char-1.jpg', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_versions WHERE asset_id = 5 AND version_number = 1);

UPDATE workspace_assets SET current_version_id = (SELECT id FROM asset_versions WHERE asset_id = 5 AND version_number = 1) WHERE uuid = 'seed-char-1' AND current_version_id IS NULL;

INSERT INTO market_listings (publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
SELECT 'platform_seed', 1, 5, (SELECT id FROM asset_versions WHERE asset_id = 5 AND version_number = 1), 'CHARACTER', '{"name":"都市男主角 — 青年","description":"现代都市题材青年男性角色资产","tags":["角色","男性","青年","都市"],"previews":["/assets/preview/char-1.jpg"],"author_name":"AI视觉师"}', 'FREE', 'LISTED', 420, 4.3, 0
WHERE NOT EXISTS (SELECT 1 FROM market_listings WHERE source_asset_id = 5);

INSERT INTO workspace_assets (uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
SELECT 'seed-char-2', 'platform_seed', 'enterprise', 1, 'CHARACTER', '校园女主角 — 少女', '校园题材少女角色资产', '["角色","女性","少女","校园"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM workspace_assets WHERE uuid = 'seed-char-2');

INSERT INTO asset_versions (asset_id, version_number, metadata, preview_url, created_by)
SELECT 6, 1, '{"character_type":"heroine","gender":"female","age":"teen","setting":"school"}', '/assets/preview/char-2.jpg', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_versions WHERE asset_id = 6 AND version_number = 1);

UPDATE workspace_assets SET current_version_id = (SELECT id FROM asset_versions WHERE asset_id = 6 AND version_number = 1) WHERE uuid = 'seed-char-2' AND current_version_id IS NULL;

INSERT INTO market_listings (publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
SELECT 'platform_seed', 1, 6, (SELECT id FROM asset_versions WHERE asset_id = 6 AND version_number = 1), 'CHARACTER', '{"name":"校园女主角 — 少女","description":"校园题材少女角色资产","tags":["角色","女性","少女","校园"],"previews":["/assets/preview/char-2.jpg"],"author_name":"二次元画师"}', 'FREE', 'LISTED', 680, 4.5, 0
WHERE NOT EXISTS (SELECT 1 FROM market_listings WHERE source_asset_id = 6);

-- 场景资产 (2条)
INSERT INTO workspace_assets (uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
SELECT 'seed-scene-1', 'platform_seed', 'enterprise', 1, 'SCENE', '现代都市街道', '现代都市街道场景资产', '["场景","现代","都市","室外"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM workspace_assets WHERE uuid = 'seed-scene-1');

INSERT INTO asset_versions (asset_id, version_number, metadata, preview_url, created_by)
SELECT 7, 1, '{"scene_type":"exterior","setting":"urban","time_of_day":"day","mood":"busy"}', '/assets/preview/scene-1.jpg', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_versions WHERE asset_id = 7 AND version_number = 1);

UPDATE workspace_assets SET current_version_id = (SELECT id FROM asset_versions WHERE asset_id = 7 AND version_number = 1) WHERE uuid = 'seed-scene-1' AND current_version_id IS NULL;

INSERT INTO market_listings (publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
SELECT 'platform_seed', 1, 7, (SELECT id FROM asset_versions WHERE asset_id = 7 AND version_number = 1), 'SCENE', '{"name":"现代都市街道","description":"现代都市街道场景","tags":["场景","现代","都市","室外"],"previews":["/assets/preview/scene-1.jpg"],"author_name":"写实派"}', 'FREE', 'LISTED', 310, 4.1, 0
WHERE NOT EXISTS (SELECT 1 FROM market_listings WHERE source_asset_id = 7);

INSERT INTO workspace_assets (uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
SELECT 'seed-scene-2', 'platform_seed', 'enterprise', 1, 'SCENE', '教室与走廊', '日系校园教室走廊场景', '["场景","校园","室内","日系"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM workspace_assets WHERE uuid = 'seed-scene-2');

INSERT INTO asset_versions (asset_id, version_number, metadata, preview_url, created_by)
SELECT 8, 1, '{"scene_type":"interior","setting":"school","time_of_day":"afternoon","mood":"nostalgic"}', '/assets/preview/scene-2.jpg', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_versions WHERE asset_id = 8 AND version_number = 1);

UPDATE workspace_assets SET current_version_id = (SELECT id FROM asset_versions WHERE asset_id = 8 AND version_number = 1) WHERE uuid = 'seed-scene-2' AND current_version_id IS NULL;

INSERT INTO market_listings (publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
SELECT 'platform_seed', 1, 8, (SELECT id FROM asset_versions WHERE asset_id = 8 AND version_number = 1), 'SCENE', '{"name":"教室与走廊","description":"日系校园教室走廊场景","tags":["场景","校园","室内","日系"],"previews":["/assets/preview/scene-2.jpg"],"author_name":"二次元画师"}', 'FREE', 'LISTED', 250, 4.0, 0
WHERE NOT EXISTS (SELECT 1 FROM market_listings WHERE source_asset_id = 8);

-- 提示词资产 (2条)
INSERT INTO workspace_assets (uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
SELECT 'seed-prompt-1', 'platform_seed', 'enterprise', 1, 'PROMPT', '韩漫都市对话提示词模板', '韩漫都市题材对话场景提示词', '["提示词","韩漫","对话"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM workspace_assets WHERE uuid = 'seed-prompt-1');

INSERT INTO asset_versions (asset_id, version_number, metadata, preview_url, created_by)
SELECT 9, 1, '{"prompt_type":"dialogue","style":"korean_manhwa","setting":"urban","tone":"romantic"}', null, 1
WHERE NOT EXISTS (SELECT 1 FROM asset_versions WHERE asset_id = 9 AND version_number = 1);

UPDATE workspace_assets SET current_version_id = (SELECT id FROM asset_versions WHERE asset_id = 9 AND version_number = 1) WHERE uuid = 'seed-prompt-1' AND current_version_id IS NULL;

INSERT INTO market_listings (publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
SELECT 'platform_seed', 1, 9, (SELECT id FROM asset_versions WHERE asset_id = 9 AND version_number = 1), 'PROMPT', '{"name":"韩漫都市对话提示词模板","description":"韩漫都市题材对话场景提示词模板","tags":["提示词","韩漫","对话"],"previews":[],"author_name":"AI视觉师"}', 'FREE', 'LISTED', 150, 4.2, 0
WHERE NOT EXISTS (SELECT 1 FROM market_listings WHERE source_asset_id = 9);

INSERT INTO workspace_assets (uuid, workspace_id, workspace_type, creator_user_id, asset_type, name, description, tags, access_scope, source_type, status, row_version, created_by, updated_by)
SELECT 'seed-prompt-2', 'platform_seed', 'enterprise', 1, 'PROMPT', '日系校园氛围提示词模板', '日系校园氛围场景提示词模板', '["提示词","日系","氛围"]', 'PRIVATE', 'CREATED', 'ACTIVE', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM workspace_assets WHERE uuid = 'seed-prompt-2');

INSERT INTO asset_versions (asset_id, version_number, metadata, preview_url, created_by)
SELECT 10, 1, '{"prompt_type":"atmosphere","style":"japanese_aesthetic","setting":"school","tone":"nostalgic"}', null, 1
WHERE NOT EXISTS (SELECT 1 FROM asset_versions WHERE asset_id = 10 AND version_number = 1);

UPDATE workspace_assets SET current_version_id = (SELECT id FROM asset_versions WHERE asset_id = 10 AND version_number = 1) WHERE uuid = 'seed-prompt-2' AND current_version_id IS NULL;

INSERT INTO market_listings (publisher_workspace_id, publisher_user_id, source_asset_id, source_version_id, asset_type, public_snapshot, license_type, status, use_count, rating, row_version)
SELECT 'platform_seed', 1, 10, (SELECT id FROM asset_versions WHERE asset_id = 10 AND version_number = 1), 'PROMPT', '{"name":"日系校园氛围提示词模板","description":"日系校园氛围场景提示词模板","tags":["提示词","日系","氛围"],"previews":[],"author_name":"二次元画师"}', 'FREE', 'LISTED', 200, 4.4, 0
WHERE NOT EXISTS (SELECT 1 FROM market_listings WHERE source_asset_id = 10);
