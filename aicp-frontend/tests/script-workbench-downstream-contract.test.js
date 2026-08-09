import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  addBeat,
  addScriptScene,
  addStoryboardShot,
  applyBlockAiAction,
  approveReviewEpisode,
  archiveStoryboard,
  bindScriptSceneAsset,
  buildCanvasCreationPayload,
  buildShotAssetBinding,
  changeSceneSpace,
  createReviewState,
  createScriptBodyState,
  createStoryboardState,
  createStructuredScriptState,
  createStoryboardShot,
  createCanvasProject,
  filterReviewIssues,
  groupContinuityIssues,
  mergeStoryboardShots,
  runContinuityCheck,
  saveLocalRevision,
  splitStoryboardShot
} from '../src/views/content-project/workbench/downstreamStageModel.js'
import { toContentProjectPayload } from '../src/views/content-project/workbench/downstreamApiPayload.js'

const here = path.dirname(fileURLToPath(import.meta.url))
const contentProject = path.resolve(here, '../src/views/content-project')

test('downstream stages expose every approved business action', () => {
  const files = [
    'stages/StructuredScriptStage.vue',
    'stages/ScriptBodyStage.vue',
    'stages/ReviewRevisionStage.vue',
    'stages/TextStoryboardStage.vue'
  ]
  const source = files.map(file => fs.readFileSync(path.join(contentProject, file), 'utf8')).join('\n')
  for (const action of [
    'open-episode-structure', 'add-beat', 'regenerate-beat', 'continue-selected-block',
    'strengthen-conflict', 'condense-dialogue', 'rewrite-tone', 'check-character-consistency',
    'add-scene', 'add-script-block', 'run-script-check', 'export-script',
    'filter-review-issues', 'save-local-revision', 'compare-revision', 'approve-episode',
    'add-shot', 'split-shot', 'merge-shot', 'toggle-shot-view', 'run-continuity-check',
    'complete-and-archive', 'configure-mindmap', 'create-canvas-project', 'regenerate-current-artifact'
  ]) assert.match(source, new RegExp(`data-action=["']${action}["']`), action)
})

test('stable IDs survive structured beat and script scene creation', async () => {
  const structured = createStructuredScriptState({ episodes: [{ id: 'EP-007', title: '第七集', beats: [] }] })
  const beat = await addBeat(structured, 'EP-007', { title: '门被推开' }, async payload => ({ persisted: true, beat: payload }))
  assert.equal(beat.id, 'EP-007-BEAT-001')
  assert.equal(structured.episodes[0].beats[0].id, 'EP-007-BEAT-001')

  const body = createScriptBodyState({ episodes: [{ id: 'EP-007', scenes: [] }] })
  const scene = addScriptScene(body, 'EP-007', { heading: 'INT. 仓库 - 夜' })
  assert.equal(scene.id, 'EP-007-SCENE-001')
  assert.equal(scene.bindingState, 'UNBOUND')
  assert.equal(body.sceneAssetPicker.open, true)
  assert.equal(body.preStoryboardWarnings[0].sceneId, scene.id)
})

test('AI body actions stay clickable and return guidance until a block is selected', async () => {
  const state = createScriptBodyState({ episodes: [{ id: 'EP-001', scenes: [] }] })
  let calls = 0
  const blocked = await applyBlockAiAction(state, 'strengthen-conflict', async () => { calls += 1 })
  assert.equal(blocked.code, 'SCRIPT_BLOCK_REQUIRED')
  assert.equal(calls, 0)
})

test('script scene binding pins exact versions and rejects archived or degraded assets', async () => {
  const state = createScriptBodyState({ episodes: [{ id: 'EP-001', scenes: [{ id: 'SC-1', blocks: [] }] }] })
  const archived = await bindScriptSceneAsset(state, 'SC-1', {
    id: 7, currentVersionId: 12, status: 'ARCHIVED'
  }, { id: 'VAR-001', version: 2 }, async () => ({ persisted: true }))
  assert.equal(archived.code, 'SCENE_ASSET_UNAVAILABLE')

  const degraded = await bindScriptSceneAsset(state, 'SC-1', {
    id: 7, currentVersionId: 12, status: 'ACTIVE'
  }, { id: 'VAR-001', version: 2 }, async () => ({ persisted: true }), { degraded: true })
  assert.equal(degraded.code, 'DEGRADED_READ_ONLY')

  let sent
  const bound = await bindScriptSceneAsset(state, 'SC-1', {
    id: 7, currentVersionId: 12, status: 'ACTIVE'
  }, { id: 'VAR-001', version: 2 }, async payload => {
    sent = payload
    return { persisted: true, bindingVersion: 4 }
  })
  assert.deepEqual(sent, {
    sceneAssetId: 7, sceneAssetVersionId: 12,
    sceneVariantId: 'VAR-001', sceneVariantVersion: 2, sceneOverride: {}
  })
  assert.equal(bound.bindingVersion, 4)
  assert.equal(state.preStoryboardWarnings.length, 0)
})

