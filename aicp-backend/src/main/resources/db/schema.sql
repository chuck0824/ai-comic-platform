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
    maturity_level ENUM('L0','L1','L2','L3','L4') DEFAULT 'L0' COMMENT '资产成熟度',
    plugin_pack JSON COMMENT '插件包配置',
    rating DECIMAL(2,1) DEFAULT 0 COMMENT '评分1-5',
    review_count INT DEFAULT 0 COMMENT '评论数',
    sales_count INT DEFAULT 0 COMMENT '销量',
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
-- 9. Script Trading Market (V6 — unified trade domain)
-- All money in BIGINT cents. Currency is CNY.
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='剧本上架表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='授权选项表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易订单表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='授权凭证表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='已购剧本副本表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业采购申请表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款申请表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易Outbox事件表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易审计日志表';

-- ============================================================
-- 11. 画布项目表
-- ============================================================
CREATE TABLE canvas_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL COMMENT '项目名称',
    user_id BIGINT NOT NULL COMMENT '创建者',
    enterprise_id BIGINT COMMENT '企业ID',
    workspace_id VARCHAR(64) COMMENT '工作区ID',
    script_id BIGINT COMMENT 'legacy: replaced by content_project_id + production_unit_id',
    episode_index INT DEFAULT 1 COMMENT 'legacy: replaced by production_unit_id',
    style_config JSON COMMENT '风格配置(style_id/aspect_ratio/resolution/fps)',
    applied_asset_ids JSON DEFAULT ('[]') COMMENT '已应用资产ID列表',
    status ENUM('editing','generating','composing','exporting','completed','archived') DEFAULT 'editing',
    canvas_version INT DEFAULT 1 COMMENT '画布版本号',
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
    INDEX idx_uuid (uuid),
    INDEX idx_user (user_id),
    INDEX idx_script (script_id),
    INDEX idx_canvas_owner_status (user_id, status, updated_at),
    INDEX idx_canvas_content_unit (content_project_id, production_unit_id)
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

-- 测试剧本 (幂等: INSERT IGNORE, 覆盖5种状态 × 4种题材)
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
VALUES ('scr-demo-004', 'PROJ_DEMO_004', '校园奇妙物语', 2, 2, 'personal', 24, 24, 35000,
        '平凡高中生在校园角落发现一扇通往"里世界"的门，与同伴们一起在表里世界之间守护日常的奇妙冒险。',
        '奇幻', '["校园","冒险","青春"]', '["轻松","治愈","搞笑"]', '现代', 'ai_generated', 'draft', 'v0.5', 'L0', 3.8, 15, 0);

INSERT IGNORE INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, owner_type, episode_count, completed_episodes, total_words, synopsis, genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version, maturity_level, rating, review_count, sales_count)
VALUES ('scr-demo-005', 'PROJ_DEMO_005', '锦绣未央之医女倾城', 1, 1, 'personal', 52, 30, 58000,
        '现代女医生穿越古代成为落魄医女，凭借精湛医术与智慧在乱世中立足，收获爱情与事业的双重逆袭。',
        '言情', '["穿越","医术","逆袭"]', '["甜宠","励志","虐心"]', '古代', 'ai_generated', 'pending_review', 'v0.8', 'L1', 4.2, 42, 12);

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
  style_config, applied_asset_ids, status, owner_id, content_project_id, production_unit_type,
  production_unit_id, source_content_version_id, source_storyboard_version_id,
  production_snapshot, purpose, idempotency_key, revision, is_deleted)
VALUES
('canvas_a1b2c3', '霸道总裁的替身新娘 - 画布项目', 1, 1, 1,
 '{"style_id":"STYLE_KMANGA","aspect_ratio":"9:16","resolution":"1080p","fps":25}',
 '[]', 'editing', 1, 1, 'episode', 1, 1, 1,
 '{"contentVersionId":1,"storyboardVersionId":1,"platformRuleVersion":"v1","aspectRatio":"9:16","resolution":"1080p","fps":25}',
 'official', 'migrated:canvas_a1b2c3', 0, 0);

-- 测试订单 (V6 schema)
INSERT INTO trade_orders (order_no, buyer_user_id, buyer_workspace_id, seller_user_id, seller_workspace_id,
  total_amount_cents, platform_fee_cents, seller_income_cents, status, create_idempotency_key, paid_at)
VALUES
('ORD20260608153000001', 2, 'ws_personal_2', 1, 'ws_personal_1', 2990, 598, 2392, 'FULFILLED', 'seed-ord-001', '2026-06-08 15:32:00');

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
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'active',
    adopted_version_id BIGINT,
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
CREATE INDEX IF NOT EXISTS idx_cp_owner_lifecycle_updated ON content_projects(owner_user_id, lifecycle_status, updated_at);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cp_legacy_script ON content_projects(legacy_script_id);
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

