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

test('sidebar 画布视频工作台 links to /canvas-projects', () => {
  const sidebar = fs.readFileSync(sidebarPath, 'utf8')
  assert.match(sidebar, /to="\/canvas-projects"/)
})

test('bare /canvas redirects to /canvas-projects', () => {
  const router = fs.readFileSync(routerPath, 'utf8')
  assert.match(router, /path:\s*['"]canvas['"],\s*\n\s*redirect:\s*['"]\/canvas-projects['"]/)
})

// ── Creative Bible navigation ──

test('WorkInfoNav has 创作圣经 navigation section', () => {
  const navPath = fileURLToPath(new URL('../src/views/work-editor/WorkInfoNav.vue', import.meta.url))
  const nav = fs.readFileSync(navPath, 'utf8')
  assert.match(nav, /创作圣经/)
  assert.match(nav, /bible-overview/)
  assert.match(nav, /ecosystem/)
  assert.match(nav, /writing-guide/)
})

test('TagEditor imports CreativeBibleOverview and EcosystemPanel', () => {
  const tagPath = fileURLToPath(new URL('../src/views/TagEditor.vue', import.meta.url))
  const tag = fs.readFileSync(tagPath, 'utf8')
  assert.match(tag, /CreativeBibleOverview/)
  assert.match(tag, /EcosystemPanel/)
  assert.match(tag, /WritingGuidePanel/)
})

// ── Canvas creation navigation contract ──

const createCanvasDialogPath = fileURLToPath(new URL(
  '../src/views/canvas-project/CreateCanvasDialog.vue', import.meta.url
))

test('canvas dialog defaults to an independent experimental canvas', () => {
  const source = fs.readFileSync(createCanvasDialogPath, 'utf8')
  assert.match(source, /const linkContent = ref\(false\)/)
  assert.match(source, /purpose:\s*'experiment'/)
})

test('canvas dialog uses camelCase admission query parameters', () => {
  const source = fs.readFileSync(createCanvasDialogPath, 'utf8')
  assert.match(source, /buildAdmissionParams\(form\.value\)/)
  assert.doesNotMatch(source, /content_project_id:\s*form\.value\.contentProjectId/)
})

test('canvas center routes a newly created canvas to the existing editor', () => {
  const centerPath = fileURLToPath(new URL(
    '../src/views/canvas-project/CanvasProjectCenter.vue', import.meta.url
  ))
  const source = fs.readFileSync(centerPath, 'utf8')
  assert.match(source, /router\.push\(`\/canvas\/\$\{canvas\.uuid\}`\)/)
})

// ── Production SOP navigation ──

test('production SOP uses list and project-scoped routes', () => {
  const router = fs.readFileSync(routerPath, 'utf8')
  const sidebar = fs.readFileSync(sidebarPath, 'utf8')
  assert.match(router, /path:\s*['"]sop['"]/)
  assert.match(router, /path:\s*['"]content-projects\/:projectId\/sop['"]/)
  assert.match(sidebar, /to="\/sop"/)
  assert.doesNotMatch(sidebar, /\/sop\/1/)
})

test('SOP workspace route points to SopWorkspace component', () => {
  const router = fs.readFileSync(routerPath, 'utf8')
  assert.match(router, /SopWorkspace/)
})

test('SOP project list route points to SopProjectList component', () => {
  const router = fs.readFileSync(routerPath, 'utf8')
  assert.match(router, /SopProjectList/)
})