test('space changes require explicit local or master scope', async () => {
  const state = createScriptBodyState({ episodes: [{ id: 'EP-1', scenes: [{ id: 'SC-1', space: '仓库', assetBinding: { sceneAssetId: 7 } }] }] })
  let calls = 0
  assert.equal((await changeSceneSpace(state, 'SC-1', '码头', null, async () => { calls += 1 })).code, 'SPACE_CHANGE_SCOPE_REQUIRED')
  assert.equal(calls, 0)
  await changeSceneSpace(state, 'SC-1', '码头', 'CURRENT_SCENE', async payload => ({ persisted: true, ...payload }))
  assert.equal(state.episodes[0].scenes[0].space, '码头')
  assert.equal(state.episodes[0].scenes[0].assetBinding.sceneOverride.space, '码头')
})

test('review filtering and revisions are functional and approval blocks open high issues', async () => {
  const state = createReviewState({ issues: [
    { id: 'I-1', severity: 'HIGH', status: 'OPEN', sceneId: 'SC-1' },
    { id: 'I-2', severity: 'LOW', status: 'OPEN', sceneId: 'SC-2' }
  ] })
  assert.deepEqual(filterReviewIssues(state, { severity: 'HIGH' }).map(issue => issue.id), ['I-1'])
  const blocked = await approveReviewEpisode(state, 'EP-1', async () => ({ persisted: true }))
  assert.equal(blocked.code, 'REVIEW_BLOCKERS_REMAIN')
  assert.deepEqual(state.filters.severities, ['BLOCKER', 'HIGH'])
  assert.equal(state.focusIssueList, true)
  const revision = await saveLocalRevision(state, 'I-1', { after: '已修订' }, async payload => ({ persisted: true, version: 2, revision: payload }))
  assert.equal(revision.version, 2)
  assert.equal(state.issues[0].status, 'RESOLVED')
})

test('storyboard payload pins exact scene asset versions', () => {
  assert.deepEqual(buildShotAssetBinding({ assetId: 7, versionId: 12, variantId: 'VAR-001', variantVersion: 2 }), {
    scene_asset_id: 7, scene_asset_version_id: 12,
    scene_variant_id: 'VAR-001', scene_variant_version: 2, scene_override: {}
  })
})

test('split and merge never silently lose scene asset bindings', async () => {
  const binding = { sceneAssetId: 7, sceneAssetVersionId: 12, sceneVariantId: 'VAR-001', sceneVariantVersion: 2, sceneOverride: {} }
  const state = createStoryboardState({ shots: [
    createStoryboardShot({ id: 'SHOT-1', sceneId: 'SC-1', assetBinding: binding }),
    createStoryboardShot({ id: 'SHOT-2', sceneId: 'SC-1', assetBinding: binding })
  ] })
  const failedSplit = await splitStoryboardShot(state, 'SHOT-1', async () => ({ persisted: true, shots: [
    { id: 'SHOT-1A', sceneId: 'SC-1', assetBinding: binding },
    { id: 'SHOT-1B', sceneId: 'SC-1', assetBinding: null }
  ] }))
  assert.equal(failedSplit.code, 'SHOT_BINDING_CONFLICT')
  assert.deepEqual(state.shots.map(shot => shot.id), ['SHOT-1', 'SHOT-2'])

  state.shots[1].assetBinding = { ...binding, sceneVariantVersion: 3 }
  const failedMerge = await mergeStoryboardShots(state, ['SHOT-1', 'SHOT-2'], async () => ({ persisted: true }))
  assert.equal(failedMerge.code, 'SHOT_BINDING_CONFLICT')
  assert.equal(failedMerge.targetAction, 'resolve_shot_binding_conflict')
})

