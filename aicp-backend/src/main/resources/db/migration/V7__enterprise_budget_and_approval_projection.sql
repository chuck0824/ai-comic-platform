-- ============================================================
-- V7: Enterprise Budget Governance & Unified Approval Projection
-- ============================================================
-- Money is BIGINT cents. Workspace IDs are VARCHAR(64).
-- All mutating tables carry row_version for optimistic locking.
-- Budget entries are immutable (ledger semantics).
-- Approval items are a rebuildable projection from domain Outbox events.

-- -----------------------------------------------------------
-- 1. Purchase budget policies
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS enterprise_purchase_budgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    subject_type VARCHAR(16) NOT NULL,   -- WORKSPACE, DEPARTMENT, or MEMBER
    subject_id VARCHAR(64) NOT NULL,
    period_month VARCHAR(7) NOT NULL,    -- YYYY-MM

    amount_cents BIGINT NOT NULL DEFAULT 0,
    single_limit_cents BIGINT NOT NULL DEFAULT 0,

    -- Projections maintained inside transactions
    reserved_cents BIGINT NOT NULL DEFAULT 0,
    consumed_cents BIGINT NOT NULL DEFAULT 0,

    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_budget_scope UNIQUE (workspace_id, subject_type, subject_id, period_month)
);

CREATE INDEX IF NOT EXISTS idx_budget_workspace ON enterprise_purchase_budgets(workspace_id);

-- -----------------------------------------------------------
-- 2. Immutable budget ledger entries
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS enterprise_purchase_budget_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,

    entry_type VARCHAR(16) NOT NULL,     -- RESERVE, RELEASE, CONSUME, REVERSE
    amount_cents BIGINT NOT NULL,

    -- Source trace
    source_type VARCHAR(32) NOT NULL,    -- purchase_request, order, refund
    source_id VARCHAR(64) NOT NULL,

    -- 3001 wallet linkage (for CONSUME / REVERSE)
    wallet_transfer_no VARCHAR(64) NULL,

    -- Idempotency key (immutable, unique per entry)
    idempotency_key VARCHAR(128) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_budget_entry_idempotent UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_budget_entry_budget ON enterprise_purchase_budget_entries(budget_id);
CREATE INDEX IF NOT EXISTS idx_budget_entry_source ON enterprise_purchase_budget_entries(source_type, source_id);

-- -----------------------------------------------------------
-- 3. Unified approval projection (rebuildable from Outbox)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS enterprise_approval_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) DEFAULT '',

    source_type VARCHAR(32) NOT NULL,    -- PURCHASE, ASSET_PUBLISH, PROJECT_EXPORT
    source_id VARCHAR(64) NOT NULL,
    source_version INT NOT NULL DEFAULT 0,

    requester_user_id BIGINT NOT NULL,
    summary VARCHAR(500),
    amount_cents BIGINT,
    currency VARCHAR(3) DEFAULT 'CNY',

    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',

    -- Source domain allowed actions (hint, not authoritative)
    allowed_actions_json VARCHAR(2000),

    submitted_at TIMESTAMP NULL,
    decided_at TIMESTAMP NULL,
    last_event_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_approval_source UNIQUE (source_type, source_id)
);

CREATE INDEX IF NOT EXISTS idx_approval_workspace ON enterprise_approval_items(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_approval_dept ON enterprise_approval_items(workspace_id, department_id);
CREATE INDEX IF NOT EXISTS idx_approval_requester ON enterprise_approval_items(requester_user_id, status);
CREATE INDEX IF NOT EXISTS idx_approval_last_event ON enterprise_approval_items(last_event_at);

-- -----------------------------------------------------------
-- 4. Asset outbox events (drives approval projection)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS asset_outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,

    CONSTRAINT uk_asset_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_asset_outbox_dispatch ON asset_outbox_events(status, created_at);
