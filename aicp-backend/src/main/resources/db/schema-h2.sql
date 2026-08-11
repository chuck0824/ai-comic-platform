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
    -- V12 Canvas 生产内核
    canvas_mode VARCHAR(16) DEFAULT 'EXPLORATION',
    schema_version INT DEFAULT 1,
    storyboard_revision INT,
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
    -- V12 生产内核
    shot_unit_id BIGINT,
    node_schema_version VARCHAR(32) DEFAULT 'legacy',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS canvas_edges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL, source_node_id BIGINT NOT NULL, target_node_id BIGINT NOT NULL,
    source_port VARCHAR(20) DEFAULT 'out', target_port VARCHAR(20) DEFAULT 'in',
    edge_type VARCHAR(20) DEFAULT 'data', metadata VARCHAR(2000),
    -- V12 类型化端口
    port_contract_version VARCHAR(32) DEFAULT 'legacy',
    status VARCHAR(32) DEFAULT 'NEEDS_CONFIRMATION',
    role VARCHAR(64),
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
    template_version VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workflow_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    owner_id BIGINT,
    name VARCHAR(200) NOT NULL, description VARCHAR(500), category VARCHAR(50),
    config VARCHAR(4000), variables VARCHAR(4000), thumbnail_url VARCHAR(500),
    visibility VARCHAR(20) DEFAULT 'private', status VARCHAR(20) DEFAULT 'active',
    usage_count INT DEFAULT 0, rating DECIMAL(3,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS canvas_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL, name VARCHAR(200), node_ids VARCHAR(4000),
    color VARCHAR(20), workflow_template_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- V12: ShotWorkUnit / 候选 / 采用 / 迁移
CREATE TABLE IF NOT EXISTS canvas_shot_units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    mode VARCHAR(16) NOT NULL DEFAULT 'EXPLORATION',
    provisional_shot_id VARCHAR(64),
    source_shot_id BIGINT,
    source_shot_revision INT,
    target_duration_ms INT NOT NULL DEFAULT 5000,
    fps INT NOT NULL DEFAULT 24,
    aspect_ratio VARCHAR(16) NOT NULL DEFAULT '16:9',
    sort_order INT NOT NULL DEFAULT 0,
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS generation_request_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    node_id BIGINT NOT NULL,
    shot_unit_id BIGINT NOT NULL,
    payload_json CLOB NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    resolved_model_id VARCHAR(128) NOT NULL,
    resolved_model_version VARCHAR(128),
    adapter_version VARCHAR(64) NOT NULL,
    estimated_credits INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS generation_candidates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    request_snapshot_id BIGINT NOT NULL,
    task_id BIGINT,
    attempt_no INT NOT NULL DEFAULT 1,
    asset_version_id BIGINT,
    model_id VARCHAR(128),
    seed BIGINT,
    actual_credits INT,
    safety_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    is_selected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS shot_adoptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    shot_unit_id BIGINT NOT NULL,
    revision INT NOT NULL,
    candidate_id BIGINT NOT NULL,
    adopted_by BIGINT NOT NULL,
    reason VARCHAR(500),
    override_reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS canvas_migration_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    backup_json CLOB NOT NULL,
    backup_checksum VARCHAR(64) NOT NULL,
    node_count INT NOT NULL DEFAULT 0,
    edge_count INT NOT NULL DEFAULT 0,
    ambiguous_items_json CLOB,
    status VARCHAR(32) NOT NULL DEFAULT 'UPGRADED',
    idempotency_key VARCHAR(128),
    error_detail VARCHAR(2000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS generation_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    project_id BIGINT, node_id BIGINT, shot_id BIGINT,
    type VARCHAR(30), sub_type VARCHAR(30), provider VARCHAR(50), model_id VARCHAR(100),
    parameters VARCHAR(8000), status VARCHAR(20) DEFAULT 'pending', progress INT DEFAULT 0,
    credit_cost INT DEFAULT 0, error_code VARCHAR(50), error_message VARCHAR(2000),
    output_assets VARCHAR(4000),
    workspace_id VARCHAR(64) NOT NULL DEFAULT 'personal_1',
    created_by BIGINT NOT NULL DEFAULT 0,
    content_project_id BIGINT,
    asset_type VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    retry_of_task_id BIGINT,
    idempotency_key VARCHAR(64),
    request_id VARCHAR(64),
    started_at TIMESTAMP, completed_at TIMESTAMP,
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
    type VARCHAR(30), name VARCHAR(200),
    file_url VARCHAR(500), thumbnail_url VARCHAR(500),
    prompt VARCHAR(4000), model_id VARCHAR(100), parameters VARCHAR(4000),
    file_size BIGINT DEFAULT 0, duration_ms INT,
    width INT, height INT, metadata VARCHAR(4000), tags VARCHAR(2000),
    favorite TINYINT DEFAULT 0,
    owner_user_id BIGINT, enterprise_id BIGINT,
    visibility VARCHAR(20) DEFAULT 'private',
    maturity_level VARCHAR(10) DEFAULT 'L0',
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
    user_id BIGINT NOT NULL, skill_id BIGINT, project_id BIGINT,
    title VARCHAR(200), agent_config VARCHAR(4000),
    status VARCHAR(20) DEFAULT 'active',
    estimated_seconds INT, total_credit_cost INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL,
    role VARCHAR(20), content VARCHAR(16000),
    tool_calls VARCHAR(4000), tool_results VARCHAR(4000),
    confidence DECIMAL(3,2), tokens_used INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- Script Trading Market (V6 — unified trade domain)
-- ============================================================

CREATE TABLE IF NOT EXISTS script_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL, seller_user_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL, script_version_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL, synopsis VARCHAR(5000),
    cover_url VARCHAR(500), tags_json VARCHAR(2000) DEFAULT '[]',
    characters_json TEXT, episode_count INT DEFAULT 0,
    author_display_name VARCHAR(100),
    preview_episode_count INT NOT NULL DEFAULT 1, preview_episodes_json TEXT,
    review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', review_reason VARCHAR(2000),
    reviewed_by BIGINT, reviewed_at TIMESTAMP NULL,
    listing_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    exclusive_license_type VARCHAR(10) NULL,
    historical_normal_count INT NOT NULL DEFAULT 0,
    reserved_order_no VARCHAR(32) NULL, reservation_expires_at TIMESTAMP NULL,
    row_version INT NOT NULL DEFAULT 0,
    listed_at TIMESTAMP NULL, delisted_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_listings_status ON script_listings(listing_status);
CREATE INDEX IF NOT EXISTS idx_listings_workspace ON script_listings(workspace_id);

CREATE TABLE IF NOT EXISTS listing_license_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT NOT NULL, license_type VARCHAR(10) NOT NULL,
    price_cents BIGINT NOT NULL DEFAULT 0, currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    term_json TEXT, agreement_text TEXT,
    agreement_version VARCHAR(20), agreement_hash VARCHAR(64),
    enabled TINYINT NOT NULL DEFAULT 1, row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_license_listing_type UNIQUE (listing_id, license_type)
);

CREATE TABLE IF NOT EXISTS trade_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
    buyer_user_id BIGINT NOT NULL, buyer_workspace_id VARCHAR(64) NOT NULL,
    buyer_workspace_type VARCHAR(16) NOT NULL DEFAULT 'PERSONAL',
    seller_user_id BIGINT NOT NULL, seller_workspace_id VARCHAR(64) NOT NULL,
    total_amount_cents BIGINT NOT NULL DEFAULT 0,
    platform_fee_cents BIGINT NOT NULL DEFAULT 0,
    seller_income_cents BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    wallet_transfer_no VARCHAR(64) NULL, wallet_status VARCHAR(20) NULL,
    create_idempotency_key VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NULL, paid_at TIMESTAMP NULL,
    fulfilled_at TIMESTAMP NULL, completed_at TIMESTAMP NULL,
    refunded_at TIMESTAMP NULL,
    row_version INT NOT NULL DEFAULT 0, failure_reason VARCHAR(2000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_trade_order_no UNIQUE (order_no),
    CONSTRAINT uk_trade_order_idempotent UNIQUE (buyer_workspace_id, create_idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_trade_orders_buyer ON trade_orders(buyer_user_id, buyer_workspace_id);
CREATE INDEX IF NOT EXISTS idx_trade_orders_seller ON trade_orders(seller_user_id);
CREATE INDEX IF NOT EXISTS idx_trade_orders_status ON trade_orders(status);

CREATE TABLE IF NOT EXISTS trade_order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL, listing_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL, script_version_id BIGINT NOT NULL,
    license_type VARCHAR(10) NOT NULL, price_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    title_snapshot VARCHAR(200), author_snapshot VARCHAR(100),
    tags_snapshot VARCHAR(2000),
    agreement_text TEXT, agreement_version VARCHAR(20), agreement_hash VARCHAR(64),
    historical_normal_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_item UNIQUE (order_id)
);

CREATE TABLE IF NOT EXISTS script_entitlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL, beneficiary_workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL, script_version_id BIGINT NOT NULL,
    license_type VARCHAR(10) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP, effective_until TIMESTAMP NULL,
    max_accounts INT NULL, allow_commercial TINYINT NOT NULL DEFAULT 0,
    allow_adaptation TINYINT NOT NULL DEFAULT 0,
    allow_sublicense TINYINT NOT NULL DEFAULT 0,
    territory_restriction VARCHAR(200),
    revoked_at TIMESTAMP NULL, revoke_reason VARCHAR(2000),
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_entitlement_order_item UNIQUE (order_item_id)
);

CREATE TABLE IF NOT EXISTS purchased_script_copies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL, workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL, source_version_id BIGINT NOT NULL,
    content_json TEXT, title VARCHAR(200),
    created_by_user_id BIGINT NOT NULL,
    source_listing_id BIGINT, source_author_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_copy_order_item UNIQUE (order_item_id)
);

CREATE TABLE IF NOT EXISTS purchase_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL, requester_user_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL, license_type VARCHAR(10) NOT NULL,
    amount_cents BIGINT NOT NULL, currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    reason VARCHAR(2000),
    approver_user_id BIGINT NULL, approval_comment VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    order_no VARCHAR(32) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_pr_workspace_status ON purchase_requests(workspace_id, status);

CREATE TABLE IF NOT EXISTS refund_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL, requester_user_id BIGINT NOT NULL,
    reason_code VARCHAR(30), reason_text VARCHAR(2000), evidence_json TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reviewer_user_id BIGINT NULL, review_comment VARCHAR(2000), reviewed_at TIMESTAMP NULL,
    refund_amount_cents BIGINT, wallet_reversal_no VARCHAR(64),
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rf_order ON refund_requests(order_no);
CREATE INDEX IF NOT EXISTS idx_rf_status ON refund_requests(status);

CREATE TABLE IF NOT EXISTS trade_outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL, aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL, payload TEXT,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0, max_retries INT NOT NULL DEFAULT 10,
    next_retry_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(2000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_outbox_idempotent UNIQUE (aggregate_type, aggregate_id, event_type, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_outbox_dispatch ON trade_outbox_events(status, next_retry_at);

CREATE TABLE IF NOT EXISTS trade_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT, workspace_id VARCHAR(64),
    action VARCHAR(50) NOT NULL, target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    before_summary VARCHAR(2000), after_summary VARCHAR(2000),
    correlation_id VARCHAR(64), client_ip VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_audit_target ON trade_audit_logs(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON trade_audit_logs(actor_user_id, created_at);

-- ============================================================
-- AI 资产市场 统一模型 (V2 — 替换旧 market_assets)
-- ============================================================

CREATE TABLE IF NOT EXISTS workspace_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    workspace_id VARCHAR(64) NOT NULL,
    workspace_type VARCHAR(16) NOT NULL,
    creator_user_id BIGINT NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    tags VARCHAR(1000) DEFAULT '[]',
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
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT,
    purge_at TIMESTAMP NULL,
    purge_blocked_reason VARCHAR(64),
    legacy_platform_asset_id BIGINT,
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
    source_task_id BIGINT,
    storage_provider VARCHAR(24),
    storage_bucket VARCHAR(128),
    storage_key VARCHAR(768),
    mime_type VARCHAR(128),
    file_size BIGINT DEFAULT 0,
    width INT,
    height INT,
    duration_ms INT,
    metadata VARCHAR(4000),
    preview_url VARCHAR(500),
    content_ref VARCHAR(500),
    checksum VARCHAR(128),
    generation_snapshot VARCHAR(4000),
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

-- ── Asset workbench tables ──────────────────────────────────────────

CREATE TABLE IF NOT EXISTS workspace_asset_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_waf_user_workspace_asset UNIQUE (user_id, workspace_id, asset_id)
);

CREATE TABLE IF NOT EXISTS asset_activity_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    before_data VARCHAR(2000),
    after_data VARCHAR(2000),
    request_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS canvas_asset_placements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    asset_version_id BIGINT NOT NULL,
    canvas_project_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    placed_by BIGINT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    released_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cap_workspace_idem_key UNIQUE (workspace_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS asset_command_idempotencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_code INT,
    response_body VARCHAR(4000),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_aci_workspace_user_key UNIQUE (workspace_id, user_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS generation_settlement_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    payload VARCHAR(4000),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    last_error VARCHAR(2000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gso_task_stage UNIQUE (task_id, stage)
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

-- ============================================================
-- 综合演示种子数据 (Comprehensive Demo Seed Data)
-- 覆盖除画布工作台外的所有模块，支持完整功能演示
-- 所有 INSERT 使用 WHERE NOT EXISTS 保证幂等
-- ============================================================

-- ============================================================
-- A. 更多测试用户
-- ============================================================
INSERT INTO users (uuid, nickname, phone, password_hash, account_type, member_level, real_name_status, status)
SELECT 'dev-user-002', '编剧小李', '13800000002', '$2a$04$YaRTTXEk1gK50gdoa8ZHKuwmTUu8REzBDEkrygu4HRWVz0LH.8agS', 'free_user', 'creator', 'verified', 'active'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE uuid = 'dev-user-002');

INSERT INTO users (uuid, nickname, phone, password_hash, account_type, member_level, real_name_status, status)
SELECT 'dev-user-003', '导演小王', '13800000003', '$2a$04$YaRTTXEk1gK50gdoa8ZHKuwmTUu8REzBDEkrygu4HRWVz0LH.8agS', 'free_user', 'creator', 'verified', 'active'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE uuid = 'dev-user-003');

INSERT INTO users (uuid, nickname, phone, password_hash, account_type, member_level, real_name_status, status)
SELECT 'dev-user-004', '张总', '13800000004', '$2a$04$YaRTTXEk1gK50gdoa8ZHKuwmTUu8REzBDEkrygu4HRWVz0LH.8agS', 'free_user', 'enterprise', 'verified', 'active'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE uuid = 'dev-user-004');

INSERT INTO users (uuid, nickname, phone, password_hash, account_type, member_level, real_name_status, status)
SELECT 'dev-user-005', '漫迷小赵', '13800000005', '$2a$04$YaRTTXEk1gK50gdoa8ZHKuwmTUu8REzBDEkrygu4HRWVz0LH.8agS', 'free_user', 'free', 'unverified', 'active'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE uuid = 'dev-user-005');

-- ============================================================
-- 补充表定义: 企业、脚本上架、采购申请 (schema-h2未包含)
-- ============================================================
CREATE TABLE IF NOT EXISTS enterprises (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    license_number VARCHAR(100),
    license_image_url VARCHAR(500),
    contact_name VARCHAR(100),
    contact_phone VARCHAR(20),
    verify_status VARCHAR(20) DEFAULT 'unverified',
    member_limit INT DEFAULT 10,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS enterprise_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(50) DEFAULT 'writer',
    permissions VARCHAR(4000),
    department VARCHAR(100),
    purchase_budget_monthly DECIMAL(10,2) DEFAULT 0,
    purchase_budget_single DECIMAL(10,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'pending',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ent_user UNIQUE (enterprise_id, user_id)
);

CREATE TABLE IF NOT EXISTS enterprise_purchase_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    license_type VARCHAR(20) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    reason VARCHAR(500),
    budget_remaining DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'pending',
    approver_user_id BIGINT,
    approval_note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS script_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    license_types VARCHAR(1000),
    status VARCHAR(20) DEFAULT 'active',
    listed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    delisted_at TIMESTAMP,
    CONSTRAINT uk_listing_script UNIQUE (script_id)
);

-- ============================================================
-- V6: Trade market tables
-- ============================================================
CREATE TABLE IF NOT EXISTS trade_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL UNIQUE,
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
    expires_at TIMESTAMP NULL,
    paid_at TIMESTAMP NULL,
    fulfilled_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    refunded_at TIMESTAMP NULL,
    row_version INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trade_order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS script_entitlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL UNIQUE,
    beneficiary_workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL,
    script_version_id BIGINT NOT NULL,
    license_type VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_until TIMESTAMP NULL,
    max_accounts INT NULL,
    allow_commercial TINYINT NOT NULL DEFAULT 0,
    allow_adaptation TINYINT NOT NULL DEFAULT 0,
    allow_sublicense TINYINT NOT NULL DEFAULT 0,
    territory_restriction VARCHAR(200),
    revoked_at TIMESTAMP NULL,
    revoke_reason VARCHAR(2000),
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS purchased_script_copies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL UNIQUE,
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
    budget_subject_type VARCHAR(16),
    budget_subject_id VARCHAR(64),
    budget_reservation_entry_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refund_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    reason_code VARCHAR(30),
    reason_text VARCHAR(2000),
    evidence_json TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reviewer_user_id BIGINT NULL,
    review_comment VARCHAR(2000),
    reviewed_at TIMESTAMP NULL,
    refund_amount_cents BIGINT,
    wallet_reversal_no VARCHAR(64),
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
    next_retry_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_budget_scope UNIQUE (workspace_id, subject_type, subject_id, period_month)
);