test('continuity groups typed issues and archive requires a pass plus locked snapshots', async () => {
  const state = createStoryboardState({ shots: [createStoryboardShot({ id: 'SHOT-1', sceneId: 'SC-1' })] })
  const issues = [
    { code: 'STALE_ASSET', sceneId: 'SC-1' },
    { code: 'VARIANT_MISMATCH', sceneId: 'SC-1' },
    { code: 'FIXED_PROP_CONFLICT', sceneId: 'SC-1' },
    { code: 'CHARACTER_STATE_CONFLICT', sceneId: 'SC-2' },
    { code: 'AXIS_CONFLICT', sceneId: 'SC-2' }
  ]
  const checked = await runContinuityCheck(state, async () => ({ persisted: true, passed: false, issues }))
  assert.deepEqual(Object.keys(checked.groups), ['scene', 'variant', 'fixedProp', 'characterState', 'axis'])
  assert.equal(groupContinuityIssues(issues).scene.length, 1)
  let archiveCalls = 0
  const blocked = await archiveStoryboard(state, async () => { archiveCalls += 1 })
  assert.equal(blocked.code, 'CONTINUITY_PASS_REQUIRED')
  assert.equal(archiveCalls, 0)
})

test('canvas creation sends locked versions and immutable scene snapshots only', async () => {
  const lockedShot = createStoryboardShot({
    id: 'SHOT-1', sceneId: 'SC-1', snapshotLocked: true,
    sceneAssetSnapshotRef: { shotId: 'SHOT-1', fingerprint: 'sha256:abc', sceneAssetId: 7, sceneAssetVersionId: 12 }
  })
  const state = createStoryboardState({ contentVersionId: 31, contentVersionLocked: true, storyboardVersionId: 41, storyboardVersionLocked: true, continuity: { status: 'PASSED', passed: true }, shots: [lockedShot] })
  const payload = buildCanvasCreationPayload(state, { name: '第一集画布', purpose: 'production' })
  assert.deepEqual(payload, {
    name: '第一集画布', purpose: 'production', content_version_id: 31, storyboard_version_id: 41,
    scene_snapshot_references: [{ shot_id: 'SHOT-1', fingerprint: 'sha256:abc', scene_asset_id: 7, scene_asset_version_id: 12 }]
  })
  let sent
  const created = await createCanvasProject(state, { name: '第一集画布', purpose: 'production' }, async value => { sent = value; return { persisted: true, canvasProjectId: 'CANVAS-1' } })
  assert.equal(created.canvasProjectId, 'CANVAS-1')
  assert.deepEqual(sent, payload)

  const unlockedVersions = createStoryboardState({ contentVersionId: 31, storyboardVersionId: 41, shots: [lockedShot] })
  assert.equal((await createCanvasProject(unlockedVersions, {}, async () => ({ persisted: true }))).code, 'LOCKED_VERSIONS_REQUIRED')
  const unlocked = createStoryboardState({ contentVersionId: 31, contentVersionLocked: true, storyboardVersionId: 41, storyboardVersionLocked: true, continuity: { status: 'PASSED', passed: true }, shots: [{ ...lockedShot, snapshotLocked: false }] })
  assert.equal((await createCanvasProject(unlocked, {}, async () => ({ persisted: true }))).code, 'LOCKED_SNAPSHOTS_REQUIRED')
})

test('content project API boundary recursively emits backend snake_case payloads', () => {
  assert.deepEqual(toContentProjectPayload({
    sceneAssetId: 7,
    sceneOverride: { fixedProps: ['旧钟'] },
    shotIds: ['SHOT-1']
  }), {
    scene_asset_id: 7,
    scene_override: { fixed_props: ['旧钟'] },
    shot_ids: ['SHOT-1']
  })
})

test('adding storyboard shots executes persistence and keeps exact binding', async () => {
  const binding = { sceneAssetId: 7, sceneAssetVersionId: 12, sceneVariantId: 'VAR-1', sceneVariantVersion: 1, sceneOverride: {} }
  const state = createStoryboardState()
  const result = await addStoryboardShot(state, { sceneId: 'SC-1', assetBinding: binding }, async payload => ({ persisted: true, shot: payload }))
  assert.equal(result.id, 'SHOT-001')
  assert.deepEqual(result.assetBinding, binding)
})

test('adding a storyboard shot requires an existing stable scene ID and never persists a placeholder', async () => {
  const state = createStoryboardState()
  let calls = 0
  const blocked = await addStoryboardShot(state, { description: '新镜头' }, async () => { calls += 1 })
  assert.equal(blocked.code, 'STORYBOARD_SCENE_REQUIRED')
  assert.equal(calls, 0)
  const source = fs.readFileSync(path.join(contentProject, 'stages/TextStoryboardStage.vue'), 'utf8')
  assert.doesNotMatch(source, /SCENE-PENDING/)
})

