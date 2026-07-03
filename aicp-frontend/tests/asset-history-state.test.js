import test from 'node:test'
import assert from 'node:assert/strict'
import {
  parseAssetHistoryQuery, serializeAssetHistoryState,
  mapRecordCard, ASSET_TYPE_LABELS, STATUS_LABELS
} from '../src/views/asset-history/assetHistoryState.js'

test('restores project, category, statuses and page from URL', () => {
  const state = parseAssetHistoryQuery({
    project_uuid: 'p1', asset_type: 'CHARACTER',
    status: 'running,failed', page: '2'
  })
  assert.deepEqual(state.statuses, ['running', 'failed'])
  assert.equal(state.projectUuid, 'p1')
  assert.equal(state.page, 2)
})

test('serializes only non-default workbench state', () => {
  const q = serializeAssetHistoryState({
    scope: 'mine', page: 1, pageSize: 24, statuses: [],
    projectUuid: '', assetType: '', collection: '', mediaType: '',
    keyword: '', sort: 'created_at:desc', view: 'grid',
    recordKind: '', recordUuid: ''
  })
  assert.equal(Object.keys(q).length, 0)
})

test('maps failed task to retry card without asset actions', () => {
  const card = mapRecordCard({
    recordKind: 'TASK', recordId: 'task-abc', name: '测试',
    status: 'failed', allowedActions: ['RETRY_TASK', 'TRASH']
  })
  assert.equal(card.canRetry, true)
  assert.equal(card.canDownload, false)
  assert.equal(card.canEdit, false)
})

test('favorite asset has correct allowed actions', () => {
  const card = mapRecordCard({
    recordKind: 'ASSET', recordId: 'asset-xyz', name: '角色',
    assetType: 'CHARACTER', status: 'ACTIVE',
    allowedActions: ['PREVIEW', 'EDIT', 'FAVORITE', 'DOWNLOAD', 'SEND_TO_CANVAS', 'TRASH']
  })
  assert.equal(card.canFavorite, true)
  assert.equal(card.canSendToCanvas, true)
  assert.equal(card.canPublish, false) // not in allowed list
  assert.equal(card.canRestore, false)
})

test('type labels cover all asset types', () => {
  const keys = ['CHECKPOINT', 'LORA', 'STYLE_PACK', 'PROMPT', 'CHARACTER',
    'SCENE', 'PROP', 'STORYBOARD', 'VOICE', 'MUSIC', 'OTHER']
  for (const k of keys) {
    assert.ok(ASSET_TYPE_LABELS[k], `Missing label for ${k}`)
  }
})

test('status labels cover key statuses', () => {
  assert.equal(STATUS_LABELS.PENDING, '排队中')
  assert.equal(STATUS_LABELS.RUNNING, '生成中')
  assert.equal(STATUS_LABELS.SUCCEEDED, '已完成')
  assert.equal(STATUS_LABELS.FAILED, '失败')
  assert.equal(STATUS_LABELS.TRASHED, '回收站')
})
