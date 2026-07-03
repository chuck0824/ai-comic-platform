/**
 * Pure functions for asset-history URL state, defaults, and card projections.
 */
export const ASSET_TYPE_LABELS = {
  CHECKPOINT: '底模', LORA: 'LoRA', STYLE_PACK: '风格包', PROMPT: '提示词',
  CHARACTER: '角色', SCENE: '场景', PROP: '道具', STORYBOARD: '分镜',
  VOICE: '配音', MUSIC: '音乐', OTHER: '其他'
}

export const STATUS_LABELS = {
  PENDING: '排队中', RUNNING: '生成中', SUCCEEDED: '已完成',
  FAILED: '失败', CANCELED: '已取消',
  ACTIVE: '活跃', ARCHIVED: '已归档', TRASHED: '回收站'
}

export const COLLECTION_LABELS = {
  UNFILED: '未归档', FAVORITES: '收藏', PUBLISHED: '已发布', TRASH: '回收站'
}

const DEFAULTS = { scope: 'mine', page: 1, pageSize: 24, statuses: [], view: 'grid' }

/**
 * Parse URL query params into workbench state.
 */
export function parseAssetHistoryQuery(query) {
  return {
    scope: query.scope || DEFAULTS.scope,
    projectUuid: query.project_uuid || '',
    collection: query.collection || '',
    assetType: query.asset_type || '',
    statuses: query.status ? query.status.split(',') : DEFAULTS.statuses,
    mediaType: query.media_type || '',
    keyword: query.keyword || '',
    sort: query.sort || 'created_at:desc',
    page: Math.max(1, Number(query.page) || DEFAULTS.page),
    pageSize: [24, 48, 96].includes(Number(query.pageSize)) ? Number(query.pageSize) : DEFAULTS.pageSize,
    view: query.view || DEFAULTS.view,
    recordKind: query.record_kind || '',
    recordUuid: query.record_uuid || ''
  }
}

/**
 * Serialize workbench state back to URL query (omit defaults).
 */
export function serializeAssetHistoryState(state) {
  const q = {}
  if (state.scope && state.scope !== 'mine') q.scope = state.scope
  if (state.projectUuid) q.project_uuid = state.projectUuid
  if (state.collection) q.collection = state.collection
  if (state.assetType) q.asset_type = state.assetType
  if (state.statuses && state.statuses.length > 0) q.status = state.statuses.join(',')
  if (state.mediaType) q.media_type = state.mediaType
  if (state.keyword) q.keyword = state.keyword
  if (state.sort && state.sort !== 'created_at:desc') q.sort = state.sort
  if (state.page > 1) q.page = String(state.page)
  if (state.pageSize !== 24) q.page_size = String(state.pageSize)
  if (state.view && state.view !== 'grid') q.view = state.view
  if (state.recordKind) q.record_kind = state.recordKind
  if (state.recordUuid) q.record_uuid = state.recordUuid
  return q
}

/**
 * Map a raw API record into a card view-model, using allowed_actions only.
 */
export function mapRecordCard(record) {
  const actions = record.allowedActions || []
  return {
    id: record.recordId,
    kind: record.recordKind,
    name: record.name || '未命名',
    assetType: record.assetType || 'OTHER',
    assetTypeLabel: ASSET_TYPE_LABELS[record.assetType] || record.assetType,
    mediaType: record.mediaType,
    status: record.status,
    statusLabel: STATUS_LABELS[record.status?.toUpperCase()] || record.status,
    previewUrl: record.previewUrl,
    modelId: record.modelId,
    progress: record.progress,
    creditCost: record.creditCost,
    errorCode: record.errorCode,
    errorSummary: record.errorSummary,
    fileSize: record.fileSize,
    width: record.width,
    height: record.height,
    durationMs: record.durationMs,
    favorite: record.favorite,
    published: record.published,
    projectName: record.projectName,
    createdAt: record.createdAt,
    canPreview: actions.includes('PREVIEW'),
    canEdit: actions.includes('EDIT'),
    canFavorite: actions.includes('FAVORITE'),
    canDownload: actions.includes('DOWNLOAD'),
    canSendToCanvas: actions.includes('SEND_TO_CANVAS'),
    canRegenerate: actions.includes('REGENERATE'),
    canPublish: actions.includes('PUBLISH'),
    canTrash: actions.includes('TRASH'),
    canRestore: actions.includes('RESTORE'),
    canCancelTask: actions.includes('CANCEL_TASK'),
    canRetry: actions.includes('RETRY_TASK')
  }
}