CREATE TABLE IF NOT EXISTS enterprise_purchase_budget_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    entry_type VARCHAR(16) NOT NULL,
    amount_cents BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    wallet_transfer_no VARCHAR(64) NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
    submitted_at TIMESTAMP NULL,
    decided_at TIMESTAMP NULL,
    last_event_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_approval_source UNIQUE (source_type, source_id)
);

CREATE TABLE IF NOT EXISTS asset_outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL
);

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
    approved_at TIMESTAMP NULL,
    export_task_id BIGINT NULL,
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
    event_id VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- B. 企业数据
-- ============================================================
INSERT INTO enterprises (owner_user_id, name, license_number, verify_status, member_limit)
SELECT 4, '星辰动漫工作室', 'SC20260601001', 'verified', 20
WHERE NOT EXISTS (SELECT 1 FROM enterprises WHERE name = '星辰动漫工作室');

INSERT INTO enterprises (owner_user_id, name, license_number, verify_status, member_limit)
SELECT 4, '梦想影视有限公司', 'SC20260602002', 'verified', 50
WHERE NOT EXISTS (SELECT 1 FROM enterprises WHERE name = '梦想影视有限公司');

-- 企业成员
INSERT INTO enterprise_members (enterprise_id, user_id, role, department, purchase_budget_monthly, purchase_budget_single, status)
SELECT 1, 4, 'admin', '管理层', 10000.00, 2000.00, 'active'
WHERE NOT EXISTS (SELECT 1 FROM enterprise_members WHERE enterprise_id = 1 AND user_id = 4);

INSERT INTO enterprise_members (enterprise_id, user_id, role, department, purchase_budget_monthly, purchase_budget_single, status)
SELECT 1, 2, 'writer', '编剧组', 5000.00, 1000.00, 'active'
WHERE NOT EXISTS (SELECT 1 FROM enterprise_members WHERE enterprise_id = 1 AND user_id = 2);

INSERT INTO enterprise_members (enterprise_id, user_id, role, department, purchase_budget_monthly, purchase_budget_single, status)
SELECT 1, 3, 'artist', '美术组', 3000.00, 500.00, 'active'
WHERE NOT EXISTS (SELECT 1 FROM enterprise_members WHERE enterprise_id = 1 AND user_id = 3);

INSERT INTO enterprise_members (enterprise_id, user_id, role, department, purchase_budget_monthly, purchase_budget_single, status)
SELECT 2, 4, 'admin', '管理层', 20000.00, 5000.00, 'active'
WHERE NOT EXISTS (SELECT 1 FROM enterprise_members WHERE enterprise_id = 2 AND user_id = 4);

-- ============================================================
-- C. 旧版剧本数据 (脚本仓库/交易市场使用)
-- ============================================================
INSERT INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, owner_type, episode_count, completed_episodes, total_words, synopsis, genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version, maturity_level, rating, review_count, sales_count)
SELECT 'scr-demo-001', 'PROJ_DEMO_001', '暗夜追光者', 1, 1, 'personal', 60, 60, 95000, '前刑警队长林深为追查妹妹失踪真相，卧底进入神秘组织"暗夜"。在正义与黑暗的边缘，他发现了一个惊天秘密…', '悬疑', '["卧底","复仇","悬疑"]', '["紧张","反转","暗黑"]', '现代', 'ai_generated', 'listed', 'v2.0', 'L2', 4.7, 128, 45
WHERE NOT EXISTS (SELECT 1 FROM scripts WHERE uuid = 'scr-demo-001');

INSERT INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, owner_type, episode_count, completed_episodes, total_words, synopsis, genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version, maturity_level, rating, review_count, sales_count)
SELECT 'scr-demo-002', 'PROJ_DEMO_002', '星海迷航', 2, 2, 'personal', 80, 45, 72000, '公元2250年，人类星际移民船团在深空遭遇未知文明。年轻的领航员苏瑾被迫在人类存亡与外星文明之间做出抉择…', '科幻', '["星际","文明冲突","冒险"]', '["宏大","紧张","感人"]', '未来', 'ai_generated', 'listed', 'v1.5', 'L1', 4.4, 89, 32
WHERE NOT EXISTS (SELECT 1 FROM scripts WHERE uuid = 'scr-demo-002');

INSERT INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, owner_type, episode_count, completed_episodes, total_words, synopsis, genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version, maturity_level, rating, review_count, sales_count)
SELECT 'scr-demo-003', 'PROJ_DEMO_003', '长安十二时辰之幻术师', 1, 1, 'personal', 40, 40, 68000, '盛唐长安，天才幻术师白鹤在朱雀大街摆摊卖艺，却卷入了一场涉及皇室秘宝的惊天阴谋。真幻交织，谁才是幕后黑手？', '奇幻', '["古装","探案","玄幻"]', '["奇幻","紧张","史诗"]', '古代', 'ai_generated', 'listed', 'v1.0', 'L1', 4.9, 256, 98
WHERE NOT EXISTS (SELECT 1 FROM scripts WHERE uuid = 'scr-demo-003');

INSERT INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, owner_type, episode_count, completed_episodes, total_words, synopsis, genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version, maturity_level, rating, review_count, sales_count)
SELECT 'scr-demo-004', 'PROJ_DEMO_004', '校园奇妙物语', 3, 3, 'personal', 24, 24, 35000, '平凡高中生在校园角落发现一扇通往"里世界"的门，与同伴们一起在表里世界之间守护日常的奇妙冒险。', '奇幻', '["校园","冒险","青春"]', '["轻松","治愈","搞笑"]', '现代', 'ai_generated', 'draft', 'v0.5', 'L0', 3.8, 15, 0
WHERE NOT EXISTS (SELECT 1 FROM scripts WHERE uuid = 'scr-demo-004');

INSERT INTO scripts (uuid, project_id, title, author_user_id, owner_user_id, owner_type, episode_count, completed_episodes, total_words, synopsis, genre_tag, plot_tags, tone_tags, setting_tag, source, status, current_version, maturity_level, rating, review_count, sales_count)
SELECT 'scr-demo-005', 'PROJ_DEMO_005', '锦绣未央之医女倾城', 1, 1, 'personal', 52, 30, 58000, '现代女医生穿越古代成为落魄医女，凭借精湛医术与智慧在乱世中立足，收获爱情与事业的双重逆袭。', '言情', '["穿越","医术","逆袭"]', '["甜宠","励志","虐心"]', '古代', 'ai_generated', 'pending_review', 'v0.8', 'L1', 4.2, 42, 12
WHERE NOT EXISTS (SELECT 1 FROM scripts WHERE uuid = 'scr-demo-005');

-- 剧本分集 (为暗夜追光者添加前5集)
INSERT INTO script_episodes (script_id, episode_number, title, content, word_count, status)
SELECT 1, 1, '消失的妹妹', '林深站在废弃码头的集装箱前，手电筒的光束扫过斑驳的铁皮。三天前，妹妹林小雨在这里最后一次出现在监控画面中……', 3200, 'completed'
WHERE NOT EXISTS (SELECT 1 FROM script_episodes WHERE script_id = 1 AND episode_number = 1);

INSERT INTO script_episodes (script_id, episode_number, title, content, word_count, status)
SELECT 1, 2, '暗夜初现', '「暗夜不是你想的那样。」神秘人留下这句话后消失在夜色中。林深握紧拳头，他决定深入虎穴，以卧底身份潜入这个组织……', 3100, 'completed'
WHERE NOT EXISTS (SELECT 1 FROM script_episodes WHERE script_id = 1 AND episode_number = 2);

INSERT INTO script_episodes (script_id, episode_number, title, content, word_count, status)
SELECT 1, 3, '卧底试炼', '加入暗夜的仪式比想象中复杂。林深必须在72小时内完成三项不可能的任务，证明自己的「价值」……', 2900, 'completed'
WHERE NOT EXISTS (SELECT 1 FROM script_episodes WHERE script_id = 1 AND episode_number = 3);

INSERT INTO script_episodes (script_id, episode_number, title, content, word_count, status)
SELECT 1, 4, '第一层真相', '暗夜的内部档案室，林深颤抖着翻开了那本标着「林小雨」的卷宗。里面的内容让他几乎窒息……', 3050, 'completed'
WHERE NOT EXISTS (SELECT 1 FROM script_episodes WHERE script_id = 1 AND episode_number = 4);

INSERT INTO script_episodes (script_id, episode_number, title, content, word_count, status)
SELECT 1, 5, '背叛还是救赎', '林深被迫在自己的原则和妹妹的生命之间做出选择。暗夜的领导者「夜王」终于现身，却带来了一个意想不到的提议……', 3300, 'completed'
WHERE NOT EXISTS (SELECT 1 FROM script_episodes WHERE script_id = 1 AND episode_number = 5);

-- 剧本版本
INSERT INTO script_versions (script_id, version, content, change_summary, created_by)
SELECT 1, 'v1.0', '{"version":"v1.0","chapters":60}', '初始版本：完成全部60集剧本', 2
WHERE NOT EXISTS (SELECT 1 FROM script_versions WHERE script_id = 1 AND version = 'v1.0');

INSERT INTO script_versions (script_id, version, content, change_summary, created_by)
SELECT 1, 'v2.0', '{"version":"v2.0","chapters":60}', '修订版：优化前10集钩子节奏，增加反转密度', 2
WHERE NOT EXISTS (SELECT 1 FROM script_versions WHERE script_id = 1 AND version = 'v2.0');

-- 剧集审核报告
INSERT INTO episode_review_reports (script_id, episode_id, episode_number, overall_status, overall_score, hook_score, showrunner_score, director_score, report_json)
SELECT 1, 1, 1, 'approved', 8.5, 8.2, 8.7, 8.5, '{"summary":"开场钩子有力，人物动机清晰，建议增加环境描写","strengths":["节奏紧凑","对白真实"],"weaknesses":["描写略少"]}'
WHERE NOT EXISTS (SELECT 1 FROM episode_review_reports WHERE script_id = 1 AND episode_id = 1);

INSERT INTO episode_review_reports (script_id, episode_id, episode_number, overall_status, overall_score, hook_score, showrunner_score, director_score, report_json)
SELECT 1, 2, 2, 'approved', 8.8, 9.0, 8.5, 8.8, '{"summary":"第二集转折精彩，神秘人塑造成功","strengths":["悬念设置","角色塑造"],"weaknesses":[]}'
WHERE NOT EXISTS (SELECT 1 FROM episode_review_reports WHERE script_id = 1 AND episode_id = 2);

INSERT INTO episode_review_reports (script_id, episode_id, episode_number, overall_status, overall_score, hook_score, showrunner_score, director_score, report_json)
SELECT 1, 3, 3, 'needs_revision', 6.5, 5.8, 7.0, 6.8, '{"summary":"试炼场景节奏偏慢，钩子力度不足","strengths":["世界观扩展"],"weaknesses":["节奏拖沓","钩子薄弱"]}'
WHERE NOT EXISTS (SELECT 1 FROM episode_review_reports WHERE script_id = 1 AND episode_id = 3);

-- 剧本钩子
INSERT INTO episode_hooks (episode_id, script_id, hook_type, content, strength_score, position, status)
SELECT 1, 1, 'opening', '林深的手电筒照见了什么？妹妹失踪的背后隐藏着什么？', 8.5, 0, 'active'
WHERE NOT EXISTS (SELECT 1 FROM episode_hooks WHERE episode_id = 1 AND hook_type = 'opening');

INSERT INTO episode_hooks (episode_id, script_id, hook_type, content, strength_score, position, status)
SELECT 1, 1, 'closing', '码头尽头，一个与妹妹背影一模一样的身影一闪而过…', 9.2, 1, 'active'
WHERE NOT EXISTS (SELECT 1 FROM episode_hooks WHERE episode_id = 1 AND hook_type = 'closing');

INSERT INTO episode_hooks (episode_id, script_id, hook_type, content, strength_score, position, status)
SELECT 2, 1, 'opening', '神秘人说的"暗夜不是你想的那样"到底意味着什么？', 8.0, 0, 'active'
WHERE NOT EXISTS (SELECT 1 FROM episode_hooks WHERE episode_id = 2 AND hook_type = 'opening');

INSERT INTO episode_hooks (episode_id, script_id, hook_type, content, strength_score, position, status)
SELECT 2, 1, 'closing', '林深踏入了暗夜的大门，他能否守住自己的身份？', 9.0, 1, 'active'
WHERE NOT EXISTS (SELECT 1 FROM episode_hooks WHERE episode_id = 2 AND hook_type = 'closing');

-- ============================================================
-- D. 脚本仓库资产 (旧 assets 表)
-- ============================================================
INSERT INTO repo_assets (asset_id, asset_type, name, description, owner_user_id, maturity_level)
SELECT 'CH_NIGHT_KING', 'character', '夜王 (反派)', '暗夜组织的神秘领导者，身份成谜，智商超群', 2, 'L2'
WHERE NOT EXISTS (SELECT 1 FROM repo_assets WHERE asset_id = 'CH_NIGHT_KING');

