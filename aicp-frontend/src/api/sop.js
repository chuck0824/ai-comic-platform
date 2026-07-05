import request from './request'

export const sopApi = {
  listProjects: (params) => request.get('/sop/projects', { params }),

  getSummary: (projectId) => request.get(`/sop/projects/${projectId}/summary`),

  runCheck: (projectId, data) => request.post(`/sop/projects/${projectId}/checks`, data),

  listChecks: (projectId, params) => request.get(`/sop/projects/${projectId}/checks`, { params }),

  getCheckReport: (projectId, runId) => request.get(`/sop/projects/${projectId}/checks/${runId}`),

  listWorkOrders: (projectId, params) => request.get(`/sop/projects/${projectId}/work-orders`, { params }),

  createWorkOrder: (projectId, data) => request.post(`/sop/projects/${projectId}/work-orders`, data),

  transitionWorkOrder: (projectId, orderId, data) =>
    request.patch(`/sop/projects/${projectId}/work-orders/${orderId}`, data),

  reviewWorkOrder: (projectId, orderId, data) =>
    request.post(`/sop/projects/${projectId}/work-orders/${orderId}/review`, data),

  evaluateGate: (projectId, gateType, data) =>
    request.post(`/sop/projects/${projectId}/gates/${gateType}/evaluate`, data),

  executeFix: (projectId, resultId, data) =>
    request.post(`/sop/projects/${projectId}/fixes/${resultId}`, data),

  compatCheck: (data) => request.post('/sop/check/production-readiness', data),
}
