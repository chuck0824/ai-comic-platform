/**
 * Pure mapping functions for Platform Home.
 * No side effects, no API calls — just data → view-model transforms.
 */

export const CREATION_CARDS = [
  { mode: 'short_drama', label: '短剧创作', description: '创建短剧内容项目，进入完整创作生产流程', icon: 'VideoCamera' },
  { mode: 'long_form', label: '长篇创作', description: '创建长篇内容项目，按章节管理故事结构', icon: 'Document' },
  { mode: 'tvc', label: 'TVC 创作', description: '创建 TVC 广告项目，按版本方案管理', icon: 'Promotion' }
]

/**
 * Derive the correct action for a continue-working item based on its stage.
 */
export function continuationAction(item) {
  if (!item) return null
  switch (item.stage) {
    case 'content':
    case 'story_seed':
    case 'characters':
    case 'synopsis':
    case 'outline':
    case 'review':
      return { label: '继续创作', path: `/script-gen/${item.id}/workspace` }
    case 'storyboard':
      return { label: '进入分镜', path: `/content-projects/${item.id}/storyboards/latest` }
    case 'canvas':
    case 'editing':
    case 'generating':
    case 'composing':
      return {
        label: '进入画布',
        path: item.canvasProjectUuid ? `/canvas/${item.canvasProjectUuid}` : '/canvas-projects'
      }
    case 'completed':
      return { label: '查看成果', path: item.canvasProjectUuid ? `/canvas/${item.canvasProjectUuid}` : null }
    default:
      return { label: '继续工作', path: `/script-gen/${item.id}/workspace` }
  }
}

/**
 * Sort continue-working items: errors first, then by updatedAt descending.
 */
export function sortContinueWorking(items) {
  return [...items].sort((a, b) => {
    if (a.hasErrors !== b.hasErrors) return a.hasErrors ? -1 : 1
    return new Date(b.updatedAt) - new Date(a.updatedAt)
  })
}

/**
 * Build the home view model from raw data.
 */
export function buildHomeViewModel({ continueWorking, canvasSummary, metrics }) {
  const sorted = sortContinueWorking(continueWorking || [])
  const displayItems = sorted.slice(0, 5)

  return {
    creationCards: CREATION_CARDS,
    continueWorking: displayItems.map(item => ({
      ...item,
      action: continuationAction(item),
      timeAgo: timeAgo(item.updatedAt)
    })),
    continueWorkingEmpty: displayItems.length === 0,
    canvasSummary: canvasSummary || { active: 0, generating: 0, errors: 0 },
    metrics: {
      contentProjects: metrics?.contentProjects ?? 0,
      monthlyLockedScripts: metrics?.monthlyLockedScripts ?? 0,
      generatedAssets: metrics?.generatedAssets ?? 0,
      pendingTasks: metrics?.pendingTasks ?? 0
    }
  }
}

function timeAgo(dateStr) {
  if (!dateStr) return ''
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  const minutes = Math.floor((now - then) / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return new Date(dateStr).toLocaleDateString('zh-CN')
}