INSERT INTO repo_assets (asset_id, asset_type, name, description, owner_user_id, maturity_level)
SELECT 'CH_LIN_SHEN', 'character', '林深 (男主)', '前刑警队长，冷峻坚毅，为救妹妹甘愿化身卧底', 2, 'L2'
WHERE NOT EXISTS (SELECT 1 FROM repo_assets WHERE asset_id = 'CH_LIN_SHEN');

INSERT INTO repo_assets (asset_id, asset_type, name, description, owner_user_id, maturity_level)
SELECT 'LOC_DOCK', 'scene', '废弃码头', '城市边缘的废弃货运码头，集装箱堆叠如迷宫', 2, 'L1'
WHERE NOT EXISTS (SELECT 1 FROM repo_assets WHERE asset_id = 'LOC_DOCK');

INSERT INTO repo_assets (asset_id, asset_type, name, description, owner_user_id, maturity_level)
SELECT 'LOC_NIGHT_BASE', 'scene', '暗夜秘密基地', '地下深处的秘密基地，高科技与黑暗风格并存', 2, 'L1'
WHERE NOT EXISTS (SELECT 1 FROM repo_assets WHERE asset_id = 'LOC_NIGHT_BASE');

INSERT INTO repo_assets (asset_id, asset_type, name, description, owner_user_id, maturity_level)
SELECT 'PROP_AMULET', 'prop', '妹妹的护身符', '林小雨留给哥哥的最后一件物品，暗藏线索', 2, 'L1'
WHERE NOT EXISTS (SELECT 1 FROM repo_assets WHERE asset_id = 'PROP_AMULET');

-- ============================================================
-- E. 交易市场数据 (V6 schema)
-- ============================================================
-- 脚本上架 (seed listings for demo)
INSERT INTO script_listings (workspace_id, seller_user_id, script_id, script_version_id, title, synopsis, listing_status, review_status)
SELECT 'ws_personal_2', 2, 1, 1, '霸道总裁的替身新娘', '都市甜宠短剧', 'LISTED', 'LISTED'
WHERE NOT EXISTS (SELECT 1 FROM script_listings WHERE script_id = 1 AND seller_user_id = 2);

INSERT INTO script_listings (workspace_id, seller_user_id, script_id, script_version_id, title, synopsis, listing_status, review_status)
SELECT 'ws_personal_3', 3, 2, 1, '重生之商业帝国', '商战复仇长篇', 'LISTED', 'LISTED'
WHERE NOT EXISTS (SELECT 1 FROM script_listings WHERE script_id = 2 AND seller_user_id = 3);

INSERT INTO script_listings (workspace_id, seller_user_id, script_id, script_version_id, title, synopsis, listing_status, review_status)
SELECT 'ws_personal_2', 2, 3, 1, '古剑奇谭外传', '古装奇幻冒险', 'LISTED', 'LISTED'
WHERE NOT EXISTS (SELECT 1 FROM script_listings WHERE script_id = 3 AND seller_user_id = 2);

-- Demo orders (free claims for testing)
INSERT INTO trade_orders (order_no, status, buyer_user_id, buyer_workspace_id, buyer_workspace_type,
    seller_user_id, seller_workspace_id, total_amount_cents, create_idempotency_key)
SELECT 'ORD-DEMO-001', 'FULFILLED', 5, 'ws_personal_5', 'PERSONAL', 2, 'ws_personal_2', 0, 'demo-key-001'
WHERE NOT EXISTS (SELECT 1 FROM trade_orders WHERE order_no = 'ORD-DEMO-001');

INSERT INTO trade_orders (order_no, status, buyer_user_id, buyer_workspace_id, buyer_workspace_type,
    seller_user_id, seller_workspace_id, total_amount_cents, create_idempotency_key)
SELECT 'ORD-DEMO-002', 'FULFILLED', 5, 'ws_personal_5', 'PERSONAL', 3, 'ws_personal_3', 0, 'demo-key-002'
WHERE NOT EXISTS (SELECT 1 FROM trade_orders WHERE order_no = 'ORD-DEMO-002');

INSERT INTO trade_orders (order_no, status, buyer_user_id, buyer_workspace_id, buyer_workspace_type,
    seller_user_id, seller_workspace_id, total_amount_cents, create_idempotency_key)
SELECT 'ORD-DEMO-003', 'PENDING_PAYMENT', 1, 'ws_personal_1', 'PERSONAL', 2, 'ws_personal_2', 4990, 'demo-key-003'
WHERE NOT EXISTS (SELECT 1 FROM trade_orders WHERE order_no = 'ORD-DEMO-003');

-- 企业采购申请
INSERT INTO purchase_requests (workspace_id, requester_user_id, listing_id, license_type, amount_cents, reason, status)
SELECT 'ws_ent_1', 2, 3, 'EXCLUSIVE', 19900, '公司新项目需要优质古装奇幻剧本进行漫改，此剧本评分较高且题材契合', 'PENDING_APPROVAL'
WHERE NOT EXISTS (SELECT 1 FROM purchase_requests WHERE workspace_id = 'ws_ent_1' AND listing_id = 3 AND requester_user_id = 2);

INSERT INTO purchase_requests (workspace_id, requester_user_id, listing_id, license_type, amount_cents, reason, status)
SELECT 'ws_ent_1', 3, 1, 'NORMAL', 2990, '悬疑题材与公司储备方向一致，建议采购作为素材库补充', 'APPROVED'
WHERE NOT EXISTS (SELECT 1 FROM purchase_requests WHERE workspace_id = 'ws_ent_1' AND listing_id = 1 AND requester_user_id = 3);

-- ============================================================
-- F. AI生成任务历史 (用于任务监控 + 资产历史)
-- ============================================================
INSERT INTO gen_tasks (user_id, project_id, gen_type, storyboard_tier, input_params, output_data, prompt_used, model_used, status, progress, tokens_used, duration_ms)
SELECT 1, 'PROJ_DEMO_001', 'synopsis', NULL, '{"topic":"悬疑卧底","length":"long"}', '{"synopsis":"前刑警队长卧底神秘组织..."}', '你是一位悬疑小说大师，请为以下题材创作故事梗概...', 'gpt-4o', 'completed', 100, 4500, 12000
WHERE NOT EXISTS (SELECT 1 FROM gen_tasks WHERE user_id = 1 AND project_id = 'PROJ_DEMO_001' AND gen_type = 'synopsis');

INSERT INTO gen_tasks (user_id, project_id, gen_type, storyboard_tier, input_params, output_data, prompt_used, model_used, status, progress, tokens_used, duration_ms)
SELECT 1, 'PROJ_DEMO_001', 'outline', NULL, '{"episodes":60}', '{"outline":"60集分集大纲..."}', '基于以下梗概，为60集剧本创作详细分集大纲...', 'claude-opus-4-8', 'completed', 100, 12000, 45000
WHERE NOT EXISTS (SELECT 1 FROM gen_tasks WHERE user_id = 1 AND project_id = 'PROJ_DEMO_001' AND gen_type = 'outline');

INSERT INTO gen_tasks (user_id, project_id, gen_type, storyboard_tier, input_params, output_data, prompt_used, model_used, status, progress, tokens_used, duration_ms)
SELECT 1, 'PROJ_DEMO_001', 'episode', 'A', '{"episode":1}', '{"content":"第一集完整剧本..."}', '基于大纲创作第一集完整剧本，要求开场即高能...', 'claude-sonnet-4-6', 'completed', 100, 8500, 28000
WHERE NOT EXISTS (SELECT 1 FROM gen_tasks WHERE user_id = 1 AND project_id = 'PROJ_DEMO_001' AND gen_type = 'episode');

INSERT INTO gen_tasks (user_id, project_id, gen_type, storyboard_tier, input_params, output_data, prompt_used, model_used, status, progress, tokens_used, duration_ms)
SELECT 1, 'PROJ_DEMO_001', 'storyboard', 'A', '{"episode":1,"style":"korean_manhwa"}', '{"shots":45,"duration_sec":180}', '为第一集剧本创建A档分镜...', 'gpt-4o', 'processing', 65, 3200, 15000
WHERE NOT EXISTS (SELECT 1 FROM gen_tasks WHERE user_id = 1 AND project_id = 'PROJ_DEMO_001' AND gen_type = 'storyboard' AND status = 'processing');

INSERT INTO gen_tasks (user_id, project_id, gen_type, storyboard_tier, input_params, output_data, prompt_used, model_used, status, progress, tokens_used, duration_ms, error_msg)
SELECT 2, 'PROJ_DEMO_002', 'adaptation', NULL, '{"source":"novel","target":"comic"}', NULL, '将以下小说章节改编为漫画脚本...', 'gpt-4o', 'failed', 45, 2100, 8000, 'AI服务暂时不可用，请稍后重试'
WHERE NOT EXISTS (SELECT 1 FROM gen_tasks WHERE user_id = 2 AND project_id = 'PROJ_DEMO_002' AND gen_type = 'adaptation');

INSERT INTO gen_tasks (user_id, project_id, gen_type, storyboard_tier, input_params, output_data, prompt_used, model_used, status, progress, tokens_used, duration_ms)
SELECT 3, 'PROJ_DEMO_003', 'topic', NULL, '{"genre":"奇幻","length":"medium"}', '{"topics":["长安幻术师","时空旅人","魔法学院"]}', '生成3个奇幻题材的剧本选题...', 'claude-sonnet-4-6', 'completed', 100, 3200, 10000
WHERE NOT EXISTS (SELECT 1 FROM gen_tasks WHERE user_id = 3 AND project_id = 'PROJ_DEMO_003' AND gen_type = 'topic');

INSERT INTO gen_tasks (user_id, project_id, gen_type, storyboard_tier, input_params, output_data, prompt_used, model_used, status, progress, tokens_used, duration_ms)
SELECT 3, 'PROJ_DEMO_003', 'promotion', NULL, '{"platform":"douyin","duration":30}', '{"script":"30秒抖音推广文案..."}', '为剧本创作30秒短视频推广文案...', 'gpt-4o', 'completed', 100, 1800, 5000
WHERE NOT EXISTS (SELECT 1 FROM gen_tasks WHERE user_id = 3 AND project_id = 'PROJ_DEMO_003' AND gen_type = 'promotion');

INSERT INTO gen_tasks (user_id, project_id, gen_type, storyboard_tier, input_params, output_data, prompt_used, model_used, status, progress, tokens_used, duration_ms)
SELECT 1, 'PROJ_DEMO_001', 'quick', NULL, '{"message":"帮我润色这个开场白"}', '{"result":"已润色的开场白..."}', '润色以下文本...', 'claude-haiku-4-5', 'completed', 100, 500, 2000
WHERE NOT EXISTS (SELECT 1 FROM gen_tasks WHERE user_id = 1 AND project_id = 'PROJ_DEMO_001' AND gen_type = 'quick');

-- 画布相关的生成任务 (generation_tasks — 属于画布模块但提供任务监控数据)
INSERT INTO generation_tasks (uuid, project_id, type, sub_type, provider, model_id, parameters, status, progress, credit_cost, started_at, completed_at)
SELECT 'gen-task-001', 1, 'image', 'character_design', 'openai', 'dall-e-3', '{"prompt":"暗夜组织首领角色设计图","size":"1024x1792","style":"anime"}', 'succeeded', 100, 5, DATEADD('MINUTE', -120, CURRENT_TIMESTAMP), DATEADD('MINUTE', -118, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM generation_tasks WHERE uuid = 'gen-task-001');

INSERT INTO generation_tasks (uuid, project_id, type, sub_type, provider, model_id, parameters, status, progress, credit_cost, started_at, completed_at)
SELECT 'gen-task-002', 1, 'image', 'scene', 'openai', 'dall-e-3', '{"prompt":"废弃码头夜景全景","size":"1792x1024"}', 'succeeded', 100, 3, DATEADD('MINUTE', -90, CURRENT_TIMESTAMP), DATEADD('MINUTE', -88, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM generation_tasks WHERE uuid = 'gen-task-002');

INSERT INTO generation_tasks (uuid, project_id, type, sub_type, provider, model_id, parameters, status, progress, credit_cost, error_message)
SELECT 'gen-task-003', 1, 'video', 'shot_render', 'kling', 'kling-v1', '{"duration":4,"resolution":"1080p","fps":25}', 'failed', 30, 20, '视频生成超时，请降低分辨率后重试'
WHERE NOT EXISTS (SELECT 1 FROM generation_tasks WHERE uuid = 'gen-task-003');

INSERT INTO generation_tasks (uuid, project_id, type, sub_type, provider, model_id, parameters, status, progress, credit_cost)
SELECT 'gen-task-004', 1, 'audio', 'dubbing', 'openai', 'tts-1', '{"text":"你还记得五年前的那个夜晚吗？","voice":"echo"}', 'pending', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM generation_tasks WHERE uuid = 'gen-task-004');

-- 生成变体
INSERT INTO generation_variants (uuid, parent_task_id, variant_index, parameters, output_url, status, selected)
SELECT 'variant-001', 1, 1, '{"seed":42}', '/generated/char-night-king-v1.png', 'completed', 1
WHERE NOT EXISTS (SELECT 1 FROM generation_variants WHERE uuid = 'variant-001');

INSERT INTO generation_variants (uuid, parent_task_id, variant_index, parameters, output_url, status, selected)
SELECT 'variant-002', 1, 2, '{"seed":123}', '/generated/char-night-king-v2.png', 'completed', 0
WHERE NOT EXISTS (SELECT 1 FROM generation_variants WHERE uuid = 'variant-002');

INSERT INTO generation_variants (uuid, parent_task_id, variant_index, parameters, output_url, status, selected)
SELECT 'variant-003', 2, 1, '{"seed":77}', '/generated/scene-dock-v1.png', 'completed', 1
WHERE NOT EXISTS (SELECT 1 FROM generation_variants WHERE uuid = 'variant-003');

-- ============================================================
-- G. 平台资产库 (资产历史页面使用)
-- ============================================================
INSERT INTO platform_assets (uuid, project_id, source_task_id, type, name, prompt, model_id, owner_user_id, maturity_level)
SELECT 'plat-asset-001', 1, 1, 'image', '夜王角色设计图 v1', '暗夜组织首领角色设计图，黑色风衣，半机械面具', 'dall-e-3', 1, 'L2'
WHERE NOT EXISTS (SELECT 1 FROM platform_assets WHERE uuid = 'plat-asset-001');

INSERT INTO platform_assets (uuid, project_id, source_task_id, type, name, prompt, model_id, owner_user_id, maturity_level)
SELECT 'plat-asset-002', 1, 2, 'image', '废弃码头夜景全景', '城市边缘废弃货运码头夜景，月光透过集装箱间隙，氛围暗黑紧张', 'dall-e-3', 1, 'L1'
WHERE NOT EXISTS (SELECT 1 FROM platform_assets WHERE uuid = 'plat-asset-002');

INSERT INTO platform_assets (uuid, project_id, source_node_id, type, name, prompt, model_id, owner_user_id, maturity_level)
SELECT 'plat-asset-003', 1, NULL, 'image', '林深角色立绘', '前刑警队长角色立绘，冷峻坚毅，黑色短发，穿便装夹克', 'midjourney-v6', 1, 'L2'
WHERE NOT EXISTS (SELECT 1 FROM platform_assets WHERE uuid = 'plat-asset-003');

