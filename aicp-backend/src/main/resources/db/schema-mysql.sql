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
    name VARCHAR(200) NOT NULL DEFAULT '未命名画布项目',
    script_id BIGINT, episode_index INT DEFAULT 1,
    style_config JSON,
    status ENUM('editing','generating','composing','exporting','completed','archived') DEFAULT 'editing',
    canvas_version INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
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
    type ENUM('image','video','audio','compose','export','quality','agent','skill') NOT NULL,
    sub_type VARCHAR(50),
    provider VARCHAR(50), model_id VARCHAR(100),
    parameters JSON,
    status ENUM('pending','running','succeeded','failed','canceled') DEFAULT 'pending',
    progress INT DEFAULT 0, credit_cost INT DEFAULT 0,
    error_code VARCHAR(50), error_message TEXT,
    output_assets JSON,
    started_at DATETIME, completed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成任务表';

-- 高频查询索引
CREATE INDEX IF NOT EXISTS idx_gen_task_project ON generation_tasks(project_id, created_at);
CREATE INDEX IF NOT EXISTS idx_gen_task_status ON generation_tasks(status, created_at);

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

-- === 7. 交易支付 (trade-svc) ===
CREATE TABLE IF NOT EXISTS script_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL, seller_id BIGINT NOT NULL,
    license_types JSON,
    status ENUM('active','sold','delisted') DEFAULT 'active',
    listed_at DATETIME DEFAULT CURRENT_TIMESTAMP, delisted_at DATETIME,
    FOREIGN KEY (script_id) REFERENCES scripts(id),
    UNIQUE KEY uk_listing_script (script_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧本上架表';

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL UNIQUE,
    buyer_id BIGINT NOT NULL, buyer_enterprise_id BIGINT,
    seller_id BIGINT NOT NULL, script_id BIGINT NOT NULL,
    license_type ENUM('normal','exclusive','buyout') NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    platform_fee DECIMAL(10,2) DEFAULT 0, seller_income DECIMAL(10,2) DEFAULT 0,
    status ENUM('pending','paid','refunded','expired') DEFAULT 'pending',
    payment_method VARCHAR(50), paid_at DATETIME, expire_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (buyer_id) REFERENCES users(id),
    FOREIGN KEY (seller_id) REFERENCES users(id),
    FOREIGN KEY (script_id) REFERENCES scripts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE IF NOT EXISTS purchase_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT NOT NULL, requester_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    license_type ENUM('normal','exclusive','buyout') NOT NULL,
    amount DECIMAL(10,2) NOT NULL, reason TEXT,
    status ENUM('pending','approved','rejected') DEFAULT 'pending',
    approver_id BIGINT, approval_note TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购申请表';

CREATE TABLE IF NOT EXISTS withdrawals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, amount DECIMAL(10,2) NOT NULL,
    status ENUM('pending','completed','rejected') DEFAULT 'pending',
    payment_method VARCHAR(50), account_info JSON,
    processed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现记录表';

-- === 8. AI资产市场 (asset-market-svc) ===
CREATE TABLE IF NOT EXISTS market_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_type ENUM('checkpoint','lora','style_pack','character','scene','prompt','voice','sound') NOT NULL,
    name VARCHAR(200) NOT NULL, author_id BIGINT NOT NULL,
    description TEXT, preview_urls JSON, tags JSON,
    price DECIMAL(10,2) DEFAULT 0, recommended_params JSON,
    use_count INT DEFAULT 0, rating DECIMAL(2,1) DEFAULT 0, review_count INT DEFAULT 0,
    status ENUM('listed','unlisted','removed') DEFAULT 'listed',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI资产市场表';

CREATE TABLE IF NOT EXISTS asset_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, asset_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (asset_id) REFERENCES market_assets(id) ON DELETE CASCADE,
    UNIQUE KEY uk_fav_user_asset (user_id, asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产收藏表';

CREATE TABLE IF NOT EXISTS asset_downloads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, asset_id BIGINT NOT NULL,
    downloaded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (asset_id) REFERENCES market_assets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产下载表';

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
    revision INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cp_tenant_updated (tenant_type, tenant_id, updated_at),
    INDEX idx_cp_owner_updated (owner_user_id, updated_at)
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
