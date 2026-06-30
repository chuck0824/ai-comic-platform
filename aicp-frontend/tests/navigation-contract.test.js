import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'

const routerPath = fileURLToPath(new URL('../src/router/index.js', import.meta.url))
const sidebarPath = fileURLToPath(new URL('../src/components/Sidebar.vue', import.meta.url))

test('root redirects to home, not canvas', () => {
  const router = fs.readFileSync(routerPath, 'utf8')
  assert.match(router, /redirect:\s*['"]\/home['"]/)
})

test('canvas-projects route exists', () => {
  const router = fs.readFileSync(routerPath, 'utf8')
  assert.match(router, /path:\s*['"]canvas-projects['"]/)
})

test('only one sidebar 剧本创作 entry', () => {
  const sidebar = fs.readFileSync(sidebarPath, 'utf8')
  const matches = sidebar.match(/>\s*剧本创作\s*</g) || []
  assert.equal(matches.length, 1)
})

test('sidebar has 首页 entry', () => {
  const sidebar = fs.readFileSync(sidebarPath, 'utf8')
  assert.match(sidebar, /首页/)
})

test('sidebar 画布工作台 links to /canvas-projects', () => {
  const sidebar = fs.readFileSync(sidebarPath, 'utf8')
  assert.match(sidebar, /to="\/canvas-projects"/)
})

test('bare /canvas redirects to /canvas-projects', () => {
  const router = fs.readFileSync(routerPath, 'utf8')
  assert.match(router, /path:\s*['"]canvas['"],\s*\n\s*redirect:\s*['"]\/canvas-projects['"]/)
})
