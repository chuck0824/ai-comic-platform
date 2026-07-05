-- V14 undo
DROP TABLE IF EXISTS model_capability_profiles;
ALTER TABLE generation_tasks DROP COLUMN IF EXISTS retry_strategy;
ALTER TABLE generation_tasks DROP COLUMN IF EXISTS settlement_ref;
ALTER TABLE generation_tasks DROP COLUMN IF EXISTS actual_credit_cost;
ALTER TABLE generation_tasks DROP COLUMN IF EXISTS request_snapshot_id;
DROP TABLE IF EXISTS generation_task_attempts;
