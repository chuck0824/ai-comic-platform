import { STAGES } from './scriptWorkbenchModel.js'

const STAGE_KEYS = STAGES.map(stage => stage.key)
const LEGACY_STAGE_MAP = Object.freeze({
  story_seed: 'creation_settings',
  import_review: 'novel_upload',
  characters: 'novel_analysis',
  synopsis: 'novel_analysis',
  outline: 'structured_script',
  content: 'script_body',
  review: 'review_revision',
  destination: 'text_storyboard',
  storyboard: 'text_storyboard'
})

export function normalizePersistedStage(stage) {
  return STAGE_KEYS.includes(stage) ? stage : (LEGACY_STAGE_MAP[stage] ?? null)
}

export function workspaceTarget({ id, entryMode = 'quick', stage } = {}) {
  const validStage = normalizePersistedStage(stage) ?? STAGE_KEYS[0]
  const query = new URLSearchParams({ stage: validStage })
  if (entryMode === 'upload') query.set('next', 'novel_upload')
  if (entryMode === 'tvc') query.set('variant', 'tvc')
  return `/script-gen/${id}/workspace?${query}`
}

export function resolveWorkspaceStage({ persistedStage, queryStage } = {}) {
  const normalizedPersisted = normalizePersistedStage(persistedStage)
  const persistedIndex = STAGE_KEYS.indexOf(normalizedPersisted)
  const safePersistedIndex = persistedIndex >= 0 ? persistedIndex : 0
  const queryIndex = STAGE_KEYS.indexOf(queryStage)
  const persistedWasLegacy = Boolean(normalizedPersisted && !STAGE_KEYS.includes(persistedStage))
  if (persistedWasLegacy && queryStage === STAGE_KEYS[0]) return normalizedPersisted
  if (queryIndex >= 0 && queryIndex <= safePersistedIndex) return STAGE_KEYS[queryIndex]
  return STAGE_KEYS[safePersistedIndex]
}

export function restoreWorkbenchStage(state, activeStage, persistedStage = activeStage) {
  const index = STAGE_KEYS.indexOf(activeStage)
  const safeIndex = index >= 0 ? index : 0
  const persistedIndex = STAGE_KEYS.indexOf(normalizePersistedStage(persistedStage))
  const enteredIndex = Math.max(safeIndex, persistedIndex >= 0 ? persistedIndex : 0)
  state.activeStage = STAGE_KEYS[safeIndex]
  state.enteredStages = STAGE_KEYS.slice(0, enteredIndex + 1)
  state.completedStages = STAGE_KEYS.slice(0, enteredIndex)
  state.stages = STAGES.map((stage, stageIndex) => ({
    ...stage,
    status: stageIndex === safeIndex ? 'current' : (stageIndex < enteredIndex ? 'completed' : 'pending')
  }))
  return state
}

export function nextStageKey(stageKey) {
  const index = STAGE_KEYS.indexOf(stageKey)
  return index >= 0 && index < STAGE_KEYS.length - 1 ? STAGE_KEYS[index + 1] : null
}
