import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const read = relative => readFileSync(new URL(relative, `file://${root}/`), 'utf8')

test('creation entries resolve to the native project workbench', async () => {
  const { workspaceTarget } = await import('../src/views/content-project/workbench/workspaceRouting.js')
  assert.equal(workspaceTarget({ id: 21, entryMode: 'quick' }), '/script-gen/21/workspace?stage=creation_settings')
  assert.equal(workspaceTarget({ id: 22, entryMode: 'professional' }), '/script-gen/22/workspace?stage=creation_settings')
  assert.equal(workspaceTarget({ id: 23, entryMode: 'upload' }), '/script-gen/23/workspace?stage=creation_settings&next=novel_upload')
  assert.equal(workspaceTarget({ id: 24, entryMode: 'tvc' }), '/script-gen/24/workspace?stage=creation_settings&variant=tvc')
  assert.equal(workspaceTarget({ id: 25, stage: 'content' }), '/script-gen/25/workspace?stage=script_body')
  assert.equal(workspaceTarget({ id: 26, stage: 'review' }), '/script-gen/26/workspace?stage=review_revision')
  assert.equal(workspaceTarget({ id: 27, stage: 'import_review' }), '/script-gen/27/workspace?stage=novel_upload')
})

test('persisted and query stages are validated without allowing skips', async () => {
  const { resolveWorkspaceStage, shouldAdvanceResume } = await import('../src/views/content-project/workbench/workspaceRouting.js')
  assert.equal(resolveWorkspaceStage({ persistedStage: 'script_body' }), 'script_body')
  assert.equal(resolveWorkspaceStage({ persistedStage: 'script_body', queryStage: 'novel_analysis' }), 'novel_analysis')
  assert.equal(resolveWorkspaceStage({ persistedStage: 'script_body', queryStage: 'text_storyboard' }), 'script_body')
  assert.equal(resolveWorkspaceStage({ persistedStage: 'legacy_destination', queryStage: 'hacked' }), 'creation_settings')
  const legacy = {
    story_seed: 'creation_settings', import_review: 'novel_upload', characters: 'novel_analysis', synopsis: 'novel_analysis',
    outline: 'structured_script', content: 'script_body', review: 'review_revision',
    destination: 'text_storyboard', storyboard: 'text_storyboard'
  }
  for (const [persistedStage, expected] of Object.entries(legacy)) {
    assert.equal(resolveWorkspaceStage({ persistedStage }), expected)
  }
  assert.equal(resolveWorkspaceStage({ persistedStage: 'content', queryStage: 'creation_settings' }), 'script_body')
  assert.equal(resolveWorkspaceStage({ persistedStage: 'review', queryStage: 'creation_settings' }), 'review_revision')
  assert.equal(resolveWorkspaceStage({ persistedStage: 'import_review', queryStage: 'creation_settings' }), 'novel_upload')
  assert.equal(shouldAdvanceResume('content', 'novel_analysis'), false)
  assert.equal(shouldAdvanceResume('content', 'review_revision'), true)
  assert.equal(shouldAdvanceResume('review', 'script_body'), false)
  assert.equal(shouldAdvanceResume('import_review', 'novel_analysis'), true)
})

test('batch generation response chooses the first real job and preserves failure detail', async () => {
  const { normalizeBatchGeneration } = await import('../src/views/content-project/workbench/workspaceAdapters.js')
  assert.deepEqual(normalizeBatchGeneration({ total: 2, jobs: [{ id: 71, status: 'queued' }, { id: 72 }] }), {
    ok: true, total: 2, job: { id: 71, status: 'queued' }, jobs: [{ id: 71, status: 'queued' }, { id: 72 }]
  })
  assert.deepEqual(normalizeBatchGeneration({ total: 1, jobs: [] }), {
    ok: false, code: 'GENERATION_JOB_MISSING', message: '生成服务未返回可跟踪任务（声明 1 个，实际 0 个）。'
  })
})

test('project load generations reject stale responses and reset missing project fields', async () => {
  const { createProjectLoadGuard, resetProjectWorkspaceData } = await import('../src/views/content-project/workbench/workspaceLoadState.js')
  const guard = createProjectLoadGuard()
  const first = guard.begin(1); const second = guard.begin(2)
  assert.equal(guard.accept(first, 1), false)
  assert.equal(guard.accept(second, 2), true)
  const state = { creationSettings: { genre: '旧题材', tone: '旧基调' }, novelAnalysis: { synopsis: '旧梗概' }, adaptation: { hooks: [{ id: 1 }] } }
  resetProjectWorkspaceData(state)
  assert.deepEqual(state.creationSettings, {})
  assert.deepEqual(state.novelAnalysis, {})
  assert.deepEqual(state.adaptation, { hooks: [] })
})

