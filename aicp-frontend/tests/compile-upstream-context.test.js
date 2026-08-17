import test from 'node:test'
import assert from 'node:assert/strict'

import { compileUpstreamContext } from '../src/views/canvas/utils/compileUpstreamContext.js'

function node(partial) {
  return {
    uuid: partial.uuid,
    id: partial.id,
    type: partial.type,
    name: partial.name,
    input_data: JSON.stringify(partial.data || {}),
    output_data: partial.output ? JSON.stringify(partial.output) : undefined,
  }
}

test('compiles character, scene and prompt into the downstream image request', () => {
  const image = node({ uuid: 'n-image', id: 3, type: 'image', data: { prompt: '持剑回望' } })
  const compiled = compileUpstreamContext(image, [
    node({ uuid: 'n-char', id: 1, type: 'character', data: { name: '林深', appearance: '青衫长发', prompt: '同一张脸' } }),
    node({ uuid: 'n-scene', id: 2, type: 'scene', data: { name: '雨巷', environment: '石板路', atmosphere: '冷青灯光' } }),
    node({ uuid: 'n-prompt', id: 4, type: 'prompt', data: { prompt: '电影感，9:16' } }),
    node({ uuid: 'n-model', id: 5, type: 'model', data: { model_id: 'flux-1.1-pro' } }),
    image,
  ], [
    { source_node_id: 'n-char', target_node_id: 'n-image' },
    { sourceNodeId: 2, targetNodeId: 3 },
    { source: 'n-prompt', target: 'n-image' },
    { source_node_id: 5, target_node_id: 'n-image' },
  ])

  assert.match(compiled.prompt, /角色「林深」/)
  assert.match(compiled.prompt, /场景「雨巷」/)
  assert.match(compiled.prompt, /电影感，9:16/)
  assert.match(compiled.prompt, /持剑回望/)
  assert.equal(compiled.model_id, 'flux-1.1-pro')
  assert.equal(compiled.hasUpstream, true)
  assert.ok(compiled.sources.includes('角色「林深」'))
})

test('uses connected image preview as video first frame', () => {
  const video = node({ uuid: 'n-video', id: 8, type: 'video', data: { prompt: '镜头推进' } })
  const compiled = compileUpstreamContext(video, [
    node({
      uuid: 'n-still',
      id: 7,
      type: 'image',
      data: { prompt: '定格' },
      output: { preview_url: 'https://cdn.example/frame.png' },
    }),
    video,
  ], [
    { source_node_id: 'n-still', target_node_id: 'n-video' },
  ])

  assert.equal(compiled.first_frame_url, 'https://cdn.example/frame.png')
  assert.match(compiled.prompt, /镜头推进/)
})

test('allows empty local prompt when only upstream prompt exists', () => {
  const image = node({ uuid: 'n-image', id: 3, type: 'image', data: { prompt: '' } })
  const compiled = compileUpstreamContext(image, [
    node({ uuid: 'n-prompt', id: 4, type: 'prompt', data: { prompt: '水墨风夜雨' } }),
    image,
  ], [
    { source_node_id: 'n-prompt', target_node_id: 'n-image' },
  ])

  assert.equal(compiled.prompt, '水墨风夜雨')
  assert.equal(compiled.local_prompt, '')
})
