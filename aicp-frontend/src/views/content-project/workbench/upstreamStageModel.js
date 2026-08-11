export const CHINESE_PASTE_LIMIT = 2000

export const BUILT_IN_DEMO_MODELS = Object.freeze([
  {
    id: 'demo-script-standard', name: '内置演示·通用剧本', status: 'available', source: 'builtin',
    sourceBadge: '内置演示', pointRule: '演示模式不扣除积分', estimatedPoints: 0, demo: true
  },
  {
    id: 'demo-script-fast', name: '内置演示·快速草稿', status: 'available', source: 'builtin',
    sourceBadge: '内置演示', pointRule: '演示模式不扣除积分', estimatedPoints: 0, demo: true
  }
])

/** Matches GenerationController#estimateCredits: type is a required generation enum. */
export function buildCreditEstimateRequest(model = {}) {
  return {
    type: 'agent',
    model_id: model.id ?? model.modelId,
    parameters: { operation: 'script_workbench' }
  }
}

function rejected(code, title, message, targetAction = null) {
  return { allowed: false, code, title, message, targetAction }
}

function clone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}

function text(value) {
  return String(value ?? '').trim()
}

function unwrapModels(response) {
  const payload = response?.data?.data ?? response?.data ?? response ?? {}
  return Array.isArray(payload) ? payload : (payload.models ?? payload.items ?? [])
}

function normalizeRemoteModel(model) {
  const id = model.id ?? model.modelId ?? model.model_id
  const name = model.name ?? model.modelName ?? model.model_name ?? id
  const status = String(model.status ?? 'available').toLowerCase()
  if (!id || !['available', 'active', 'enabled', 'ready'].includes(status)) return null
  const estimated = Number(model.estimatedPoints ?? model.estimated_points ?? model.estimatedCredits ?? model.estimated_credits)
  return {
    id,
    name,
    status: 'available',
    source: model.source ?? model.provider ?? '3001',
    sourceBadge: model.sourceBadge ?? model.source_badge ?? '3001 平台',
    pointRule: model.pointRule ?? model.point_rule ?? model.creditRule ?? model.credit_rule ?? '按 3001 平台实际用量结算',
    estimatedPoints: Number.isFinite(estimated) && estimated > 0 ? estimated : null,
    demo: false
  }
}

/** Loads the 3001-compatible model catalog. Demos are returned only as a visible fallback. */
export async function loadCreationModels(fetchModels) {
  try {
    if (typeof fetchModels !== 'function') throw new Error('模型服务未配置')
    const models = unwrapModels(await fetchModels()).map(normalizeRemoteModel).filter(Boolean)
    if (models.length) return { mode: 'remote', models, guidance: null }
    return {
      mode: 'demo', models: BUILT_IN_DEMO_MODELS.map(clone),
      guidance: rejected('MODEL_CATALOG_EMPTY', '暂无可用平台模型', '3001 暂无可用模型，已切换到内置演示模型。', 'refresh_model_catalog')
    }
  } catch (error) {
    return {
      mode: 'demo', models: BUILT_IN_DEMO_MODELS.map(clone),
      guidance: rejected('MODEL_CATALOG_UNREACHABLE', '模型服务不可用', `无法连接 3001 模型服务，已切换到内置演示模型。${error?.message ? `（${error.message}）` : ''}`, 'refresh_model_catalog')
    }
  }
}

export function countChineseCharacters(value = '') {
  return Array.from(String(value).matchAll(/\p{Script=Han}/gu)).length
}

export function validatePastedNovel(value = '') {
  const chineseCount = countChineseCharacters(value)
  const excess = Math.max(0, chineseCount - CHINESE_PASTE_LIMIT)
  return {
    allowed: excess === 0,
    code: excess ? 'NOVEL_TEXT_TOO_LONG' : null,
    chineseCount,
    limit: CHINESE_PASTE_LIMIT,
    excess,
    message: excess ? `已超出 ${excess} 个汉字，请删减后重试，或改用文件上传。` : ''
  }
}

const REQUIRED_CREATION_FIELDS = [
  ['creationType', '创作类型'], ['genre', '题材分类'], ['tone', '故事基调'], ['audience', '目标受众'],
  ['adaptationStrength', '改编强度'], ['outputFormat', '输出格式']
]