INSERT INTO platform_assets (uuid, project_id, source_task_id, type, name, prompt, model_id, owner_user_id, maturity_level)
SELECT 'plat-asset-004', 1, NULL, 'audio', '背景音乐-紧张氛围', '悬疑紧张氛围背景音乐，低音弦乐+电子合成器', 'suno-v3', 1, 'L1'
WHERE NOT EXISTS (SELECT 1 FROM platform_assets WHERE uuid = 'plat-asset-004');

INSERT INTO platform_assets (uuid, project_id, source_node_id, type, name, prompt, model_id, owner_user_id, maturity_level)
SELECT 'plat-asset-005', 1, NULL, 'image', '暗夜基地内景概念图', '地下高科技基地内景，暗色调金属质感，全息屏幕闪烁', 'dall-e-3', 1, 'L1'
WHERE NOT EXISTS (SELECT 1 FROM platform_assets WHERE uuid = 'plat-asset-005');

-- ============================================================
-- H. 积分交易记录
-- ============================================================
INSERT INTO credit_transactions (user_id, amount, type, description)
SELECT 1, 1000, 'charge', '充值1000积分'
WHERE NOT EXISTS (SELECT 1 FROM credit_transactions WHERE user_id = 1 AND amount = 1000 AND type = 'charge');

INSERT INTO credit_transactions (user_id, amount, type, description)
SELECT 1, -5, 'consumption', '生成任务 gen-task-001: 角色设计图'
WHERE NOT EXISTS (SELECT 1 FROM credit_transactions WHERE user_id = 1 AND amount = -5 AND type = 'consumption');

INSERT INTO credit_transactions (user_id, amount, type, description)
SELECT 1, -3, 'consumption', '生成任务 gen-task-002: 场景图'
WHERE NOT EXISTS (SELECT 1 FROM credit_transactions WHERE user_id = 1 AND amount = -3 AND type = 'consumption');

INSERT INTO credit_transactions (user_id, amount, type, description)
SELECT 1, 500, 'bonus', '新用户注册奖励'
WHERE NOT EXISTS (SELECT 1 FROM credit_transactions WHERE user_id = 1 AND amount = 500 AND type = 'bonus');

INSERT INTO credit_transactions (user_id, amount, type, description)
SELECT 1, -20, 'consumption', '生成任务 gen-task-003: 视频渲染(失败已退款)'
WHERE NOT EXISTS (SELECT 1 FROM credit_transactions WHERE user_id = 1 AND amount = -20 AND type = 'consumption');

INSERT INTO credit_transactions (user_id, amount, type, description)
SELECT 1, 20, 'refund', '任务 gen-task-003 失败退款'
WHERE NOT EXISTS (SELECT 1 FROM credit_transactions WHERE user_id = 1 AND amount = 20 AND type = 'refund');

INSERT INTO credit_transactions (user_id, amount, type, description)
SELECT 2, 500, 'charge', '充值500积分'
WHERE NOT EXISTS (SELECT 1 FROM credit_transactions WHERE user_id = 2 AND amount = 500 AND type = 'charge');

INSERT INTO credit_transactions (user_id, amount, type, description)
SELECT 2, 100, 'gift', '邀请好友注册奖励'
WHERE NOT EXISTS (SELECT 1 FROM credit_transactions WHERE user_id = 2 AND amount = 100 AND type = 'gift');

-- ============================================================
-- I. 通知数据
-- ============================================================
INSERT INTO notifications (user_id, type, title, content, action_url, is_read)
SELECT 1, 'script_generated', '剧本生成完成', '您的剧本《暗夜追光者》第1-3集已生成完毕，点击查看。', '/script-gen-legacy', 0
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 1 AND type = 'script_generated' AND content LIKE '%暗夜追光者%');

INSERT INTO notifications (user_id, type, title, content, action_url, is_read)
SELECT 1, 'order_paid', '订单支付成功', '您的剧本《长安十二时辰之幻术师》已被购买，收入49.90元已到账。', '/market', 0
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 1 AND type = 'order_paid' AND content LIKE '%长安%');

INSERT INTO notifications (user_id, type, title, content, action_url, is_read)
SELECT 1, 'asset_published', '资产上架成功', '您的角色资产"夜王"已通过审核，正式上架AI资产市场。', '/asset-market', 1
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 1 AND type = 'asset_published' AND content LIKE '%夜王%');

INSERT INTO notifications (user_id, type, title, content, action_url, is_read)
SELECT 1, 'system', '系统维护通知', '平台将于2026年7月5日凌晨2:00-4:00进行系统升级维护，届时部分功能可能不可用。', '', 1
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 1 AND type = 'system' AND content LIKE '%系统升级%');

INSERT INTO notifications (user_id, type, title, content, action_url, is_read)
SELECT 1, 'agent_completed', 'Agent任务完成', '编剧Agent已完成《暗夜追光者》第4-5集的钩子优化建议，点击查看审核报告。', '/agent', 0
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 1 AND type = 'agent_completed' AND content LIKE '%钩子优化%');

INSERT INTO notifications (user_id, type, title, content, action_url, is_read)
SELECT 1, 'audit_failed', '审核未通过', '第3集审核未通过（评分6.5），请根据建议修改后重新提交。', '/script-gen-legacy', 0
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 1 AND type = 'audit_failed' AND content LIKE '%第3集%');

INSERT INTO notifications (user_id, type, title, content, action_url, is_read)
SELECT 2, 'script_listed', '剧本上架成功', '您的剧本《暗夜追光者》已成功上架交易市场。', '/market', 0
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 2 AND type = 'script_listed' AND content LIKE '%暗夜追光者%');

INSERT INTO notifications (user_id, type, title, content, action_url, is_read)
SELECT 2, 'export_completed', '导出完成', '您的画布项目导出已完成，点击下载MP4文件。', '/canvas-projects', 0
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 2 AND type = 'export_completed');

-- ============================================================
-- J. Agent会话与消息
-- ============================================================
INSERT INTO agent_sessions (uuid, user_id, skill_id, title, status)
SELECT 'agent-session-001', 1, NULL, '暗夜追光者 角色设定讨论', 'active'
WHERE NOT EXISTS (SELECT 1 FROM agent_sessions WHERE uuid = 'agent-session-001');

INSERT INTO agent_sessions (uuid, user_id, skill_id, title, status)
SELECT 'agent-session-002', 1, NULL, '第3集钩子优化', 'completed'
WHERE NOT EXISTS (SELECT 1 FROM agent_sessions WHERE uuid = 'agent-session-002');

INSERT INTO agent_sessions (uuid, user_id, skill_id, title, status)
SELECT 'agent-session-003', 2, NULL, '星海迷航 世界观构建', 'active'
WHERE NOT EXISTS (SELECT 1 FROM agent_sessions WHERE uuid = 'agent-session-003');

-- Agent消息
INSERT INTO agent_messages (session_id, role, content)
SELECT 1, 'user', '我想为悬疑剧本《暗夜追光者》设计一个复杂的反派角色"夜王"，你能帮我吗？'
WHERE NOT EXISTS (SELECT 1 FROM agent_messages WHERE session_id = 1 AND content LIKE '我想为悬疑剧本%');

INSERT INTO agent_messages (session_id, role, content)
SELECT 1, 'assistant', '当然可以！让我们一步步来构建"夜王"这个角色。首先，我们需要确定几个核心问题：\n\n1. **动机层**：他创建"暗夜"组织的真正目的是什么？\n2. **身份层**：他的真实身份是否与主角有关联？\n3. **能力层**：他的优势是什么？（智力/资源/武力）\n\n基于悬疑题材的特点，我建议给夜王一个"灰色"动机——他不是纯粹的反派。比如：他可能也在追查同一个幕后势力，只是手段不同。这样能让角色更有深度。'
WHERE NOT EXISTS (SELECT 1 FROM agent_messages WHERE session_id = 1 AND content LIKE '当然可以！让我们一步步%');

INSERT INTO agent_messages (session_id, role, content)
SELECT 1, 'user', '好主意！让我确定一下：夜王的真实身份就是林深失踪的妹妹林小雨的导师，他创建暗夜是为了对抗一个更大的犯罪集团。但他的手段过于极端。'
WHERE NOT EXISTS (SELECT 1 FROM agent_messages WHERE session_id = 1 AND content LIKE '好主意！让我确定一下%');

INSERT INTO agent_messages (session_id, role, content)
SELECT 1, 'assistant', '这个设定非常棒！兄妹关系的反转（导师VS哥哥）可以制造强烈的情感冲突。让我帮你深化这个角色：\n\n**夜王角色卡**\n- 真实身份：陆砚秋，前国安特工，林小雨的导师\n- 年龄：42岁\n- 外貌：半张脸被机械义体替代（某次任务中的"代价"）\n- 性格：冷静到近乎冷酷，但对林小雨有父亲般的保护欲\n- 标志台词："这个世界从来不是非黑即白，林警官。"\n\n需要我继续设计他的能力体系和与主角团的互动关系吗？'
WHERE NOT EXISTS (SELECT 1 FROM agent_messages WHERE session_id = 1 AND content LIKE '这个设定非常棒！%');

INSERT INTO agent_messages (session_id, role, content)
SELECT 2, 'user', '第3集的钩子审核没过，说节奏太慢，帮我分析一下问题在哪。'
WHERE NOT EXISTS (SELECT 1 FROM agent_messages WHERE session_id = 2 AND content LIKE '第3集的钩子审核没过%');

INSERT INTO agent_messages (session_id, role, content)
SELECT 2, 'assistant', '我来分析第3集《卧底试炼》的节奏问题：\n\n**诊断结果：**\n1. 试炼场景占全集的65%，但每项试炼的紧张感在递减（读者预期疲劳）\n2. 关键转折（夜王现身）被放在了末尾5%，来不及展开\n3. 缺少"微观钩子"——每个试炼场景结尾缺少短悬念\n\n**优化建议：**\n- 将三项试炼压缩为两项，增加每项的深度\n- 在第二项试炼中途插入夜王的暗中观察视角\n- 每项试炼结尾增加一个"微观hook"\n\n需要我帮你重写这部分吗？'
WHERE NOT EXISTS (SELECT 1 FROM agent_messages WHERE session_id = 2 AND content LIKE '我来分析第3集%');

INSERT INTO agent_messages (session_id, role, content)
SELECT 3, 'user', '帮我构建《星海迷航》的世界观基础设定，这是一部星际科幻题材。'
WHERE NOT EXISTS (SELECT 1 FROM agent_messages WHERE session_id = 3 AND content LIKE '帮我构建《星海迷航》%');

-- ============================================================
-- K. Skill 数据
-- ============================================================
INSERT INTO skills (uuid, name, description, content, type, version, visibility, owner_id, usage_count, rating, status)
SELECT 'skill-001', '悬疑剧本钩子优化器', '专门优化悬疑题材剧本的钩子结构和反转节奏', '{"prompt":"你是一位悬疑小说编辑，擅长分析钩子结构...","parameters":{"genre":"悬疑","focus":"hook","style":"tight"}}', 'script', '1.0.0', 'public', 1, 230, 4.6, 'published'
WHERE NOT EXISTS (SELECT 1 FROM skills WHERE uuid = 'skill-001');

INSERT INTO skills (uuid, name, description, content, type, version, visibility, owner_id, usage_count, rating, status)
SELECT 'skill-002', '韩漫角色设计助手', '基于角色文字描述生成韩漫风格的角色设计提示词', '{"prompt":"你是一位韩漫角色设计师，将文字描述转化为专业的角色设计提示词...","parameters":{"style":"korean_manhwa","output":"prompt"}}', 'image', '2.1.0', 'public', 1, 450, 4.8, 'published'
WHERE NOT EXISTS (SELECT 1 FROM skills WHERE uuid = 'skill-002');

INSERT INTO skills (uuid, name, description, content, type, version, visibility, owner_id, usage_count, rating, status)
SELECT 'skill-003', '分镜节奏分析器', '分析分镜版本的节奏曲线，识别拖沓和高能段落', '{"prompt":"分析以下分镜版本的节奏分布...","parameters":{"metrics":["pacing","tension","variety"]}}', 'storyboard', '1.2.0', 'public', 1, 180, 4.3, 'published'
WHERE NOT EXISTS (SELECT 1 FROM skills WHERE uuid = 'skill-003');

INSERT INTO skills (uuid, name, description, content, type, version, visibility, owner_id, usage_count, rating, status)
SELECT 'skill-004', '视频分镜自动配乐', '根据分镜情感标签和节奏数据自动推荐BGM', '{"prompt":"基于分镜数据推荐合适的背景音乐...","parameters":{"source":"storyboard","output":"bgm_playlist"}}', 'audio', '1.0.0', 'private', 2, 12, 4.0, 'draft'
WHERE NOT EXISTS (SELECT 1 FROM skills WHERE uuid = 'skill-004');

INSERT INTO skills (uuid, name, description, content, type, version, visibility, owner_id, usage_count, rating, status)
SELECT 'skill-005', '漫画质量检查员', '全自动检查漫画输出的视觉一致性和质量问题', '{"prompt":"检查以下漫画页面的质量...","parameters":{"checks":["consistency","anatomy","composition","color"]}}', 'quality', '3.0.0', 'public', 1, 890, 4.9, 'published'
WHERE NOT EXISTS (SELECT 1 FROM skills WHERE uuid = 'skill-005');

-- ============================================================
-- L. SOP审计数据
-- ============================================================
INSERT INTO sop_audits (uuid, project_id, auditor_id, status, result)
SELECT 'sop-001', 1, 1, 'completed', '{"score":92,"issues":[],"summary":"所有检查项通过，质量优秀"}'
WHERE NOT EXISTS (SELECT 1 FROM sop_audits WHERE uuid = 'sop-001');

INSERT INTO sop_audits (uuid, project_id, auditor_id, status, result)
SELECT 'sop-002', 1, 1, 'completed', '{"score":65,"issues":[{"type":"continuity","severity":"P1","description":"第3集与第2集角色位置不一致"}],"summary":"发现1个P1问题需修复"}'
WHERE NOT EXISTS (SELECT 1 FROM sop_audits WHERE uuid = 'sop-002');

INSERT INTO sop_audits (uuid, project_id, auditor_id, status, result)
SELECT 'sop-003', 1, 2, 'pending', NULL
WHERE NOT EXISTS (SELECT 1 FROM sop_audits WHERE uuid = 'sop-003');

-- ============================================================
-- M. 更多内容项目 (V7 Content Projects)
-- ============================================================
INSERT INTO content_projects (uuid, tenant_type, tenant_id, owner_user_id, name, creation_mode, source_mode, storyboard_intent_status, content_status, production_status, market_status, last_stage_key)
SELECT 'demo-proj-002', 'personal', 2, 2, '暗夜追光者·漫改项目', 'short_drama', 'ai_manual', 'in_progress', 'approved', 'in_progress', 'private', 'storyboard'
WHERE NOT EXISTS (SELECT 1 FROM content_projects WHERE uuid = 'demo-proj-002');

INSERT INTO content_projects (uuid, tenant_type, tenant_id, owner_user_id, name, creation_mode, source_mode, storyboard_intent_status, content_status, production_status, market_status, last_stage_key)
SELECT 'demo-proj-003', 'personal', 1, 1, '长安幻术师·动画短片', 'short_drama', 'ai_generated', 'not_decided', 'draft', 'not_started', 'private', 'synopsis'
WHERE NOT EXISTS (SELECT 1 FROM content_projects WHERE uuid = 'demo-proj-003');

