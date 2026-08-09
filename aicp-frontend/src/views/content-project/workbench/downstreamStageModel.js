function clone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}

function guidance(code, title, message, targetAction) {
  return { allowed: false, ok: false, code, title, message, targetAction }
}

function adapterRequired(name) {
  return guidance('PERSISTENCE_ADAPTER_REQUIRED', '服务暂不可用', `${name}服务未连接，请稍后重试。`, 'retry_action')
}

async function persist(adapter, payload, name) {
  if (typeof adapter !== 'function') return adapterRequired(name)
  try {
    const response = await adapter(clone(payload))
    if (response?.persisted !== true) {
      return guidance('PERSISTENCE_FAILED', `${name}失败`, response?.message || '服务未确认保存，未修改当前产物。', 'retry_action')
    }
    return response
  } catch (error) {
    return guidance('PERSISTENCE_FAILED', `${name}失败`, error?.message || '请求失败，请重试。', 'retry_action')
  }
}

function nextStableId(items, prefix) {
  let index = 1
  const used = new Set(items.map(item => item.id))
  while (used.has(`${prefix}-${String(index).padStart(3, '0')}`)) index += 1
  return `${prefix}-${String(index).padStart(3, '0')}`
}

function allScenes(state) {
  return state.episodes.flatMap(episode => episode.scenes || [])
}

function findScene(state, sceneId) {
  return allScenes(state).find(scene => scene.id === sceneId) || null
}

function sameBinding(left, right) {
  return JSON.stringify(left ?? null) === JSON.stringify(right ?? null)
}

function normalizeBinding(binding = {}) {
  return {
    sceneAssetId: binding.sceneAssetId ?? binding.assetId ?? null,
    sceneAssetVersionId: binding.sceneAssetVersionId ?? binding.versionId ?? null,
    sceneVariantId: binding.sceneVariantId ?? binding.variantId ?? null,
    sceneVariantVersion: binding.sceneVariantVersion ?? binding.variantVersion ?? null,
    sceneOverride: clone(binding.sceneOverride ?? {})
  }
}

export function createStructuredScriptState(input = {}) {
  return {
    episodes: clone(input.episodes ?? []),
    openedEpisodeId: input.openedEpisodeId ?? null,
    activeGeneration: null,
    lastResult: null
  }
}

export async function openEpisodeStructure(state, episodeId, adapter) {
  const episode = state.episodes.find(item => item.id === episodeId)
  if (!episode) return guidance('EPISODE_REQUIRED', '未找到单集', '请选择存在的单集后再打开结构。', 'focus_episode_list')
  const response = await persist(adapter, { episodeId }, '打开单集结构')
  if (response.allowed === false) return response
  state.openedEpisodeId = episodeId
  return episode
}

export async function addBeat(state, episodeId, draft = {}, adapter) {
  const episode = state.episodes.find(item => item.id === episodeId)
  if (!episode) return guidance('EPISODE_REQUIRED', '未找到单集', '请选择单集后再新增节拍。', 'focus_episode_list')
  episode.beats ||= []
  const candidate = { ...clone(draft), id: nextStableId(episode.beats, `${episode.id}-BEAT`) }
  const response = await persist(adapter, candidate, '新增节拍')
  if (response.allowed === false) return response
  const beat = { ...candidate, ...clone(response.beat ?? {}), id: candidate.id }
  episode.beats.push(beat)
  return beat
}

export async function regenerateBeat(state, episodeId, beatId, adapter) {
  const episode = state.episodes.find(item => item.id === episodeId)
  const beat = episode?.beats?.find(item => item.id === beatId)
  if (!beat) return guidance('BEAT_REQUIRED', '请选择节拍', '选择需要重新生成的节拍后再操作。', 'focus_beat_list')
  const response = await persist(adapter, { episodeId, beatId, before: beat }, '重新生成节拍')
  if (response.allowed === false) return response
  Object.assign(beat, clone(response.beat ?? {}), { id: beatId })
  state.lastResult = clone(response.result ?? null)
  return beat
}

export function createScriptBodyState(input = {}) {
  const state = {
    episodes: clone(input.episodes ?? []),
    selectedBlockId: input.selectedBlockId ?? null,
    sceneAssetPicker: { open: false, sceneId: null },
    preStoryboardWarnings: [],
    lastCheck: clone(input.lastCheck ?? null),
    lastExport: null,
    lastResult: null
  }
  for (const scene of allScenes(state)) {
    scene.blocks ||= []
    scene.bindingState ||= scene.assetBinding ? 'BOUND' : 'UNBOUND'
    if (!scene.assetBinding) state.preStoryboardWarnings.push({ sceneId: scene.id, code: 'SCENE_ASSET_UNBOUND', message: '进入文字分镜前请绑定场景资产。' })
  }
  return state
}