export function validateCreationSettings(settings = {}) {
  for (const [key, label] of REQUIRED_CREATION_FIELDS) {
    if (!text(settings[key])) return rejected('CREATION_SETTING_REQUIRED', `请完善${label}`, `${label}为必填项。`, `focus_${key}`)
  }
  if (!Number.isInteger(Number(settings.episodeCount)) || Number(settings.episodeCount) <= 0) {
    return rejected('EPISODE_COUNT_INVALID', '请填写有效集数', '集数必须是大于 0 的整数。', 'focus_episode_count')
  }
  if (!Number.isFinite(Number(settings.episodeDuration)) || Number(settings.episodeDuration) <= 0) {
    return rejected('EPISODE_DURATION_INVALID', '请填写有效时长', '单集时长必须大于 0。', 'focus_episode_duration')
  }
  const model = settings.model
  if (!model?.id) return rejected('MODEL_REQUIRED', '请选择模型', '选择可用模型后才能保存创作设置。', 'focus_model_selector')
  if (model.demo !== true && !(Number.isFinite(Number(settings.estimatedPoints)) && Number(settings.estimatedPoints) > 0)) {
    return rejected('POINT_ESTIMATE_REQUIRED', '请确认积分预估', '非演示模型需要大于 0 的预估积分。', 'focus_point_estimate')
  }
  return { allowed: true, code: null }
}

function normalizeList(value) {
  return Array.isArray(value) ? clone(value) : []
}

export function createAnalysisState(seed = {}) {
  return {
    synopsis: seed.synopsis ?? '',
    events: normalizeList(seed.events),
    chapterOutline: normalizeList(seed.chapterOutline),
    worldview: clone(seed.worldview ?? { worldType: '', timeSetting: '', powerSystem: '', rules: '', factions: [] }),
    locations: normalizeList(seed.locations),
    characters: normalizeList(seed.characters),
    artifactVersions: normalizeList(seed.artifactVersions),
    sectionVersions: { ...(seed.sectionVersions ?? {}) }
  }
}

function validListItem(section, item) {
  if (section === 'events') return Boolean(text(item?.title) && text(item?.summary))
  if (section === 'chapterOutline') return Boolean(text(item?.title) && text(item?.summary))
  if (section === 'characters') return Boolean(text(item?.name) && text(item?.role) && text(item?.goal) && text(item?.personality) && text(item?.arc))
  return false
}

export function validateAnalysisSection(section, value) {
  if (section === 'synopsis' && !text(value)) {
    return rejected('ANALYSIS_FIELD_REQUIRED', '请填写故事梗概', '故事梗概不能为空。', 'focus_synopsis_editor')
  }
  if (['events', 'chapterOutline', 'characters'].includes(section)) {
    if (!Array.isArray(value) || !value.length || value.some(item => !validListItem(section, item))) {
      return rejected('ANALYSIS_FIELD_REQUIRED', '请补全分析信息', `${section} 存在空缺的必填字段。`, `focus_${section}_editor`)
    }
  }
  if (section === 'worldview') {
    if (!text(value?.worldType) || !text(value?.timeSetting) || !text(value?.powerSystem) || !text(value?.rules) || !Array.isArray(value?.factions) || !value.factions.length) {
      return rejected('ANALYSIS_FIELD_REQUIRED', '请补全世界观', '世界类型与时间、力量体系/规则、势力阵营均为必填项。', 'focus_worldview_editor')
    }
    if (value.locations != null && (!Array.isArray(value.locations) || value.locations.some(location => !text(location?.id) || !text(location?.name) || !text(location?.spaceType)))) {
      return rejected('ANALYSIS_FIELD_REQUIRED', '请补全主要地点', '地点 ID、名称和空间类型均为必填项。', 'focus_worldview_editor')
    }
  }
  if (!['synopsis', 'events', 'chapterOutline', 'worldview', 'characters'].includes(section)) {
    return rejected('ANALYSIS_SECTION_UNKNOWN', '未知分析区域', '无法保存未知的分析区域。')
  }
  return { allowed: true, code: null }
}

/** Records a new artifact version only after the persistence adapter confirms it. */
export async function saveAnalysisSection(state, section, value, persistArtifact) {
  const validation = validateAnalysisSection(section, value)
  if (!validation.allowed) return validation
  if (typeof persistArtifact !== 'function') {
    return rejected('ANALYSIS_PERSISTENCE_FAILED', '分析保存服务不可用', '无法保存新版本，请稍后重试。', 'retry_analysis_save')
  }
  const draft = clone(value)
  const previousVersion = Number(state.sectionVersions[section]) || 0
  try {
    const response = await persistArtifact({ section, value: draft, previousVersion })
    if (response?.persisted !== true || !(Number(response.version) > previousVersion)) {
      return rejected('ANALYSIS_PERSISTENCE_FAILED', '分析内容未保存', response?.message || '服务端未返回新产物版本，本地内容未替换。', 'retry_analysis_save')
    }
    if (section === 'worldview' && Array.isArray(draft.locations)) {
      const { locations, ...worldview } = draft
      state.worldview = worldview
      state.locations = locations
    } else {
      state[section] = draft
    }
    state.sectionVersions[section] = Number(response.version)
    const record = {
      ok: true,
      section,
      version: Number(response.version),
      artifactPath: response.artifactPath ?? null,
      impact: clone(response.impact ?? null)
    }
    state.artifactVersions.push(record)
    return record
  } catch (error) {
    return rejected('ANALYSIS_PERSISTENCE_FAILED', '分析内容未保存', error?.message || '保存失败，请重试。', 'retry_analysis_save')
  }
}

