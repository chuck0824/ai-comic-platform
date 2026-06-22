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
  orchestrateCanvas: (projectId, data) => request.post('/agent/orchestrate/canvas', { project_id: projectId, ...data })
}