export function selectScriptBlock(state, blockId) {
  const exists = allScenes(state).some(scene => scene.blocks?.some(block => block.id === blockId))
  state.selectedBlockId = exists ? blockId : null
  return exists
}

export function addScriptScene(state, episodeId, draft = {}) {
  const episode = state.episodes.find(item => item.id === episodeId)
  if (!episode) return guidance('EPISODE_REQUIRED', '请选择单集', '选择单集后再新增场景。', 'focus_episode_list')
  episode.scenes ||= []
  const scene = {
    ...clone(draft), id: nextStableId(episode.scenes, `${episode.id}-SCENE`), blocks: clone(draft.blocks ?? []),
    assetBinding: null, bindingState: 'UNBOUND'
  }
  episode.scenes.push(scene)
  state.sceneAssetPicker = { open: true, sceneId: scene.id }
  state.preStoryboardWarnings.push({ sceneId: scene.id, code: 'SCENE_ASSET_UNBOUND', message: '进入文字分镜前请绑定场景资产。' })
  return scene
}

export function addScriptBlock(state, sceneId, draft = {}) {
  const scene = findScene(state, sceneId)
  if (!scene) return guidance('SCRIPT_SCENE_REQUIRED', '请选择场景', '选择正文场景后再新增正文块。', 'focus_script_scenes')
  scene.blocks ||= []
  const block = { ...clone(draft), id: nextStableId(scene.blocks, `${scene.id}-BLOCK`), type: draft.type || 'action', text: draft.text || '' }
  scene.blocks.push(block)
  state.selectedBlockId = block.id
  return block
}

export async function applyBlockAiAction(state, action, adapter) {
  if (!state.selectedBlockId) return guidance('SCRIPT_BLOCK_REQUIRED', '请先选择正文块', '选择动作、对白或旁白正文块后才能执行此操作。', 'focus_script_blocks')
  const scene = allScenes(state).find(item => item.blocks?.some(block => block.id === state.selectedBlockId))
  const block = scene?.blocks?.find(item => item.id === state.selectedBlockId)
  if (!block) return guidance('SCRIPT_BLOCK_REQUIRED', '正文块已失效', '重新选择正文块后再试。', 'focus_script_blocks')
  const response = await persist(adapter, { action, sceneId: scene.id, block: clone(block) }, '正文 AI 操作')
  if (response.allowed === false) return response
  if (response.block) Object.assign(block, clone(response.block), { id: block.id })
  state.lastResult = clone(response.result ?? null)
  return response
}

export async function bindScriptSceneAsset(state, sceneId, asset, variant, adapter, context = {}) {
  const scene = findScene(state, sceneId)
  if (!scene) return guidance('SCRIPT_SCENE_REQUIRED', '请选择场景', '选择正文场景后再绑定资产。', 'focus_script_scenes')
  if (context.degraded) return guidance('DEGRADED_READ_ONLY', '资产服务处于只读状态', '当前展示的是缓存资产，恢复连接后才能新增绑定。', 'retry_scene_assets')
  if (String(asset?.status || '').toUpperCase() === 'ARCHIVED') {
    return guidance('SCENE_ASSET_UNAVAILABLE', '场景资产不可绑定', '已归档资产不能创建新的场景引用。', 'choose_active_scene_asset')
  }
  const binding = normalizeBinding({
    sceneAssetId: asset?.id,
    sceneAssetVersionId: asset?.currentVersionId ?? asset?.assetVersionId ?? asset?.versionId,
    sceneVariantId: variant?.id,
    sceneVariantVersion: variant?.version,
    sceneOverride: {}
  })
  if ([binding.sceneAssetId, binding.sceneAssetVersionId].some(value => value == null || value === '')) {
    return guidance('SCENE_ASSET_VERSION_REQUIRED', '请选择完整资产版本', '绑定必须包含母资产和母资产版本。', 'choose_scene_asset_version')
  }
  const hasVariantId = binding.sceneVariantId != null && binding.sceneVariantId !== ''
  const hasVariantVersion = binding.sceneVariantVersion != null && binding.sceneVariantVersion !== ''
  if (hasVariantId !== hasVariantVersion) {
    return guidance('SCENE_ASSET_VARIANT_PAIR_REQUIRED', '场景变体版本不完整', '变体为可选；选择变体时必须同时包含变体 ID 和变体版本。', 'choose_scene_asset_version')
  }
  const response = await persist(adapter, binding, '绑定场景资产')
  if (response.allowed === false) return response
  scene.assetBinding = binding
  scene.bindingState = 'BOUND'
  scene.bindingVersion = response.bindingVersion ?? response.version ?? null
  state.preStoryboardWarnings = state.preStoryboardWarnings.filter(item => item.sceneId !== sceneId)
  state.sceneAssetPicker = { open: false, sceneId: null }
  return scene
}