INSERT INTO content_projects (uuid, tenant_type, tenant_id, owner_user_id, name, creation_mode, source_mode, storyboard_intent_status, content_status, production_status, market_status, last_stage_key)
SELECT 'demo-proj-004', 'enterprise', 1, 4, '星辰动漫·都市爱情系列', 'long_form', 'uploaded', 'not_decided', 'reviewing', 'not_started', 'private', 'import_review'
WHERE NOT EXISTS (SELECT 1 FROM content_projects WHERE uuid = 'demo-proj-004');

INSERT INTO content_projects (uuid, tenant_type, tenant_id, owner_user_id, name, creation_mode, source_mode, storyboard_intent_status, content_status, production_status, market_status, last_stage_key)
SELECT 'demo-proj-005', 'personal', 1, 1, 'TVC·新能量饮料推广', 'tvc', 'ai_manual', 'not_decided', 'draft', 'not_started', 'private', 'tvc_brief'
WHERE NOT EXISTS (SELECT 1 FROM content_projects WHERE uuid = 'demo-proj-005');

-- 项目成员
INSERT INTO project_members (project_id, user_id, role)
SELECT 2, 2, 'owner'
WHERE NOT EXISTS (SELECT 1 FROM project_members WHERE project_id = 2 AND user_id = 2);

INSERT INTO project_members (project_id, user_id, role)
SELECT 3, 1, 'owner'
WHERE NOT EXISTS (SELECT 1 FROM project_members WHERE project_id = 3 AND user_id = 1);

INSERT INTO project_members (project_id, user_id, role)
SELECT 4, 4, 'owner'
WHERE NOT EXISTS (SELECT 1 FROM project_members WHERE project_id = 4 AND user_id = 4);

INSERT INTO project_members (project_id, user_id, role)
SELECT 4, 2, 'writer'
WHERE NOT EXISTS (SELECT 1 FROM project_members WHERE project_id = 4 AND user_id = 2);

INSERT INTO project_members (project_id, user_id, role)
SELECT 5, 1, 'owner'
WHERE NOT EXISTS (SELECT 1 FROM project_members WHERE project_id = 5 AND user_id = 1);

-- 内容单元 (为项目2添加多个episode)
INSERT INTO content_units (stable_key, project_id, unit_type, display_no, title, status, revision)
SELECT 'unit-002', 2, 'episode', 1, '第一集·消失的妹妹', 'completed', 0
WHERE NOT EXISTS (SELECT 1 FROM content_units WHERE stable_key = 'unit-002');

INSERT INTO content_units (stable_key, project_id, unit_type, display_no, title, status, revision)
SELECT 'unit-003', 2, 'episode', 2, '第二集·暗夜初现', 'completed', 0
WHERE NOT EXISTS (SELECT 1 FROM content_units WHERE stable_key = 'unit-003');

INSERT INTO content_units (stable_key, project_id, unit_type, display_no, title, status, revision)
SELECT 'unit-004', 2, 'episode', 3, '第三集·卧底试炼', 'reviewing', 0
WHERE NOT EXISTS (SELECT 1 FROM content_units WHERE stable_key = 'unit-004');

INSERT INTO content_units (stable_key, project_id, unit_type, display_no, title, status, revision)
SELECT 'unit-005', 3, 'episode', 1, '第一集·长安幻术师', 'draft', 0
WHERE NOT EXISTS (SELECT 1 FROM content_units WHERE stable_key = 'unit-005');

INSERT INTO content_units (stable_key, project_id, unit_type, display_no, title, status, revision)
SELECT 'unit-006', 4, 'chapter', 1, '第一章', 'draft', 0
WHERE NOT EXISTS (SELECT 1 FROM content_units WHERE stable_key = 'unit-006');

-- 内容版本
INSERT INTO content_versions (project_id, content_unit_id, version_no, status, content_json, plain_text, source, content_hash, created_by)
SELECT 2, 2, 1, 'locked', '{"title":"消失的妹妹","scenes":["码头搜查","神秘人出现"]}', '林深站在废弃码头的集装箱前...', 'ai_generated', 'hash-002', 2
WHERE NOT EXISTS (SELECT 1 FROM content_versions WHERE project_id = 2 AND content_unit_id = 2 AND version_no = 1);

INSERT INTO content_versions (project_id, content_unit_id, version_no, status, content_json, plain_text, source, content_hash, created_by)
SELECT 2, 3, 1, 'locked', '{"title":"暗夜初现"}', '暗夜不是你想的那样...', 'ai_generated', 'hash-003', 2
WHERE NOT EXISTS (SELECT 1 FROM content_versions WHERE project_id = 2 AND content_unit_id = 3 AND version_no = 1);

INSERT INTO content_versions (project_id, content_unit_id, version_no, status, content_json, plain_text, source, content_hash, created_by)
SELECT 2, 4, 1, 'draft', '{"title":"卧底试炼"}', '加入暗夜的仪式比想象中复杂...', 'ai_generated', 'hash-004', 2
WHERE NOT EXISTS (SELECT 1 FROM content_versions WHERE project_id = 2 AND content_unit_id = 4 AND version_no = 1);

-- 内容单元钩子
INSERT INTO content_unit_hooks (content_unit_id, content_version_id, opening_hook, closing_hook, hook_score)
SELECT 2, 2, '妹妹失踪的真相究竟是什么？', '码头尽头的神秘身影...', 8.5
WHERE NOT EXISTS (SELECT 1 FROM content_unit_hooks WHERE content_unit_id = 2);

INSERT INTO content_unit_hooks (content_unit_id, content_version_id, opening_hook, closing_hook, hook_score)
SELECT 3, 3, '暗夜组织到底是敌是友？', '林深踏入暗夜之门...', 8.8
WHERE NOT EXISTS (SELECT 1 FROM content_unit_hooks WHERE content_unit_id = 3);

INSERT INTO content_unit_hooks (content_unit_id, content_version_id, opening_hook, closing_hook, hook_score)
SELECT 4, 4, '72小时的极限试炼开始！', '夜王的真面目即将揭晓...', 7.2
WHERE NOT EXISTS (SELECT 1 FROM content_unit_hooks WHERE content_unit_id = 4);

-- ============================================================
-- N. 内容生成任务
-- ============================================================
INSERT INTO content_generation_jobs (uuid, project_id, job_type, target_type, target_id, status, input_snapshot_json, input_snapshot_hash, schema_version, model, estimated_credits, actual_credits, idempotency_key, created_by, finished_at)
SELECT 'cgj-001', 2, 'episode_generation', 'content_unit', 2, 'completed', '{"params":{"episode":1,"genre":"悬疑"}}', 'hash-input-001', 'v7.1', 'claude-sonnet-4-6', 50, 48, 'idem-cgj-001', 2, DATEADD('HOUR', -3, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM content_generation_jobs WHERE uuid = 'cgj-001');

INSERT INTO content_generation_jobs (uuid, project_id, job_type, target_type, target_id, status, input_snapshot_json, input_snapshot_hash, schema_version, model, estimated_credits, actual_credits, idempotency_key, created_by)
SELECT 'cgj-002', 2, 'storyboard_generation', 'cp_storyboard_master', 1, 'running', '{"params":{"tier":"A","episode":1}}', 'hash-input-002', 'v7.1', 'gpt-4o', 30, 0, 'idem-cgj-002', 2
WHERE NOT EXISTS (SELECT 1 FROM content_generation_jobs WHERE uuid = 'cgj-002');

INSERT INTO content_generation_jobs (uuid, project_id, job_type, target_type, target_id, status, input_snapshot_json, input_snapshot_hash, schema_version, model, estimated_credits, actual_credits, error_code, idempotency_key, created_by, finished_at)
SELECT 'cgj-003', 2, 'hook_analysis', 'content_unit', 4, 'failed', '{"params":{"unit_id":4}}', 'hash-input-003', 'v7.1', 'claude-haiku-4-5', 10, 10, 'AI_TIMEOUT', 'idem-cgj-003', 2, DATEADD('HOUR', -1, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM content_generation_jobs WHERE uuid = 'cgj-003');

-- ============================================================
-- O. CP Storyboard 数据 (V7分镜)
-- ============================================================
INSERT INTO cp_storyboard_masters (uuid, project_id, content_unit_id, tier, status, total_shots, estimated_duration_sec, source_version_id, revision)
SELECT 'cp-sbm-001', 2, 2, 'A', 'draft', 35, 150, 2, 0
WHERE NOT EXISTS (SELECT 1 FROM cp_storyboard_masters WHERE uuid = 'cp-sbm-001');

INSERT INTO cp_storyboard_scenes (master_id, scene_no, dramatic_goal, beat_description, duration_sec, sort_order)
SELECT 1, 1, '建立悬疑氛围，引出主角动机', '码头夜查→发现线索→神秘人现身', 45, 0
WHERE NOT EXISTS (SELECT 1 FROM cp_storyboard_scenes WHERE master_id = 1 AND scene_no = 1);

INSERT INTO cp_storyboard_scenes (master_id, scene_no, dramatic_goal, beat_description, duration_sec, sort_order)
SELECT 1, 2, '揭示暗夜组织，制造第一次反转', '跟踪神秘人→暗夜入口→初入基地', 55, 1
WHERE NOT EXISTS (SELECT 1 FROM cp_storyboard_scenes WHERE master_id = 1 AND scene_no = 2);

INSERT INTO cp_storyboard_scenes (master_id, scene_no, dramatic_goal, beat_description, duration_sec, sort_order)
SELECT 1, 3, '试炼开始，建立规则和代价', '夜王现身→宣布试炼规则→第一项任务', 50, 2
WHERE NOT EXISTS (SELECT 1 FROM cp_storyboard_scenes WHERE master_id = 1 AND scene_no = 3);

-- CP分镜镜头 (场景1的3个镜头)
INSERT INTO cp_storyboard_shots (uuid, scene_id, master_id, shot_no, shot_type, duration_sec, description, camera_action, status, sort_order, image_prompt, video_prompt)
SELECT 'cp-shot-001', 1, 1, 1, '全景', 15, '夜幕下的废弃码头全景，集装箱如巨大墓碑林立，月光透过缝隙洒下冷光', '缓慢推进(Dolly In)，从码头入口推向深处', 'draft', 0, 'wide shot, abandoned dock at night, moonlight through container gaps, noir atmosphere, cinematic', 'slow dolly forward into the dock'
WHERE NOT EXISTS (SELECT 1 FROM cp_storyboard_shots WHERE uuid = 'cp-shot-001');

INSERT INTO cp_storyboard_shots (uuid, scene_id, master_id, shot_no, shot_type, duration_sec, description, camera_action, status, sort_order, image_prompt, video_prompt)
SELECT 'cp-shot-002', 1, 1, 2, '中景', 12, '林深手持电筒在集装箱间穿行，光束扫过角落，照到一只沾血的鞋子', '跟拍(Tracking)，跟随主角移动', 'draft', 1, 'medium shot, detective with flashlight walking between containers, beam of light revealing clues, tense', 'tracking shot following detective'
WHERE NOT EXISTS (SELECT 1 FROM cp_storyboard_shots WHERE uuid = 'cp-shot-002');

INSERT INTO cp_storyboard_shots (uuid, scene_id, master_id, shot_no, shot_type, duration_sec, description, camera_action, status, sort_order, image_prompt, video_prompt)
SELECT 'cp-shot-003', 1, 1, 3, '特写', 8, '林深瞳孔收缩，手中的手电筒照亮角落里的血迹和妹妹的护身符', '快速推进(Crash Zoom)到护身符', 'draft', 2, 'extreme close-up, eyes widening, flashlight beam on bloodstained amulet, shallow depth of field', 'crash zoom to amulet, handheld shake'
WHERE NOT EXISTS (SELECT 1 FROM cp_storyboard_shots WHERE uuid = 'cp-shot-003');

-- ============================================================
-- P. Storyboard Professional V2 附加数据
-- ============================================================
-- 情绪节奏段
INSERT INTO storyboard_emotion_segments (version_id, emotion_type, shot_range, intensity, core_expression, sort_order)
SELECT 1, '紧张/悬疑', 'S01-C01 ~ S01-C03', 7, '从平静到警觉，紧张感逐步升级', 0
WHERE NOT EXISTS (SELECT 1 FROM storyboard_emotion_segments WHERE version_id = 1 AND emotion_type = '紧张/悬疑');

INSERT INTO storyboard_emotion_segments (version_id, emotion_type, shot_range, intensity, core_expression, sort_order)
SELECT 1, '对峙/冲突', 'S02-C01 ~ S02-C02', 8, '屋顶对峙，愤怒与揭示交织', 1
WHERE NOT EXISTS (SELECT 1 FROM storyboard_emotion_segments WHERE version_id = 1 AND emotion_type = '对峙/冲突');

-- 提示词模板
INSERT INTO storyboard_prompt_templates (version_id, template_code, emotion_name, shot_refs_json, image_prompt, video_motion_prompt, sort_order)
SELECT 1, 'TEMPLATE_NIGHT_ALLEY', '紧张悬疑', '["shot-key-1","shot-key-2","shot-key-3"]', 'cinematic shot, {location} at night, {lighting}, {character_action}, volumetric lighting, hyperrealistic, 8k, noir style', 'slow {camera_motion}, atmospheric fog particles', 0
WHERE NOT EXISTS (SELECT 1 FROM storyboard_prompt_templates WHERE version_id = 1 AND template_code = 'TEMPLATE_NIGHT_ALLEY');

INSERT INTO storyboard_prompt_templates (version_id, template_code, emotion_name, shot_refs_json, image_prompt, video_motion_prompt, sort_order)
SELECT 1, 'TEMPLATE_ROOFTOP', '对峙冲突', '["shot-key-4","shot-key-5"]', 'low angle, {character} on rooftop against moon, silhouette, dramatic lighting, wuxia cinematic style', 'camera {camera_motion}, wind effects, dramatic reveal', 1
WHERE NOT EXISTS (SELECT 1 FROM storyboard_prompt_templates WHERE version_id = 1 AND template_code = 'TEMPLATE_ROOFTOP');

-- 创意规则
INSERT INTO storyboard_creative_rules (version_id, rule_type, dimension_name, principle, implementation_text, status, sort_order)
SELECT 1, 'visual', '色彩对比', '冷蓝vs血红：体现主角理性与暗夜暴力的冲突', '夜景场景使用冷蓝色调（色温5600K），关键物品（血迹/护身符）用暖红色强调', 'active', 0
WHERE NOT EXISTS (SELECT 1 FROM storyboard_creative_rules WHERE version_id = 1 AND rule_type = 'visual' AND dimension_name = '色彩对比');

INSERT INTO storyboard_creative_rules (version_id, rule_type, dimension_name, principle, implementation_text, status, sort_order)
SELECT 1, 'camera', '手持感', '跟踪镜头使用微晃手持风格，增强第一人称代入感', '林深POV镜头使用4-8Hz微震动，对峙场景使用稳定锁头', 'active', 1
WHERE NOT EXISTS (SELECT 1 FROM storyboard_creative_rules WHERE version_id = 1 AND rule_type = 'camera' AND dimension_name = '手持感');

