function body(response) { return response?.data?.data ?? response?.data ?? response ?? {} }
function failure(error, fallbackCode) {
  return {
    ok: false,
    code: error?.code || error?.response?.data?.code || fallbackCode,
    message: error?.response?.data?.message || error?.message || '生成结果处理失败'
  }
}

/** Loads the server-accepted version identified by the unit's authoritative current pointer. */
export async function loadAcceptedGeneration({ response, listUnits, listVersions }) {
  const unitId = response?.target_id ?? response?.targetId
  const resultVersionId = response?.result_version_id ?? response?.resultVersionId
  const units = await listUnits()
  const unit = units.find(item => Number(item.id) === Number(unitId))
  if (!unit) throw new Error('生成结果对应的内容单元不存在')
  const currentVersionId = unit.current_version_id ?? unit.currentVersionId
  if (Number(currentVersionId) !== Number(resultVersionId)) throw new Error('当前内容版本尚未切换到已采用结果')
  const versions = await listVersions(unit.id)
  const version = versions.find(item => Number(item.id) === Number(currentVersionId))
  if (!version) throw new Error('已采用的内容版本不可用')
  return { units, unit, version, content: JSON.parse(version.content_json ?? version.contentJson ?? '{}') }
}

/** Persists the candidate decision before mutating the local workbench audit state. */
export async function persistGenerationDecision({ decision, serverJobId, localTaskId, api, workbench, refresh }) {
  const accepted = decision === 'accept'
  const remote = accepted ? api?.acceptGenerationJob : api?.discardGenerationJob
  const local = accepted ? workbench?.acceptGeneration : workbench?.discardGeneration
  if (!['accept', 'discard'].includes(decision) || !serverJobId || typeof remote !== 'function' || typeof local !== 'function') {
    return { ok: false, code: 'GENERATION_DECISION_UNAVAILABLE', message: '生成候选版本处理服务不可用' }
  }
  try {
    const response = body(await remote(serverJobId))
    const localResult = local(localTaskId)
    if (localResult?.allowed === false) return localResult
    let refreshFailure = null
    if (accepted && typeof refresh === 'function') {
      try { await refresh(response) } catch (error) { refreshFailure = failure(error, 'GENERATION_ACCEPT_REFRESH_FAILED') }
    }
    return { ok: true, response, localResult, refreshFailure }
  } catch (error) {
    return failure(error, accepted ? 'GENERATION_ACCEPT_FAILED' : 'GENERATION_DISCARD_FAILED')
  }
}
