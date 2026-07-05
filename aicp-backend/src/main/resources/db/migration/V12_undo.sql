-- V12 undo: 回滚 Canvas 生产内核 schema
DROP TABLE IF EXISTS canvas_migration_reports;
DROP TABLE IF EXISTS shot_adoptions;
DROP TABLE IF EXISTS generation_candidates;
DROP TABLE IF EXISTS generation_request_snapshots;
DROP TABLE IF EXISTS canvas_shot_units;

ALTER TABLE canvas_edges DROP INDEX IF EXISTS uk_edge_target_role;
ALTER TABLE canvas_edges DROP COLUMN IF EXISTS role;
ALTER TABLE canvas_edges DROP COLUMN IF EXISTS status;
ALTER TABLE canvas_edges DROP COLUMN IF EXISTS port_contract_version;

ALTER TABLE canvas_nodes DROP COLUMN IF EXISTS node_schema_version;
ALTER TABLE canvas_nodes DROP COLUMN IF EXISTS shot_unit_id;

ALTER TABLE canvas_projects DROP COLUMN IF EXISTS storyboard_revision;
ALTER TABLE canvas_projects DROP COLUMN IF EXISTS content_project_id;
ALTER TABLE canvas_projects DROP COLUMN IF EXISTS schema_version;
ALTER TABLE canvas_projects DROP COLUMN IF EXISTS canvas_mode;
