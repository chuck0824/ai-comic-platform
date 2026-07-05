/**
 * Pure helpers for script warehouse views.
 * No side effects, no API calls — just data transforms and validation.
 */

export const STATUS_LABELS = {
  draft: '草稿',
  pending_review: '待审核',
  listed: '已上架',
  sold: '已售出',
  delisted: '已下架',
  purchased: '已购'
}

export const STATUS_TYPES = {
  draft: 'info',
  pending_review: 'warning',
  listed: 'success',
  sold: '',
  delisted: 'info',
  purchased: 'warning'
}

export const GENRE_LABELS = {
  '言情': '言情',
  '现实情感': '现实情感',
  '悬疑': '悬疑',
  '惊悚': '惊悚',
  '科幻': '科幻',
  '武侠': '武侠',
  '脑洞': '脑洞',
  '太空歌剧': '太空歌剧',
  '赛博朋克': '赛博朋克',
  '游戏': '游戏',
  '仙侠': '仙侠',
  '历史': '历史'
}

export const GENRE_OPTIONS = Object.keys(GENRE_LABELS)

export const SORT_OPTIONS = [
  { label: '最近更新', value: 'updatedAt' },
  { label: '最近创建', value: 'createdAt' },
  { label: '标题', value: 'title' },
  { label: '集数', value: 'episodeCount' },
  { label: '评分', value: 'rating' }
]

export const SOURCE_LABELS = {
  ai_generated: 'AI 生成',
  uploaded: '上传',
  purchased: '已购'
}

/**
 * Serialize filter state to API query params.
 * Omits empty/default values to keep URLs clean.
 */
export function buildQueryParams({ page, pageSize, status, genre, sort, keyword } = {}) {
  const params = {}
  if (page && page > 1) params.page = page
  if (pageSize && pageSize !== 20) params.page_size = pageSize
  if (status) params.status = status
  if (genre) params.genre = genre
  if (sort && sort !== 'updatedAt') params.sort = sort
  if (keyword) params.keyword = keyword
  return params
}

/**
 * Relative time display (Chinese locale).
 */
export function formatTimeAgo(dateStr) {
  if (!dateStr) return ''
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  const diff = now - then
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  if (days < 30) return `${Math.floor(days / 7)}周前`
  if (days < 365) return `${Math.floor(days / 30)}个月前`
  return `${Math.floor(days / 365)}年前`
}

/**
 * Determine which actions are available for a script based on its status and source.
 */
export function scriptActions(script) {
  const status = script?.status
  const source = script?.source
  const isOwner = true // scoped to current user by backend

  return {
    canEdit: status === 'draft' || status === 'delisted' || status === 'listed',
    canDelete: status === 'draft' || status === 'delisted',
    canGenerateComic: status && status !== 'purchased' && (script?.episodeCount || 0) > 0,
    canList: status === 'draft' || status === 'delisted',
    canDelist: status === 'listed' || status === 'sold',
    canViewSales: status === 'listed' || status === 'sold',
    canDownload: source === 'purchased' || status === 'purchased' || status === 'sold',
    canView: status === 'purchased'
  }
}

/**
 * Parse plot tags from JSON string to array.
 */
export function parseTags(jsonStr) {
  if (!jsonStr) return []
  try {
    return JSON.parse(jsonStr)
  } catch {
    // If it's a plain comma-separated string
    return jsonStr.split(',').map(t => t.trim()).filter(Boolean)
  }
}
