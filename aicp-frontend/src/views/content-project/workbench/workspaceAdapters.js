function payload(response) {
  return response?.data?.data ?? response?.data ?? response ?? {}
}

function numericId(value) {
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

function stableIdempotencyKey(sceneId, assetId, versionId) {
  return `script-scene:${String(sceneId)}:asset:${String(assetId)}:version:${String(versionId ?? 'current')}`
}

export function normalizeBatchGeneration(response = {}) {
  const body = payload(response)
  const jobs = Array.isArray(body.jobs) ? body.jobs : []
  const total = Number(body.total ?? jobs.length)
  if (!jobs.length || jobs[0]?.id == null) {
    return {
      ok: false,
      code: 'GENERATION_JOB_MISSING',
      message: `生成服务未返回可跟踪任务（声明 ${Number.isFinite(total) ? total : 0} 个，实际 ${jobs.length} 个）。`
    }
  }
  return { ok: true, total: Number.isFinite(total) ? total : jobs.length, job: jobs[0], jobs }
}

/** Real HTTP adapters shared by the native eight-stage shell. */
export function createWorkspaceAdapters({ projectId, project, api, sceneApi, activeUnitId }) {
  const id = () => Number(typeof projectId === 'function' ? projectId() : projectId)
  const currentProject = () => (typeof project === 'function' ? project() : project) ?? {}
  const unitId = () => (typeof activeUnitId === 'function' ? activeUnitId() : activeUnitId)

  async function persistStage(stageKey) {
    const response = payload(await api.saveResume(id(), {
      stage_key: stageKey,
      task_key: stageKey,
      content_unit_id: unitId() ?? null,
      revision: currentProject().revision ?? 0
    }))
    if (response.revision != null) currentProject().revision = response.revision
    currentProject().last_stage_key = stageKey
    return { persisted: true }
  }

  async function persistSettings(settings) {
    const response = payload(await api.addParameters(id(), {
      revision: currentProject().revision ?? 0,
      payload: { kind: 'script_workbench_settings', ...settings }
    }))
    if (response.revision != null) currentProject().revision = response.revision
    return { persisted: true, versionId: response.id ?? response.version_id ?? response.versionId ?? null }
  }

  async function persistArtifact(stageKey, artifact) {
    const targetUnitId = unitId()
    if (!targetUnitId) return { persisted: false, message: '当前阶段内容单元尚未创建。' }
    const response = payload(await api.saveDraft(targetUnitId, {
      revision: currentProject().revision ?? 0,
      content_json: JSON.stringify({ stageKey, artifact }),
      plain_text: typeof artifact === 'string' ? artifact : JSON.stringify(artifact, null, 2)
    }))
    if (response.revision != null) currentProject().revision = response.revision
    return { persisted: true, revision: response.revision ?? null }
  }

  async function bindScriptScene(binding, { sceneId } = {}) {
    if (!sceneId) return { persisted: false, message: '缺少稳定场景 ID。' }
    const response = payload(await sceneApi.apply(id(), binding.sceneAssetId, {
      targetType: 'SCRIPT_SCENE',
      targetId: numericId(sceneId),
      targetKey: String(sceneId),
      idempotencyKey: stableIdempotencyKey(sceneId, binding.sceneAssetId, binding.sceneAssetVersionId)
    }))
    const result = {
      persisted: true,
      applicationId: response.applicationId ?? response.application_id ?? response.id ?? null
    }
    if (binding.sceneAssetVersionId != null) result.bindingVersion = binding.sceneAssetVersionId
    return result
  }

  return { persistStage, persistSettings, persistArtifact, bindScriptScene }
}
