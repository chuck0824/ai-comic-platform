# Canvas Node Floating Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the canvas right property drawer and bottom generator with one collision-aware, node-bound floating editor while preserving specialist script and director workspaces.

**Architecture:** Render one unscaled floating editor inside the canvas viewport and position it from the selected node's screen rectangle. Keep node-specific form state inside the editor, emit node-bound immutable updates, and route every generation/tool action through the existing canvas task pipeline with the visible parameters attached.

**Tech Stack:** Vue 3 Composition API, Element Plus, Vite, Node.js built-in test runner.

---

## File Structure

- Create `aicp-frontend/src/views/canvas/utils/floatingEditorPosition.js`: pure placement and viewport-clamping logic.
- Create `aicp-frontend/tests/floating-editor-position.test.js`: deterministic placement coverage.
- Create `aicp-frontend/src/views/canvas/components/NodeFloatingEditor.vue`: shared shell and six node-type experiences.
- Modify `aicp-frontend/src/views/canvas/components/CanvasNodeAgentBox.vue`: support embedded rendering inside the floating editor.
- Modify `aicp-frontend/src/views/Canvas.vue`: mount the floating editor, remove duplicate surfaces, simplify node actions, and pass exact execution parameters.

### Task 1: Floating placement utility

**Files:**
- Create: `aicp-frontend/src/views/canvas/utils/floatingEditorPosition.js`
- Test: `aicp-frontend/tests/floating-editor-position.test.js`

- [ ] **Step 1: Write failing tests for right, left, vertical fallback, and clamping**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { computeFloatingEditorPosition } from '../src/views/canvas/utils/floatingEditorPosition.js'

test('places editor to the right when space is available', () => {
  assert.equal(computeFloatingEditorPosition({
    nodeRect: { left: 100, top: 80, width: 200, height: 160 },
    viewport: { width: 1200, height: 800 }, panel: { width: 440, height: 520 }
  }).placement, 'right')
})
```

- [ ] **Step 2: Run tests and verify the missing-module failure**

Run: `node --test aicp-frontend/tests/floating-editor-position.test.js`

Expected: FAIL because `floatingEditorPosition.js` does not exist.

- [ ] **Step 3: Implement deterministic placement**

```js
export function computeFloatingEditorPosition({ nodeRect, viewport, panel, gap = 18, margin = 16 }) {
  const candidates = [
    { placement: 'right', x: nodeRect.left + nodeRect.width + gap, y: nodeRect.top },
    { placement: 'left', x: nodeRect.left - panel.width - gap, y: nodeRect.top },
    { placement: 'bottom', x: nodeRect.left, y: nodeRect.top + nodeRect.height + gap },
    { placement: 'top', x: nodeRect.left, y: nodeRect.top - panel.height - gap }
  ]
  const fits = candidate => candidate.x >= margin && candidate.y >= margin &&
    candidate.x + panel.width <= viewport.width - margin &&
    candidate.y + panel.height <= viewport.height - margin
  const selected = candidates.find(fits) || candidates[0]
  return {
    placement: selected.placement,
    x: Math.min(Math.max(selected.x, margin), Math.max(margin, viewport.width - panel.width - margin)),
    y: Math.min(Math.max(selected.y, margin), Math.max(margin, viewport.height - panel.height - margin))
  }
}
```

- [ ] **Step 4: Run the placement tests**

Run: `node --test aicp-frontend/tests/floating-editor-position.test.js`

Expected: all placement tests PASS.

### Task 2: Embedded text Agent

**Files:**
- Modify: `aicp-frontend/src/views/canvas/components/CanvasNodeAgentBox.vue`

- [ ] **Step 1: Add an embedded prop and class**

```vue
<div :class="['node-agent-box', { embedded }]" @mousedown.stop>
```

```js
embedded: { type: Boolean, default: false }
```

- [ ] **Step 2: Add embedded layout styles**

```css
.node-agent-box.embedded {
  position: static;
  width: 100%;
  min-height: 0;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  background: transparent;
}
```

- [ ] **Step 3: Build to validate the Vue template**

Run: `npm run build` in `aicp-frontend`.

Expected: Vite build succeeds.

### Task 3: Node-bound floating editor component

**Files:**
- Create: `aicp-frontend/src/views/canvas/components/NodeFloatingEditor.vue`

- [ ] **Step 1: Create the common shell**

The component must accept `node`, `style`, `placement`, `shots`, `projectId`, and `localMode`; emit `close`, `update`, `generate`, `tool`, `open-shot-editor`, `open-director`, `duplicate`, `reuse`, `save-asset`, `delete`, and `agent-applied`.

```vue
<section class="node-floating-editor" :class="`placement-${placement}`" :style="style" @mousedown.stop>
  <header class="floating-head">
    <strong>{{ draft.name || nodeTypeLabel }}</strong>
    <span class="save-state">{{ saveState }}</span>
    <button @click="collapsed = !collapsed">−</button>
    <button @click="$emit('close')">×</button>
  </header>
  <div v-if="!collapsed" class="floating-body"><slot /></div>
