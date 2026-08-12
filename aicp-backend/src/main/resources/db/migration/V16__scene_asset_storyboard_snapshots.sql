-- V16: immutable scene-asset snapshots on active V2 storyboard shots
ALTER TABLE storyboard_version_shots
    ADD COLUMN IF NOT EXISTS scene_asset_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS scene_asset_version_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS scene_variant_id VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS scene_variant_version INT NULL,
    ADD COLUMN IF NOT EXISTS scene_asset_snapshot JSON NULL;

CREATE INDEX IF NOT EXISTS idx_sbshot_scene_asset
    ON storyboard_version_shots(scene_asset_id);
CREATE INDEX IF NOT EXISTS idx_sbshot_scene_asset_version
    ON storyboard_version_shots(scene_asset_version_id);
