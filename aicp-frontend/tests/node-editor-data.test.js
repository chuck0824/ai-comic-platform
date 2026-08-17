import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildNodeDraft,
  buildTaskParameters,
  readNodePreviewUrl,
  shouldSelectNode,
  slashCommandForNode,
  validateNodeDraft,
} from '../src/views/canvas/utils/nodeEditorData.js'

test('builds a video draft from persisted data without replacing existing values', () => {
  const draft = buildNodeDraft({
    id: 9,
    type: 'video',
    name: '镜头视频',
    input_data: JSON.stringify({
      prompt: '雨夜追逐',
      model_id: 'kling-1.6',
      aspect_ratio: '16:9',
      duration: 8,
      variants: 3,
    }),
  })

  assert.equal(draft.model_id, 'kling-1.6')
  assert.equal(draft.aspect_ratio, '16:9')
  assert.equal(draft.duration, 8)
  assert.equal(draft.variants, 3)
})

test('uses type defaults only when persisted values are absent', () => {
  const draft = buildNodeDraft({ id: 2, type: 'audio', name: '旁白', data: {} })

  assert.equal(draft.model_id, 'volcano-tts')
  assert.equal(draft.duration, 5)
  assert.equal(draft.mode, 'tts')
})

test('builds task parameters from the visible draft', () => {
  const parameters = buildTaskParameters({
    prompt: '机械城日落',
    model_id: 'seedream-5.0',
    aspect_ratio: '1:1',
    variants: 4,
    mode: 'image',
  })

  assert.deepEqual(parameters, {
    prompt: '机械城日落',
    model_id: 'seedream-5.0',
    aspect_ratio: '1:1',
    variants: 4,
    mode: 'image',
  })
})

test('requires a prompt for ordinary media generation', () => {
  assert.deepEqual(validateNodeDraft('image', { prompt: '   ' }), { prompt: '请输入提示词' })
  assert.deepEqual(validateNodeDraft('image', { prompt: '有效提示词' }), {})
})

test('allows empty local prompt when upstream compiled prompt exists', () => {
  assert.deepEqual(validateNodeDraft('image', { prompt: '' }, { compiledPrompt: '角色「林深」' }), {})
  assert.deepEqual(validateNodeDraft('audio', { prompt: '  ' }, { compiledPrompt: '  ' }), {
    prompt: '请输入文本或提示词',
  })
})

test('reads preview url from output_data when input_data has none', () => {
  assert.equal(readNodePreviewUrl({
    input_data: JSON.stringify({ prompt: '夜雨' }),
    output_data: JSON.stringify({ preview_url: 'https://cdn.example/a.png' }),
  }), 'https://cdn.example/a.png')
  assert.equal(slashCommandForNode('image'), 'generate-image')
  assert.equal(slashCommandForNode('video'), 'generate-video')
})

test('does not toggle an already selected node during the click phase', () => {
  assert.equal(shouldSelectNode('node-1', 'node-1'), false)
  assert.equal(shouldSelectNode(null, 'node-1'), true)
  assert.equal(shouldSelectNode('node-2', 'node-1'), true)
})
