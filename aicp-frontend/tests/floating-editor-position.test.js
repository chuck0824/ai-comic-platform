import test from 'node:test'
import assert from 'node:assert/strict'

import { computeFloatingEditorPosition } from '../src/views/canvas/utils/floatingEditorPosition.js'

const panel = { width: 440, height: 520 }
const viewport = { width: 1280, height: 800 }

test('places the editor to the right when space is available', () => {
  const result = computeFloatingEditorPosition({
    nodeRect: { left: 120, top: 80, width: 240, height: 180 },
    viewport,
    panel
  })

  assert.deepEqual(result, { placement: 'right', x: 378, y: 80 })
})

test('falls back to the left near the right edge', () => {
  const result = computeFloatingEditorPosition({
    nodeRect: { left: 980, top: 100, width: 240, height: 180 },
    viewport,
    panel
  })

  assert.equal(result.placement, 'left')
  assert.equal(result.x, 522)
})

test('falls back below when neither horizontal side fits', () => {
  const result = computeFloatingEditorPosition({
    nodeRect: { left: 390, top: 80, width: 500, height: 120 },
    viewport: { width: 900, height: 900 },
    panel,
  })

  assert.equal(result.placement, 'bottom')
  assert.equal(result.y, 218)
})

test('falls back above when only the top fits', () => {
  const result = computeFloatingEditorPosition({
    nodeRect: { left: 380, top: 640, width: 500, height: 120 },
    viewport: { width: 900, height: 800 },
    panel,
  })

  assert.equal(result.placement, 'top')
  assert.equal(result.y, 102)
})

test('clamps the editor inside a viewport smaller than the preferred placement', () => {
  const result = computeFloatingEditorPosition({
    nodeRect: { left: 8, top: 8, width: 200, height: 180 },
    viewport: { width: 460, height: 560 },
    panel,
  })

  assert.equal(result.x, 16)
  assert.equal(result.y, 16)
})