function normalizedSceneAsset(asset) {
  return {
    id: asset.id,
    stableId: asset.stableId,
    versionId: asset.currentVersionId ?? asset.versionId,
    versionNo: asset.currentVersionNo ?? asset.versionNo,
    status: asset.status
  }
}

export async function convertLocationToSceneAsset(state, locationId, adapter = {}) {
  const location = state.locations.find(item => item.id === locationId)
  if (!location) return rejected('WORLD_LOCATION_NOT_FOUND', '未找到主要地点', '请刷新小说分析后重试。', 'refresh_analysis')
  if (location.sceneAsset?.id) {
    if (typeof adapter.openAsset === 'function') await adapter.openAsset(clone(location.sceneAsset))
    return { ok: true, created: false, asset: clone(location.sceneAsset) }
  }
  if (typeof adapter.createFromLocation !== 'function') {
    return rejected('SCENE_ASSET_ADAPTER_REQUIRED', '场景资产服务不可用', '请恢复场景资产服务后重试。', 'retry_scene_conversion')
  }
  try {
    const response = await adapter.createFromLocation({
      worldLocationRef: location.id,
      name: location.name,
      spaceType: location.spaceType
    })
    const asset = response?.data ?? response?.asset ?? response
    if (response?.ok === false || !asset?.id || !asset?.stableId || !(asset.currentVersionId ?? asset.versionId)) {
      return rejected('SCENE_ASSET_CONVERSION_FAILED', '地点转换失败', response?.message || '服务端未返回有效场景资产版本。', 'retry_scene_conversion')
    }
    location.sceneAsset = normalizedSceneAsset(asset)
    return { ok: true, created: true, asset: clone(location.sceneAsset) }
  } catch (error) {
    return rejected('SCENE_ASSET_CONVERSION_FAILED', '地点转换失败', error?.message || '请稍后重试。', 'retry_scene_conversion')
  }
}

export function createAdaptationState(seed = {}) {
  return {
    hooks: normalizeList(seed.hooks),
    selectedHookId: seed.selectedHookId ?? null,
    hookVersion: Number(seed.hookVersion) || 0,
    rules: normalizeList(seed.rules),
    nextRuleNo: Number(seed.nextRuleNo) || (seed.rules?.length ?? 0) + 1,
    confirmed: seed.confirmed === true,
    version: Number(seed.version) || 0,
    impact: clone(seed.impact ?? null)
  }
}

export async function persistHookSelection(state, hookId, persistHook) {
  const guarded = ensureAdaptationDraft(state)
  if (guarded) return guarded
  if (!state.hooks.some(hook => hook.id === hookId)) return rejected('HOOK_NOT_FOUND', '开场方案不存在', '请重新选择高压开场。', 'focus_high_pressure_hooks')
  if (typeof persistHook !== 'function') return rejected('HOOK_PERSISTENCE_FAILED', '开场方案未保存', '保存服务不可用。', 'retry_hook_save')
  try {
    const response = await persistHook(hookId)
    if (response?.persisted !== true) return rejected('HOOK_PERSISTENCE_FAILED', '开场方案未保存', response?.message || '请稍后重试。', 'retry_hook_save')
    if (!(Number(response.version) > state.hookVersion)) return rejected('HOOK_PERSISTENCE_FAILED', '开场方案未保存', '服务端未返回新的开场版本，请重试。', 'retry_hook_save')
    state.selectedHookId = hookId
    state.hookVersion = Number(response.version)
    return { ok: true, hookId, version: state.hookVersion }
  } catch (error) {
    return rejected('HOOK_PERSISTENCE_FAILED', '开场方案未保存', error?.message || '请稍后重试。', 'retry_hook_save')
  }
}

function ensureAdaptationDraft(state) {
  return state.confirmed ? rejected('ADAPTATION_CONFIRMED', '改编方案已确认', '需要先创建新版本，才能继续修改规则。', 'create_adaptation_version') : null
}

