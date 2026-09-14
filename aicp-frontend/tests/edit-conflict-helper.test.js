import test from 'node:test'
import assert from 'node:assert/strict'
import {
  conflictActionOptions,
  extractEditConflict,
  formatEditConflictMessage
} from '../src/views/content-project/workbench/editConflictHelper.js'

test('extractEditConflict reads nested and top-level codes', () => {
  assert.deepEqual(extractEditConflict({
    response: { data: { code: 43003, data: { server_revision: 5, base_revision: 4 } } }
  }), { server_revision: 5, base_revision: 4 })

  assert.equal(extractEditConflict({
    response: { data: { code: 40001, message: 'other' } }
  }), null)
})

test('conflictActionOptions always offers comparison', () => {
  const basic = conflictActionOptions({ server_revision: 2 })
  assert.deepEqual(basic.map(item => item.code), [
    'load_remote_draft',
    'keep_local_retry',
    'open_comparison'
  ])

  const withDiff = conflictActionOptions({ comparison_url: '/conflicts/1' })
  assert.ok(withDiff.some(item => item.code === 'open_comparison' && item.href === '/conflicts/1'))
})

test('formatEditConflictMessage includes revisions', () => {
  const message = formatEditConflictMessage({ server_revision: 9, base_revision: 8 })
  assert.match(message, /revision=9/)
  assert.match(message, /base=8/)
})
