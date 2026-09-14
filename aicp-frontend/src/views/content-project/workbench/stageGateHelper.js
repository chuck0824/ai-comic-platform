/**
 * R2-A 阶段门禁预览 / 警告确认辅助。
 */

export function normalizeGateView(raw = {}) {
  return {
    stageKey: raw.stage_key ?? raw.stageKey ?? '',
    blockers: Array.isArray(raw.blockers) ? raw.blockers : [],
    warnings: Array.isArray(raw.warnings) ? raw.warnings : [],
    evidence: raw.evidence && typeof raw.evidence === 'object' ? raw.evidence : {}
  }
}

export function issueCode(issue) {
  return issue?.code != null ? String(issue.code) : ''
}

export function issueCodes(issues = []) {
  return issues.map(issueCode).filter(Boolean)
}

export function formatGateIssues(issues = []) {
  return issues
    .map(issue => {
      const code = issueCode(issue)
      const message = issue?.message || issue?.title || ''
      if (code && message) return `• [${code}] ${message}`
      return `• ${message || code || '未知问题'}`
    })
    .join('\n')
}

export function isStageGateBlockedError(caught) {
  const code = caught?.response?.data?.code
  return code === 43010 || code === 'STAGE_GATE_BLOCKED'
}

/** 从 BizException 响应中取出 blockers/warnings（可能在 data 根或 data.details） */
export function extractGateDetailsFromError(caught) {
  const body = caught?.response?.data
  if (!body) return null
  const payload = body.data ?? body
  if (payload && (Array.isArray(payload.blockers) || Array.isArray(payload.warnings))) {
    return normalizeGateView(payload)
  }
  if (payload?.details && (Array.isArray(payload.details.blockers) || Array.isArray(payload.details.warnings))) {
    return normalizeGateView(payload.details)
  }
  return null
}

export function hasDurationVarianceUnconfirmed(gate) {
  return (gate?.blockers || []).some(issue => issueCode(issue) === 'DURATION_VARIANCE_UNCONFIRMED')
}
