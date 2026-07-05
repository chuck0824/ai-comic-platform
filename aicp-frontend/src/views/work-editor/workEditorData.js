/**
 * 作品编辑中心 — 纯数据工具函数。
 * 无 Vue 响应式依赖，可独立进行 Node 单元测试。
 */

// ---- 1/3/3/1 标签限制 ----
export const TAG_LIMITS = {
  genre: 1,
  plot: 3,
  tone: 3,
  setting: 1
}

/** 标签合法值校验（基于给定字典） */
export function validateTagSelection(selection, dictionary) {
  const errors = []
  const { genre, plot, tone, setting } = selection

  // 题材：最多 1
  if (genre && genre.length > TAG_LIMITS.genre) {
    errors.push(`题材最多选 ${TAG_LIMITS.genre} 个`)
  }
  if (genre && dictionary?.genres) {
    const invalid = genre.filter(g => !dictionary.genres.some(d => d.value === g))
    if (invalid.length) errors.push(`无效题材: ${invalid.join(', ')}`)
  }

  // 情节：最多 3，去重
  const uniquePlot = [...new Set(plot || [])]
  if (uniquePlot.length > TAG_LIMITS.plot) {
    errors.push(`情节最多选 ${TAG_LIMITS.plot} 个`)
  }
  if (dictionary?.plots) {
    const invalid = uniquePlot.filter(p => !dictionary.plots.some(d => d.value === p))
    if (invalid.length) errors.push(`无效情节: ${invalid.join(', ')}`)
  }

  // 情绪：最多 3
  const uniqueTone = [...new Set(tone || [])]
  if (uniqueTone.length > TAG_LIMITS.tone) {
    errors.push(`情绪最多选 ${TAG_LIMITS.tone} 个`)
  }
  if (dictionary?.tones) {
    const invalid = uniqueTone.filter(t => !dictionary.tones.some(d => d.value === t))
    if (invalid.length) errors.push(`无效情绪: ${invalid.join(', ')}`)
  }

  // 时空：最多 1
  if (setting && setting.length > TAG_LIMITS.setting) {
    errors.push(`时空最多选 ${TAG_LIMITS.setting} 个`)
  }
  if (setting && dictionary?.settings) {
    const invalid = setting.filter(s => !dictionary.settings.some(d => d.value === s))
    if (invalid.length) errors.push(`无效时空: ${invalid.join(', ')}`)
  }

  return { valid: errors.length === 0, errors }
}

// ---- 旧编辑器响应标准化 ----
export function normalizeEditorResponse(data) {
  return {
    projectId: data.project_id ?? data.projectId,
    title: data.title ?? '',
    totalWords: data.total_words ?? data.totalWords ?? 0,
    permissions: data.permissions ?? 'viewer',
    profile: data.profile ? {
      genreTag: data.profile.genre_tag ?? data.profile.genreTag ?? '',
      plotTags: data.profile.plot_tags ?? data.profile.plotTags ?? [],
      toneTags: data.profile.tone_tags ?? data.profile.toneTags ?? [],
      settingTag: data.profile.setting_tag ?? data.profile.settingTag ?? '',
      synopsis: data.profile.synopsis ?? '',
      outline: data.profile.outline ?? '',
      revision: data.profile.revision ?? 0
    } : null,
    revision: data.revision ?? 0,
    settingCounts: data.setting_counts ?? data.settingCounts ?? {},
    pendingExtractionCount: data.pending_extraction_count ?? data.pendingExtractionCount ?? 0,
    bibleHealth: data.bible_health ?? data.bibleHealth ?? null
  }
}

// ---- 标签更新载荷 ----
export function makeTagPayload(profile, newTags) {
  return {
    genre: newTags.genre ?? profile?.genreTag ?? null,
    plot: newTags.plot ?? profile?.plotTags ?? [],
    tone: newTags.tone ?? profile?.toneTags ?? [],
    setting: newTags.setting ?? profile?.settingTag ?? null,
    revision: profile?.revision ?? 0
  }
}

// ---- 设定类型定义 ----
export const SETTING_TYPES = ['character', 'background', 'faction', 'location', 'item']

export const SETTING_TYPE_LABELS = {
  character: '角色',
  background: '背景',
  faction: '势力',
  location: '地点',
  item: '物品'
}

// ---- 提取决策载荷 ----
export function makeDecisionPayload(candidates) {
  return {
    decisions: candidates.map(c => ({
      candidate_id: c.id ?? c.candidate_id,
      field_decisions: c.field_decisions ?? c.fieldDecisions ?? {},
      review_status: c.review_status ?? c.reviewStatus ?? 'accepted'
    }))
  }
}

// ---- Tag Dictionary 缓存 ----
let cachedDictionary = null
let cachedVersion = null

export function getCachedDictionary() {
  return cachedDictionary
}

export function setCachedDictionary(dict) {
  cachedDictionary = dict
  cachedVersion = dict?.version
}

export function isDictionaryStale(newVersion) {
  return cachedVersion != null && newVersion != null && cachedVersion !== newVersion
}

// ---- 序列化保存队列 ----
export function createSaveQueue() {
  let queue = Promise.resolve()
  return {
    enqueue(fn) {
      queue = queue.then(fn, fn)
      return queue
    }
  }
}
