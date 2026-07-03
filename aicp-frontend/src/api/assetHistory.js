import request from './request'

export const assetHistoryApi = {
  /** List content projects with asset counts for the sidebar tree. */
  listProjects: () => request.get('/assets/workbench/projects'),

  /** Unified task + asset query. */
  queryRecords: (params) => request.get('/assets/history/records', { params }),

  /** Detail for a single task or asset. */
  getDetail: (recordKind, recordUuid) =>
    request.get(`/assets/history/records/${recordKind}/${recordUuid}`),

  /** Edit asset metadata. */
  edit: (assetUuid, body) => request.patch(`/assets/${assetUuid}`, body),

  /** Toggle favorite on/off. */
  favorite: (assetUuid) => request.put(`/assets/${assetUuid}/favorite`),
  unfavorite: (assetUuid) => request.delete(`/assets/${assetUuid}/favorite`),

  /** Move asset to a different project. */
  move: (assetUuid, body) => request.post(`/assets/${assetUuid}/move`, body),

  /** Batch trash or restore. */
  batchOperate: (body) => request.post('/assets/batch', body),

  /** Soft-delete (trash) a single asset. */
  trash: (assetUuid) => request.delete(`/assets/${assetUuid}`),

  /** Restore from trash. */
  restore: (assetUuid) => request.post(`/assets/${assetUuid}/restore`),

  /** Get short-lived download URL. */
  getDownloadUrl: (assetUuid) => request.get(`/assets/${assetUuid}/download-url`),

  /** Regenerate with optional parameter patches. */
  regenerate: (assetUuid, body) => request.post(`/assets/${assetUuid}/regenerate`, body),

  /** Publish asset to marketplace. */
  publish: (assetUuid, body) => request.post(`/assets/${assetUuid}/publish`, body)
}
