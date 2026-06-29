-- ============================================================
-- AI漫剧中转平台 (AICP) · 数据库Schema
-- Version: 1.0 | H2 (dev) / MySQL 8.0+ (prod)
-- ============================================================
-- CREATE DATABASE / USE 仅对 MySQL 有效，H2 通过 JDBC URL 指定库名
-- ============================================================

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE COMMENT '对外暴露UUID',
    phone VARCHAR(20) UNIQUE COMMENT '手机号(加密)',
    email VARCHAR(255) UNIQUE COMMENT '邮箱(加密)',
    wechat_openid VARCHAR(128) UNIQUE COMMENT '微信OpenID',
    password_hash VARCHAR(255) COMMENT 'bcrypt密码哈希',
    nickname VARCHAR(100) NOT NULL COMMENT '昵称',
    avatar_url VARCHAR(500) COMMENT '头像URL',
    account_type ENUM('personal','enterprise') DEFAULT 'personal' COMMENT '账户类型',
    real_name_status ENUM('unverified','pending','verified') DEFAULT 'unverified' COMMENT '实名状态',
    member_level ENUM('free','creator','enterprise') DEFAULT 'free' COMMENT '会员等级',
    member_expire_at DATETIME COMMENT '会员到期时间',
    status ENUM('active','disabled','deleted') DEFAULT 'active' COMMENT '账户状态',
    last_login_at DATETIME COMMENT '最后登录时间',
    last_login_ip VARCHAR(45) COMMENT '最后登录IP',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_uuid (uuid),
    INDEX idx_phone (phone),
    INDEX idx_email (email),
    INDEX idx_status (status),
    INDEX idx_member_level (member_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================================
-- 2. 企业表
-- ============================================================
CREATE TABLE enterprises (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL COMMENT '企业管理员',
    name VARCHAR(200) NOT NULL COMMENT '企业名称',
    license_number VARCHAR(100) COMMENT '营业执照号',
    license_image_url VARCHAR(500) COMMENT '营业执照图片',
    contact_name VARCHAR(100) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    verify_status ENUM('unverified','pending','verified','rejected') DEFAULT 'unverified' COMMENT '认证状态',
    member_limit INT DEFAULT 10 COMMENT '成员上限',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_user_id) REFERENCES users(id),
    INDEX idx_verify_status (verify_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业表';

-- ============================================================
-- 3. 企业成员表
-- ============================================================
CREATE TABLE enterprise_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(50) DEFAULT 'writer' COMMENT '角色:admin/dept_head/writer/artist/editor/reviewer',
    permissions JSON COMMENT '11项细粒度权限',
    department VARCHAR(100) COMMENT '所属部门',
    purchase_budget_monthly DECIMAL(10,2) DEFAULT 0 COMMENT '月度采购预算',
    purchase_budget_single DECIMAL(10,2) DEFAULT 0 COMMENT '单笔采购上限',
    status ENUM('pending','active','disabled') DEFAULT 'pending',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (enterprise_id) REFERENCES enterprises(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uk_ent_user (enterprise_id, user_id),
    INDEX idx_enterprise_status (enterprise_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业成员表';

-- ============================================================
-- 4. 剧本表
-- ============================================================
CREATE TABLE scripts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id VARCHAR(50) COMMENT '项目统一ID',
    title VARCHAR(200) NOT NULL COMMENT '剧本名称',
    author_user_id BIGINT NOT NULL COMMENT '作者ID',
    owner_user_id BIGINT NOT NULL COMMENT '当前拥有者',
    owner_type ENUM('personal','enterprise') DEFAULT 'personal',
    enterprise_id BIGINT COMMENT '所属企业',
    episode_count INT DEFAULT 0 COMMENT '总集数',
    completed_episodes INT DEFAULT 0 COMMENT '已完成集数',
    total_words INT DEFAULT 0 COMMENT '总字数',
    cover_image_url VARCHAR(500) COMMENT '封面图',
    synopsis TEXT COMMENT '故事梗概',
    genre_tag VARCHAR(50) COMMENT '题材标签(4轴-题材)',
    plot_tags JSON COMMENT '情节标签(4轴-情节)',
    tone_tags JSON COMMENT '情绪标签(4轴-情绪)',
    setting_tag VARCHAR(50) COMMENT '时空标签(4轴-时空)',
    source ENUM('ai_generated','purchased','uploaded') DEFAULT 'ai_generated' COMMENT '剧本来源',
    status ENUM('draft','pending_review','listed','sold','delisted') DEFAULT 'draft',
    current_version VARCHAR(20) DEFAULT 'v0.1',
    is_deleted TINYINT DEFAULT 0 COMMENT '软删除标记',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (author_user_id) REFERENCES users(id),
    FOREIGN KEY (owner_user_id) REFERENCES users(id),
    INDEX idx_uuid (uuid),
    INDEX idx_author (author_user_id),
    INDEX idx_owner (owner_user_id),
    INDEX idx_status (status),
    INDEX idx_genre (genre_tag),
    INDEX idx_user_status (author_user_id, status, is_deleted),
    INDEX idx_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='剧本表';

-- ============================================================
-- 5. 剧本版本表
-- ============================================================
CREATE TABLE script_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    version VARCHAR(20) NOT NULL COMMENT '版本号',
    content LONGTEXT COMMENT '完整剧本+分镜数据JSON',
    change_summary VARCHAR(500) COMMENT '变更说明',
    created_by BIGINT COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_script_version (script_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='剧本版本表';

-- ============================================================
-- 6. 剧本分集表
-- ============================================================
CREATE TABLE script_episodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    episode_number INT NOT NULL COMMENT '集序号',
    title VARCHAR(200) COMMENT '单集标题',
    content LONGTEXT COMMENT '单集剧本内容',
    storyboard_data JSON COMMENT '分镜数据(A/B/C档)',
    storyboard_tier VARCHAR(10) COMMENT '分镜档位 A/B/C',
    word_count INT DEFAULT 0,
    opening_hook TEXT COMMENT '开场钩子',
    closing_hook TEXT COMMENT '结尾悬念',
    hook_score_avg DECIMAL(3,2) COMMENT '钩子平均分',
    hook_count INT DEFAULT 0 COMMENT '钩子数量',
    status ENUM('draft','completed') DEFAULT 'draft',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    UNIQUE KEY uk_script_ep (script_id, episode_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='剧本分集表';

CREATE TABLE episode_review_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT,
    episode_id BIGINT,
    episode_number INT,
    overall_status ENUM('pass','needs_revision','approved') DEFAULT 'needs_revision',
    overall_score DECIMAL(4,2),
    hook_score DECIMAL(4,2),
    showrunner_score DECIMAL(4,2),
    director_score DECIMAL(4,2),
    report_json JSON COMMENT '钩子/编导/导演联合审核报告',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    FOREIGN KEY (episode_id) REFERENCES script_episodes(id) ON DELETE CASCADE,
    INDEX idx_episode_review (episode_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每集联合审核报告表';

CREATE TABLE chapter_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT,
    episode_id BIGINT,
    chapter_number INT COMMENT '章节/集序号',
    title VARCHAR(200),
    content LONGTEXT COMMENT '单章正文版本内容',
    content_format VARCHAR(30) DEFAULT 'novel' COMMENT 'novel/screenplay/tvc',
    version_no VARCHAR(30),
    change_summary VARCHAR(500),
    source VARCHAR(30) DEFAULT 'manual_edit',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    FOREIGN KEY (episode_id) REFERENCES script_episodes(id) ON DELETE CASCADE,
    INDEX idx_chapter_version (episode_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单章正文版本表';

CREATE TABLE adaptation_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT,
    source_chapter_version_id BIGINT,
    source_project_version_id BIGINT,
    target_type ENUM('ai_comic','short_drama','web_drama','tvc') DEFAULT 'ai_comic',
    version_no VARCHAR(30),
    title VARCHAR(200),
    content LONGTEXT COMMENT '改编脚本文本或结构化JSON',
    hook_strategy_json JSON COMMENT '继承和重构后的钩子策略',
    status ENUM('draft','needs_sync','reviewing','locked') DEFAULT 'draft',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    FOREIGN KEY (source_chapter_version_id) REFERENCES chapter_versions(id) ON DELETE SET NULL,
    INDEX idx_adaptation_script (script_id, target_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='源头文本改编脚本版本表';

-- ============================================================
-- 7. 资产表
-- ============================================================
CREATE TABLE assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id VARCHAR(50) NOT NULL UNIQUE COMMENT '统一资产ID(CH_xxx/LOC_xxx/PROP_xxx)',
    asset_type ENUM('character','scene','prop','voice','style') NOT NULL,
    name VARCHAR(200) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    enterprise_id BIGINT COMMENT '所属企业(企业共享)',
    maturity_level ENUM('L0','L1','L2','L3','L4') DEFAULT 'L0' COMMENT '资产成熟度',
    is_locked TINYINT DEFAULT 0 COMMENT '是否锁定',
    face_id VARCHAR(50) COMMENT '角色面部ID',
    costume_id VARCHAR(50) COMMENT '角色服装ID',
    voice_id VARCHAR(50) COMMENT '角色声音ID',
    location_id VARCHAR(50) COMMENT '场景位置ID',
    description TEXT COMMENT '文字描述',
    short_anchor VARCHAR(500) COMMENT '短锚点描述',
    long_anchor TEXT COMMENT '详细锚点描述',
    reference_image_urls JSON COMMENT '参考图URL列表',
    consistency_prompt TEXT COMMENT '一致性提示词',
    seed_value BIGINT COMMENT '固定种子值',
    metadata JSON COMMENT '扩展元数据',
    is_public TINYINT DEFAULT 0 COMMENT '是否公开可上架',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_user_id) REFERENCES users(id),
    INDEX idx_asset_id (asset_id),
    INDEX idx_owner (owner_user_id),
    INDEX idx_maturity (maturity_level),
    INDEX idx_type (asset_type),
    INDEX idx_owner_type (owner_user_id, asset_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产表';

-- ============================================================
-- 8. AI资产市场商品表
-- ============================================================
CREATE TABLE asset_market_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_type ENUM('checkpoint','lora','style_pack','character','scene','prompt','voice','bgm','sfx') NOT NULL,
    name VARCHAR(200) NOT NULL,
    author_user_id BIGINT NOT NULL,
    preview_urls JSON COMMENT '预览图/试听URL列表',
    tags JSON COMMENT '分类标签',
    description TEXT COMMENT '详细描述',
    price DECIMAL(10,2) DEFAULT 0 COMMENT '价格(0=免费)',
    recommended_params JSON COMMENT '推荐参数配置',
    download_count INT DEFAULT 0,
    use_count INT DEFAULT 0,
    rating DECIMAL(3,2) DEFAULT 0 COMMENT '评分1-5',
    status ENUM('listed','delisted') DEFAULT 'listed',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (author_user_id) REFERENCES users(id),
    INDEX idx_type (asset_type),
    INDEX idx_status (status),
    INDEX idx_rating (rating DESC),
    INDEX idx_type_status (asset_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI资产市场商品表';

-- ============================================================
-- 9. 订单表
-- ============================================================
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL UNIQUE COMMENT '订单号',
    buyer_user_id BIGINT NOT NULL,
    buyer_enterprise_id BIGINT COMMENT '买家企业',
    seller_user_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    license_type ENUM('normal','exclusive','buyout') NOT NULL COMMENT '授权类型',
    amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    platform_fee DECIMAL(10,2) DEFAULT 0 COMMENT '平台手续费',
    seller_income DECIMAL(10,2) DEFAULT 0 COMMENT '卖家收入',
    status ENUM('pending','paid','completed','refunded','cancelled') DEFAULT 'pending',
    payment_method ENUM('wechat','alipay') COMMENT '支付方式',
    paid_at DATETIME,
    expire_at DATETIME COMMENT '订单过期时间(15分钟)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (buyer_user_id) REFERENCES users(id),
    FOREIGN KEY (seller_user_id) REFERENCES users(id),
    FOREIGN KEY (script_id) REFERENCES scripts(id),
    INDEX idx_order_no (order_no),
    INDEX idx_buyer (buyer_user_id),
    INDEX idx_seller (seller_user_id),
    INDEX idx_status (status),
    INDEX idx_buyer_status (buyer_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ============================================================
-- 10. 企业采购申请表
-- ============================================================
CREATE TABLE enterprise_purchase_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL COMMENT '申请人',
    script_id BIGINT NOT NULL,
    license_type ENUM('normal','exclusive','buyout') NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    reason VARCHAR(500) COMMENT '申请理由',
    budget_remaining DECIMAL(10,2) COMMENT '采购后剩余预算',
    status ENUM('pending','approved','rejected','cancelled') DEFAULT 'pending',
    approver_user_id BIGINT COMMENT '审批人',
    approval_note VARCHAR(500) COMMENT '审批意见',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (enterprise_id) REFERENCES enterprises(id),
    FOREIGN KEY (requester_user_id) REFERENCES users(id),
    FOREIGN KEY (script_id) REFERENCES scripts(id),
    FOREIGN KEY (approver_user_id) REFERENCES users(id),
    INDEX idx_enterprise_status (enterprise_id, status),
    INDEX idx_requester (requester_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业采购申请表';

-- ============================================================
-- 11. 画布项目表
-- ============================================================
CREATE TABLE canvas_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL COMMENT '项目名称',
    script_id BIGINT COMMENT '关联剧本',
    episode_index INT DEFAULT 1 COMMENT '当前编辑集数',
    user_id BIGINT NOT NULL COMMENT '创建者',
    style_config JSON COMMENT '风格配置(style_id/aspect_ratio/resolution/fps)',
    canvas_state LONGTEXT COMMENT '画布完整状态JSON(shots+timeline)',
    status ENUM('editing','composing','exporting','completed') DEFAULT 'editing',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES scripts(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_uuid (uuid),
    INDEX idx_user (user_id),
    INDEX idx_script (script_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='画布项目表';

-- ============================================================
-- 12. AI生成任务表
-- ============================================================
CREATE TABLE gen_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    script_id BIGINT COMMENT '关联剧本',
    gen_type ENUM('topic','synopsis','outline','episode','adaptation','storyboard','promotion','quick') NOT NULL,
    storyboard_tier ENUM('A','B','C') COMMENT '分镜档位',
    input_params JSON COMMENT '输入参数',
    output_data JSON COMMENT '生成结果',
    prompt_used TEXT COMMENT '使用的Prompt',
    model_used VARCHAR(100) COMMENT '使用的模型',
    status ENUM('pending','processing','completed','failed','cancelled') DEFAULT 'pending',
    progress INT DEFAULT 0 COMMENT '进度0-100',
    tokens_used INT DEFAULT 0,
    duration_ms INT DEFAULT 0 COMMENT '耗时毫秒',
    error_msg TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_status (user_id, status),
    INDEX idx_script (script_id),
    INDEX idx_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI生成任务表';

-- ============================================================
-- 13. 导出任务表
-- ============================================================
CREATE TABLE export_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    canvas_project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    params JSON COMMENT '导出参数',
    status ENUM('pending','processing','completed','failed') DEFAULT 'pending',
    progress INT DEFAULT 0,
    download_url VARCHAR(500) COMMENT '下载URL(签名)',
    file_info JSON COMMENT '文件信息(name/size/duration/resolution/codec)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    FOREIGN KEY (canvas_project_id) REFERENCES canvas_projects(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导出任务表';

-- ============================================================
-- 14. 通知表
-- ============================================================
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL COMMENT '通知类型',
    title VARCHAR(200) NOT NULL,
    content TEXT COMMENT '通知内容',
    target_url VARCHAR(500) COMMENT '点击跳转URL',
    is_read TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_read (user_id, is_read),
    INDEX idx_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';

-- ============================================================
-- 15. 审计记录表
-- ============================================================
CREATE TABLE audit_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL COMMENT '项目ID',
    episode_id VARCHAR(20) COMMENT '集ID',
    shot_id VARCHAR(50) COMMENT '镜头ID',
    check_item VARCHAR(100) COMMENT '检查项',
    issue_type VARCHAR(100) COMMENT '问题类型',
    severity ENUM('P0','P1','P2','P3') NOT NULL DEFAULT 'P2' COMMENT '严重等级',
    quality_grade ENUM('S','A','B','C','D') DEFAULT 'B' COMMENT '质量等级',
    description TEXT COMMENT '问题描述',
    fix_suggestion TEXT COMMENT '修复建议',
    responsible_role VARCHAR(50) COMMENT '责任岗位',
    status ENUM('open','fixing','fixed','verified') DEFAULT 'open',
    created_by BIGINT COMMENT '提交人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_project (project_id),
    INDEX idx_severity (severity),
    INDEX idx_status (status),
    INDEX idx_project_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计记录表';

-- ============================================================
-- 16. API Key表
-- ============================================================
CREATE TABLE user_api_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT 'Key名称',
    api_key VARCHAR(100) NOT NULL UNIQUE,
    api_secret VARCHAR(100) NOT NULL COMMENT 'HMAC签名密钥',
    scopes JSON COMMENT '权限范围',
    ip_whitelist JSON COMMENT 'IP白名单',
    is_active TINYINT DEFAULT 1,
    last_used_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user (user_id),
    INDEX idx_key (api_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Key表';

-- ============================================================
-- 17. 通知偏好表
-- ============================================================
CREATE TABLE notification_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    preferences JSON NOT NULL COMMENT '各类通知渠道偏好',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知偏好表';

-- ============================================================
-- 18. 画布节点表 (V1.2 — LibTV 无限画布)
-- ============================================================
CREATE TABLE canvas_nodes (
    id VARCHAR(50) PRIMARY KEY COMMENT '节点ID',
    project_id VARCHAR(50) NOT NULL COMMENT '所属画布项目',
    type ENUM('text','image','video','audio','script') NOT NULL COMMENT '节点类型',
    label VARCHAR(200) COMMENT '节点标题',
    x INT DEFAULT 0 COMMENT 'X坐标',
    y INT DEFAULT 0 COMMENT 'Y坐标',
    width INT DEFAULT 200 COMMENT '宽度',
    height INT DEFAULT 150 COMMENT '高度',
    data JSON COMMENT '节点数据(分镜表/生成参数/提示词等)',
    style_config JSON COMMENT '样式配置',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project (project_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='画布节点表';

-- ============================================================
-- 19. 画布连线表 (V1.2)
-- ============================================================
CREATE TABLE canvas_connections (
    id VARCHAR(50) PRIMARY KEY COMMENT '连线ID',
    project_id VARCHAR(50) NOT NULL COMMENT '所属画布项目',
    source_node_id VARCHAR(50) NOT NULL COMMENT '源节点',
    source_port ENUM('out') DEFAULT 'out' COMMENT '源端口',
    target_node_id VARCHAR(50) NOT NULL COMMENT '目标节点',
    target_port ENUM('in') DEFAULT 'in' COMMENT '目标端口',
    metadata JSON COMMENT '连线元数据',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_node_id) REFERENCES canvas_nodes(id) ON DELETE CASCADE,
    FOREIGN KEY (target_node_id) REFERENCES canvas_nodes(id) ON DELETE CASCADE,
    INDEX idx_project (project_id),
    UNIQUE KEY uk_connection (source_node_id, target_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='画布连线表';

-- ============================================================
-- 20. 画布工作流模板表 (V1.2)
-- ============================================================
CREATE TABLE canvas_workflows (
    id VARCHAR(50) PRIMARY KEY COMMENT '工作流ID',
    user_id BIGINT NOT NULL COMMENT '创建者',
    name VARCHAR(200) NOT NULL COMMENT '工作流名称',
    description VARCHAR(500) COMMENT '工作流描述',
    nodes_snapshot JSON COMMENT '节点快照',
    connections_snapshot JSON COMMENT '连线快照',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='画布工作流模板表';

-- ============================================================
-- 种子数据
-- ============================================================

-- 测试用户 (密码都是 Abc@123456, bcrypt hash)
INSERT INTO users (uuid, phone, email, nickname, password_hash, account_type, member_level, real_name_status)
VALUES
('usr_a1b2c3d4e5f6', '13800000001', 'creator@aicp.com', '创作者小明',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'personal', 'creator', 'verified'),
('usr_b2c3d4e5f6a1', '13800000002', 'buyer@aicp.com', '漫剧达人',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'personal', 'free', 'unverified');

-- 测试剧本
INSERT INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, episode_count, total_words, synopsis,
  genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version)
VALUES
('scr_abc123def456', 'PROJ_DOMINEERING_PRESIDENT', '霸道总裁的替身新娘', 1, 1, 40, 58000,
 '她是被家族抛弃的私生女，意外成为权势滔天的商业帝王唯一在意的女人…',
 '言情', '["重生", "先婚后爱"]', '["甜宠", "打脸", "爽文"]', '现代', 'ai_generated', 'draft', 'v0.1'),
('scr_def456abc123', 'PROJ_REBIRTH_EMPIRE', '重生之商业帝国', 1, 2, 80, 120000,
 '商业大亨被害身亡，重生回到20年前，这一世他要改写一切…',
 '言情', '["重生", "权谋"]', '["爽文", "逆袭"]', '现代', 'ai_generated', 'listed', 'v1.0');

-- 测试资产
INSERT INTO assets (asset_id, asset_type, name, owner_user_id, maturity_level, is_locked,
  face_id, costume_id, voice_id, description, short_anchor, consistency_prompt, seed_value)
VALUES
('CH_LIN', 'character', '林默 (男主)', 1, 'L2', 0, 'FACE_LIN_V01', 'CST_LIN_SUIT_V01', 'VOICE_LIN_V01',
 '28岁，林氏集团总裁，冷面霸道，身高185cm', '冷峻面容，剑眉星目，黑色短发，穿定制深色西装',
 'Chinese male CEO, sharp jawline, short black hair, tailored dark suit, cold expression', 42424242),
('LOC_OFFICE', 'scene', '总裁办公室', 1, 'L1', 0, NULL, NULL, NULL, NULL,
 '宽敞明亮的顶层办公室，落地窗俯瞰城市天际线，红木办公桌', 'Modern CEO office, floor-to-ceiling windows, mahogany desk, city skyline view', NULL);

-- 测试画布项目
INSERT INTO canvas_projects (uuid, name, script_id, episode_index, user_id,
  style_config, canvas_state, status)
VALUES
('canvas_a1b2c3', '霸道总裁的替身新娘 - 画布项目', 1, 1, 1,
 '{"style_id":"STYLE_KMANGA","aspect_ratio":"9:16","resolution":"1080p","fps":25}',
 '{"version":1,"shots":[]}', 'editing');

-- 测试订单
INSERT INTO orders (order_no, buyer_user_id, seller_user_id, script_id, license_type,
  amount, platform_fee, seller_income, status, payment_method, paid_at)
VALUES
('ORD20260608153000001', 2, 1, 2, 'normal', 29.90, 5.98, 23.92, 'paid', 'wechat', '2026-06-08 15:32:00');

-- 测试通知偏好
INSERT INTO notification_preferences (user_id, preferences) VALUES
(1, '{"script_generated":{"in_app":true,"email":false,"push":true},"order_paid":{"in_app":true,"email":true,"sms":true},"export_completed":{"in_app":true,"email":true,"push":true}}');

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cp_sb_shot UNIQUE (master_id, shot_no)
);

CREATE INDEX IF NOT EXISTS idx_cp_sbm_project ON cp_storyboard_masters(project_id, tier);
CREATE INDEX IF NOT EXISTS idx_cp_sbs_master ON cp_storyboard_scenes(master_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_cp_sbsh_master ON cp_storyboard_shots(master_id, sort_order);