INSERT INTO storyboard_creative_rules (version_id, rule_type, dimension_name, principle, implementation_text, status, sort_order)
SELECT 1, 'editing', '节奏控制', '每3-4个慢镜头后插入一个快速剪辑，制造呼吸感', '悬疑段落平均镜头长度3-5秒，动作段落1-2秒', 'active', 2
WHERE NOT EXISTS (SELECT 1 FROM storyboard_creative_rules WHERE version_id = 1 AND rule_type = 'editing' AND dimension_name = '节奏控制');

-- 人物视觉规范
INSERT INTO storyboard_character_visuals (version_id, character_name, core_identity, daily_look, task_look, performance_anchor, prompt_lock, sort_order)
SELECT 1, '林夜(主角)', '25岁，前刑警，冷峻内敛，身高182cm，习惯性按刀柄', '黑色风衣、深灰高领毛衣、深色牛仔裤', '黑色战术夹克、腰间配刀、手套', '眼神先动→身体再跟→最后是动作', 'Chinese male detective, 25yo, short black hair, sharp jawline, tall lean build, wearing black trench coat, cold expression, realistic anime style', 0
WHERE NOT EXISTS (SELECT 1 FROM storyboard_character_visuals WHERE version_id = 1 AND character_name = '林夜(主角)');

INSERT INTO storyboard_character_visuals (version_id, character_name, core_identity, daily_look, task_look, performance_anchor, prompt_lock, sort_order)
SELECT 1, '蒙面人/夜王', '42岁，半机械改造，神秘冷酷，亦敌亦友', '斗篷+面具（前期）/ 高领大衣（后期）', '机械面具、金属义肢、暗色战术装备', '先摘面具→金属反光→嘴角微扬', 'cyborg antagonist, half mechanical face, liquid metal surface, dark cloak, glowing eye, cyberpunk meets wuxia, menacing yet elegant', 1
WHERE NOT EXISTS (SELECT 1 FROM storyboard_character_visuals WHERE version_id = 1 AND character_name = '蒙面人/夜王');

-- 镜头-人物视觉绑定
INSERT INTO storyboard_shot_visual_bindings (version_id, shot_id, character_visual_id, application_note, anti_drift_requirement)
SELECT 1, 1, 1, '全景入场，风衣下摆飘动幅度15-20度', '风衣颜色#1a1a1a，不可偏蓝或偏棕'
WHERE NOT EXISTS (SELECT 1 FROM storyboard_shot_visual_bindings WHERE version_id = 1 AND shot_id = 1 AND character_visual_id = 1);

INSERT INTO storyboard_shot_visual_bindings (version_id, shot_id, character_visual_id, application_note, anti_drift_requirement)
SELECT 1, 4, 1, '低角度英雄镜头，月光勾勒剪影', '身形比例保持182cm/78kg视觉标准'
WHERE NOT EXISTS (SELECT 1 FROM storyboard_shot_visual_bindings WHERE version_id = 1 AND shot_id = 4 AND character_visual_id = 1);

INSERT INTO storyboard_shot_visual_bindings (version_id, shot_id, character_visual_id, application_note, anti_drift_requirement)
SELECT 1, 5, 2, '摘面具关键帧，机械面部高光需流动', '金属表面反射率0.85，色彩#c0c0d0'
WHERE NOT EXISTS (SELECT 1 FROM storyboard_shot_visual_bindings WHERE version_id = 1 AND shot_id = 5 AND character_visual_id = 2);

-- 审核问题
INSERT INTO storyboard_review_issues (version_id, fingerprint, issue_type, severity, shot_id, message, evidence, suggestion, status)
SELECT 1, 'fp-001', 'visual_continuity', 'P1', 3, '血迹颜色在shot-003和shot-001中不一致', 'shot-001: 暗红色 #8B0000, shot-003: 亮红色 #FF0000', '统一使用暗红色 #8B0000，考虑月光下的色温偏移', 'open'
WHERE NOT EXISTS (SELECT 1 FROM storyboard_review_issues WHERE version_id = 1 AND fingerprint = 'fp-001');

INSERT INTO storyboard_review_issues (version_id, fingerprint, issue_type, severity, shot_id, message, evidence, suggestion, status)
SELECT 1, 'fp-002', 'pacing', 'P2', 4, '屋顶跃上镜头时长4.5s偏长，建议压缩至3.5s', '同类动作场景行业标准2.5-3.5s', '将shot-004 duration_ms 调整为3500', 'open'
WHERE NOT EXISTS (SELECT 1 FROM storyboard_review_issues WHERE version_id = 1 AND fingerprint = 'fp-002');

INSERT INTO storyboard_review_issues (version_id, fingerprint, issue_type, severity, shot_id, message, evidence, suggestion, status, resolution_note, resolved_by, resolved_at)
SELECT 1, 'fp-003', 'dialogue', 'P2', 5, '台词"你还记得五年前的那个夜晚吗？"与前文缺少铺垫', '前4个镜头未提及五年前事件', '在shot-003中加入一个闪回暗示（如护身符特写）', 'resolved', '已在shot-003中加入护身符特写镜头', 1, DATEADD('HOUR', -2, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM storyboard_review_issues WHERE version_id = 1 AND fingerprint = 'fp-003');

-- 分镜任务
INSERT INTO storyboard_jobs (uuid, project_id, storyboard_id, version_id, job_type, status, idempotency_key, progress_percent, current_stage, created_by, started_at, finished_at)
SELECT 'sb-job-001', 1, 1, 1, 'consistency_check', 'completed', 'idem-check-001', 100, 'done', 1, DATEADD('HOUR', -5, CURRENT_TIMESTAMP), DATEADD('HOUR', -4, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM storyboard_jobs WHERE uuid = 'sb-job-001');

INSERT INTO storyboard_jobs (uuid, project_id, storyboard_id, version_id, job_type, status, idempotency_key, progress_percent, current_stage, created_by, started_at)
SELECT 'sb-job-002', 1, 1, 1, 'prompt_generation', 'running', 'idem-prompt-001', 60, 'generating_image_prompts', 1, DATEADD('MINUTE', -30, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM storyboard_jobs WHERE uuid = 'sb-job-002');

-- 分镜审计日志
INSERT INTO storyboard_audit_logs (version_id, actor_user_id, action_type, target_type, target_id, operation_id, created_at)
SELECT 1, 1, 'create_version', 'storyboard_version', 1, 'op-001', DATEADD('HOUR', -6, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM storyboard_audit_logs WHERE version_id = 1 AND operation_id = 'op-001');

INSERT INTO storyboard_audit_logs (version_id, actor_user_id, action_type, target_type, target_id, operation_id, created_at)
SELECT 1, 1, 'add_shot', 'storyboard_version_shot', 1, 'op-002', DATEADD('HOUR', -5, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM storyboard_audit_logs WHERE version_id = 1 AND operation_id = 'op-002');

INSERT INTO storyboard_audit_logs (version_id, actor_user_id, action_type, target_type, target_id, operation_id, created_at)
SELECT 1, 1, 'resolve_issue', 'storyboard_review_issue', 3, 'op-003', DATEADD('HOUR', -2, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM storyboard_audit_logs WHERE version_id = 1 AND operation_id = 'op-003');

-- ============================================================
-- Q. 世界构建数据 (Worldbuilding — M3 Long-form)
-- ============================================================
-- 角色档案 (属于 content_project id=2: 暗夜追光者)
INSERT INTO character_profiles (project_id, name, role, archetype, appearance, personality, motivation, long_term_goal, dialogue_style, backstory, status)
SELECT 2, '林深', '主角', '反英雄/孤独的追寻者', '32岁，前刑警队长，身高183cm，黑色短发微乱，眼神锐利但疲惫，常穿深色便装夹克', '冷静、执着、正义感强但手段灵活，有自我毁灭倾向', '找到失踪的妹妹林小雨，揭开她消失背后的真相', '摧毁"暗夜"背后的真正黑手，重建自己的人生', '言简意赅，善于反问，偶尔有黑色幽默', '曾是警队最年轻的刑警队长，因妹妹失踪案与上级发生冲突后辞职。精通刑侦、格斗和心理学。', 'draft'
WHERE NOT EXISTS (SELECT 1 FROM character_profiles WHERE project_id = 2 AND name = '林深');

INSERT INTO character_profiles (project_id, name, role, archetype, appearance, personality, motivation, long_term_goal, dialogue_style, backstory, status)
SELECT 2, '夜王/陆砚秋', '反派/导师', '灰色导师/悲剧反派', '42岁，半张脸被机械义体覆盖，身高188cm，穿暗色定制西装或斗篷', '冷酷但不残忍，有自己的一套道德准则，对弟子林小雨有父亲般的保护欲', '用极端手段对抗更大的犯罪集团"深渊"，为过去牺牲的战友复仇', '摧毁"深渊"，然后接受法律的审判', '言谈优雅但暗藏威胁，喜欢用隐喻，标志句："这个世界从来不是非黑即白"', '前国安特工，五年前的一次行动中被背叛，全队覆没。重伤后被机械改造，从此以"夜王"身份活动。在流浪中发现了林小雨的天赋，收为弟子。', 'draft'
WHERE NOT EXISTS (SELECT 1 FROM character_profiles WHERE project_id = 2 AND name = '夜王/陆砚秋');

INSERT INTO character_profiles (project_id, name, role, archetype, appearance, personality, motivation, long_term_goal, dialogue_style, backstory, status)
SELECT 2, '林小雨', '关键人物/麦高芬', '失踪的天才/隐藏的钥匙', '19岁，黑色长发，面容与哥哥有七分相似但更柔和', '聪明绝顶但天真，在被导师培养后变得更加沉稳', '在"暗夜"中找到自己的价值和归属', '在哥哥和导师之间找到第三条路', '前期天真好奇，后期沉稳冷静', '计算机天才，16岁即被发现拥有罕见的密码学天赋。三年前被"深渊"盯上，被陆砚秋救下后成为其弟子。她的失踪是推动整个故事的引擎。', 'draft'
WHERE NOT EXISTS (SELECT 1 FROM character_profiles WHERE project_id = 2 AND name = '林小雨');

-- 情节任务
INSERT INTO plot_tasks (project_id, task_type, title, description, stage_goals, obstacles, character_ids, status, sort_order)
SELECT 2, 'main', '潜入暗夜', '林深以卧底身份加入暗夜组织，赢取夜王的信任', '1.通过三轮试炼 2.找到妹妹线索 3.不被识破身份', '组织的严密审查、其他成员的怀疑、道德困境', '1,2,3', 'in_progress', 0
WHERE NOT EXISTS (SELECT 1 FROM plot_tasks WHERE project_id = 2 AND title = '潜入暗夜');

INSERT INTO plot_tasks (project_id, task_type, title, description, stage_goals, obstacles, character_ids, status, sort_order)
SELECT 2, 'main', '揭露深渊', '在夜王的引导下，发现真正的敌人——犯罪集团"深渊"', '1.获取深渊情报 2.理解夜王的动机 3.决定是否合作', '信息不对称、FBI/警方介入、信任危机', '1,2', 'planned', 1
WHERE NOT EXISTS (SELECT 1 FROM plot_tasks WHERE project_id = 2 AND title = '揭露深渊');

INSERT INTO plot_tasks (project_id, task_type, title, description, stage_goals, obstacles, character_ids, status, sort_order)
SELECT 2, 'sub', '兄妹重逢', '林深最终找到妹妹，但发现她已不再是当年的小女孩', '1.定位妹妹 2.面对她的选择 3.接受或改变', '妹妹不愿意离开暗夜、夜王的反对', '1,3', 'planned', 2
WHERE NOT EXISTS (SELECT 1 FROM plot_tasks WHERE project_id = 2 AND title = '兄妹重逢');

-- 卷大纲
INSERT INTO volume_outlines (project_id, volume_no, title, goal, turns, volume_end_hook, chapter_count, status, sort_order)
SELECT 2, 1, '第一卷·暗夜之门', '林深加入暗夜，通过试炼，获得初步信任', '码头追查→神秘人现身→加入暗夜→三轮试炼→初见夜王', '夜王摘下面具的瞬间，林深发现那张脸上有与妹妹相同的刺青...', 12, 'completed', 0
WHERE NOT EXISTS (SELECT 1 FROM volume_outlines WHERE project_id = 2 AND volume_no = 1);

INSERT INTO volume_outlines (project_id, volume_no, title, goal, turns, volume_end_hook, chapter_count, status, sort_order)
SELECT 2, 2, '第二卷·深渊之影', '林深参与第一次暗夜任务，逐渐发现真相', '首次任务→接触深渊→内部叛徒→夜王的过去→矛盾升级', '夜王重伤倒地，对林深说："保护好小雨...她才是他们的目标。"', 15, 'draft', 1
WHERE NOT EXISTS (SELECT 1 FROM volume_outlines WHERE project_id = 2 AND volume_no = 2);

-- 世界地点
INSERT INTO world_locations (project_id, name, tier, description, area_type, visual_reference)
SELECT 2, '废弃货运码头', 'L2', '城市东郊的废弃港口，90年代曾是繁华的货运枢纽。集装箱堆叠如同巨大的钢铁迷宫，海风穿过缝隙发出呜咽声。', 'urban_decay', '参考《银翼杀手2049》中的废弃工厂场景，但保留现代中国港口特色'
WHERE NOT EXISTS (SELECT 1 FROM world_locations WHERE project_id = 2 AND name = '废弃货运码头');

INSERT INTO world_locations (project_id, name, tier, description, area_type, visual_reference)
SELECT 2, '暗夜基地', 'L2', '位于码头地下30米深处的秘密基地。原为冷战时期的防空洞，被夜王改造为高科技据点。金属走廊、全息屏幕、恒温恒湿。', 'underground_bunker', '参考《攻壳机动队》中的公安九课总部，融合工业风与高科技'
WHERE NOT EXISTS (SELECT 1 FROM world_locations WHERE project_id = 2 AND name = '暗夜基地');

INSERT INTO world_locations (project_id, name, tier, description, area_type, visual_reference)
SELECT 2, '林深的公寓', 'L1', '城市中心的单身公寓，简约但凌乱。墙上贴满了妹妹失踪案的线索地图和照片。窗户永远半拉着百叶窗。', 'urban_apartment', '参考经典侦探电影的调查墙，体现主角的执念'
WHERE NOT EXISTS (SELECT 1 FROM world_locations WHERE project_id = 2 AND name = '林深的公寓');

-- 故事时间线
INSERT INTO story_timeline (project_id, event_name, description, relative_time, involved_characters, sort_order)
SELECT 2, '林小雨失踪', '19岁的林小雨在放学路上失踪，监控最后拍到她在码头附近出现', '故事开始前3个月', '1,3', 0
WHERE NOT EXISTS (SELECT 1 FROM story_timeline WHERE project_id = 2 AND event_name = '林小雨失踪');

INSERT INTO story_timeline (project_id, event_name, description, relative_time, involved_characters, sort_order)
SELECT 2, '林深辞职', '林深因调查方向与上级冲突，从警队辞职开始独立调查', '故事开始前2个月', '1', 1
WHERE NOT EXISTS (SELECT 1 FROM story_timeline WHERE project_id = 2 AND event_name = '林深辞职');

