<template>
  <div class="vue-flow-stage" @drop="$emit('drop', $event)" @dragover.prevent @dblclick="onDblClick">
    <VueFlow
      :id="flowId"
      v-model:nodes="nodes"
      v-model:edges="edges"
      :default-viewport="{ x: 40, y: 40, zoom: 0.85 }"
      :min-zoom="0.2"
      :max-zoom="2"
      :snap-to-grid="true"
      :snap-grid="[16, 16]"
      :nodes-connectable="true"
      :edges-updatable="true"
      :connection-mode="ConnectionMode.Loose"
      :connection-radius="28"
      :connection-line-style="{ stroke: '#60a5fa', strokeWidth: 2 }"
      elevate-edges-on-select
      @node-click="onNodeClick"
      @pane-click="onPaneClick"
      @connect="onConnect"
      @edge-click="onEdgeClick"
      @node-drag-stop="onNodeDragStop"
      @viewport-change-end="onViewportChange"
    >
      <Background :gap="18" pattern-color="#334155" />
      <MiniMap pannable zoomable position="bottom-right" />
      <Controls position="bottom-left" :show-interactive="false" />

      <template #node-aicp="nodeProps">
        <CanvasFlowNode
          v-bind="nodeProps"
          @open-shot="$emit('open-shot', $event)"
          @open-director="$emit('open-director', $event)"
          @node-context="(e, raw) => $emit('node-context', e, raw)"
        />
      </template>
    </VueFlow>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { VueFlow, useVueFlow, ConnectionMode } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'

import CanvasFlowNode from './CanvasFlowNode.vue'
import { toFlowNodes, toFlowEdges } from './mapCanvasToFlow.js'

const props = defineProps({
  domainNodes: { type: Array, default: () => [] },
  domainConnections: { type: Array, default: () => [] },
  selectedNodeId: { type: String, default: null },
  selectedEdgeId: { type: String, default: null }
})

const emit = defineEmits([
  'select-node',
  'deselect',
  'connect',
  'select-edge',
  'nodes-moved',
  'viewport',
  'pane-dblclick',
  'drop',
  'open-shot',
  'open-director',
  'node-context'
])

const flowId = 'aicp-main-canvas'
const nodes = ref([])
const edges = ref([])

const { setViewport, getViewport, fitView, screenToFlowCoordinate, zoomIn, zoomOut } = useVueFlow(flowId)

function syncFromDomain() {
  nodes.value = toFlowNodes(props.domainNodes).map(n => ({
    ...n,
    selected: props.selectedNodeId != null && n.id === props.selectedNodeId
  }))
  edges.value = toFlowEdges(props.domainConnections, props.domainNodes).map(e => ({
    ...e,
    selected: props.selectedEdgeId != null && e.id === props.selectedEdgeId
  }))
}

watch(() => [props.domainNodes, props.domainConnections], syncFromDomain, { deep: true, immediate: true })

watch(() => props.selectedNodeId, (id) => {
  nodes.value = nodes.value.map(n => ({ ...n, selected: id != null && n.id === id }))
})

watch(() => props.selectedEdgeId, (id) => {
  edges.value = edges.value.map(e => ({ ...e, selected: id != null && e.id === id }))
})
function onNodeClick({ node }) {
  emit('select-node', node.data?.raw || node)
}

function onPaneClick() {
  emit('deselect')
}

function onDblClick(e) {
  if (e.target?.closest?.('.vue-flow__node')) return
  emit('pane-dblclick', e)
}

function onConnect(params) {
  emit('connect', {
    source: params.source,
    target: params.target,
    sourcePort: params.sourceHandle || 'out',
    targetPort: params.targetHandle || 'in'
  })
}

function onEdgeClick({ edge }) {
  emit('select-edge', edge.id, edge.data?.raw)
}

function onNodeDragStop({ nodes: moved }) {
  if (!moved?.length) return
  emit('nodes-moved', moved.map(n => ({
    node_id: n.id,
    x: Math.round(n.position.x),
    y: Math.round(n.position.y)
  })))
}

function onViewportChange(viewport) {
  emit('viewport', {
    zoom: Math.round((viewport.zoom || 1) * 100),
    x: viewport.x || 0,
    y: viewport.y || 0
  })
}

function screenToCanvas(clientX, clientY) {
  return screenToFlowCoordinate({ x: clientX, y: clientY })
}

defineExpose({
  zoomIn: () => zoomIn(),
  zoomOut: () => zoomOut(),
  resetZoom: () => setViewport({ x: 40, y: 40, zoom: 0.85 }),
  fitView: () => fitView({ padding: 0.2 }),
  getViewport,
  setViewport,
  screenToCanvas
})
</script>

<style scoped>
.vue-flow-stage {
  position: absolute;
  inset: 0;
  background: #0f172a;
}
.vue-flow-stage :deep(.vue-flow) {
  width: 100%;
  height: 100%;
}
.vue-flow-stage :deep(.vue-flow__minimap) {
  background: rgba(15, 23, 42, .92);
  border: 1px solid #334155;
  border-radius: 8px;
  margin: 12px;
}
.vue-flow-stage :deep(.vue-flow__controls) {
  box-shadow: none;
  border: 1px solid #334155;
  border-radius: 8px;
  overflow: hidden;
  margin: 12px;
  display: flex;
  flex-direction: column;
}
.vue-flow-stage :deep(.vue-flow__controls-button) {
  background: #1e293b !important;
  border-bottom: 1px solid #334155 !important;
  fill: #e2e8f0 !important;
  width: 18px;
  height: 18px;
}
.vue-flow-stage :deep(.vue-flow__controls-button:hover) {
  background: #334155 !important;
}
.vue-flow-stage :deep(.vue-flow__edge-path) {
  stroke: #60a5fa;
  stroke-width: 2;
}
.vue-flow-stage :deep(.vue-flow__edge.selected .vue-flow__edge-path) {
  stroke: #fbbf24;
}
.vue-flow-stage :deep(.vue-flow__handle) {
  pointer-events: all;
}
.vue-flow-stage :deep(.vue-flow__node) {
  overflow: visible;
}
.vue-flow-stage :deep(.vue-flow__connection-path) {
  stroke: #60a5fa;
  stroke-width: 2;
}
</style>
