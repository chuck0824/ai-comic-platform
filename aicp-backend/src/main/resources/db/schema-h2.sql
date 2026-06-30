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
    name VARCHAR(200),
    script_id BIGINT,
    episode_index INT DEFAULT 1,
    style_config VARCHAR(4000),
    status VARCHAR(20) DEFAULT 'editing',
    canvas_version INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS market_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    type VARCHAR(30), name VARCHAR(200), description VARCHAR(500),
    price DECIMAL(10,2), owner_id BIGINT, status VARCHAR(20) DEFAULT 'listed',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