INSERT INTO story_timeline (project_id, event_name, description, relative_time, involved_characters, sort_order)
SELECT 2, '码头追踪', '林深在码头追踪线索时首次遭遇暗夜成员', '第1集', '1', 2
WHERE NOT EXISTS (SELECT 1 FROM story_timeline WHERE project_id = 2 AND event_name = '码头追踪');

INSERT INTO story_timeline (project_id, event_name, description, relative_time, involved_characters, sort_order)
SELECT 2, '加入暗夜', '林深通过试炼，正式成为暗夜成员', '第3-4集', '1,2,3', 3
WHERE NOT EXISTS (SELECT 1 FROM story_timeline WHERE project_id = 2 AND event_name = '加入暗夜');

INSERT INTO story_timeline (project_id, event_name, description, relative_time, involved_characters, sort_order)
SELECT 2, '夜王揭面', '夜王首次摘下面具，揭示真实身份', '第5集末尾', '1,2', 4
WHERE NOT EXISTS (SELECT 1 FROM story_timeline WHERE project_id = 2 AND event_name = '夜王揭面');

-- 伏笔项
INSERT INTO foreshadowing_items (project_id, description, planted_in_unit_id, payoff_in_unit_id, status, category, character_ids)
SELECT 2, '林深公寓墙上的照片中，有一张照到了夜王年轻时的背影', 2, NULL, 'planted', 'visual', '1,2'
WHERE NOT EXISTS (SELECT 1 FROM foreshadowing_items WHERE project_id = 2 AND description LIKE '%夜王年轻时的背影%');

INSERT INTO foreshadowing_items (project_id, description, planted_in_unit_id, payoff_in_unit_id, status, category, character_ids)
SELECT 2, '妹妹的护身符内部藏有加密芯片', 2, 4, 'planted', 'prop', '1,3'
WHERE NOT EXISTS (SELECT 1 FROM foreshadowing_items WHERE project_id = 2 AND description LIKE '%加密芯片%');

INSERT INTO foreshadowing_items (project_id, description, planted_in_unit_id, payoff_in_unit_id, status, category, character_ids)
SELECT 2, '第一集码头集装箱上的陌生标记，是深渊组织的暗号', 2, NULL, 'planted', 'clue', '1,2'
WHERE NOT EXISTS (SELECT 1 FROM foreshadowing_items WHERE project_id = 2 AND description LIKE '%陌生标记%');

-- ============================================================
-- R. TVC 商业脚本数据 (project_id=5)
-- ============================================================
INSERT INTO tvc_briefs (project_id, brand_name, product_name, target_audience, budget, platforms, duration, additional_notes, status)
SELECT 5, '脉动星', '脉动星·觉醒能量饮料', '18-30岁年轻职场人群，追求活力与效率', '50-100万', '抖音,小红书,B站', '30秒', '需要突出"自然能量、快速恢复"的核心卖点，避免传统功能饮料的刻板印象', 'draft'
WHERE NOT EXISTS (SELECT 1 FROM tvc_briefs WHERE project_id = 5);

INSERT INTO brand_facts (project_id, fact_type, content, evidence_status, is_must_express)
SELECT 5, 'product_claim', '100%天然植物提取成分，不含人工咖啡因', 'verified', 'yes'
WHERE NOT EXISTS (SELECT 1 FROM brand_facts WHERE project_id = 5 AND content LIKE '%天然植物%');

INSERT INTO brand_facts (project_id, fact_type, content, evidence_status, is_must_express)
SELECT 5, 'product_claim', '饮用后15分钟开始发挥作用，持续4-6小时', 'verified', 'yes'
WHERE NOT EXISTS (SELECT 1 FROM brand_facts WHERE project_id = 5 AND content LIKE '%15分钟%');

INSERT INTO brand_facts (project_id, fact_type, content, evidence_status, is_must_express)
SELECT 5, 'brand_value', '品牌理念：唤醒每一个奋斗的灵魂', 'verified', 'yes'
WHERE NOT EXISTS (SELECT 1 FROM brand_facts WHERE project_id = 5 AND content LIKE '%唤醒%');

INSERT INTO brand_facts (project_id, fact_type, content, evidence_status, is_must_not_express)
SELECT 5, 'competitor_mention', '请勿暗示或比较竞品（红牛、魔爪等）', 'verified', 'yes'
WHERE NOT EXISTS (SELECT 1 FROM brand_facts WHERE project_id = 5 AND content LIKE '%竞品%');

INSERT INTO creative_strategies (project_id, angle_no, angle_name, opening_hook, value_proposition, brand_memory_point, platform, status)
SELECT 5, 1, '深夜加班·觉醒时刻', '凌晨2点，办公室里只剩他一个人。他看着屏幕上的倒计时，打开了那罐银色的饮料...', '在每一个需要坚持的深夜，脉动星给你最自然的能量支撑', '银色罐身上的星轨图案在黑暗中微微发光', '抖音', 'draft'
WHERE NOT EXISTS (SELECT 1 FROM creative_strategies WHERE project_id = 5 AND angle_no = 1);

INSERT INTO creative_strategies (project_id, angle_no, angle_name, opening_hook, value_proposition, brand_memory_point, platform, status)
SELECT 5, 2, '晨跑·新的一天', '城市还在沉睡，她已经在江边跑了五公里。停下来喝水的那一刻，阳光刚好升起。', '脉动星陪你迎接每一个清晨的第一缕阳光', '易拉罐开启的声音与日出同步', '小红书', 'draft'
WHERE NOT EXISTS (SELECT 1 FROM creative_strategies WHERE project_id = 5 AND angle_no = 2);

INSERT INTO tvc_scripts (project_id, source_unit_id, version_name, content_json, plain_text, duration_sec, platforms, status, content_hash)
SELECT 5, NULL, 'v1-深夜加班版', '{"scenes":[{"id":1,"description":"深夜办公室全景","duration":5},{"id":2,"description":"主角困倦特写","duration":5},{"id":3,"description":"打开脉动星","duration":8},{"id":4,"description":"喝下后精力恢复","duration":7},{"id":5,"description":"产品展示+品牌口号","duration":5}]}', '深夜办公室，一个年轻人正在加班。困意袭来，他拿起了脉动星。喝下之后，眼神重新聚焦，手指在键盘上飞舞。脉动星，唤醒每一个奋斗的灵魂。', 30, '抖音,B站', 'draft', 'hash-tvc-001'
WHERE NOT EXISTS (SELECT 1 FROM tvc_scripts WHERE project_id = 5 AND version_name = 'v1-深夜加班版');

-- ============================================================
-- S. 质量报告
-- ============================================================
INSERT INTO quality_reports (uuid, project_id, canvas_project_id, correctness_score, security_score, performance_score, cost_score, consistency_score, issues_json, summary, status)
SELECT 'qr-001', 2, NULL, 85, 90, 78, 82, 88, '[{"type":"continuity","severity":"minor","desc":"第2集与第3集码头场景色调有轻微差异"}]', '整体质量良好，连续性方面有1个小问题建议修正', 'open'
WHERE NOT EXISTS (SELECT 1 FROM quality_reports WHERE uuid = 'qr-001');

INSERT INTO quality_reports (uuid, project_id, canvas_project_id, correctness_score, security_score, performance_score, cost_score, consistency_score, issues_json, summary, status)
SELECT 'qr-002', 2, NULL, 92, 95, 85, 88, 94, '[]', '全部指标达到A级标准，建议通过', 'open'
WHERE NOT EXISTS (SELECT 1 FROM quality_reports WHERE uuid = 'qr-002');

-- ============================================================
-- T. 标签字典
-- ============================================================
INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'genre', 'romance', '言情', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'genre' AND tag_value = 'romance');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'genre', 'suspense', '悬疑', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'genre' AND tag_value = 'suspense');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'genre', 'scifi', '科幻', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'genre' AND tag_value = 'scifi');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'genre', 'fantasy', '奇幻', 4, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'genre' AND tag_value = 'fantasy');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'genre', 'wuxia', '武侠', 5, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'genre' AND tag_value = 'wuxia');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'plot', 'rebirth', '重生', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'plot' AND tag_value = 'rebirth');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'plot', 'time_travel', '穿越', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'plot' AND tag_value = 'time_travel');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'plot', 'revenge', '复仇', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'plot' AND tag_value = 'revenge');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'plot', 'undercover', '卧底', 4, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'plot' AND tag_value = 'undercover');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'plot', 'adventure', '冒险', 5, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'plot' AND tag_value = 'adventure');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'tone', 'sweet', '甜宠', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'tone' AND tag_value = 'sweet');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'tone', 'thrilling', '紧张', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'tone' AND tag_value = 'thrilling');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'tone', 'funny', '搞笑', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'tone' AND tag_value = 'funny');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'tone', 'epic', '史诗', 4, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'tone' AND tag_value = 'epic');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'tone', 'healing', '治愈', 5, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'tone' AND tag_value = 'healing');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'setting', 'modern', '现代', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'setting' AND tag_value = 'modern');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'setting', 'ancient', '古代', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'setting' AND tag_value = 'ancient');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'setting', 'future', '未来', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'setting' AND tag_value = 'future');

INSERT INTO tag_dictionary (axis, tag_value, tag_label, sort_order, is_active)
SELECT 'setting', 'republic', '民国', 4, 1
WHERE NOT EXISTS (SELECT 1 FROM tag_dictionary WHERE axis = 'setting' AND tag_value = 'republic');

-- ============================================================
-- U. 内容项目资料 (Project Profiles)
-- ============================================================
INSERT INTO content_project_profiles (project_id, genre_tag, plot_tags, tone_tags, setting_tag, synopsis, outline, updated_by)
SELECT 2, '悬疑', '["卧底","复仇","悬疑"]', '["紧张","反转","暗黑"]', '现代', '前刑警队长林深为追查妹妹失踪真相，卧底进入神秘组织"暗夜"。在正义与黑暗的边缘，他发现了一个惊天秘密——妹妹的失踪与一个更大的犯罪集团"深渊"有关。', '60集悬疑剧集大纲：第一卷(1-12集)潜入暗夜，第二卷(13-27集)深渊之影，第三卷(28-45集)真相大白，第四卷(46-60集)终极对决', 2
WHERE NOT EXISTS (SELECT 1 FROM content_project_profiles WHERE project_id = 2);

INSERT INTO content_project_profiles (project_id, genre_tag, plot_tags, tone_tags, setting_tag, synopsis, updated_by)
SELECT 3, '奇幻', '["古装","探案","玄幻"]', '["奇幻","紧张","史诗"]', '古代', '盛唐长安，天才幻术师白鹤卷入皇室秘宝惊天阴谋。真幻交织之间，他发现最大的幻术，是人心。', 1
WHERE NOT EXISTS (SELECT 1 FROM content_project_profiles WHERE project_id = 3);

-- ============================================================
-- V. 项目设定实体 (角色/场景/道具设定)
-- ============================================================
INSERT INTO project_setting_entities (project_id, setting_type, canonical_name, summary, details_json, status, source_type, current_version_no, created_by)
SELECT 2, 'character', '林深', '32岁前刑警队长，冷峻执着，为救妹妹甘愿化身卧底', '{"age":32,"gender":"男","height":183,"occupation":"前刑警队长/卧底","appearance":"黑色短发微乱，眼神锐利但疲惫","clothing":"深色便装夹克、黑色长裤","signature_item":"妹妹的护身符"}', 'draft', 'manual', 1, 2
WHERE NOT EXISTS (SELECT 1 FROM project_setting_entities WHERE project_id = 2 AND setting_type = 'character' AND canonical_name = '林深');

INSERT INTO project_setting_entities (project_id, setting_type, canonical_name, summary, details_json, status, source_type, current_version_no, created_by)
SELECT 2, 'character', '夜王', '暗夜组织领袖，半机械改造人，神秘冷酷', '{"age":42,"gender":"男","height":188,"occupation":"暗夜组织首领","appearance":"半张脸覆盖机械义体","clothing":"暗色定制西装/斗篷","signature_item":"机械面具"}', 'draft', 'manual', 1, 2
WHERE NOT EXISTS (SELECT 1 FROM project_setting_entities WHERE project_id = 2 AND setting_type = 'character' AND canonical_name = '夜王');

INSERT INTO project_setting_entities (project_id, setting_type, canonical_name, summary, details_json, status, source_type, current_version_no, created_by)
SELECT 2, 'location', '废弃货运码头', '城市东郊废弃港口，集装箱迷宫，暗夜基地入口所在地', '{"era":"现代","type":"废弃工业区","size":"约2平方公里","features":["集装箱堆场","废弃仓库","地下入口"]}', 'draft', 'manual', 1, 2
WHERE NOT EXISTS (SELECT 1 FROM project_setting_entities WHERE project_id = 2 AND setting_type = 'location' AND canonical_name = '废弃货运码头');

INSERT INTO project_setting_entities (project_id, setting_type, canonical_name, summary, details_json, status, source_type, current_version_no, created_by)
SELECT 2, 'location', '暗夜基地', '码头地下30米的秘密基地，冷战时期防空洞改造', '{"era":"现代","type":"地下基地","size":"约5000平方米","features":["指挥中心","训练场","武器库","监牢"]}', 'draft', 'manual', 1, 2
WHERE NOT EXISTS (SELECT 1 FROM project_setting_entities WHERE project_id = 2 AND setting_type = 'location' AND canonical_name = '暗夜基地');

INSERT INTO project_setting_entities (project_id, setting_type, canonical_name, summary, details_json, status, source_type, current_version_no, created_by)
SELECT 2, 'prop', '妹妹的护身符', '林小雨留给哥哥的最后物品，银质月牙吊坠，内部藏有加密芯片', '{"material":"925银","shape":"月牙","size":"2cm","secret":"内置加密芯片"}', 'draft', 'manual', 1, 2
WHERE NOT EXISTS (SELECT 1 FROM project_setting_entities WHERE project_id = 2 AND setting_type = 'prop' AND canonical_name = '妹妹的护身符');

-- 项目设定版本
INSERT INTO project_setting_versions (entity_id, version_no, snapshot_json, field_changes_json, operated_by)
SELECT 1, 1, '{"age":32,"gender":"男","height":183,"occupation":"前刑警队长","appearance":"黑色短发微乱"}', '{"initial":"创建角色设定"}', 2
WHERE NOT EXISTS (SELECT 1 FROM project_setting_versions WHERE entity_id = 1 AND version_no = 1);

INSERT INTO project_setting_versions (entity_id, version_no, snapshot_json, field_changes_json, operated_by)
SELECT 2, 1, '{"age":42,"gender":"男","height":188,"occupation":"暗夜组织首领","appearance":"半机械脸"}', '{"initial":"创建角色设定"}', 2
WHERE NOT EXISTS (SELECT 1 FROM project_setting_versions WHERE entity_id = 2 AND version_no = 1);

-- ============================================================
-- W. 资产市场交互数据 (收藏/授权/发布申请)
-- ============================================================
-- 资产收藏
INSERT INTO asset_favorites (user_id, workspace_id, listing_id)
SELECT 1, 'personal_1', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_favorites WHERE user_id = 1 AND listing_id = 1);

INSERT INTO asset_favorites (user_id, workspace_id, listing_id)
SELECT 1, 'personal_1', 3
WHERE NOT EXISTS (SELECT 1 FROM asset_favorites WHERE user_id = 1 AND listing_id = 3);

INSERT INTO asset_favorites (user_id, workspace_id, listing_id)
SELECT 1, 'personal_1', 5
WHERE NOT EXISTS (SELECT 1 FROM asset_favorites WHERE user_id = 1 AND listing_id = 5);

