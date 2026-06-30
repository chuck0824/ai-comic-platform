/**
 * Pure query and permission helpers for AI Asset Market.
 * No side effects — all functions are deterministic given their inputs.
 */

/** Map asset type enum to Chinese label */
export function assetTypeLabel(type) {
  const map = {
    CHECKPOINT: 'Checkpoint 模型',
    LORA: 'LoRA 模型',
    STYLE_PACK: '风格包',
    CHARACTER: '角色资产',
    SCENE: '场景资产',
    PROMPT: '提示词模板'
  }
  return map[type] ?? type
}

/** Category tab state — voice/BGM are disabled in this release */
export function categoryState(type) {
  const disabled = type === 'voice' || type === 'bgm'
  return {
    key: type,
    label: disabled ? '即将开放' : assetTypeLabel(type),
    disabled
  }
}

/** All active category keys */
export const ACTIVE_CATEGORIES = ['STYLE_PACK', 'CHARACTER', 'SCENE', 'PROMPT']
export const DISABLED_CATEGORIES = ['voice', 'bgm']

/** Build listing query params, dropping empty values and enforcing page size limits */
export function toListingParams({ keyword, type, sort, page, page_size }) {
  const params = {}
  if (keyword) params.keyword = keyword
  if (type) params.type = type
  if (sort && sort !== 'latest') params.sort = sort
  params.page = page || 1
  params.page_size = Math.min(page_size || 20, 50)
  return params
}

/** Check if the user has review (approval) permission */
export function canReview(permissions) {
  return Array.isArray(permissions) && permissions.includes('asset.publish.approve')
}

/** Check if the user can publish directly (personal workspace) */
export function canPublishDirectly(workspaceType, permissions) {
  return workspaceType === 'personal' && Array.isArray(permissions) && permissions.includes('asset.manage')
}

/** Check if the user can request enterprise publish */
export function canRequestPublish(workspaceType, permissions) {
  return workspaceType === 'enterprise' && Array.isArray(permissions) && permissions.includes('asset.publish.request')
}

/** Workspace type label */
export function workspaceLabel(type) {
  return type === 'personal' ? '个人空间' : type === 'enterprise' ? '企业空间' : type
}

/** Source type label */
export function sourceTypeLabel(type) {
  const map = {
    CREATED: '我创建的',
    MARKET_CLAIMED: '市场领取',
    PROJECT_GENERATED: '项目生成',
    IMPORTED: '导入'
  }
  return map[type] ?? type
}

/** Status label for publish requests */
export function publishStatusLabel(status) {
  const map = { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已撤回' }
  return map[status] ?? status
}

/** Status tag type for Element Plus el-tag */
export function publishStatusTagType(status) {
  const map = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'info' }
  return map[status] ?? 'info'
}
