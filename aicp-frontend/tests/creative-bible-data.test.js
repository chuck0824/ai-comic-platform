import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  sectionsForMode, mergeWritingGuide, normalizeBibleHealth,
  NON_OVERRIDABLE, ECOSYSTEM_RULE_TYPES
} from '../src/views/work-editor/creativeBibleData.js'

describe('sectionsForMode', () => {
  it('long form shows full ecosystem', () => {
    assert.deepEqual(sectionsForMode('long_form'),
      ['overview', 'ecosystem', 'characters', 'relations', 'writing', 'continuity'])
  })

  it('short drama stays compact', () => {
    assert.deepEqual(sectionsForMode('short_drama'),
      ['overview', 'ecosystem', 'characters', 'relations', 'writing'])
  })

  it('tvc shows minimal sections', () => {
    assert.deepEqual(sectionsForMode('tvc'),
      ['overview', 'ecosystem', 'characters', 'writing'])
  })
})

describe('mergeWritingGuide', () => {
  it('unit overrides explicit field but cannot clear hard bans', () => {
    const result = mergeWritingGuide(
      { pace: 'fast', hard_bans: ['辱骂'] },
      { pace: 'slow', hard_bans: [] }
    )
    assert.equal(result.resolved.pace, 'slow')
    assert.deepEqual(result.resolved.hard_bans, ['辱骂'])
    assert.deepEqual(result.conflicts, ['hard_bans'])
  })

  it('platform_rules cannot be overridden', () => {
    const result = mergeWritingGuide(
      { platform_rules: ['禁止色情描写'] },
      { platform_rules: ['允许擦边'] }
    )
    assert.deepEqual(result.resolved.platform_rules, ['禁止色情描写'])
    assert.deepEqual(result.conflicts, ['platform_rules'])
  })

  it('compliance_rules cannot be overridden', () => {
    const result = mergeWritingGuide(
      { compliance_rules: ['禁止未成年人饮酒'] },
      { compliance_rules: [] }
    )
    assert.deepEqual(result.resolved.compliance_rules, ['禁止未成年人饮酒'])
    assert.deepEqual(result.conflicts, ['compliance_rules'])
  })

  it('returns project guide when unit guide is null', () => {
    const result = mergeWritingGuide({ pov: 'third' }, null)
    assert.equal(result.resolved.pov, 'third')
    assert.deepEqual(result.conflicts, [])
  })
})

describe('normalizeBibleHealth', () => {
  it('missing health normalizes to safe blocked state', () => {
    assert.deepEqual(normalizeBibleHealth(null), {
      status: 'missing', current_version_id: 0, current_version_no: 0,
      confirmed_fact_count: 0, pending_change_count: 0, ready_for_generation: false
    })
  })

  it('passes through valid health data', () => {
    const result = normalizeBibleHealth({
      status: 'confirmed', current_version_id: 5, ready_for_generation: true,
      confirmed_fact_count: 3, pending_change_count: 1
    })
    assert.equal(result.status, 'confirmed')
    assert.equal(result.ready_for_generation, true)
  })
})

describe('NON_OVERRIDABLE', () => {
  it('contains all three non-overridable keys', () => {
    assert.deepEqual([...NON_OVERRIDABLE].sort(),
      ['compliance_rules', 'hard_bans', 'platform_rules'])
  })
})

describe('ECOSYSTEM_RULE_TYPES', () => {
  it('has nine rule types', () => {
    assert.equal(ECOSYSTEM_RULE_TYPES.length, 9)
  })
})
