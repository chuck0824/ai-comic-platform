-- V10_undo.sql
-- Rollback: drop all 6 new tables created in V10

DROP TABLE IF EXISTS sop_gate_decisions;
DROP TABLE IF EXISTS sop_work_order_events;
DROP TABLE IF EXISTS sop_work_orders;
DROP TABLE IF EXISTS sop_check_results;
DROP TABLE IF EXISTS sop_check_runs;
DROP TABLE IF EXISTS sop_rule_set_versions;