test('scene asset binding accepts a master-only version and requires optional variant fields as a pair', async () => {
  const makeState = () => createScriptBodyState({ episodes: [{ id: 'EP-1', scenes: [{ id: 'SC-1', blocks: [] }] }] })
  let sent
  const masterOnly = await bindScriptSceneAsset(makeState(), 'SC-1', { id: 7, currentVersionId: 12, status: 'ACTIVE' }, null, async payload => {
    sent = payload
    return { persisted: true }
  })
  assert.equal(masterOnly.bindingState, 'BOUND')
  assert.deepEqual(sent, { sceneAssetId: 7, sceneAssetVersionId: 12, sceneVariantId: null, sceneVariantVersion: null, sceneOverride: {} })
  assert.equal((await bindScriptSceneAsset(makeState(), 'SC-1', { id: 7, currentVersionId: 12 }, { id: 'VAR-1' }, async () => ({ persisted: true }))).code, 'SCENE_ASSET_VARIANT_PAIR_REQUIRED')
  assert.equal((await bindScriptSceneAsset(makeState(), 'SC-1', { id: 7, currentVersionId: 12 }, { version: 2 }, async () => ({ persisted: true }))).code, 'SCENE_ASSET_VARIANT_PAIR_REQUIRED')
})

test('master-space updates atomically repin the current scene to the returned asset version', async () => {
  const state = createScriptBodyState({ episodes: [{ id: 'EP-1', scenes: [{ id: 'SC-1', space: '仓库', blocks: [], assetBinding: { sceneAssetId: 7, sceneAssetVersionId: 12, sceneVariantId: null, sceneVariantVersion: null, sceneOverride: {} } }] }] })
  const result = await changeSceneSpace(state, 'SC-1', '码头', 'MASTER_ASSET', async () => ({ persisted: true, sceneAssetVersionId: 13, sceneAssetVersionNo: 4 }))
  assert.equal(result.assetBinding.sceneAssetVersionId, 13)
  assert.equal(result.assetBinding.sceneAssetVersionNo, 4)
  assert.equal(result.space, '码头')
  const rejectedState = createScriptBodyState({ episodes: [{ id: 'EP-1', scenes: [{ id: 'SC-1', space: '仓库', blocks: [], assetBinding: { sceneAssetId: 7, sceneAssetVersionId: 12, sceneVariantId: null, sceneVariantVersion: null, sceneOverride: {} } }] }] })
  const rejected = await changeSceneSpace(rejectedState, 'SC-1', '码头', 'MASTER_ASSET', async () => ({ persisted: true }))
  assert.equal(rejected.code, 'SCENE_ASSET_VERSION_REQUIRED')
  assert.equal(rejectedState.episodes[0].scenes[0].space, '仓库')
  assert.equal(rejectedState.episodes[0].scenes[0].assetBinding.sceneAssetVersionId, 12)
})

test('blocked review approval clears stale single filters and shows every unresolved high risk issue', async () => {
  const state = createReviewState({
    filters: { severity: 'LOW', status: 'OPEN', severities: [], statuses: [] },
    issues: [
      { id: 'I-1', severity: 'HIGH', status: 'OPEN' },
      { id: 'I-2', severity: 'BLOCKER', status: 'IN_PROGRESS' },
      { id: 'I-3', severity: 'HIGH', status: 'RESOLVED' },
      { id: 'I-4', severity: 'BLOCKER', status: 'WAIVED' }
    ]
  })
  await approveReviewEpisode(state, 'EP-1', async () => ({ persisted: true }))
  assert.equal('severity' in state.filters, false)
  assert.equal('status' in state.filters, false)
  assert.deepEqual(filterReviewIssues(state).map(issue => issue.id), ['I-1', 'I-2'])
  assert.equal(state.focusIssueList, true)
})

test('blocked review approval resets scene and every hiding filter to show cross-scene blockers', async () => {
  const state = createReviewState({
    filters: { sceneId: 'SC-1', severity: 'LOW', status: 'OPEN', severities: ['LOW'], statuses: ['OPEN'], assignee: 'me' },
    issues: [
      { id: 'I-1', sceneId: 'SC-1', severity: 'LOW', status: 'OPEN' },
      { id: 'I-2', sceneId: 'SC-2', severity: 'HIGH', status: 'OPEN' },
      { id: 'I-3', sceneId: 'SC-3', severity: 'BLOCKER', status: 'IN_PROGRESS' },
      { id: 'I-4', sceneId: 'SC-4', severity: 'HIGH', status: 'RESOLVED' }
    ]
  })
  const result = await approveReviewEpisode(state, 'EP-1', async () => ({ persisted: true }))
  assert.equal(result.code, 'REVIEW_BLOCKERS_REMAIN')
  assert.deepEqual(state.filters, { severities: ['BLOCKER', 'HIGH'], statuses: [], excludedStatuses: ['RESOLVED', 'WAIVED'] })
  assert.deepEqual(filterReviewIssues(state).map(issue => issue.id), ['I-2', 'I-3'])
})

