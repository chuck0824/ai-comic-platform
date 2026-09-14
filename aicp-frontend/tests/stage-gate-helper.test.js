import test from 'node:test'
import assert from 'node:assert/strict'
import {
  extractGateDetailsFromError,
  formatGateIssues,
  hasDurationVarianceUnconfirmed,
  isStageGateBlockedError,
  issueCodes,
  normalizeGateView
} from '../src/views/content-project/workbench/stageGateHelper.js'

test('normalizes gate preview payload', () => {
  const gate = normalizeGateView({
    stage_key: 'structured_script',
    blockers: [{ code: 'STRUCTURED_BEATS_REQUIRED', message: '需要节拍' }],
    warnings: [{ code: 'DURATION_VARIANCE', message: '偏差 20%' }]
  })
  assert.equal(gate.stageKey, 'structured_script')
  assert.equal(gate.blockers.length, 1)
  assert.deepEqual(issueCodes(gate.warnings), ['DURATION_VARIANCE'])
})

test('formats gate issues for dialog copy', () => {
  const text = formatGateIssues([
    { code: 'A', message: '阻断 A' },
    { message: '仅消息' }
  ])
  assert.match(text, /\[A\] 阻断 A/)
  assert.match(text, /仅消息/)
})

test('detects duration variance unconfirmed blocker', () => {
  assert.equal(hasDurationVarianceUnconfirmed({
    blockers: [{ code: 'DURATION_VARIANCE_UNCONFIRMED' }]
  }), true)
  assert.equal(hasDurationVarianceUnconfirmed({ blockers: [] }), false)
})

test('extracts gate details from error response', () => {
  const caught = {
    response: {
      data: {
        code: 43010,
        message: '门禁存在阻断项',
        data: {
          blockers: [{ code: 'X', message: '阻断' }],
          warnings: []
        }
      }
    }
  }
  assert.equal(isStageGateBlockedError(caught), true)
  const gate = extractGateDetailsFromError(caught)
  assert.equal(gate.blockers[0].code, 'X')
})
