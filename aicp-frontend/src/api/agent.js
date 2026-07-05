import request from './request'

export const agentApi = {
  // 会话
  createSession: (data) => request.post('/agent/sessions', data),
  getSession: (id) => request.get(`/agent/sessions/${id}`),
  sendMessage: (id, data) => request.post(`/agent/sessions/${id}/messages`, data),
  getExecutions: (sessionId) => request.get(`/agent/sessions/${sessionId}/executions`),

  // Skill
  getSkills: (params) => request.get('/skills', { params }),
  getSkill: (id) => request.get(`/skills/${id}`),
  createSkill: (data) => request.post('/skills', data),
  updateSkill: (id, data) => request.put(`/skills/${id}`, data),
  deleteSkill: (id) => request.delete(`/skills/${id}`),
  executeSkill: (id, data) => request.post(`/skills/${id}/execute`, data),

  // 编排
  orchestrate: (data) => request.post('/agent/orchestrate', data),
  orchestrateCanvas: (projectId, data) => request.post('/agent/orchestrate/canvas', { project_id: projectId, ...data }),

  // ============================================================
  // Agent 配置中心 (M1)
  // ============================================================

  // Blueprint
  getBlueprints: () => request.get('/agent/blueprints'),
  getBlueprint: (id) => request.get(`/agent/blueprints/${id}`),

  // 用户 Agent 定义
  getDefinitions: (params) => request.get('/agent/definitions', { params }),
  getDefinition: (id) => request.get(`/agent/definitions/${id}`),
  createDefinition: (data) => request.post('/agent/definitions', data),
  updateDefinition: (id, data) => request.patch(`/agent/definitions/${id}`, data),
  copyDefinition: (id) => request.post(`/agent/definitions/${id}/copies`),
  archiveDefinition: (id) => request.post(`/agent/definitions/${id}/archive`),

  // 版本
  getVersions: (id) => request.get(`/agent/definitions/${id}/versions`),
  createDraft: (id) => request.post(`/agent/definitions/${id}/drafts`),
  getVersion: (id) => request.get(`/agent/versions/${id}`),
  updateVersion: (id, data) => request.put(`/agent/versions/${id}`, data),
  validateVersion: (id) => request.post(`/agent/versions/${id}/validate`),
  publishVersion: (id, data) => request.post(`/agent/versions/${id}/publish`, data),
  activateVersion: (id) => request.post(`/agent/versions/${id}/activate`),

  // 试跑
  testVersion: (id, data) => request.post(`/agent/versions/${id}/test-runs`, data),
  getTestRun: (id) => request.get(`/agent/test-runs/${id}`),

  // 绑定
  getUserBindings: () => request.get('/agent/user-bindings'),
  setUserBinding: (roleType, data) => request.put(`/agent/user-bindings/${roleType}`, data),
  deleteUserBinding: (roleType) => request.delete(`/agent/user-bindings/${roleType}`),

  // 解析预览
  resolvePreview: (data) => request.post('/agent/resolve-preview', data),
  getSnapshot: (id) => request.get(`/agent/execution-snapshots/${id}`)
}
