import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 画布全局状态管理
 * 管理缩放、平移、选中节点、画布尺寸、小地图等
 */
export function useCanvasState() {
  const route = useRoute()

  // 项目
  const projectId = ref(route.params.projectId || 'canvas_a1b2c3')
  const projectName = ref('未命名画布项目')
  const projectStatus = ref('editing')

  // 缩放 & 平移
  const zoomLevel = ref(100)
  const panOffset = ref({ x: 0, y: 0 })
  const canvasSize = ref({ width: 4000, height: 3000 })

  // 选中
  const selectedNodeId = ref(null)
  const selectedNodes = ref([])

  // 左侧面板
  const activeLeftTab = ref('add')

  // 右键菜单
  const contextMenu = ref({ visible: false, x: 0, y: 0, nodeId: null })

  // 全屏编辑器
  const fullscreenEditor = ref(null) // 'shotTable' | 'timeline' | null

  // 自动保存
  const lastSaved = ref(new Date().toLocaleTimeString())
  const isDirty = ref(false)

  // 连接拖拽
  const draggingConnection = ref(null) // { sourceNodeId, sourcePort, x, y }

  // 计算属性
  const zoomPercent = computed(() => zoomLevel.value + '%')
  const canvasTransform = computed(() =>
    `translate(${panOffset.value.x}px, ${panOffset.value.y}px) scale(${zoomLevel.value / 100})`)

  // 方法
  function zoomIn() {
    zoomLevel.value = Math.min(200, zoomLevel.value + 10)
  }

  function zoomOut() {
    zoomLevel.value = Math.max(20, zoomLevel.value - 10)
  }

  function resetZoom() {
    zoomLevel.value = 100
    panOffset.value = { x: 0, y: 0 }
  }

  function selectNode(nodeId) {
    selectedNodeId.value = nodeId
    if (nodeId && !selectedNodes.value.includes(nodeId)) {
      selectedNodes.value.push(nodeId)
    }
  }

  function deselectAll() {
    selectedNodeId.value = null
    selectedNodes.value = []
  }

  function openContextMenu(e, nodeId) {
    contextMenu.value = {
      visible: true,
      x: e.clientX,
      y: e.clientY,
      nodeId
    }
  }

  function closeContextMenu() {
    contextMenu.value = { visible: false, x: 0, y: 0, nodeId: null }
  }

  function openFullscreenEditor(type) {
    fullscreenEditor.value = type
  }

  function closeFullscreenEditor() {
    fullscreenEditor.value = null
  }

  function markSaved() {
    lastSaved.value = new Date().toLocaleTimeString()
    isDirty.value = false
  }

  function markDirty() {
    isDirty.value = true
  }

  // 监听 projectId 变化
  watch(() => route.params.projectId, (newId) => {
    if (newId) projectId.value = newId
  })

  return {
    projectId, projectName, projectStatus,
    zoomLevel, panOffset, canvasSize,
    selectedNodeId, selectedNodes,
    activeLeftTab,
    contextMenu,
    fullscreenEditor,
    lastSaved, isDirty,
    draggingConnection,
    zoomPercent, canvasTransform,
    zoomIn, zoomOut, resetZoom,
    selectNode, deselectAll,
    openContextMenu, closeContextMenu,
    openFullscreenEditor, closeFullscreenEditor,
    markSaved, markDirty
  }
}
