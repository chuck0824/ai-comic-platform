-- V16 undo
DROP INDEX IF EXISTS idx_sbshot_scene_asset_version ON storyboard_version_shots;
DROP INDEX IF EXISTS idx_sbshot_scene_asset ON storyboard_version_shots;
ALTER TABLE storyboard_version_shots DROP COLUMN IF EXISTS scene_asset_snapshot;
ALTER TABLE storyboard_version_shots DROP COLUMN IF EXISTS scene_variant_version;
ALTER TABLE storyboard_version_shots DROP COLUMN IF EXISTS scene_variant_id;
ALTER TABLE storyboard_version_shots DROP COLUMN IF EXISTS scene_asset_version_id;
ALTER TABLE storyboard_version_shots DROP COLUMN IF EXISTS scene_asset_id;
