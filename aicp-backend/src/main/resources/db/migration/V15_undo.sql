-- V15 undo
ALTER TABLE delivery_manifests DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE generation_candidates DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE canvas_shot_units DROP COLUMN IF EXISTS deleted_at;
DROP TABLE IF EXISTS delivery_manifest_items;
DROP TABLE IF EXISTS delivery_manifests;
DROP TABLE IF EXISTS canvas_quality_issues;
DROP TABLE IF EXISTS canvas_quality_reports;