INSERT INTO asset_favorites (user_id, workspace_id, listing_id)
SELECT 2, 'personal_2', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_favorites WHERE user_id = 2 AND listing_id = 1);

INSERT INTO asset_favorites (user_id, workspace_id, listing_id)
SELECT 2, 'personal_2', 4
WHERE NOT EXISTS (SELECT 1 FROM asset_favorites WHERE user_id = 2 AND listing_id = 4);

-- 资产授权 (用户1申领了资产1和5)
INSERT INTO asset_entitlements (beneficiary_workspace_id, listing_id, source_version_id, grant_type, claimed_by)
SELECT 'personal_1', 1, 1, 'FREE_CLAIM', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_entitlements WHERE beneficiary_workspace_id = 'personal_1' AND listing_id = 1);

INSERT INTO asset_entitlements (beneficiary_workspace_id, listing_id, source_version_id, grant_type, claimed_by)
SELECT 'personal_1', 5, 5, 'FREE_CLAIM', 1
WHERE NOT EXISTS (SELECT 1 FROM asset_entitlements WHERE beneficiary_workspace_id = 'personal_1' AND listing_id = 5);

-- 资产发布申请
INSERT INTO asset_publish_requests (workspace_id, asset_id, version_id, requester_id, status, reason)
SELECT 'personal_1', 11, 11, 1, 'PENDING', '申请将自制角色"林深"上架到公共市场，已确保不侵犯第三方权益'
WHERE NOT EXISTS (SELECT 1 FROM asset_publish_requests WHERE workspace_id = 'personal_1' AND asset_id = 11);

INSERT INTO asset_publish_requests (workspace_id, asset_id, version_id, requester_id, reviewer_id, status, reason, review_comment)
SELECT 'personal_2', 12, 12, 2, 1, 'APPROVED', '申请上架"废弃码头"场景资产', '资产质量合格，同意上架'
WHERE NOT EXISTS (SELECT 1 FROM asset_publish_requests WHERE workspace_id = 'personal_2' AND asset_id = 12);

-- ============================================================
-- X. 上传文件记录
-- ============================================================
INSERT INTO content_upload_files (uuid, user_id, original_name, file_type, file_size, parsed_text, parse_status)
SELECT 'upload-001', 2, '暗夜追光者_原著小说_前3章.docx', 'docx', 245760, '第一章·消失的妹妹 林深站在废弃码头的集装箱前...（以下省略解析文本）', 'completed'
WHERE NOT EXISTS (SELECT 1 FROM content_upload_files WHERE uuid = 'upload-001');

INSERT INTO content_upload_files (uuid, user_id, original_name, file_type, file_size, parse_status)
SELECT 'upload-002', 4, '都市爱情系列_完整手稿.pdf', 'pdf', 1048576, 'pending'
WHERE NOT EXISTS (SELECT 1 FROM content_upload_files WHERE uuid = 'upload-002');

-- ============================================================
-- Y. 连续性快照
-- ============================================================
INSERT INTO continuity_snapshots (project_id, content_unit_id, snapshot_json, content_hash)
SELECT 2, 2, '{"characters":{"林深":{"status":"active","location":"废弃码头","emotion":"紧张"},"夜王":{"status":"behind_scenes","location":"暗夜基地"}},"plot_points":{"妹妹线索":"已发现护身符"},"locations_visited":["废弃码头"]}', 'cont-hash-002'
WHERE NOT EXISTS (SELECT 1 FROM continuity_snapshots WHERE project_id = 2 AND content_unit_id = 2);

-- ============================================================
-- Z. 设定提取批次 (演示AI提取功能)
-- ============================================================
INSERT INTO setting_extraction_batches (project_id, source_version_id, target_setting_types, idempotency_key, status, model_id, created_by)
SELECT 2, 2, '["character","location","prop"]', 'extract-batch-001', 'completed', 'claude-sonnet-4-6', 2
WHERE NOT EXISTS (SELECT 1 FROM setting_extraction_batches WHERE project_id = 2 AND idempotency_key = 'extract-batch-001');

-- ============================================================
-- AA. Agent 配置中心 (M1)
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_blueprints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE,
    role_type VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    parameter_schema_json TEXT NOT NULL,
    default_parameters_json TEXT NOT NULL,
    locked_system_prompt TEXT NOT NULL,
    editable_prompt_template TEXT NOT NULL,
    input_schema_json TEXT NOT NULL,
    output_schema_json TEXT NOT NULL,
    allowed_tools_json TEXT NOT NULL,
    context_policy_json TEXT NOT NULL,
    model_policy_json TEXT NOT NULL,
    blueprint_version INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bp_role_version ON agent_blueprints(role_type, blueprint_version);

CREATE TABLE IF NOT EXISTS user_agent_definitions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE,
    blueprint_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    current_published_version_id BIGINT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    icon VARCHAR(500),
    applicable_genres_json VARCHAR(4000),
    platforms_json VARCHAR(4000),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_def_owner_name ON user_agent_definitions(owner_user_id, name);
CREATE INDEX IF NOT EXISTS idx_def_blueprint ON user_agent_definitions(blueprint_id);
CREATE INDEX IF NOT EXISTS idx_def_owner ON user_agent_definitions(owner_user_id);

CREATE TABLE IF NOT EXISTS agent_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE,
    user_agent_id BIGINT NOT NULL,
    blueprint_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    parameters_json TEXT,
    editable_prompt TEXT,
    examples_json TEXT,
    model_policy_json TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    change_summary VARCHAR(500),
    content_hash VARCHAR(64),
    created_by BIGINT,
    published_by BIGINT,
    published_at TIMESTAMP,
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ver_agent_no ON agent_versions(user_agent_id, version_no);
CREATE INDEX IF NOT EXISTS idx_ver_user_agent ON agent_versions(user_agent_id);
CREATE INDEX IF NOT EXISTS idx_ver_status ON agent_versions(status);

CREATE TABLE IF NOT EXISTS agent_bindings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE,
    scope_type VARCHAR(20) NOT NULL,
    scope_id VARCHAR(50) NOT NULL,
    role_type VARCHAR(20) NOT NULL,
    user_agent_id BIGINT NOT NULL,
    agent_version_id BIGINT NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bind_scope_role ON agent_bindings(scope_type, scope_id, role_type);
CREATE INDEX IF NOT EXISTS idx_bind_version ON agent_bindings(agent_version_id);

CREATE TABLE IF NOT EXISTS agent_test_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE,
    agent_version_id BIGINT NOT NULL,
    input_snapshot_json TEXT,
    context_snapshot_json TEXT,
    output_json TEXT,
    output_schema_valid TINYINT,
    model_id VARCHAR(100),
    prompt_tokens INT,
    completion_tokens INT,
    credit_cost DOUBLE,
    duration_ms INT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_code VARCHAR(50),
    error_message VARCHAR(2000),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tr_version ON agent_test_runs(agent_version_id);
CREATE INDEX IF NOT EXISTS idx_tr_status ON agent_test_runs(status);

CREATE TABLE IF NOT EXISTS agent_execution_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(50) NOT NULL UNIQUE,
    blueprint_id BIGINT NOT NULL,
    blueprint_version INT NOT NULL,
    user_agent_id BIGINT,
    agent_version_id BIGINT,
    binding_source VARCHAR(20) NOT NULL,
    resolved_parameters_json TEXT,
    temporary_overrides_json TEXT,
    resolved_prompt TEXT,
    prompt_hash VARCHAR(64),
    output_schema_version VARCHAR(50),
    project_id BIGINT,
    context_hash VARCHAR(64),
    context_refs_json TEXT,
    business_task_type VARCHAR(50),
    business_task_id VARCHAR(100),
    model_id VARCHAR(100),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_snap_version ON agent_execution_snapshots(agent_version_id);
CREATE INDEX IF NOT EXISTS idx_snap_project ON agent_execution_snapshots(project_id);

-- Blueprint 种子数据 (H2: WHERE NOT EXISTS)
INSERT INTO agent_blueprints (uuid, role_type, name, description, parameter_schema_json, default_parameters_json, locked_system_prompt, editable_prompt_template, input_schema_json, output_schema_json, allowed_tools_json, context_policy_json, model_policy_json, blueprint_version, status)
SELECT 'bp-hook-v1', 'HOOK', '钩子 Agent', '钩子生成、分析和审核',
 '{"type":"object","additionalProperties":false,"properties":{"opening_seconds":{"type":"integer","minimum":1,"maximum":10,"default":3},"hook_density":{"type":"string","enum":["low","medium","high","extreme"],"enumLabels":["低","中","高","极高"],"default":"medium"},"reversal_strength":{"type":"number","minimum":0,"maximum":1,"default":0.5},"closing_hook_strength":{"type":"string","enum":["weak","moderate","strong"],"enumLabels":["弱","中","强"],"default":"moderate"},"minimum_score":{"type":"integer","minimum":0,"maximum":100,"default":60}}}',
 '{"opening_seconds":3,"hook_density":"medium","reversal_strength":0.5,"closing_hook_strength":"moderate","minimum_score":60}',
 '平台锁定：仅生成钩子结构。你必须严格遵循以下工具权限、安全规则和输出协议。不得输出与钩子无关的内容。',
 '{{user_method}}',
 '{"type":"object","properties":{"script_excerpt":{"type":"string"}}}',
 '{"type":"object","properties":{"score":{"type":"integer"},"hooks":{"type":"array"},"analysis":{"type":"string"}}}',
 '[]',
 '{"max_context_length":16000}',
 '{"default_model":"deepseek-v3","max_tokens":4096,"temperature":{"default":0.7}}',
 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM agent_blueprints WHERE uuid = 'bp-hook-v1');

INSERT INTO agent_blueprints (uuid, role_type, name, description, parameter_schema_json, default_parameters_json, locked_system_prompt, editable_prompt_template, input_schema_json, output_schema_json, allowed_tools_json, context_policy_json, model_policy_json, blueprint_version, status)
SELECT 'bp-screenwriter-v1', 'SCREENWRITER', '编剧 Agent', '大纲、分集、正文生成和编剧修订',
 '{"type":"object","additionalProperties":false,"properties":{"revision_mode":{"type":"string","enum":["conservative","balanced","rewrite"],"default":"balanced"},"target_duration_seconds":{"type":"integer","minimum":30,"maximum":600,"default":180},"dialogue_density":{"type":"string","enum":["sparse","normal","dense"],"default":"normal"},"conflict_pace":{"type":"string","enum":["slow","moderate","fast","intense"],"default":"moderate"},"character_consistency":{"type":"string","enum":["loose","normal","strict"],"default":"normal"}}}',
 '{"revision_mode":"balanced","target_duration_seconds":180,"dialogue_density":"normal","conflict_pace":"moderate","character_consistency":"normal"}',
 '平台锁定：仅执行编剧任务。你必须严格遵循创作圣经和项目约束。所有输出必须符合剧本格式规范。',
 '{{user_method}}',
 '{"type":"object","properties":{"task_type":{"type":"string","enum":["outline","episode","body","revise"]},"context":{"type":"string"}}}',
 '{"type":"object","properties":{"content":{"type":"string"},"revision_summary":{"type":"string"}}}',
 '[]',
 '{"max_context_length":32000}',
 '{"default_model":"deepseek-v3","max_tokens":8192,"temperature":{"default":0.7}}',
 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM agent_blueprints WHERE uuid = 'bp-screenwriter-v1');

INSERT INTO agent_blueprints (uuid, role_type, name, description, parameter_schema_json, default_parameters_json, locked_system_prompt, editable_prompt_template, input_schema_json, output_schema_json, allowed_tools_json, context_policy_json, model_policy_json, blueprint_version, status)
SELECT 'bp-storyboard-v1', 'STORYBOARD', '分镜 Agent', 'A/B/C 档分镜生成和镜头策略',
 '{"type":"object","additionalProperties":false,"properties":{"tier":{"type":"string","enum":["A","B","C"],"default":"B"},"average_shot_seconds":{"type":"number","minimum":1,"maximum":30,"default":4},"shot_density":{"type":"string","enum":["sparse","normal","dense"],"default":"normal"},"camera_complexity":{"type":"string","enum":["simple","moderate","complex"],"default":"moderate"},"continuity_level":{"type":"string","enum":["basic","standard","strict"],"default":"standard"},"production_cost_mode":{"type":"string","enum":["low","balanced","quality_first"],"default":"balanced"}}}',
 '{"tier":"B","average_shot_seconds":4,"shot_density":"normal","camera_complexity":"moderate","continuity_level":"standard","production_cost_mode":"balanced"}',
 '平台锁定：输出专业分镜结构。你必须输出符合行业标准的分镜脚本格式，包含镜头号、景别、运镜、动作、对白、时长。',
 '{{user_method}}',
 '{"type":"object","properties":{"script_text":{"type":"string"},"bible_context":{"type":"string"},"tier_override":{"type":"string","enum":["A","B","C"]}}}',
 '{"type":"object","properties":{"shots":{"type":"array"},"summary":{"type":"string"},"estimated_duration_seconds":{"type":"number"}}}',
 '[]',
 '{"max_context_length":16000}',
 '{"default_model":"deepseek-v3","max_tokens":8192,"temperature":{"default":0.6}}',
 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM agent_blueprints WHERE uuid = 'bp-storyboard-v1');

INSERT INTO agent_blueprints (uuid, role_type, name, description, parameter_schema_json, default_parameters_json, locked_system_prompt, editable_prompt_template, input_schema_json, output_schema_json, allowed_tools_json, context_policy_json, model_policy_json, blueprint_version, status)
SELECT 'bp-director-v1', 'DIRECTOR', '导演 Agent', '节奏、画面、可拍性和导演审核',
 '{"type":"object","additionalProperties":false,"properties":{"visual_style":{"type":"string","enum":["realistic","stylized","cinematic","minimalist"],"default":"cinematic"},"pacing_mode":{"type":"string","enum":["slow_burn","balanced","fast_paced","rhythmic"],"default":"balanced"},"feasibility_level":{"type":"string","enum":["strict","pragmatic","creative"],"default":"pragmatic"},"budget_mode":{"type":"string","enum":["micro","low","medium","unlimited"],"default":"medium"},"approval_threshold":{"type":"integer","minimum":50,"maximum":100,"default":70},"output_mode":{"type":"string","enum":["review_only","suggestions","patch","full_revision"],"default":"suggestions"}}}',
 '{"visual_style":"cinematic","pacing_mode":"balanced","feasibility_level":"pragmatic","budget_mode":"medium","approval_threshold":70,"output_mode":"suggestions"}',
 '平台锁定：输出导演审核结构。你必须输出评分、问题列表、严重度分级、建议和可执行修订项。不得跳过可拍性检查。',
 '{{user_method}}',
 '{"type":"object","properties":{"script_or_storyboard":{"type":"string"},"bible_context":{"type":"string"},"budget_constraints":{"type":"object"}}}',
 '{"type":"object","properties":{"overall_score":{"type":"integer"},"issues":{"type":"array"},"suggestions":{"type":"array"},"feasibility_report":{"type":"string"}}}',
 '[]',
 '{"max_context_length":32000}',
 '{"default_model":"deepseek-v3","max_tokens":8192,"temperature":{"default":0.4}}',
 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM agent_blueprints WHERE uuid = 'bp-director-v1');
