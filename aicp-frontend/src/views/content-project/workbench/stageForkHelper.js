/**
 * R2-A：完成/锁定阶段首次编辑前是否需要 Fork。
 */

export function shouldForkBeforeEdit(serverState) {
  return serverState === 'COMPLETED' || serverState === 'LOCKED'
}

export function buildForkStageRequest({
  checkpointRevision = 0,
  adoptedContentVersionId = null,
  projectRevision = null
} = {}) {
  return {
    checkpoint_revision: checkpointRevision ?? 0,
    base_adopted_content_version_id: adoptedContentVersionId ?? null,
    project_revision: projectRevision ?? null
  }
}
