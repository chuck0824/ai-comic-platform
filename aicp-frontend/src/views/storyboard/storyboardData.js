/**
 * Storyboard data helpers — pure functions for revision queue, normalization,
 * optimistic patches, shot-code sorting, scene grouping, and conflict diffs.
 */

// ---- Revision Save Queue ----

export function createRevisionSaveQueue(initialRevision) {
  let revision = initialRevision
  let tail = Promise.resolve()
  return {
    enqueue(save) {
      const run = tail.then(() => save(revision)).then(result => {
        revision = result.revision
        return result
      })
      tail = run.catch(() => undefined)
      return run
    },
    getRevision: () => revision,
    reset: (r) => { revision = r; tail = Promise.resolve() }
  }
}

// ---- Field Presets ----

export const SHOT_SIZE_OPTIONS = [
  '远景', '全景', '中景', '近景', '特写', '大特写', '中近景', '中全景'
]

export const SHOT_STATUS_OPTIONS = [
  { value: 'draft', label: '草稿' },
  { value: 'confirmed', label: '已确认' },
  { value: 'needs_review', label: '待检查' }
]

export const EMOTION_OPTIONS = [
  '平静', '紧张', '愤怒', '悲伤', '喜悦', '恐惧', '惊讶', '厌恶', '期待', '焦虑'
]

export const FIELD_PRESETS = {
  'default': ['shotCode', 'durationMs', 'shotSize', 'visualDescriptionSummary', 'dialogueText', 'status'],
  'director': ['shotCode', 'durationMs', 'shotSize', 'visualDescriptionSummary', 'characterAction', 'emotionDescription', 'status'],
  'production': ['shotCode', 'durationMs', 'imagePrompt', 'videoMotionPrompt', 'failureStrategy', 'status'],
  'prompts': ['shotCode', 'imagePrompt', 'videoMotionPrompt', 'status'],
}

// ---- Normalization ----

export function normalizeShot(shot) {
  return {
    ...shot,
    sceneTags: Array.isArray(shot.sceneTags) ? shot.sceneTags
      : (shot.sceneTagsJson ? parseJson(shot.sceneTagsJson) : []),
    visualDescriptionSummary: summarize(shot.visualDescription, 200),
    locked: false
  }
}

export function applyOptimisticPatch(shot, patch) {
  const updated = { ...shot }
  for (const [key, value] of Object.entries(patch)) {
    if (value !== undefined && key !== 'revision') {
      updated[key] = value
    }
  }
  return updated
}

// ---- Sorting & Grouping ----

export function sortByShotCode(a, b) {
  return (a.shotCode || '').localeCompare(b.shotCode || '')
}

export function groupShotsByScene(shots, scenes) {
  const sceneMap = new Map()
  for (const scene of scenes) {
    sceneMap.set(scene.id, { ...scene, shots: [] })
  }
  for (const shot of shots) {
    const group = sceneMap.get(shot.sceneId)
    if (group) {
      group.shots.push(shot)
    }
  }
  return Array.from(sceneMap.values())
}

export function totalDuration(shots) {
  return shots.reduce((sum, s) => sum + (s.durationMs || 0), 0)
}

// ---- Conflict Diff ----

export function buildConflictDiff(localShot, serverShot) {
  const diffs = []
  const fields = ['visualDescription', 'dialogueText', 'shotSize', 'durationMs',
    'lightingAtmosphere', 'characterAction', 'emotionDescription', 'imagePrompt',
    'videoMotionPrompt', 'soundEffect', 'referenceText', 'status']
  for (const field of fields) {
    if (localShot[field] !== serverShot[field]) {
      diffs.push({ field, local: localShot[field], server: serverShot[field] })
    }
  }
  return diffs
}

// ---- Helpers ----

function summarize(text, maxLen) {
  if (!text) return ''
  return text.length <= maxLen ? text : text.substring(0, maxLen) + '...'
}

function parseJson(str) {
  try { return JSON.parse(str) } catch { return [] }
}

export function formatDuration(ms) {
  if (!ms || ms <= 0) return '0.0s'
  return (ms / 1000).toFixed(1) + 's'
}

export function generateIdempotencyKey() {
  return `${Date.now()}-${Math.random().toString(36).substring(2, 10)}`
}

export const SAVE_STATES = {
  IDLE: 'idle',
  WAITING: 'waiting',
  SAVING: 'saving',
  SAVED: 'saved',
  FAILED: 'failed',
  CONFLICT: 'conflict'
}
