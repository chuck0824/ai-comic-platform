/**
 * R2-A：EDIT_CONFLICT 解析与冲突三方动作目录。
 */

export function extractEditConflict(caught) {
  const body = caught?.response?.data
  if (!body) return null
  if (body.code === 43003 || body.code === 'EDIT_CONFLICT') return body.data || body
  if (body.data?.code === 'EDIT_CONFLICT') return body.data
  return null
}

export function formatEditConflictMessage(conflict) {
  if (!conflict) return '草稿与服务端冲突，请刷新或保留本地后重试。'
  const server = conflict.server_revision ?? conflict.serverRevision ?? '—'
  const base = conflict.base_revision ?? conflict.baseRevision ?? '—'
  return `服务端 revision=${server}，本地 base=${base}。可加载远端草稿、保留本地重试，或查看差异。`
}

export function conflictActionOptions(conflict) {
  return [
    { code: 'load_remote_draft', label: '加载远端草稿', primary: true },
    { code: 'keep_local_retry', label: '保留本地并重试', primary: false },
    { code: 'keep_local_as_draft', label: '保留本地另存草稿', primary: false },
    {
      code: 'open_comparison',
      label: '查看三方差异',
      primary: false,
      href: conflict?.comparison_url || conflict?.comparisonUrl || null
    }
  ]
}