export function deferSceneAssetBinding(state, sceneId) {
  state.sceneAssetPicker = { open: false, sceneId: null }
  if (!state.preStoryboardWarnings.some(item => item.sceneId === sceneId)) {
    state.preStoryboardWarnings.push({ sceneId, code: 'SCENE_ASSET_UNBOUND', message: '进入文字分镜前请绑定场景资产。' })
  }
  return { ok: true, deferred: true }
}

export async function changeSceneSpace(state, sceneId, space, scope, adapter) {
  const scene = findScene(state, sceneId)
  if (!scene) return guidance('SCRIPT_SCENE_REQUIRED', '请选择场景', '选择场景后再修改空间。', 'focus_script_scenes')
  if (!['CURRENT_SCENE', 'MASTER_ASSET'].includes(scope)) {
    return guidance('SPACE_CHANGE_SCOPE_REQUIRED', '请选择空间修改范围', '明确选择“仅当前场景”或“更新母资产”。', 'choose_space_change_scope')
  }
  if (scope === 'MASTER_ASSET' && !scene.assetBinding) {
    return guidance('SCENE_ASSET_VERSION_REQUIRED', '场景尚未绑定母资产', '先绑定母资产版本，再选择更新母资产。', 'choose_scene_asset_version')
  }
  const response = await persist(adapter, { sceneId, space, scope, assetBinding: scene.assetBinding }, scope === 'MASTER_ASSET' ? '更新母资产' : '更新当前场景')
  if (response.allowed === false) return response
  if (scope === 'MASTER_ASSET' && !(Number(response.sceneAssetVersionId) > 0)) {
    return guidance('SCENE_ASSET_VERSION_REQUIRED', '母资产版本未更新', '服务未返回新的母资产版本，当前场景仍保留原绑定。', 'retry_master_asset_update')
  }
  scene.space = space
  if (scope === 'CURRENT_SCENE' && scene.assetBinding) {
    scene.assetBinding.sceneOverride = { ...(scene.assetBinding.sceneOverride || {}), space }
  }
  if (scope === 'MASTER_ASSET' && scene.assetBinding) {
    scene.assetBinding.sceneAssetVersionId = response.sceneAssetVersionId ?? scene.assetBinding.sceneAssetVersionId
    if (response.sceneAssetVersionNo != null) scene.assetBinding.sceneAssetVersionNo = response.sceneAssetVersionNo
  }
  return scene
}

export async function runScriptCheck(state, adapter) {
  const response = await persist(adapter, { episodes: state.episodes }, '运行正文检查')
  if (response.allowed === false) return response
  state.lastCheck = clone(response.check ?? response)
  return state.lastCheck
}

export async function exportScript(state, adapter, format = 'markdown') {
  const response = await persist(adapter, { format, episodes: state.episodes }, '导出正文')
  if (response.allowed === false) return response
  state.lastExport = clone(response.export ?? response)
  return state.lastExport
}

export function createReviewState(input = {}) {
  return {
    issues: clone(input.issues ?? []),
    filters: clone(input.filters ?? { severities: [], statuses: [], sceneId: null }),
    focusIssueList: false,
    revisions: clone(input.revisions ?? []),
    comparison: null,
    approvedEpisodeIds: clone(input.approvedEpisodeIds ?? []),
    lastResult: null
  }
}

