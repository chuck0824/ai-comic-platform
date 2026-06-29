import request from './request'

export const contentProjectApi = {
  list: (params) => request.get('/content-projects', { params }),
  create: (data) => request.post('/content-projects', data),
  get: (id) => request.get(`/content-projects/${id}`),
  update: (id, data) => request.patch(`/content-projects/${id}`, data),
  workflow: (id) => request.get(`/content-projects/${id}/workflow`),
  saveResume: (id, data) => request.put(`/content-projects/${id}/resume-position`, data),
  addParameters: (id, data) => request.post(`/content-projects/${id}/parameter-versions`, data),
  setStoryboardIntent: (id, intent, sourceVersionId) => request.put(
    `/content-projects/${id}/storyboard-intent`,
    { intent, source_version_id: sourceVersionId }
  ),
  // Members
  listMembers: (id) => request.get(`/content-projects/${id}/members`),
  addMember: (id, data) => request.post(`/content-projects/${id}/members`, data),
  updateMember: (id, memberId, data) => request.patch(`/content-projects/${id}/members/${memberId}`, data),
  removeMember: (id, memberId) => request.delete(`/content-projects/${id}/members/${memberId}`),
  // Content units (under project)
  listUnits: (projectId) => request.get(`/content-projects/${projectId}/content-units`),
  createUnit: (projectId, data) => request.post(`/content-projects/${projectId}/content-units`, data),
  // Content unit operations
  getDraft: (unitId) => request.get(`/content-units/${unitId}/draft`),
  saveDraft: (unitId, data) => request.put(`/content-units/${unitId}/draft`, data),
  listVersions: (unitId) => request.get(`/content-units/${unitId}/versions`),
  createVersion: (unitId, data) => request.post(`/content-units/${unitId}/versions`, data),
  restoreVersion: (unitId, versionId) => request.post(`/content-units/${unitId}/versions/${versionId}/restore`),
  // M1: Three-Agent Review
  reviewUnit: (unitId) => request.post(`/content-units/${unitId}/review`),
  // M1: Storyboard
  generateStoryboard: (projectId, contentUnitId) =>
    request.post(`/content-projects/${projectId}/storyboard/generate`, { content_unit_id: contentUnitId }),
  listStoryboardMasters: (projectId) => request.get(`/content-projects/${projectId}/storyboard`),
  getStoryboardMaster: (projectId, masterId) => request.get(`/content-projects/${projectId}/storyboard/${masterId}`),
  listStoryboardScenes: (projectId, masterId) => request.get(`/content-projects/${projectId}/storyboard/${masterId}/scenes`),
  listStoryboardShots: (projectId, masterId) => request.get(`/content-projects/${projectId}/storyboard/${masterId}/shots`),
  lockStoryboardMaster: (projectId, masterId) => request.post(`/content-projects/${projectId}/storyboard/${masterId}/lock`),
  // Legacy
  backfillLegacy: () => request.post('/content-projects/backfill-legacy')
}
