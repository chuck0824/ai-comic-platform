-- V17 undo
DROP INDEX idx_aa_target_key ON asset_applications;
ALTER TABLE asset_applications DROP COLUMN target_key;
