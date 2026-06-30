-- V2__backfill_canvas_ownership.sql
-- Backfill content_project_id, production_unit_type, production_unit_id,
-- source_content_version_id, source_storyboard_version_id, production_snapshot,
-- purpose, owner_id, idempotency_key for existing canvas_projects rows.
-- SAFE: can be run multiple times (idempotent WHERE ... IS NULL guards).

-- ============================================================
-- 1. Archive orphan canvases (no script_id)
-- ============================================================
UPDATE canvas_projects
SET status = 'archived', archived_at = NOW()
WHERE script_id IS NULL
  AND status != 'archived'
  AND (content_project_id IS NULL);

-- ============================================================
-- 2. Backfill for canvases with valid script_id + episode_index
-- ============================================================
UPDATE canvas_projects cp
SET
  content_project_id = COALESCE(cp.content_project_id,
    (SELECT s.content_project_id FROM scripts s WHERE s.id = cp.script_id LIMIT 1)),
  production_unit_type = COALESCE(cp.production_unit_type, 'episode'),
  production_unit_id = COALESCE(cp.production_unit_id,
    (SELECT cu.id FROM content_units cu
     WHERE cu.project_id = (SELECT s.content_project_id FROM scripts s WHERE s.id = cp.script_id LIMIT 1)
       AND cu.unit_type = 'episode'
       AND cu.display_no = cp.episode_index
     LIMIT 1)),
  source_content_version_id = COALESCE(cp.source_content_version_id,
    (SELECT cv.id FROM content_versions cv
     WHERE cv.project_id = (SELECT s.content_project_id FROM scripts s WHERE s.id = cp.script_id LIMIT 1)
       AND cv.status = 'approved'
     ORDER BY cv.created_at DESC
     LIMIT 1)),
  source_storyboard_version_id = COALESCE(cp.source_storyboard_version_id,
    (SELECT sm.id FROM cp_storyboard_masters sm
     WHERE sm.project_id = (SELECT s.content_project_id FROM scripts s WHERE s.id = cp.script_id LIMIT 1)
       AND sm.status = 'locked'
     ORDER BY sm.locked_at DESC
     LIMIT 1)),
  purpose = COALESCE(cp.purpose, 'official'),
  owner_id = COALESCE(cp.owner_id, cp.user_id),
  idempotency_key = COALESCE(cp.idempotency_key, CONCAT('migrated:', cp.uuid))
WHERE cp.content_project_id IS NULL
  AND cp.script_id IS NOT NULL
  AND cp.status != 'archived';

-- ============================================================
-- 3. Fill remaining defaults
-- ============================================================
UPDATE canvas_projects
SET owner_id = user_id WHERE owner_id IS NULL;
UPDATE canvas_projects SET revision = 0 WHERE revision IS NULL;
UPDATE canvas_projects SET is_deleted = 0 WHERE is_deleted IS NULL;