-- M5: B/C-tier storyboard shot columns (additive, no data migration needed)
ALTER TABLE cp_storyboard_shots ADD COLUMN IF NOT EXISTS director_intention TEXT AFTER sort_order;
ALTER TABLE cp_storyboard_shots ADD COLUMN IF NOT EXISTS action_motivation TEXT AFTER director_intention;
ALTER TABLE cp_storyboard_shots ADD COLUMN IF NOT EXISTS relationship_blocking TEXT AFTER action_motivation;
ALTER TABLE cp_storyboard_shots ADD COLUMN IF NOT EXISTS information_gap TEXT AFTER relationship_blocking;
ALTER TABLE cp_storyboard_shots ADD COLUMN IF NOT EXISTS edit_point TEXT AFTER information_gap;
ALTER TABLE cp_storyboard_shots ADD COLUMN IF NOT EXISTS image_prompt TEXT AFTER edit_point;
ALTER TABLE cp_storyboard_shots ADD COLUMN IF NOT EXISTS video_prompt TEXT AFTER image_prompt;
ALTER TABLE cp_storyboard_shots ADD COLUMN IF NOT EXISTS dub_text TEXT AFTER video_prompt;
ALTER TABLE cp_storyboard_shots ADD COLUMN IF NOT EXISTS subtitle TEXT AFTER dub_text;
ALTER TABLE cp_storyboard_shots ADD COLUMN IF NOT EXISTS failure_strategy VARCHAR(50) AFTER subtitle;

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
    storage_uri VARCHAR(600),
    storage_provider VARCHAR(20),
    storage_bucket VARCHAR(100),
    storage_key VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_unit_hook UNIQUE (content_unit_id)
);

CREATE TABLE IF NOT EXISTS continuity_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    content_unit_id BIGINT NOT NULL,
    snapshot_json TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_unit_snapshot UNIQUE (content_unit_id)
);

-- M3: Long-form worldbuilding
CREATE TABLE IF NOT EXISTS character_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL, role VARCHAR(50), archetype VARCHAR(100),
    appearance TEXT, personality TEXT, motivation TEXT, long_term_goal TEXT,
    knowledge_boundary TEXT, dialogue_style TEXT, backstory TEXT,
    relationships_json TEXT, status VARCHAR(20) DEFAULT 'draft',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS plot_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    task_type VARCHAR(30) NOT NULL, title VARCHAR(200) NOT NULL,
    description TEXT, stage_goals TEXT, obstacles TEXT, cost TEXT,
    character_ids TEXT, parent_task_id BIGINT,
    status VARCHAR(20) DEFAULT 'planned', sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS volume_outlines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    volume_no INT NOT NULL, title VARCHAR(200), goal TEXT, turns TEXT,
    volume_end_hook TEXT, character_changes TEXT, chapter_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'draft', sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_volume UNIQUE (project_id, volume_no)
);
CREATE TABLE IF NOT EXISTS world_locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL, tier VARCHAR(5) NOT NULL DEFAULT 'L0',
    description TEXT, parent_location_id BIGINT, area_type VARCHAR(30),
    distance_from_origin VARCHAR(50), transportation TEXT,
    faction_territory VARCHAR(100), visual_reference TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS story_timeline (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    event_name VARCHAR(200) NOT NULL, description TEXT,
    relative_time VARCHAR(100), involved_characters TEXT,
    location_id BIGINT, foreshadowing_ids TEXT, sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS foreshadowing_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL,
    description TEXT NOT NULL, planted_in_unit_id BIGINT, payoff_in_unit_id BIGINT,
    status VARCHAR(20) DEFAULT 'planted', category VARCHAR(30),
    character_ids TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- M4: TVC
CREATE TABLE IF NOT EXISTS tvc_briefs (id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT NOT NULL,brand_name VARCHAR(200),product_name VARCHAR(200),target_audience VARCHAR(500),budget VARCHAR(100),platforms VARCHAR(200),duration VARCHAR(50),additional_notes TEXT,status VARCHAR(20) DEFAULT 'draft',created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS brand_facts (id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT NOT NULL,fact_type VARCHAR(30),content TEXT,evidence_status VARCHAR(20) DEFAULT 'unverified',evidence_url VARCHAR(500),is_must_express VARCHAR(10) DEFAULT 'yes',is_must_not_express VARCHAR(10) DEFAULT 'no',created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS creative_strategies (id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT NOT NULL,angle_no INT NOT NULL,angle_name VARCHAR(200),opening_hook TEXT,value_proposition TEXT,brand_memory_point TEXT,platform VARCHAR(50),status VARCHAR(20) DEFAULT 'draft',created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS tvc_scripts (id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT NOT NULL,source_unit_id BIGINT,version_name VARCHAR(100),content_json TEXT,plain_text TEXT,duration_sec INT DEFAULT 0,platforms VARCHAR(200),status VARCHAR(20) DEFAULT 'draft',source_version_id BIGINT,content_hash VARCHAR(64),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);

-- M5: Quality reports (QualityAgent output, persisted per-node review)
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

-- M5: Plugin packs (exportable production bundles)
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
    confirmed_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cbv_project_version UNIQUE (project_id, version_no)
);

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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_eco_project_bible ON ecosystem_rules(project_id, bible_version_id);

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
    confirmed_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pwg_scope_version UNIQUE
        (project_id, bible_version_id, scope_type, scope_id, version_no)
);
CREATE INDEX IF NOT EXISTS idx_pwg_project_scope ON project_writing_guides(project_id, scope_type, scope_id);

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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gcs_job UNIQUE (generation_job_id)
);
CREATE INDEX IF NOT EXISTS idx_gcs_project ON generation_context_snapshots(project_id);
