import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const html = readFileSync(new URL('../content/effect-page-v8.html', import.meta.url), 'utf8')

test('first screen exposes two clearly interactive product cards', () => {
  assert.match(html, /data-product-card="3001"/)
  assert.match(html, /data-product-card="8080"/)
  assert.match(html, /class="interaction-hint"/)
})

test('first screen supports product focus and automatic studio capability cycling', () => {
  assert.match(html, /function activateProductCard/)
  assert.match(html, /function activateStudioItem/)
  assert.match(html, /studioCycleTimer/)
})
