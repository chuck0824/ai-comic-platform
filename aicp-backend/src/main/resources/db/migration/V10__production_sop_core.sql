-- V10__production_sop_core.sql
-- Production SOP Phase 1: core tables for check runs, results, work orders, gate decisions, rule set versions

-- 1. Rule set versions
CREATE TABLE IF NOT EXISTS sop_rule_set_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    rule_count INT NOT NULL DEFAULT 0,
    enabled_count INT NOT NULL DEFAULT 0,
    is_active TINYINT NOT NULL DEFAULT 0,
    published_at TIMESTAMP NULL,
    published_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed production-readiness-v1 with 7 enabled + 6 disabled = 13 total
INSERT INTO sop_rule_set_versions (version, name, description, rule_count, enabled_count, is_active, published_at, published_by)
VALUES ('production-readiness-v1', 'Production Readiness v1', 'Phase 1: 7 enabled rules, 6 disabled pending upstream data sources', 13, 7, 1, NOW(), NULL);

-- 2. Check runs
CREATE TABLE IF NOT EXISTS sop_check_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    content_unit_id BIGINT NULL,
    canvas_project_id BIGINT NULL,
    gate_type VARCHAR(40) NULL,
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    rule_set_version VARCHAR(32) NOT NULL,
    scope_hash VARCHAR(64) NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    source_revisions_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    overall_status VARCHAR(20) NULL,
    passed_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    blocked_count INT NOT NULL DEFAULT 0,
    not_ready_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    row_version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    UNIQUE uk_check_run_dedup (project_id, scope_hash, rule_set_version, snapshot_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX IF NOT EXISTS idx_check_run_project ON sop_check_runs(project_id, created_at DESC);

-- 3. Check results (per-rule, per-target)
CREATE TABLE IF NOT EXISTS sop_check_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    result VARCHAR(20) NOT NULL,
    severity VARCHAR(8) NOT NULL,
    critical TINYINT NOT NULL DEFAULT 0,
    target_type VARCHAR(32) NULL,
    target_id VARCHAR(64) NULL,
    issue_fingerprint VARCHAR(128) NULL,
    evidence_json TEXT NULL,
    suggestion VARCHAR(2000) NULL,
    fix_policy VARCHAR(32) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX IF NOT EXISTS idx_check_result_run ON sop_check_results(run_id);
CREATE INDEX IF NOT EXISTS idx_check_result_fingerprint ON sop_check_results(issue_fingerprint);

-- 4. Work orders
CREATE TABLE IF NOT EXISTS sop_work_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    result_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    issue_fingerprint VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    severity VARCHAR(8) NOT NULL,
    responsible_role VARCHAR(50) NULL,
    assignee_id BIGINT NULL,
    resolution_note TEXT NULL,
    deadline TIMESTAMP NULL,
    active_marker TINYINT NULL DEFAULT 1,
    row_version INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE UNIQUE INDEX IF NOT EXISTS uk_work_order_active_fingerprint ON sop_work_orders(project_id, issue_fingerprint, active_marker);
CREATE INDEX IF NOT EXISTS idx_work_order_project ON sop_work_orders(project_id, status);

-- 5. Work order events (append-only audit trail)
CREATE TABLE IF NOT EXISTS sop_work_order_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    operator_id BIGINT NOT NULL,
    note VARCHAR(2000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX IF NOT EXISTS idx_wo_event_order ON sop_work_order_events(work_order_id, created_at);

-- 6. Gate decisions
CREATE TABLE IF NOT EXISTS sop_gate_decisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    gate_type VARCHAR(40) NOT NULL,
    allowed TINYINT NOT NULL DEFAULT 0,
    blocker_count INT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(128) NOT NULL,
    row_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE UNIQUE INDEX IF NOT EXISTS uk_gate_decision_idempotency ON sop_gate_decisions(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_gate_decision_project ON sop_gate_decisions(project_id);

-- Legacy migration: convert sop_audits rows to work orders
-- Only if sop_audits table exists with the rich schema
-- INSERT INTO sop_work_orders (project_id, run_id, result_id, rule_code, issue_fingerprint, status, severity, responsible_role, assignee_id)
-- SELECT ... FROM sop_audits WHERE ...