</section>
```

- [ ] **Step 2: Add node-keyed immutable draft initialization**

```js
watch(() => nodeKey(props.node), () => {
  const data = readNodeData(props.node)
  draft.value = { name: props.node.name || '', ...data }
  activeTab.value = props.node.type === 'text' ? 'content' : 'generate'
}, { immediate: true })
```

All update emits include the concrete node object: `emit('update', { node: props.node, updates })`.

- [ ] **Step 3: Implement type-specific tabs and fields**

- Text: content and embedded AI assistant.
- Image: prompt, model, aspect ratio, variants, tools, task summary.
- Video: mode, prompt, model, aspect ratio, duration, variants, tools, task summary.
- Audio: mode, prompt, model, voice, speed, duration, tools, task summary.
- Script: shot count and `open-shot-editor` only.
- Director: scene/shot summary and `open-director` only.

- [ ] **Step 4: Implement the single primary action payload**

```js
emit('generate', {
  node: props.node,
  data: { ...readNodeData(props.node), ...draft.value },
  action: { label: primaryLabel.value, taskType: taskType.value, modelId: draft.value.model_id }
})
```

- [ ] **Step 5: Add polished UI states**

Implement visible selected tabs, disabled primary action until required fields exist, read-only status, inline errors, overflow menu, collapse behavior, viewport-safe scrolling, and placement pointer styles.

- [ ] **Step 6: Build the component**

Run: `npm run build` in `aicp-frontend`.

Expected: Vite build succeeds without unresolved components or template errors.

### Task 4: Canvas integration and duplicate-surface removal

**Files:**
- Modify: `aicp-frontend/src/views/Canvas.vue`

- [ ] **Step 1: Import the floating editor and placement helper**

```js
import NodeFloatingEditor from './canvas/components/NodeFloatingEditor.vue'
import { computeFloatingEditorPosition } from './canvas/utils/floatingEditorPosition'
```

- [ ] **Step 2: Mount the editor in the unscaled canvas viewport**

```vue
<NodeFloatingEditor v-if="selectedNodeForPanel"
  :node="selectedNodeForPanel"
  :style="floatingEditorStyle"
  :placement="floatingPlacement"
  :shots="getNodeShots(selectedNodeForPanel)"
  :project-id="state.projectId.value"
  :local-mode="canvas.localMode.value"
  @update="handleFloatingUpdate"
  @generate="handleFloatingGenerate"
  @tool="handleFloatingTool"
  @close="deselectCanvas" />
```

- [ ] **Step 3: Calculate the selected node screen rectangle**

Use `state.zoomLevel`, `state.panOffset`, `nodeW`, `nodeH`, and the measured `canvasAreaRef` size. Recompute after resize, pan, zoom, and node drag without mutating editor draft state.

- [ ] **Step 4: Remove duplicate surfaces**

Delete the `NodePropertyPanel` render/import, standalone `CanvasNodeAgentBox` render/import, bottom `generator-panel`, `generatorPrompt`, `generatorConfig`, their selection watcher, and bottom-generator-only functions and CSS.

- [ ] **Step 5: Simplify node-card actions**

Remove ordinary image/video/audio execution buttons. Script and director cards retain only `打开专业编辑器`. Text empty-state preset actions remain.

- [ ] **Step 6: Route updates to the payload's node**

```js
async function handleFloatingUpdate({ node, updates }) {
  await handleNodeUpdate(node, updates)
}
```

This must never resolve the target from `selectedNodeForPanel` after a delay.

### Task 5: Exact task parameters and specialist workflows

**Files:**
- Modify: `aicp-frontend/src/views/Canvas.vue`

- [ ] **Step 1: Save a complete draft before execution**

```js
async function handleFloatingGenerate({ node, data, action }) {
  const id = nodeKey(node)
  await canvas.updateNode(id, { data, status: 'ready' })
  const current = findNodeByRef(id) || node
  await runNodeTask(current, { ...action, parameters: data })
}
```

- [ ] **Step 2: Include visible parameters in task payloads**

```js
const params = {
  ...action.parameters,
  node_id: nodeKey(node),
  prompt: action.parameters?.prompt ?? getNodePrompt(node),
  action: action.label,
  model_id: action.modelId || action.parameters?.model_id || defaultModelForTask(taskType)
}
```

- [ ] **Step 3: Keep script generation in the shot editor**

The floating summary opens `ShotTableEditor`; image/video batch execution continues through `handleBatchFromEditor(shotIds, config)` so range and configuration are mandatory.

- [ ] **Step 4: Keep director capture inside the director workspace**

The floating summary opens `openDirectorDesk(node)`. Remove external capture/send actions from the director node card.

- [ ] **Step 5: Run focused tests and build**

Run:

```bash
node --test aicp-frontend/tests/floating-editor-position.test.js
cd aicp-frontend && npm run build
```

Expected: tests PASS and build succeeds.

### Task 6: Browser UI/UX verification and refinement

**Files:**
- Modify: `aicp-frontend/src/views/canvas/components/NodeFloatingEditor.vue` (spacing, contrast, scroll, pointer, focus, disabled and responsive styles)
- Modify: `aicp-frontend/src/views/Canvas.vue` (viewport sizing and editor placement integration)

- [ ] **Step 1: Open `/canvas` with a valid local session**

Verify the right drawer and bottom generator are absent and the canvas uses the reclaimed space.

- [ ] **Step 2: Check all six node types**

Confirm correct tabs, fields, primary action, specialist editor entry, and no irrelevant parameters.

- [ ] **Step 3: Check positioning stress cases**

Move nodes near all four viewport edges; pan and zoom from 50% to 150%; confirm right/left/bottom/top fallback and no viewport overflow.

- [ ] **Step 4: Refine visual and interaction details**

Adjust spacing, contrast, panel width, scrolling, focus states, pointer direction, disabled states, and button hierarchy based on observed behavior.

- [ ] **Step 5: Re-run verification**

Run the placement tests and production build again. Recheck browser console errors and the six-node workflow.