export function filterReviewIssues(state, filters = state.filters) {
  state.filters = { ...state.filters, ...clone(filters) }
  const severitySet = new Set(state.filters.severity ? [state.filters.severity] : (state.filters.severities || []))
  const statusSet = new Set(state.filters.status ? [state.filters.status] : (state.filters.statuses || []))
  const excludedStatusSet = new Set(state.filters.excludedStatuses || [])
  return state.issues.filter(issue => (!severitySet.size || severitySet.has(issue.severity)) &&
    (!statusSet.size || statusSet.has(issue.status)) && !excludedStatusSet.has(issue.status) &&
    (!state.filters.sceneId || issue.sceneId === state.filters.sceneId))
}

export async function saveLocalRevision(state, issueId, draft, adapter) {
  const issue = state.issues.find(item => item.id === issueId)
  if (!issue) return guidance('REVIEW_ISSUE_REQUIRED', '请选择审核问题', '选择问题后再保存局部修订。', 'focus_review_issues')
  const response = await persist(adapter, { issueId, before: issue.before ?? issue.content ?? '', ...clone(draft) }, '保存局部修订')
  if (response.allowed === false) return response
  const revision = { id: response.id ?? `REV-${state.revisions.length + 1}`, issueId, version: response.version, before: issue.before ?? issue.content ?? '', after: draft.after ?? draft.content ?? '', ...clone(response.revision ?? {}) }
  state.revisions.push(revision)
  issue.status = 'RESOLVED'
  issue.revisionId = revision.id
  return revision
}

export function compareReviewRevision(state, revisionId) {
  const revision = state.revisions.find(item => item.id === revisionId)
  if (!revision) return guidance('REVISION_REQUIRED', '请选择修订版本', '选择局部修订后再对比。', 'focus_revision_history')
  state.comparison = { revisionId, before: revision.before, after: revision.after }
  return state.comparison
}

export async function approveReviewEpisode(state, episodeId, adapter) {
  const blockers = state.issues.filter(issue => ['BLOCKER', 'HIGH'].includes(issue.severity) && !['RESOLVED', 'WAIVED'].includes(issue.status))
  if (blockers.length) {
    state.filters = { severities: ['BLOCKER', 'HIGH'], statuses: [], excludedStatuses: ['RESOLVED', 'WAIVED'] }
    state.focusIssueList = true
    return guidance('REVIEW_BLOCKERS_REMAIN', '仍有高风险问题', `解决 ${blockers.length} 个 HIGH/BLOCKER 问题后才能审核通过。`, 'focus_filtered_review_issues')
  }
  const response = await persist(adapter, { episodeId, revisionIds: state.revisions.map(item => item.id) }, '审核通过本集')
  if (response.allowed === false) return response
  if (!state.approvedEpisodeIds.includes(episodeId)) state.approvedEpisodeIds.push(episodeId)
  return response
}

export function createStoryboardShot(input = {}) {
  return {
    id: input.id ?? null,
    sceneId: input.sceneId ?? null,
    description: input.description ?? '',
    durationMs: input.durationMs ?? 3000,
    assetBinding: input.assetBinding ? normalizeBinding(input.assetBinding) : null,
    snapshotLocked: input.snapshotLocked === true,
    sceneAssetSnapshotRef: clone(input.sceneAssetSnapshotRef ?? null)
  }
}

export function createStoryboardState(input = {}) {
  return {
    shots: (input.shots ?? []).map(createStoryboardShot),
    viewMode: input.viewMode === 'table' ? 'table' : 'card',
    continuity: clone(input.continuity ?? { status: 'NOT_RUN', passed: false, issues: [], groups: groupContinuityIssues([]) }),
    archived: input.archived === true,
    contentVersionId: input.contentVersionId ?? null,
    contentVersionLocked: input.contentVersionLocked === true,
    storyboardVersionId: input.storyboardVersionId ?? null,
    storyboardVersionLocked: input.storyboardVersionLocked === true,
    mindmap: clone(input.mindmap ?? null),
    canvasProject: null,
    lastResult: null
  }
}

export async function addStoryboardShot(state, draft, adapter) {
  if (draft?.sceneId == null || String(draft.sceneId).trim() === '') {
    return guidance('STORYBOARD_SCENE_REQUIRED', '请选择分镜场景', '从已有场景中选择一个稳定场景 ID 后再新增镜头。', 'choose_storyboard_scene')
  }
  const candidate = createStoryboardShot({ ...draft, id: nextStableId(state.shots, 'SHOT') })
  const response = await persist(adapter, candidate, '新增镜头')
  if (response.allowed === false) return response
  const shot = createStoryboardShot({ ...candidate, ...clone(response.shot ?? {}), id: candidate.id, assetBinding: candidate.assetBinding })
  state.shots.push(shot)
  state.continuity = { status: 'STALE', passed: false, issues: [], groups: groupContinuityIssues([]) }
  return shot
}

