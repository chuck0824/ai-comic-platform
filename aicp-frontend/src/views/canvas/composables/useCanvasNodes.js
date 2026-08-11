import { ref, computed } from 'vue'
import { canvasApi } from '@/api/canvas'

/**
 * 画布节点 CRUD 操作
 * 管理节点的增删改查、连线、分组、工作流
 */
export function useCanvasNodes(projectId) {
  const nodes = ref([])
  const connections = ref([])
  const workflows = ref([])
  const shots = ref([])
  const timeline = ref(null)
  const loading = ref(false)
  const localMode = ref(false)

  const NODE_TYPES = [
    { type: 'text', icon: 'Document', label: '文本', desc: '手动输入或大语言模型生成', group: '基础节点' },
    { type: 'image', icon: 'Picture', label: '图片', desc: '上传图片或图像模型生成', group: '基础节点' },
    { type: 'video', icon: 'VideoCamera', label: '视频', desc: '上传视频或视频模型生成', group: '基础节点' },
    { type: 'audio', icon: 'Headset', label: '音频', desc: '上传音频、音乐、音效或TTS', group: '基础节点' },
    { type: 'script', icon: 'Film', label: '脚本 new', desc: '剧本拆解、资产管理、批量生图/视频', group: '基础节点' },
    { type: 'director', icon: 'VideoCameraFilled', label: '导演台', desc: '轻量3D构图、机位截图、发送到画布', group: '画布工具' }
  ]

  const SLASH_COMMANDS = [
    '图像编辑', '多图参考融合', '全景模式', '智能打光',
    '宫格拆分', '镜像翻转', '旋转', '分镜组',
    '视频高清', '视频解析', '分离音视频'
  ]

  // 按类型分组
  const nodesByType = computed(() => {
    const grouped = {}
    nodes.value.forEach(n => {
      const t = n.type || 'unknown'
      if (!grouped[t]) grouped[t] = []
      grouped[t].push(n)
    })
    return grouped
  })

  const scriptNodes = computed(() => nodes.value.filter(n => n.type === 'script'))
  const imageNodes = computed(() => nodes.value.filter(n => n.type === 'image'))
  const videoNodes = computed(() => nodes.value.filter(n => n.type === 'video'))

  // === API 调用 ===
  async function loadNodes() {
    loading.value = true
    try {
      const res = await canvasApi.getNodes(projectId.value)
      nodes.value = res.data.nodes || []
      connections.value = res.data.connections || []
      localMode.value = false
    } catch (e) {
      console.error('加载节点失败', e)
      localMode.value = false
      nodes.value = []
      connections.value = []
    } finally {
      loading.value = false
    }
  }

  async function addNode(type, x = 80, y = 80, data = {}, layout = {}) {
    if (localMode.value) return addLocalNode(type, x, y, data, layout)
    const res = await canvasApi.createNode(projectId.value, { type, x, y, data, ...layout })
    if (res.data) {
      nodes.value.push(res.data)
      return res.data
    }
  }

  async function updateNode(nodeId, updates) {
    if (localMode.value) {
      updateLocalNode(nodeId, updates)
      return
    }
    await canvasApi.updateNode(projectId.value, nodeId, updates)
    const idx = nodes.value.findIndex(n => String(n.id) === String(nodeId) || n.uuid === nodeId)
    if (idx >= 0) {
      Object.assign(nodes.value[idx], updates)
      if (updates.data) nodes.value[idx].input_data = JSON.stringify(updates.data)
    }
  }

  async function updateNodePositions(positions = []) {
    if (!Array.isArray(positions) || !positions.length) return
    if (localMode.value) {
      positions.forEach(pos => updateLocalNode(pos.node_id, { x: pos.x, y: pos.y }))
      saveLocalCanvas()
      return
    }
    await canvasApi.updateNodePositions(projectId.value, { positions })
    positions.forEach(pos => {
      const node = nodes.value.find(n => String(n.id) === String(pos.node_id) || n.uuid === pos.node_id)
      if (!node) return
      node.x = pos.x
      node.y = pos.y
    })
  }

  async function deleteNode(nodeId) {
    if (localMode.value) {
      deleteLocalNode(nodeId)
      return
    }
    await canvasApi.deleteNode(projectId.value, nodeId)
    nodes.value = nodes.value.filter(n => String(n.id) !== String(nodeId) && n.uuid !== nodeId)
    connections.value = connections.value.filter(c =>
      String(c.source_node_id || c.sourceNodeId || c.source) !== String(nodeId) &&
      String(c.target_node_id || c.targetNodeId || c.target) !== String(nodeId))
  }

  async function duplicateNode(nodeId) {
    if (localMode.value) return duplicateLocalNode(nodeId)
    const res = await canvasApi.duplicateNode(projectId.value, nodeId)
    if (res.data) {
      await loadNodes() // 全量刷新以获取服务端生成的 ID/timestamp 等字段
    }
    return res.data
  }

  async function connectNodes(sourceNodeId, targetNodeId, sourcePort = 'out', targetPort = 'in') {
    if (localMode.value) return connectLocalNodes(sourceNodeId, targetNodeId, sourcePort, targetPort)
    const res = await canvasApi.connectNodes(projectId.value, {
      source_node_id: sourceNodeId,
      target_node_id: targetNodeId,
      source_port: sourcePort,
      target_port: targetPort
    })
    if (res.data) connections.value.push(res.data)
    return res.data
  }

  async function deleteConnection(connId) {
    if (localMode.value) {
      connections.value = connections.value.filter(c => String(c.id) !== String(connId) && c.uuid !== connId)
      saveLocalCanvas()
      return
    }
    await canvasApi.deleteConnection(projectId.value, connId)
    connections.value = connections.value.filter(c => String(c.id) !== String(connId) && c.uuid !== connId)
  }

  async function groupNodes(nodeIds, name) {
    if (localMode.value) {
      const groupId = `local_group_${Date.now()}`
      nodeIds.forEach(nodeId => updateLocalNode(nodeId, { groupId }))
      return {
        uuid: groupId,
        name,
        node_ids: nodeIds
      }
    }
    const res = await canvasApi.groupNodes(projectId.value, { node_ids: nodeIds, name })
    const group = res.data || {}
    const groupId = group.id || group.uuid
    if (groupId) {
      nodeIds.forEach(nodeId => {
        const node = nodes.value.find(n => String(n.id) === String(nodeId) || n.uuid === nodeId)
        if (node) node.groupId = groupId
      })
    }
    return res.data
  }

  // === Workflow ===
  async function loadWorkflows() {
    if (localMode.value) {
      workflows.value = []
      return
    }
    try {
      const res = await canvasApi.getWorkflows(projectId.value)
      workflows.value = res.data || []
    } catch (e) {
      console.error('加载工作流失败', e)
      workflows.value = []
    }
  }

  async function createWorkflow(name, description, nodeIds) {
    if (localMode.value) {
      const workflow = {
        uuid: `local_workflow_${Date.now()}`,
        name,
        description,
        node_ids: nodeIds,
        status: 'ready'
      }
      workflows.value.push(workflow)
      return workflow
    }
    const res = await canvasApi.createWorkflow(projectId.value, { name, description, node_ids: nodeIds })
    if (res.data) workflows.value.push(res.data)
    return res.data
  }

  async function executeWorkflow(wfId) {
    if (localMode.value) {
      return { data: { task_id: `local_workflow_task_${Date.now()}`, workflow_id: wfId, status: 'completed' } }
    }
    return await canvasApi.executeWorkflow(projectId.value, wfId)
  }

  // === Shots ===
  async function loadShots() {
    if (localMode.value) {
      shots.value = []
      return
    }
    const res = await canvasApi.getShots(projectId.value)
    shots.value = res.data || []
  }

  async function updateShot(shotId, data) {
    if (localMode.value) return
    await canvasApi.updateShot(projectId.value, shotId, data)
  }

  async function reorderShots(shotIds) {
    if (localMode.value) return
    await canvasApi.reorderShots(projectId.value, { shot_ids: shotIds })
  }

  // === Timeline ===
  async function loadTimeline() {
    if (localMode.value) {
      timeline.value = {
        video_track: nodes.value.filter(n => n.type === 'video').map((n, i) => ({
          id: n.uuid,
          name: n.name,
          start: i * 3,
          duration: 3
        })),
        audio_track: [],
        subtitle_track: [],
        bgm_track: [],
        sfx_track: [],
        effect_track: [],
        overlay_track: []
      }
      return
    }
    const res = await canvasApi.getFullTimeline(projectId.value)
    timeline.value = res.data || {}
  }

  async function saveTimeline(data) {
    if (localMode.value) {
      timeline.value = data
      return
    }
    await canvasApi.updateFullTimeline(projectId.value, data)
    timeline.value = data
  }

  // === Export ===
  async function exportVideo(params = {}) {
    if (localMode.value) {
      return { data: { task_id: 'local_export_' + Date.now(), status: 'queued', params } }
    }
    return await canvasApi.exportVideo(projectId.value, params)
  }

  function localKey() {
    return `aicp:canvas:${projectId.value}`
  }

  function saveLocalCanvas() {
    try {
      const data = JSON.stringify({
        nodes: nodes.value,
        connections: connections.value
      })
      localStorage.setItem(localKey(), data)
    } catch (e) {
      if (e.name === 'QuotaExceededError' || e.toString().includes('quota')) {
        console.error('localStorage 容量不足，画布数据保存失败。请清理浏览器存储或减少节点数量。', e)
        // 尝试清理旧数据后重试一次
        try {
          // 移除最旧的 canvas 备份
          const keys = Object.keys(localStorage).filter(k => k.startsWith('aicp:canvas:'))
          if (keys.length > 1) {
            localStorage.removeItem(keys.sort()[0])
            localStorage.setItem(localKey(), JSON.stringify({ nodes: nodes.value, connections: connections.value }))
          }
        } catch (retryErr) {
          console.error('重试保存失败', retryErr)
        }
      } else {
        console.error('画布本地保存失败', e)
      }
    }
  }

  function loadLocalCanvas() {
    try {
      const saved = JSON.parse(localStorage.getItem(localKey()) || '{}')
      nodes.value = Array.isArray(saved.nodes) ? saved.nodes : []
      connections.value = Array.isArray(saved.connections) ? saved.connections : []
    } catch {
      nodes.value = []
      connections.value = []
    }
  }

  function addLocalNode(type, x, y, data = {}, layout = {}) {
    const node = {
      uuid: `local_node_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`,
      type,
      name: NODE_TYPES.find(n => n.type === type)?.label || '节点',
      x,
      y,
      width: layout.width || (type === 'script' ? 340 : type === 'director' ? 280 : type === 'text' ? 560 : ['image', 'video', 'audio'].includes(type) ? 520 : 200),
      height: layout.height || (type === 'script' ? 280 : type === 'director' ? 220 : type === 'text' ? 520 : ['image', 'video'].includes(type) ? 360 : type === 'audio' ? 300 : 180),
      input_data: JSON.stringify(data),
      status: 'ready'
    }
    nodes.value.push(node)
    saveLocalCanvas()
    return node
  }

  function updateLocalNode(nodeId, updates) {
    const idx = nodes.value.findIndex(n => String(n.id) === String(nodeId) || n.uuid === nodeId)
    if (idx < 0) return
    Object.assign(nodes.value[idx], updates)
    if (updates.data) nodes.value[idx].input_data = JSON.stringify(updates.data)
    saveLocalCanvas()
  }

  function deleteLocalNode(nodeId) {
    nodes.value = nodes.value.filter(n => String(n.id) !== String(nodeId) && n.uuid !== nodeId)
    connections.value = connections.value.filter(c => c.source_node_id !== nodeId && c.target_node_id !== nodeId)
    saveLocalCanvas()
  }

  function duplicateLocalNode(nodeId) {
    const source = nodes.value.find(n => String(n.id) === String(nodeId) || n.uuid === nodeId)
    if (!source) return null
    const copy = {
      ...source,
      uuid: `local_node_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`,
      name: `${source.name || '节点'} 副本`,
      x: (source.x || 0) + 40,
      y: (source.y || 0) + 40
    }
    nodes.value.push(copy)
    saveLocalCanvas()
    return copy
  }

  function connectLocalNodes(sourceNodeId, targetNodeId, sourcePort = 'out', targetPort = 'in') {
    const edge = {
      uuid: `local_conn_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`,
      source_node_id: sourceNodeId,
      target_node_id: targetNodeId,
      source_port: sourcePort,
      target_port: targetPort,
      edge_type: 'data'
    }
    connections.value.push(edge)
    saveLocalCanvas()
    return edge
  }

  async function getExportStatus(taskId) {
    return await canvasApi.getExportStatus(taskId)
  }

  return {
    nodes, connections, workflows, shots, timeline, loading, localMode,
    NODE_TYPES, SLASH_COMMANDS,
    nodesByType, scriptNodes, imageNodes, videoNodes,
    loadNodes, addNode, updateNode, updateNodePositions, deleteNode, duplicateNode,
    connectNodes, deleteConnection, groupNodes,
    loadWorkflows, createWorkflow, executeWorkflow,
    loadShots, updateShot, reorderShots,
    loadTimeline, saveTimeline,
    exportVideo, getExportStatus
  }
}
