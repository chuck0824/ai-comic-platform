import request from './request'

export const storyboardV2Api = {
  // ===== Master =====
  create: (projectId, data) =>
    request.post(`/content-projects/${projectId}/storyboards`, data),
  list: (projectId) =>
    request.get(`/content-projects/${projectId}/storyboards`),
  get: (projectId, storyboardId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}`),

  // ===== Versions =====
  listVersions: (projectId, storyboardId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions`),
  getVersion: (projectId, storyboardId, versionId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}`),
  diffVersion: (projectId, storyboardId, versionId, againstId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/diff`, { params: { against: againstId } }),
  submitReview: (projectId, storyboardId, versionId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/submit-review`, data),
  lockVersion: (projectId, storyboardId, versionId, data, idempotencyKey) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/lock`, data, { headers: { 'Idempotency-Key': idempotencyKey } }),
  forkVersion: (projectId, storyboardId, versionId, idempotencyKey) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/fork`, {}, { headers: { 'Idempotency-Key': idempotencyKey } }),
  upgradeVersion: (projectId, storyboardId, versionId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/upgrade`, data),

  // ===== Scenes =====
  listScenes: (projectId, storyboardId, versionId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/scenes`),
  createScene: (projectId, storyboardId, versionId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/scenes`, data),
  patchScene: (projectId, storyboardId, versionId, sceneId, data) =>
    request.patch(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/scenes/${sceneId}`, data),
  deleteScene: (projectId, storyboardId, versionId, sceneId) =>
    request.delete(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/scenes/${sceneId}`),
  reorderScenes: (projectId, storyboardId, versionId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/scenes/reorder`, data),

  // ===== Shots =====
  listShots: (projectId, storyboardId, versionId, params = {}) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/shots`, { params }),
  getShot: (projectId, storyboardId, versionId, shotId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/shots/${shotId}`),
  createShot: (projectId, storyboardId, versionId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/shots`, data),
  patchShot: (projectId, storyboardId, versionId, shotId, data) =>
    request.patch(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/shots/${shotId}`, data),
  deleteShot: (projectId, storyboardId, versionId, shotId) =>
    request.delete(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/shots/${shotId}`),
  batchPatchShots: (projectId, storyboardId, versionId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/shots/batch`, data),
  reorderShots: (projectId, storyboardId, versionId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/shots/reorder`, data),
  splitShot: (projectId, storyboardId, versionId, shotId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/shots/${shotId}/split`, data),
  mergeShots: (projectId, storyboardId, versionId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/shots/merge`, data),
  copyShot: (projectId, storyboardId, versionId, shotId) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/shots/${shotId}/copy`),

  // ===== Professional Modules =====
  listEmotionSegments: (projectId, storyboardId, versionId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/emotion-segments`),
  replaceEmotionSegments: (projectId, storyboardId, versionId, data) =>
    request.put(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/emotion-segments`, data),
  listPromptTemplates: (projectId, storyboardId, versionId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/prompt-templates`),
  replacePromptTemplates: (projectId, storyboardId, versionId, data) =>
    request.put(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/prompt-templates`, data),
  listCreativeRules: (projectId, storyboardId, versionId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/creative-rules`),
  replaceCreativeRules: (projectId, storyboardId, versionId, data) =>
    request.put(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/creative-rules`, data),
  listCharacterVisuals: (projectId, storyboardId, versionId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/character-visuals`),
  replaceCharacterVisuals: (projectId, storyboardId, versionId, data) =>
    request.put(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/character-visuals`, data),
  listVisualBindings: (projectId, storyboardId, versionId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/visual-bindings`),
  replaceVisualBindings: (projectId, storyboardId, versionId, data) =>
    request.put(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/visual-bindings`, data),

  // ===== Review =====
  listReviewIssues: (projectId, storyboardId, versionId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/review-issues`),
  resolveIssue: (projectId, storyboardId, versionId, issueId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/review-issues/${issueId}/resolve`, data),
  runChecks: (projectId, storyboardId, versionId) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/jobs/check`),
  evaluateGate: (projectId, storyboardId, versionId) =>
    request.get(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/gate`),

  // ===== Jobs =====
  getJob: (jobId) =>
    request.get(`/storyboard-jobs/${jobId}`),
  getJobEvents: (jobId) =>
    request.get(`/storyboard-jobs/${jobId}/events`),
  importWorkbook: (projectId, storyboardId, versionId, formData) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/jobs/import`, formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  exportWorkbook: (projectId, storyboardId, versionId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/jobs/export`, data),
  createCanvasSnapshot: (projectId, storyboardId, versionId, data) =>
    request.post(`/content-projects/${projectId}/storyboards/${storyboardId}/versions/${versionId}/jobs/canvas-snapshot`, data),
}
