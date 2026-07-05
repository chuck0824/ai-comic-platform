import request from './request'

export const canvasApi = {
  // ===== Management APIs =====
  listProjects: (params) => request.get('/canvas/projects', { params }),
  createProject: (data) => request.post('/canvas/projects', data),
  getProject: (id) => request.get(`/canvas/projects/${id}`),
  updateProject: (id, data) => request.put(`/canvas/projects/${id}`, data),
  deleteProject: (id) => request.delete(`/canvas/projects/${id}`),
  copyProject: (id, data) => request.post(`/canvas/projects/${id}/copy`, data),
  moveProject: (id, data) => request.post(`/canvas/projects/${id}/move`, data),
  archiveProject: (id) => request.post(`/canvas/projects/${id}/archive`),
  restoreProject: (id) => request.post(`/canvas/projects/${id}/restore`),
  getSourceDiff: (id) => request.get(`/canvas/projects/${id}/source-diff`),
  checkAdmission: (params) => request.get('/canvas/production-admission', { params }),
  getHomeContinueWorking: () => request.get('/home/continue-working'),
  listByContentProject: (projectId, params) => request.get(`/content-projects/${projectId}/canvas-projects`, { params }),

  // ===== Legacy =====
  importScript: (id, scriptId) => request.post(`/canvas/projects/${id}/import-script`, { script_id: scriptId }),
  getShots: (id) => request.get(`/canvas/projects/${id}/shots`),
  updateShot: (id, shotId, data) => request.put(`/canvas/projects/${id}/shots/${shotId}`, data),
  reorderShots: (id, data) => request.put(`/canvas/projects/${id}/shots/reorder`, data),
  updateKeyframe: (id, shotId, data) => request.put(`/canvas/projects/${id}/shots/${shotId}/keyframe`, data),
  inpaint: (id, shotId, data) => request.post(`/canvas/projects/${id}/shots/${shotId}/inpaint`, data),
  updateTimeline: (id, data) => request.put(`/canvas/projects/${id}/timeline/full`, data),
  generateDub: (id, data) => request.post(`/canvas/projects/${id}/timeline/dub`, data),
  exportVideo: (id, data) => request.post(`/canvas/projects/${id}/export`, data),
  getExportStatus: (taskId) => request.get(`/canvas/export/${taskId}`),
  getDownloadUrl: (taskId) => request.get(`/canvas/export/${taskId}/download`),

  createNode: (id, data) => request.post(`/canvas/projects/${id}/nodes`, data),
  getNodes: (id) => request.get(`/canvas/projects/${id}/nodes`),
  updateNode: (id, nodeId, data) => request.put(`/canvas/projects/${id}/nodes/${nodeId}`, data),
  deleteNode: (id, nodeId) => request.delete(`/canvas/projects/${id}/nodes/${nodeId}`),
  duplicateNode: (id, nodeId) => request.post(`/canvas/projects/${id}/nodes/${nodeId}/duplicate`),
  connectNodes: (id, data) => request.post(`/canvas/projects/${id}/nodes/connect`, data),
  deleteConnection: (id, connId) => request.delete(`/canvas/projects/${id}/connections/${connId}`),
  groupNodes: (id, data) => request.post(`/canvas/projects/${id}/groups`, data),
  createWorkflow: (id, data) => request.post(`/canvas/projects/${id}/workflows`, data),
  getWorkflows: (id) => request.get(`/canvas/projects/${id}/workflows`),
  applyWorkflow: (id, wfId) => request.post(`/canvas/projects/${id}/workflows/${wfId}/apply`),
  executeWorkflow: (id, wfId) => request.post(`/canvas/projects/${id}/workflows/${wfId}/execute-all`),
  generateStoryboardFromNode: (id, nodeId, data) => request.post(`/canvas/projects/${id}/nodes/${nodeId}/script/generate-storyboard`, data),
  batchGenerateImages: (id, nodeId, data) => request.post(`/canvas/projects/${id}/nodes/${nodeId}/script/batch-image`, data),
  batchGenerateVideos: (id, nodeId, data) => request.post(`/canvas/projects/${id}/nodes/${nodeId}/script/batch-video`, data),
  updateScriptCell: (id, nodeId, data) => request.put(`/canvas/projects/${id}/nodes/${nodeId}/script/cell`, data),
  runSlashCommand: (id, command, data) => request.post(`/canvas/projects/${id}/slash/${command}`, data),
  createDirectorDesk: (id, data) => request.post(`/canvas/projects/${id}/director-desk`, data),
  getDirectorDesk: (id, deskId) => request.get(`/canvas/projects/${id}/director-desk/${deskId}`),
  updateDirectorDesk: (id, deskId, data) => request.put(`/canvas/projects/${id}/director-desk/${deskId}`, data),
  uploadDirectorModel: (id, deskId, data) => request.post(`/canvas/projects/${id}/director-desk/${deskId}/assets/model`, data),
  captureDirectorDesk: (id, deskId, data = {}) => request.post(`/canvas/projects/${id}/director-desk/${deskId}/capture`, data),
  sendDirectorScreenshotToCanvas: (id, deskId, screenshotId, data = {}) =>
    request.post(`/canvas/projects/${id}/director-desk/${deskId}/screenshots/${screenshotId}/send-to-canvas`, data),
  aiImportDirectorDesk: (id, deskId, data) => request.post(`/canvas/projects/${id}/director-desk/${deskId}/ai-import`, data),
  getFullTimeline: (id) => request.get(`/canvas/projects/${id}/timeline/full`),
  updateFullTimeline: (id, data) => request.put(`/canvas/projects/${id}/timeline/full`, data),
  generateMultimodalVideo: (id, shotId, data) => request.post(`/canvas/projects/${id}/shots/${shotId}/generate-multimodal`, data),

  // === V1.5 新增 ===
  updateNodePositions: (id, data) => request.patch(`/canvas/projects/${id}/nodes/positions`, data),
  dropMaterial: (id, data) => request.post(`/canvas/projects/${id}/assets/drop`, data),
  createGroup: (id, data) => request.post(`/canvas/projects/${id}/groups`, data),

  // ===== R1: Canvas 生产内核 =====
  /** 获取迁移报告 */
  getMigrationReport: (id) => request.get(`/canvas/projects/${id}/migration-report`),
  /** 执行升级 */
  upgradeProject: (id, idempotencyKey) =>
    request.post(`/canvas/projects/${id}/upgrade`, null, { headers: { 'Idempotency-Key': idempotencyKey } }),
  /** 创建 ShotWorkUnit */
  createShotUnit: (projectId, data) => request.post(`/canvas/projects/${projectId}/shot-units`, data),
  /** 更新 ShotWorkUnit（乐观锁） */
  updateShotUnit: (projectId, unitId, expectedVersion, data) =>
    request.patch(`/canvas/projects/${projectId}/shot-units/${unitId}`, data, { headers: { 'If-Match': expectedVersion } }),
  /** 端口连接校验 */
  validatePort: (data) => request.post('/canvas/ports/validate', data),
  /** 节点候选列表 */
  listCandidates: (nodeId) => request.get(`/canvas/nodes/${nodeId}/candidates`),
  /** 选择候选 */
  selectCandidate: (nodeId, candidateId) =>
    request.put(`/canvas/nodes/${nodeId}/candidate-selection`, { candidateId }),
  /** 创建正式采用 */
  adoptShot: (projectId, unitId, data) =>
    request.post(`/canvas/projects/${projectId}/shot-units/${unitId}/adoptions`, data),

  // ===== R2: Director =====
  /** 获取导演场景 */
  getDirectorScene: (projectId, unitId) =>
    request.get(`/canvas/projects/${projectId}/shot-units/${unitId}/director-scene`),
  /** 获取导演草稿 */
  getDirectorDraft: (projectId, unitId) =>
    request.get(`/canvas/projects/${projectId}/shot-units/${unitId}/director-scene/draft`),
  /** 保存导演草稿（乐观锁） */
  saveDirectorDraft: (projectId, unitId, expectedVersion, document) =>
    request.put(`/canvas/projects/${projectId}/shot-units/${unitId}/director-scene/draft`, document,
      { headers: { 'If-Match': expectedVersion } }),
  /** 校验导演草稿 */
  validateDirectorScene: (projectId, unitId) =>
    request.post(`/canvas/projects/${projectId}/shot-units/${unitId}/director-scene/validate`),
  /** 冻结导演 revision */
  freezeDirectorRevision: (projectId, unitId, idempotencyKey) =>
    request.post(`/canvas/projects/${projectId}/shot-units/${unitId}/director-scene/revisions`, null,
      { headers: { 'Idempotency-Key': idempotencyKey } }),
  /** 获取导演 revision */
  getDirectorRevision: (projectId, unitId, revisionId) =>
    request.get(`/canvas/projects/${projectId}/shot-units/${unitId}/director-scene/revisions/${revisionId}`)
}

export const canvasAgentApi = {
  getModels: (params = {}) => request.get('/ai/models', { params }),
  planTextNode: (projectId, data) => request.post(`/canvas/projects/${projectId}/text-node-agent/plan`, data),
  applyTextNode: (projectId, data) => request.post(`/canvas/projects/${projectId}/text-node-agent/apply`, data)
}