export async function splitStoryboardShot(state, shotId, adapter) {
  const index = state.shots.findIndex(item => item.id === shotId)
  if (index < 0) return guidance('SHOT_REQUIRED', '请选择镜头', '选择镜头后再拆分。', 'focus_storyboard_shots')
  const source = state.shots[index]
  const response = await persist(adapter, { shotId, source: clone(source) }, '拆分镜头')
  if (response.allowed === false) return response
  const replacements = (response.shots ?? []).map(createStoryboardShot)
  const replacementIds = replacements.map(item => item.id)
  const remainingIds = new Set(state.shots.filter(item => item.id !== shotId).map(item => String(item.id)))
  const validIds = replacementIds.every(id => id != null && String(id).trim() !== '')
  const uniqueIds = new Set(replacementIds.map(id => String(id))).size === replacementIds.length
  const conflictFreeIds = replacementIds.every(id => !remainingIds.has(String(id)))
  if (!validIds || !uniqueIds || !conflictFreeIds) {
    return guidance('SHOT_STABLE_ID_CONFLICT', '拆分镜头 ID 无效', '服务返回了空、重复或与现有镜头冲突的 ID，原镜头已保留。', 'retry_split_shot')
  }
  if (replacements.length < 2 || replacements.some(item => !sameBinding(item.assetBinding, source.assetBinding))) {
    return guidance('SHOT_BINDING_CONFLICT', '拆分后的场景绑定不一致', '请选择统一绑定或显式解除绑定后再完成拆分。', 'resolve_shot_binding_conflict')
  }
  state.shots.splice(index, 1, ...replacements)
  state.continuity.status = 'STALE'; state.continuity.passed = false
  return replacements
}

export async function mergeStoryboardShots(state, shotIds, adapter) {
  const selected = shotIds.map(id => state.shots.find(item => item.id === id)).filter(Boolean)
  if (selected.length !== shotIds.length || selected.length < 2) return guidance('SHOT_SELECTION_REQUIRED', '请选择至少两个镜头', '选择同一场景中的连续镜头后再合并。', 'focus_storyboard_shots')
  if (selected.some(item => item.sceneId !== selected[0].sceneId) || selected.some(item => !sameBinding(item.assetBinding, selected[0].assetBinding))) {
    return guidance('SHOT_BINDING_CONFLICT', '镜头场景绑定冲突', '合并前请选择保留哪个场景资产版本，系统不会静默丢弃绑定。', 'resolve_shot_binding_conflict')
  }
  const response = await persist(adapter, { shotIds, assetBinding: selected[0].assetBinding }, '合并镜头')
  if (response.allowed === false) return response
  const merged = createStoryboardShot({ ...(response.shot ?? {}), id: response.shot?.id ?? selected[0].id, sceneId: selected[0].sceneId, assetBinding: selected[0].assetBinding })
  const firstIndex = Math.min(...selected.map(item => state.shots.indexOf(item)))
  state.shots = state.shots.filter(item => !shotIds.includes(item.id))
  state.shots.splice(firstIndex, 0, merged)
  state.continuity.status = 'STALE'; state.continuity.passed = false
  return merged
}

export function toggleShotView(state) {
  state.viewMode = state.viewMode === 'card' ? 'table' : 'card'
  return state.viewMode
}

const CONTINUITY_GROUPS = {
  scene: new Set(['MISSING_ASSET', 'STALE_ASSET', 'SCENE_CONFLICT']),
  variant: new Set(['VARIANT_MISMATCH']),
  fixedProp: new Set(['FIXED_PROP_CONFLICT']),
  characterState: new Set(['CHARACTER_STATE_CONFLICT']),
  axis: new Set(['AXIS_CONFLICT'])
}

export function groupContinuityIssues(issues = []) {
  const groups = { scene: [], variant: [], fixedProp: [], characterState: [], axis: [] }
  for (const issue of issues) {
    const key = Object.entries(CONTINUITY_GROUPS).find(([, codes]) => codes.has(issue.code))?.[0] ?? 'scene'
    groups[key].push(clone(issue))
  }
  return groups
}

