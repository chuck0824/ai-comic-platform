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
  // M1: Storyboard — now delegates to V2 professional editor APIs
  generateStoryboard: (projectId, contentUnitId) =>
    request.post(`/content-projects/${projectId}/storyboards`, { contentUnitId, sourceContentVersionId: 1, title: '分镜草稿', purpose: 'default' }),
  listStoryboardMasters: (projectId) => request.get(`/content-projects/${projectId}/storyboards`),
  getStoryboardMaster: (projectId, masterId) => request.get(`/content-projects/${projectId}/storyboards/${masterId}`),
  listStoryboardScenes: (projectId, masterId) => request.get(`/content-projects/${projectId}/storyboards/${masterId}/versions/${masterId}/scenes`),
  listStoryboardShots: (projectId, masterId) => request.get(`/content-projects/${projectId}/storyboards/${masterId}/versions/${masterId}/shots`),
  lockStoryboardMaster: (projectId, masterId) => request.post(`/content-projects/${projectId}/storyboards/${masterId}/versions/${masterId}/lock`, { revision: 0 }),
  // M1: Upload
  uploadFile: (formData) => request.post('/content-projects/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  getUpload: (uploadId) => request.get(`/content-projects/upload/${uploadId}`),
  getUploadDownloadUrl: (uploadId) => request.get(`/content-projects/upload/${uploadId}/download-url`),
  aiExtractUpload: (uploadId) => request.post(`/content-projects/upload/${uploadId}/ai-extract`),
  confirmImport: (uploadId, projectId, chapters) => request.post(`/content-projects/upload/${uploadId}/confirm-import`, { project_id: projectId, chapters }),
  // M2: Batch, Hooks, Continuity
  batchGenerate: (projectId, unitIds, jobType = 'content_generate') =>
    request.post(`/content-projects/${projectId}/batch-generate`, { unit_ids: unitIds, job_type: jobType }),
  generateHooks: (projectId) => request.post(`/content-projects/${projectId}/generate-hooks`),
  hookSummary: (projectId) => request.get(`/content-projects/${projectId}/hook-summary`),
  getUnitHooks: (projectId, unitId) => request.get(`/content-projects/${projectId}/units/${unitId}/hooks`),
  captureSnapshots: (projectId) => request.post(`/content-projects/${projectId}/capture-snapshots`),
  continuityConflicts: (projectId) => request.get(`/content-projects/${projectId}/continuity-conflicts`),
  // M2: Adaptation + Promotion
  createAdaptation: (projectId, sourceUnitId, format = 'short_drama', multiEpisode = false) =>
    request.post(`/content-projects/${projectId}/adapt`, { source_unit_id: sourceUnitId, format, multi_episode: multiEpisode }),
  generatePromotion: (projectId, sourceUnitId) =>
    request.post(`/content-projects/${projectId}/promote`, { source_unit_id: sourceUnitId }),
  // Legacy
  backfillLegacy: () => request.post('/content-projects/backfill-legacy'),
  // M6: Work Editor
  getLegacyEditor: (scriptId) => request.get(`/content-projects/legacy-scripts/${scriptId}/editor`),
  getEditor: (projectId) => request.get(`/content-projects/${projectId}/editor`),
  updateTags: (projectId, data) => request.put(`/content-projects/${projectId}/tags`, data),
  updateProfile: (projectId, data) => request.patch(`/content-projects/${projectId}/profile`, data),
  // Settings
  listSettings: (projectId, params) => request.get(`/content-projects/${projectId}/settings`, { params }),
  createSetting: (projectId, data) => request.post(`/content-projects/${projectId}/settings`, data),
  getSetting: (projectId, settingId) => request.get(`/content-projects/${projectId}/settings/${settingId}`),
  updateSetting: (projectId, settingId, data) => request.patch(`/content-projects/${projectId}/settings/${settingId}`, data),
  archiveSetting: (projectId, settingId) => request.delete(`/content-projects/${projectId}/settings/${settingId}`),
  restoreSetting: (projectId, settingId) => request.post(`/content-projects/${projectId}/settings/${settingId}/restore`),
  copySetting: (projectId, settingId) => request.post(`/content-projects/${projectId}/settings/${settingId}/copy`),
  listSettingVersions: (projectId, settingId) => request.get(`/content-projects/${projectId}/settings/${settingId}/versions`),
  // AI Extraction
  createExtraction: (projectId, data) => request.post(`/content-projects/${projectId}/setting-extractions`, data),
  getExtraction: (projectId, batchId) => request.get(`/content-projects/${projectId}/setting-extractions/${batchId}`),
  saveDecisions: (projectId, batchId, data) => request.put(`/content-projects/${projectId}/setting-extractions/${batchId}/decisions`, data),
  applyExtraction: (projectId, batchId) => request.post(`/content-projects/${projectId}/setting-extractions/${batchId}/apply`),
  retryExtraction: (projectId, batchId) => request.post(`/content-projects/${projectId}/setting-extractions/${batchId}/retry`),
  // Warehouse queries
  recent: (limit = 5) => request.get('/content-projects/recent', { params: { limit } }),
  todos: () => request.get('/content-projects/todos'),
  summary: (id) => request.get(`/content-projects/${id}/summary`),
  resolveLegacy: (scriptId) => request.get(`/content-projects/legacy-scripts/${scriptId}/resolve`),
  // Lifecycle actions
  submitReview: (id, data) => request.post(`/content-projects/${id}/submit-review`, data),
  approve: (id, data) => request.post(`/content-projects/${id}/approve`, data),
  requestRevision: (id, data) => request.post(`/content-projects/${id}/request-revision`, data),
  lock: (id, data) => request.post(`/content-projects/${id}/lock`, data),
  archive: (id, data) => request.post(`/content-projects/${id}/archive`, data),
  restore: (id, data) => request.post(`/content-projects/${id}/restore`, data),
  duplicate: (id, data) => request.post(`/content-projects/${id}/duplicate`, data),
  moveToTrash: (id, data) => request.post(`/content-projects/${id}/trash`, data),

  // M7: Creative Bible
  getCreativeBible: (projectId) => request.get(`/content-projects/${projectId}/creative-bible`),
  getCreativeBibleHealth: (projectId) => request.get(`/content-projects/${projectId}/creative-bible/health`),
  createBibleDraft: (projectId, data) => request.post(`/content-projects/${projectId}/creative-bible/versions`, data),
  confirmBible: (projectId, versionId) => request.post(`/content-projects/${projectId}/creative-bible/versions/${versionId}/confirm`),
  listEcosystemRules: (projectId, versionId, params) => request.get(`/content-projects/${projectId}/creative-bible/versions/${versionId}/ecosystem-rules`, { params }),
  createEcosystemRule: (projectId, versionId, data) => request.post(`/content-projects/${projectId}/creative-bible/versions/${versionId}/ecosystem-rules`, data),
  updateEcosystemRule: (projectId, versionId, ruleId, data) => request.patch(`/content-projects/${projectId}/creative-bible/versions/${versionId}/ecosystem-rules/${ruleId}`, data),
  listWritingGuides: (projectId, versionId, params) => request.get(`/content-projects/${projectId}/creative-bible/versions/${versionId}/writing-guides`, { params }),
  saveWritingGuide: (projectId, versionId, data) => request.post(`/content-projects/${projectId}/creative-bible/versions/${versionId}/writing-guides`, data),
  resolveWritingGuide: (projectId, versionId, data) => request.post(`/content-projects/${projectId}/creative-bible/versions/${versionId}/writing-guides/resolve`, data)
}

export const tagDictionaryApi = {
  get: () => request.get('/tag-dictionary')
}
