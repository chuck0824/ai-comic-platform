-- V17 undo
DROP INDEX IF EXISTS idx_aa_target_key;
ALTER TABLE asset_applications DROP COLUMN IF EXISTS target_key;
