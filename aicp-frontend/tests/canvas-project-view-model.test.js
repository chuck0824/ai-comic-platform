import test from 'node:test'
import assert from 'node:assert/strict'
import {
  canvasRoute, buildQueryParams, validateCanvasDraft,
  buildIdempotencyKey, canvasActions, workspaceTab, buildBreadcrumb,
  STATUS_LABELS, PURPOSE_LABELS, SEVERITY_COLORS
} from '../src/views/canvas-project/canvasProjectViewModel.js'

test('editor route uses canvas UUID', () => {
  assert.equal(canvasRoute({ uuid: 'canvas_abc' }), '/canvas/canvas_abc')
})

test('canvas route returns center when no UUID', () => {
  assert.equal(canvasRoute(null), '/canvas-projects')
  assert.equal(canvasRoute({}), '/canvas-projects')
})

test('query params omit empty keyword', () => {
  const params = buildQueryParams({ keyword: '', status: 'editing' })
  assert.equal(params.keyword, undefined)
  assert.equal(params.status, 'editing')
})

test('query params omit default page size', () => {
  const params = buildQueryParams({ page: 1, pageSize: 20 })
  assert.equal(params.page, undefined)
  assert.equal(params.page_size, undefined)
})

test('canvas creation requires every ownership field', () => {
  const missing = validateCanvasDraft({})
  assert.deepEqual(missing, [
    'name', 'contentProjectId', 'productionUnitType',
    'productionUnitId', 'sourceContentVersionId',
    'sourceStoryboardVersionId', 'purpose'
  ])
})

test('valid draft returns no missing fields', () => {
  const draft = {
    name: 'Test', contentProjectId: 1, productionUnitType: 'episode',
    productionUnitId: 1, sourceContentVersionId: 1,
    sourceStoryboardVersionId: 1, purpose: 'official'
  }
  assert.deepEqual(validateCanvasDraft(draft), [])
})

test('idempotency key is deterministic', () => {
  const k1 = buildIdempotencyKey(7, 10, 80, 501, 900, 'official')
  const k2 = buildIdempotencyKey(7, 10, 80, 501, 900, 'official')
  assert.equal(k1, k2)
  assert.match(k1, /^canvas-create:/)
})

test('archived canvas disables edit, archive, copy, move, delete; enables restore', () => {
  const actions = canvasActions({ status: 'archived' })
  assert.equal(actions.canEdit, false)
  assert.equal(actions.canCopy, false)
  assert.equal(actions.canMove, false)
  assert.equal(actions.canArchive, false)
  assert.equal(actions.canRestore, true)
  assert.equal(actions.canDelete, false)
})

test('completed canvas cannot be deleted', () => {
  const actions = canvasActions({ status: 'completed' })
  assert.equal(actions.canDelete, false)
  assert.equal(actions.canEdit, false)
})

test('workspaceTab defaults to workflow', () => {
  assert.equal(workspaceTab(undefined), 'workflow')
  assert.equal(workspaceTab('canvas'), 'canvas')
  assert.equal(workspaceTab('unknown'), 'workflow')
})

test('breadcrumb for editor from project canvas page', () => {
  const crumbs = buildBreadcrumb('canvas-editor', {
    projectName: '我的短剧', canvasName: '第08集正式生产',
    projectId: 10, referrer: 'project-canvas'
  })
  assert.deepEqual(crumbs, [
    { label: '首页', path: '/' },
    { label: '剧本创作', path: '/script-gen' },
    { label: '我的短剧', path: '/script-gen/10/workspace?tab=canvas' },
    { label: '第08集正式生产', path: null }
  ])
})

test('breadcrumb for editor from global center', () => {
  const crumbs = buildBreadcrumb('canvas-editor', {
    canvasName: '第08集正式生产', referrer: 'canvas-center'
  })
  assert.deepEqual(crumbs, [
    { label: '首页', path: '/' },
    { label: '画布项目中心', path: '/canvas-projects' },
    { label: '第08集正式生产', path: null }
  ])
})

test('severity badge colors', () => {
  assert.equal(SEVERITY_COLORS.blocking, 'danger')
  assert.equal(SEVERITY_COLORS.warning, 'warning')
  assert.equal(SEVERITY_COLORS.info, 'info')
})

test('status and purpose labels', () => {
  assert.equal(STATUS_LABELS.editing, '编辑中')
  assert.equal(STATUS_LABELS.archived, '已归档')
  assert.equal(PURPOSE_LABELS.official, '正式方案')
  assert.equal(PURPOSE_LABELS.experiment, '实验方案')
})