test('workspace adapters persist stages and preserve stable script scene identities', async () => {
  const { createWorkspaceAdapters } = await import('../src/views/content-project/workbench/workspaceAdapters.js')
  const calls = []
  const adapters = createWorkspaceAdapters({
    projectId: () => 41,
    project: () => ({ revision: 7 }),
    api: {
      saveResume: async (...args) => { calls.push(['resume', ...args]); return { data: { revision: 8 } } },
      addParameters: async (...args) => { calls.push(['parameters', ...args]); return { data: { id: 9 } } },
      saveDraft: async (...args) => { calls.push(['draft', ...args]); return { data: { revision: 9 } } }
    },
    sceneApi: {
      apply: async (...args) => { calls.push(['apply', ...args]); return { applicationId: 12 } }
    },
    activeUnitId: () => 51
  })

  assert.deepEqual(await adapters.persistStage('novel_upload'), { persisted: true })
  assert.equal(calls[0][0], 'resume')
  assert.equal(calls[0][2].stage_key, 'novel_upload')
  assert.deepEqual(await adapters.persistSettings({ genre: '悬疑' }), { persisted: true, versionId: 9 })
  assert.deepEqual(await adapters.bindScriptScene({ sceneAssetId: 5 }, { sceneId: 'EP-007-SCENE-001' }), { persisted: true, applicationId: 12 })
  assert.equal(calls.at(-1)[3].targetType, 'SCRIPT_SCENE')
  assert.equal(calls.at(-1)[3].targetId, null)
  assert.equal(calls.at(-1)[3].targetKey, 'EP-007-SCENE-001')
  assert.match(calls.at(-1)[3].idempotencyKey, /script-scene:EP-007-SCENE-001:asset:5/)
})

test('workspace mounts the authoritative shell and removes legacy stage conditionals', () => {
  const source = read('../src/views/content-project/ContentProjectWorkspace.vue')
  const apiSource = read('../src/api/contentProject.js')
  assert.doesNotMatch(source, /currentStageInfo\.key === 'story_seed'/)
  assert.doesNotMatch(source, /currentStageInfo\.key === 'destination'/)
  assert.match(source, /SceneAssetLibrary/)
  assert.match(source, /STAGES/)
  for (const name of ['CreationSettingsStage', 'NovelUploadStage', 'NovelAnalysisStage', 'AdaptationStage', 'StructuredScriptStage', 'ScriptBodyStage', 'ReviewRevisionStage', 'TextStoryboardStage']) {
    assert.match(source, new RegExp(name))
  }
  assert.match(source, /adapters\.persistStage/)
  assert.match(source, /adapters\.persistSettings/)
  assert.match(source, /createWorkspaceAdapters/)
  assert.match(source, /bindScriptScene/)
  assert.match(source, /loadUnitDrafts/)
  assert.match(source, /getDraft/)
  assert.match(source, /submitStageGeneration/)
  assert.match(source, /trackGenerationJob/)
  assert.match(source, /getGenerationJob/)
  assert.match(source, /cancelGenerationJob/)
  assert.match(source, /persistGenerationDecision/)
  assert.match(source, /await runGuardedGenerationDecision/)
  assert.match(source, /onFailure: failure => showGuidance/)
  assert.match(source, /loadUnitWorkspaceContent/)
  assert.match(apiSource, /acceptGenerationJob/)
  assert.match(apiSource, /discardGenerationJob/)
  assert.match(source, /shouldAdvanceResume/)
  assert.match(source, /GenerationProgressDialog/)
  assert.match(source, /ActionResultDrawer/)
  assert.match(source, /ActionGuidanceDialog/)
  assert.match(source, /Object\.assign\(workbench\.state, createWorkbenchState\(\)\)/)
  assert.doesNotMatch(source, /alert\s*\(/)
  assert.doesNotMatch(source, /console\.log\s*\(/)
})

test('launchpad stays at script-gen and recent projects resume authoritative stage', () => {
  const router = read('../src/router/index.js')
  const home = read('../src/views/content-project/ScriptCreationHome.vue')
  const create = read('../src/views/content-project/ContentProjectCreate.vue')
  assert.match(router, /path: 'script-gen'/)
  assert.match(router, /ScriptCreationHome\.vue/)
  assert.match(home, /CREATION_METHODS/)
  assert.match(home, /last_stage_key/)
  assert.match(home, /workspaceTarget/)
  assert.match(create, /addParameters/)
  assert.match(create, /workspaceTarget/)
  assert.match(create, /route\.query\.mode/)
  assert.match(create, /tvc/)
})