export function addAdaptationRule(state, draft = {}) {
  const guarded = ensureAdaptationDraft(state)
  if (guarded) return guarded
  if (!text(draft.title) || !text(draft.instruction)) return rejected('ADAPTATION_RULE_REQUIRED', '请补全改编规则', '规则名称与执行说明不能为空。', 'focus_adaptation_rule')
  const rule = { id: `RULE-${String(state.nextRuleNo).padStart(3, '0')}`, title: text(draft.title), instruction: text(draft.instruction) }
  state.nextRuleNo += 1
  state.rules.push(rule)
  return rule
}

export function updateAdaptationRule(state, ruleId, patch = {}) {
  const guarded = ensureAdaptationDraft(state)
  if (guarded) return guarded
  const rule = state.rules.find(item => item.id === ruleId)
  if (!rule) return rejected('ADAPTATION_RULE_NOT_FOUND', '改编规则不存在', '请刷新后重试。')
  const candidate = { ...rule, ...patch }
  if (!text(candidate.title) || !text(candidate.instruction)) return rejected('ADAPTATION_RULE_REQUIRED', '请补全改编规则', '规则名称与执行说明不能为空。', 'focus_adaptation_rule')
  Object.assign(rule, { title: text(candidate.title), instruction: text(candidate.instruction) })
  return rule
}

export function removeAdaptationRule(state, ruleId) {
  const guarded = ensureAdaptationDraft(state)
  if (guarded) return guarded
  const index = state.rules.findIndex(item => item.id === ruleId)
  if (index < 0) return rejected('ADAPTATION_RULE_NOT_FOUND', '改编规则不存在', '请刷新后重试。')
  state.rules.splice(index, 1)
  return { ok: true, ruleId }
}

export async function confirmAdaptationPlan(state, creationSettings, persistPlan) {
  if (!state.selectedHookId) return rejected('HIGH_PRESSURE_HOOK_REQUIRED', '请选择高压开场', '必须先选择并保存一个高压开场。', 'focus_high_pressure_hooks')
  if (!(Number(state.hookVersion) > 0)) return rejected('HOOK_PERSISTENCE_REQUIRED', '高压开场尚未保存', '请重新选择当前开场并完成保存，再确认改编方案。', 'retry_hook_save')
  const settingValidation = validateCreationSettings(creationSettings)
  if (!settingValidation.allowed) return settingValidation
  if (state.rules.some(rule => !text(rule.title) || !text(rule.instruction))) return rejected('ADAPTATION_RULE_REQUIRED', '请补全改编规则', '存在未完成的改编规则。', 'focus_adaptation_rules')
  if (typeof persistPlan !== 'function') return rejected('ADAPTATION_PERSISTENCE_FAILED', '改编方案未确认', '保存服务不可用。', 'retry_adaptation_confirmation')
  try {
    const response = await persistPlan({ selectedHookId: state.selectedHookId, hookVersion: state.hookVersion, rules: clone(state.rules), creationSettings: clone(creationSettings) })
    if (response?.persisted !== true || !(Number(response.version) > state.version)) {
      return rejected('ADAPTATION_PERSISTENCE_FAILED', '改编方案未确认', response?.message || '服务端未返回新版本。', 'retry_adaptation_confirmation')
    }
    state.confirmed = true
    state.version = Number(response.version)
    state.impact = clone(response.impact ?? null)
    return { ok: true, version: state.version, impact: clone(state.impact) }
  } catch (error) {
    return rejected('ADAPTATION_PERSISTENCE_FAILED', '改编方案未确认', error?.message || '请稍后重试。', 'retry_adaptation_confirmation')
  }
}

/** Bridges adaptation regeneration to Task 5's authoritative task/result state machine. */
export async function runArtifactRegeneration({ workbench, input, execute }) {
  if (!workbench || typeof workbench.beginGeneration !== 'function') return rejected('GENERATION_ADAPTER_REQUIRED', '生成服务不可用', '请恢复生成服务后重试。', 'retry_generation')
  const task = workbench.beginGeneration(input)
  if (!task?.id) return task
  workbench.updateGenerationProgress(task.id, { percentage: 10, subtask: input?.subtask || '正在准备改编方案' })
  try {
    if (typeof execute !== 'function') throw new Error('改编方案生成适配器不可用')
    const outcome = await execute(task)
    workbench.updateGenerationProgress(task.id, { percentage: 90, subtask: '正在生成差异与影响范围' })
    return workbench.finishGeneration(task.id, { status: 'completed', ...outcome })
  } catch (error) {
    return workbench.finishGeneration(task.id, {
      status: error?.status === 'cancelled' ? 'cancelled' : 'failed',
      error: error?.message || '生成失败',
      errorCode: error?.code,
      targetAction: error?.targetAction
    })
  }
}
