-- ============================================================
-- V6: Script Trading Market Foundation
-- ============================================================
-- Creates the full marketplace domain: listings, license options,
-- orders, order items, entitlements, purchased copies, purchase
-- requests, refund requests, outbox events, and audit logs.
--
-- Money is BIGINT cents, currency is CNY.
-- Workspace IDs are VARCHAR(64).
-- All mutating tables carry row_version for optimistic locking.

-- -----------------------------------------------------------
-- 1. Script listings (seller-facing)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS script_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    seller_user_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    script_version_id BIGINT NOT NULL,

    -- Public snapshot frozen on submit
    title VARCHAR(200) NOT NULL,
    synopsis VARCHAR(5000),
    cover_url VARCHAR(500),
    tags_json VARCHAR(2000) DEFAULT '[]',
    characters_json TEXT,
    episode_count INT DEFAULT 0,
    author_display_name VARCHAR(100),

    -- Preview control
    preview_episode_count INT NOT NULL DEFAULT 1,
    preview_episodes_json TEXT,

    -- Review state
    review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    review_reason VARCHAR(2000),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,

    -- Listing state
    listing_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    -- Exclusive inventory
    exclusive_license_type VARCHAR(10) NULL,
    historical_normal_count INT NOT NULL DEFAULT 0,
    reserved_order_no VARCHAR(32) NULL,
    reservation_expires_at TIMESTAMP NULL,

    -- Metadata
    row_version INT NOT NULL DEFAULT 0,
    listed_at TIMESTAMP NULL,
    delisted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_listings_status ON script_listings(listing_status);
CREATE INDEX IF NOT EXISTS idx_listings_workspace ON script_listings(workspace_id);
CREATE INDEX IF NOT EXISTS idx_listings_updated ON script_listings(updated_at);

-- -----------------------------------------------------------
-- 2. License options per listing
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS listing_license_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT NOT NULL,
    license_type VARCHAR(10) NOT NULL,
    price_cents BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    term_json TEXT,
    agreement_text TEXT,
    agreement_version VARCHAR(20),
    agreement_hash VARCHAR(64),
    enabled TINYINT NOT NULL DEFAULT 1,
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_license_listing_type UNIQUE (listing_id, license_type)
);

-- -----------------------------------------------------------
-- 3. Trade orders
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS trade_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',

    -- Buyer identity (from trusted WorkspaceContext)
    buyer_user_id BIGINT NOT NULL,
    buyer_workspace_id VARCHAR(64) NOT NULL,
    buyer_workspace_type VARCHAR(16) NOT NULL DEFAULT 'PERSONAL',

    -- Seller identity
    seller_user_id BIGINT NOT NULL,
    seller_workspace_id VARCHAR(64) NOT NULL,

    -- Money (cents)
    total_amount_cents BIGINT NOT NULL DEFAULT 0,
    platform_fee_cents BIGINT NOT NULL DEFAULT 0,
    seller_income_cents BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',

    -- 3001 wallet reference
    wallet_transfer_no VARCHAR(64) NULL,
    wallet_status VARCHAR(20) NULL,

    -- Idempotency
    create_idempotency_key VARCHAR(128) NOT NULL,

    -- Timestamps
    expires_at TIMESTAMP NULL,
    paid_at TIMESTAMP NULL,
    fulfilled_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    refunded_at TIMESTAMP NULL,

    -- Metadata
    row_version INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_trade_order_no UNIQUE (order_no),
    CONSTRAINT uk_trade_order_idempotent UNIQUE (buyer_workspace_id, create_idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_trade_orders_buyer ON trade_orders(buyer_user_id, buyer_workspace_id);
CREATE INDEX IF NOT EXISTS idx_trade_orders_seller ON trade_orders(seller_user_id);
CREATE INDEX IF NOT EXISTS idx_trade_orders_status ON trade_orders(status);
CREATE INDEX IF NOT EXISTS idx_trade_orders_updated ON trade_orders(updated_at);

-- -----------------------------------------------------------
-- 4. Order items (snapshot of what was purchased)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS trade_order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    script_version_id BIGINT NOT NULL,

    -- License snapshot
    license_type VARCHAR(10) NOT NULL,
    price_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',

    -- Public info snapshot
    title_snapshot VARCHAR(200),
    author_snapshot VARCHAR(100),
    tags_snapshot VARCHAR(2000),

    -- Agreement snapshot
    agreement_text TEXT,
    agreement_version VARCHAR(20),
    agreement_hash VARCHAR(64),

    -- Historical disclosure
    historical_normal_count INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_order_item UNIQUE (order_id)
);

-- -----------------------------------------------------------
-- 5. Script entitlements (granted licenses)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS script_entitlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    beneficiary_workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL,
    script_version_id BIGINT NOT NULL,

    license_type VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- License scope
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_until TIMESTAMP NULL,
    max_accounts INT NULL,
    allow_commercial TINYINT NOT NULL DEFAULT 0,
    allow_adaptation TINYINT NOT NULL DEFAULT 0,
    allow_sublicense TINYINT NOT NULL DEFAULT 0,
    territory_restriction VARCHAR(200),

    -- Revocation
    revoked_at TIMESTAMP NULL,
    revoke_reason VARCHAR(2000),

    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_entitlement_order_item UNIQUE (order_item_id)
);

-- -----------------------------------------------------------
-- 6. Purchased script copies (warehouse)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS purchased_script_copies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    listing_id BIGINT NOT NULL,
    source_version_id BIGINT NOT NULL,

    -- Content snapshot (immutable)
    content_json TEXT,
    title VARCHAR(200),
    created_by_user_id BIGINT NOT NULL,

    -- Source attribution (read-only)
    source_listing_id BIGINT,
    source_author_name VARCHAR(100),

    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_copy_order_item UNIQUE (order_item_id)
);

-- -----------------------------------------------------------
-- 7. Enterprise purchase requests
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS purchase_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    license_type VARCHAR(10) NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    reason VARCHAR(2000),

    -- Reviewer
    approver_user_id BIGINT NULL,
    approval_comment VARCHAR(2000),

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',

    -- Linked order (created on approval)
    order_no VARCHAR(32) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pr_workspace_status ON purchase_requests(workspace_id, status);

-- -----------------------------------------------------------
-- 8. Refund requests
-- -----------------------------------------------------------
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

CREATE INDEX IF NOT EXISTS idx_rf_order ON refund_requests(order_no);
CREATE INDEX IF NOT EXISTS idx_rf_status ON refund_requests(status);

-- -----------------------------------------------------------
-- 9. Outbox events (reliable cross-service delivery)
-- -----------------------------------------------------------
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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_outbox_idempotent UNIQUE (aggregate_type, aggregate_id, event_type, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_outbox_dispatch ON trade_outbox_events(status, next_retry_at);

-- -----------------------------------------------------------
-- 10. Audit logs
-- -----------------------------------------------------------
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

CREATE INDEX IF NOT EXISTS idx_audit_target ON trade_audit_logs(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON trade_audit_logs(actor_user_id, created_at);