test('review severity control uses authoritative state filters and cannot restore stale local severity', () => {
  const source = fs.readFileSync(path.join(contentProject, 'stages/ReviewRevisionStage.vue'), 'utf8')
  assert.match(source, /v-model=["']state\.filters\.severities["']/)
  assert.doesNotMatch(source, /const severity\s*=\s*ref/)
})

test('split rejects empty duplicate or globally conflicting stable IDs without replacing the source', async () => {
  const original = createStoryboardShot({ id: 'SHOT-1', sceneId: 'SC-1' })
  const other = createStoryboardShot({ id: 'SHOT-9', sceneId: 'SC-1' })
  for (const replacements of [
    [{ id: '', sceneId: 'SC-1' }, { id: 'SHOT-2', sceneId: 'SC-1' }],
    [{ id: 'SHOT-2', sceneId: 'SC-1' }, { id: 'SHOT-2', sceneId: 'SC-1' }],
    [{ id: 'SHOT-9', sceneId: 'SC-1' }, { id: 'SHOT-2', sceneId: 'SC-1' }]
  ]) {
    const state = createStoryboardState({ shots: [original, other] })
    const result = await splitStoryboardShot(state, 'SHOT-1', async () => ({ persisted: true, shots: replacements }))
    assert.equal(result.code, 'SHOT_STABLE_ID_CONFLICT')
    assert.deepEqual(state.shots.map(shot => shot.id), ['SHOT-1', 'SHOT-9'])
  }
})

test('archive and canvas reject incomplete locked snapshot references and omit absent optional variants', async () => {
  const base = { id: 'SHOT-1', sceneId: 'SC-1', snapshotLocked: true }
  const project = { contentVersionId: 31, contentVersionLocked: true, storyboardVersionId: 41, storyboardVersionLocked: true, continuity: { status: 'PASSED', passed: true } }
  const invalidRefs = [
    { fingerprint: 'sha256:x', sceneAssetId: 7, sceneAssetVersionId: 12 },
    { shotId: 'SHOT-1', fingerprint: 'sha256:x', sceneAssetVersionId: 12 },
    { shotId: 'SHOT-1', fingerprint: 'sha256:x', sceneAssetId: 7 },
    { shotId: 'SHOT-1', fingerprint: 'sha256:x', sceneAssetId: '', sceneAssetVersionId: 12 },
    { shotId: 'SHOT-1', fingerprint: 'sha256:x', sceneAssetId: 7, sceneAssetVersionId: 0 },
    { shotId: 'SHOT-X', fingerprint: 'sha256:x', sceneAssetId: 7, sceneAssetVersionId: 12 },
    { shotId: 'SHOT-1', fingerprint: 'sha256:x', sceneAssetId: 7, sceneAssetVersionId: 12, sceneVariantId: 'VAR-1' },
    { shotId: 'SHOT-1', fingerprint: 'sha256:x', sceneAssetId: 7, sceneAssetVersionId: 12, sceneVariantVersion: 2 },
    { shotId: 'SHOT-1', fingerprint: 'sha256:x', sceneAssetId: 7, sceneAssetVersionId: 12, sceneVariantId: '', sceneVariantVersion: 0 }
  ]
  for (const sceneAssetSnapshotRef of invalidRefs) {
    const state = createStoryboardState({ ...project, shots: [{ ...base, sceneAssetSnapshotRef }] })
    assert.equal((await archiveStoryboard(state, async () => ({ persisted: true }))).code, 'LOCKED_SNAPSHOTS_REQUIRED')
    assert.equal((await createCanvasProject(state, {}, async () => ({ persisted: true }))).code, 'LOCKED_SNAPSHOTS_REQUIRED')
  }
  const valid = createStoryboardState({ ...project, shots: [{ ...base, sceneAssetSnapshotRef: { shotId: 'SHOT-1', fingerprint: 'sha256:x', sceneAssetId: 7, sceneAssetVersionId: 12 } }] })
  assert.deepEqual(buildCanvasCreationPayload(valid, { name: '画布', purpose: 'production' }).scene_snapshot_references, [{
    shot_id: 'SHOT-1', fingerprint: 'sha256:x', scene_asset_id: 7, scene_asset_version_id: 12
  }])
})
