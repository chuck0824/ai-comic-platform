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
