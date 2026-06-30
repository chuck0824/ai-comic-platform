/**
 * workEditorData.js 纯函数单元测试
 * 运行: node --test tests/work-editor-data.test.js
 */
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  validateTagSelection,
  normalizeEditorResponse,
  makeTagPayload,
  TAG_LIMITS,
  SETTING_TYPES,
  createSaveQueue
} from '../src/views/work-editor/workEditorData.js'

const SAMPLE_DICT = {
  genres: [{ value: '言情', label: '言情' }, { value: '悬疑', label: '悬疑' }],
  plots: [{ value: '重生', label: '重生' }, { value: '先婚后爱', label: '先婚后爱' }, { value: '逆袭', label: '逆袭' }, { value: '复仇', label: '复仇' }],
  tones: [{ value: '甜宠', label: '甜宠' }, { value: '爽文', label: '爽文' }],
  settings: [{ value: '现代', label: '现代' }, { value: '古代', label: '古代' }],
  version: 1
}

describe('validateTagSelection', () => {
  it('valid selection passes', () => {
    const { valid } = validateTagSelection({
      genre: ['言情'],
      plot: ['重生', '先婚后爱'],
      tone: ['甜宠', '爽文'],
      setting: ['现代']
    }, SAMPLE_DICT)
    assert.equal(valid, true)
  })

  it('invalid genre fails', () => {
    const { valid, errors } = validateTagSelection({ genre: ['科幻'], plot: [], tone: [], setting: [] }, SAMPLE_DICT)
    assert.equal(valid, false)
    assert.ok(errors.some(e => e.includes('无效题材')))
  })

  it('too many plots fails', () => {
    const { valid, errors } = validateTagSelection({
      genre: [], plot: ['重生', '先婚后爱', '逆袭', '复仇'], tone: [], setting: []
    }, SAMPLE_DICT)
    assert.equal(valid, false)
    assert.ok(errors.some(e => e.includes('最多选')))
  })

  it('empty selection passes (clearing)', () => {
    const { valid } = validateTagSelection({ genre: [], plot: [], tone: [], setting: [] }, SAMPLE_DICT)
    assert.equal(valid, true)
  })
})

describe('normalizeEditorResponse', () => {
  it('normalizes snake_case backend response', () => {
    const raw = {
      project_id: 1, title: 'Test', total_words: 100, permissions: 'owner',
      profile: { genre_tag: '言情', plot_tags: ['重生'], tone_tags: [], setting_tag: '现代', synopsis: 'S', outline: null, revision: 3 },
      revision: 3, setting_counts: { character: 2 }, pending_extraction_count: 1
    }
    const norm = normalizeEditorResponse(raw)
    assert.equal(norm.projectId, 1)
    assert.equal(norm.profile.genreTag, '言情')
    assert.deepEqual(norm.profile.plotTags, ['重生'])
  })
})

describe('makeTagPayload', () => {
  it('creates payload with revision', () => {
    const profile = { genreTag: '言情', plotTags: ['重生'], toneTags: [], settingTag: '现代', revision: 3 }
    const payload = makeTagPayload(profile, { genre: '悬疑' })
    assert.equal(payload.genre, '悬疑')
    assert.equal(payload.revision, 3)
  })
})

describe('TAG_LIMITS', () => {
  it('enforces 1/3/3/1', () => {
    assert.equal(TAG_LIMITS.genre, 1)
    assert.equal(TAG_LIMITS.plot, 3)
    assert.equal(TAG_LIMITS.tone, 3)
    assert.equal(TAG_LIMITS.setting, 1)
  })
})

describe('SETTING_TYPES', () => {
  it('has all five types', () => {
    assert.deepEqual(SETTING_TYPES, ['character', 'background', 'faction', 'location', 'item'])
  })
})

describe('createSaveQueue', () => {
  it('serializes async operations', async () => {
    const queue = createSaveQueue()
    const order = []
    await Promise.all([
      queue.enqueue(async () => { order.push(1); return 1 }),
      queue.enqueue(async () => { order.push(2); return 2 }),
      queue.enqueue(async () => { order.push(3); return 3 })
    ])
    assert.deepEqual(order, [1, 2, 3])
  })
})