export async function runContinuityCheck(state, adapter) {
  const response = await persist(adapter, { storyboardVersionId: state.storyboardVersionId }, '连续性检查')
  if (response.allowed === false) return response
  const issues = clone(response.issues ?? [])
  state.continuity = { status: response.passed ? 'PASSED' : 'FAILED', passed: response.passed === true, issues, groups: groupContinuityIssues(issues) }
  return state.continuity
}

function completeSnapshotReference(shot) {
  const reference = shot.sceneAssetSnapshotRef
  const nonBlank = value => value != null && String(value).trim() !== ''
  const positiveVersion = value => Number.isInteger(Number(value)) && Number(value) > 0
  if (!shot.snapshotLocked || !reference || !nonBlank(reference.fingerprint) || !nonBlank(reference.shotId)) return false
  if (String(reference.shotId) !== String(shot.id)) return false
  if (!positiveVersion(reference.sceneAssetId) || !positiveVersion(reference.sceneAssetVersionId)) return false
  const variantIdProvided = reference.sceneVariantId != null
  const variantVersionProvided = reference.sceneVariantVersion != null
  if (variantIdProvided !== variantVersionProvided) return false
  if (!variantIdProvided) return true
  return nonBlank(reference.sceneVariantId) && positiveVersion(reference.sceneVariantVersion)
}

function allSnapshotsLocked(state) {
  return state.shots.length > 0 && state.shots.every(completeSnapshotReference)
}

function snapshotReferencePayload(shot) {
  const reference = shot.sceneAssetSnapshotRef
  const payload = {
    shot_id: reference.shotId,
    fingerprint: reference.fingerprint,
    scene_asset_id: reference.sceneAssetId,
    scene_asset_version_id: reference.sceneAssetVersionId
  }
  if (reference.sceneVariantId != null) payload.scene_variant_id = reference.sceneVariantId
  if (reference.sceneVariantVersion != null) payload.scene_variant_version = reference.sceneVariantVersion
  return payload
}

export async function archiveStoryboard(state, adapter) {
  if (!state.continuity?.passed) return guidance('CONTINUITY_PASS_REQUIRED', '请先通过连续性检查', '解决连续性问题并重新检查后才能归档。', 'run_continuity_check')
  if (!allSnapshotsLocked(state)) return guidance('LOCKED_SNAPSHOTS_REQUIRED', '场景快照尚未锁定', '锁定每个镜头的不可变场景快照后才能归档。', 'lock_storyboard_snapshots')
  const response = await persist(adapter, { storyboardVersionId: state.storyboardVersionId, snapshotReferences: state.shots.map(shot => shot.sceneAssetSnapshotRef) }, '完成并归档')
  if (response.allowed === false) return response
  state.archived = true
  return response
}

export async function configureMindmap(state, configuration, adapter) {
  const response = await persist(adapter, configuration, '配置导图')
  if (response.allowed === false) return response
  state.mindmap = clone(response.configuration ?? configuration)
  return state.mindmap
}

export function buildShotAssetBinding(binding = {}) {
  const normalized = normalizeBinding(binding)
  return {
    scene_asset_id: normalized.sceneAssetId,
    scene_asset_version_id: normalized.sceneAssetVersionId,
    scene_variant_id: normalized.sceneVariantId,
    scene_variant_version: normalized.sceneVariantVersion,
    scene_override: clone(normalized.sceneOverride)
  }
}

export function buildCanvasCreationPayload(state, draft = {}) {
  return {
    name: draft.name,
    purpose: draft.purpose,
    content_version_id: state.contentVersionId,
    storyboard_version_id: state.storyboardVersionId,
    scene_snapshot_references: state.shots.map(snapshotReferencePayload)
  }
}

export async function createCanvasProject(state, draft, adapter) {
  if (!state.contentVersionId || !state.storyboardVersionId || !state.contentVersionLocked || !state.storyboardVersionLocked) return guidance('LOCKED_VERSIONS_REQUIRED', '缺少锁定版本', '锁定正文和分镜版本后才能创建画布项目。', 'lock_content_and_storyboard')
  if (!allSnapshotsLocked(state)) return guidance('LOCKED_SNAPSHOTS_REQUIRED', '场景快照尚未锁定', '所有镜头必须包含不可变场景快照引用。', 'lock_storyboard_snapshots')
  const payload = buildCanvasCreationPayload(state, draft)
  const response = await persist(adapter, payload, '创建画布项目')
  if (response.allowed === false) return response
  state.canvasProject = clone(response)
  return response
}
