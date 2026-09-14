/**
 * R2-A.3 局部改写前端辅助：选区 hash、正文扁平化、Patch 构建与采用。
 */

const BLOCK_SEPARATOR = '\n\n'

const ACTION_OPERATION_MAP = Object.freeze({
  'continue-selected-block': 'continue',
  'strengthen-conflict': 'strengthen_conflict',
  'condense-dialogue': 'condense_dialogue',
  'rewrite-tone': 'rewrite_tone',
  'check-character-consistency': 'check_consistency'
})

export async function sha256Hex(text) {
  const data = new TextEncoder().encode(text ?? '')
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const digest = await crypto.subtle.digest('SHA-256', data)
    return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('')
  }
  // 非安全上下文降级：不可用于生产冲突保护
  let hash = 0
  const raw = String(text ?? '')
  for (let i = 0; i < raw.length; i += 1) hash = ((hash << 5) - hash) + raw.charCodeAt(i)
  return `fallback-${(hash >>> 0).toString(16)}`
}

export function operationFromBlockAction(action) {
  return ACTION_OPERATION_MAP[action] || 'rewrite'
}

/** 将 scenes/blocks 展平为线性 plainText，并记录每块偏移。 */
export function flattenScriptBodyPlainText(scriptBody) {
  const ranges = []
  const parts = []
  let offset = 0
  const episodes = scriptBody?.episodes || []
  for (const episode of episodes) {
    for (const scene of episode.scenes || []) {
      for (const block of scene.blocks || []) {
        const text = String(block.text ?? '')
        if (parts.length) {
          parts.push(BLOCK_SEPARATOR)
          offset += BLOCK_SEPARATOR.length
        }
        ranges.push({
          blockId: block.id,
          sceneId: scene.id,
          start: offset,
          end: offset + text.length,
          text
        })
        parts.push(text)
        offset += text.length
      }
    }
  }
  return { plainText: parts.join(''), ranges }
}

export function findBlockRange(ranges, blockId) {
  return (ranges || []).find(item => item.blockId === blockId) || null
}

export function buildLocalRewriteRequest({
  baseVersionId,
  contentUnitRevision,
  plainText,
  startOffset,
  endOffset,
  operation = 'rewrite',
  model,
  prompt
}) {
  const selected = String(plainText || '').slice(startOffset, endOffset)
  return Promise.all([sha256Hex(plainText), sha256Hex(selected)]).then(([contentHash, selectedTextHash]) => ({
    base_version_id: baseVersionId,
    content_unit_revision: contentUnitRevision,
    content_hash: contentHash,
    start_offset: startOffset,
    end_offset: endOffset,
    selected_text_hash: selectedTextHash,
    operation,
    model,
    prompt
  }))
}

export function buildAdoptPatchesRequest({ contentUnitRevision, plainText, patches }) {
  return sha256Hex(plainText).then(contentHash => ({
    content_unit_revision: contentUnitRevision,
    content_hash: contentHash,
    patches: (patches || []).map(patch => ({
      start_offset: patch.startOffset ?? patch.start_offset,
      end_offset: patch.endOffset ?? patch.end_offset,
      expected_text_hash: patch.expectedTextHash ?? patch.expected_text_hash,
      replacement: patch.replacement,
      reason: patch.reason
    }))
  }))
}

/** 将单块替换写回结构化剧本；仅改目标 block.text。 */
export function applyBlockReplacement(scriptBody, blockId, replacement) {
  const next = JSON.parse(JSON.stringify(scriptBody || { episodes: [] }))
  for (const episode of next.episodes || []) {
    for (const scene of episode.scenes || []) {
      const block = (scene.blocks || []).find(item => item.id === blockId)
      if (block) {
        block.text = replacement ?? ''
        return next
      }
    }
  }
  return next
}

export function normalizeLocalRewriteCandidate(payload = {}) {
  const patches = payload.patches || []
  const diff = payload.diff || {}
  return {
    jobId: payload.job?.id ?? payload.job_id ?? null,
    candidateVersionId: payload.candidate_version_id ?? payload.candidateVersionId ?? null,
    baseContentHash: payload.base_content_hash ?? payload.baseContentHash ?? null,
    baseRevision: payload.base_revision ?? payload.baseRevision ?? null,
    before: diff.before ?? patches[0]?.before ?? '',
    after: diff.after ?? patches[0]?.replacement ?? '',
    patches: patches.map(patch => ({
      startOffset: patch.startOffset ?? patch.start_offset,
      endOffset: patch.endOffset ?? patch.end_offset,
      expectedTextHash: patch.expectedTextHash ?? patch.expected_text_hash,
      replacement: patch.replacement,
      reason: patch.reason
    }))
  }
}

/** 按索引子集挑选 Patch（部分采用）。 */
export function selectPartialPatches(patches = [], selectedIndexes = []) {
  const allowed = new Set((selectedIndexes || []).map(Number))
  return (patches || []).filter((_, index) => allowed.has(index))
}

/**
 * 候选相对当前正文是否已过期（revision 或内容 hash 变化）。
 */
export function isCandidateStale({
  candidateContentHash,
  currentPlainTextHash,
  candidateRevision,
  currentRevision
} = {}) {
  if (candidateRevision != null && currentRevision != null
      && Number(candidateRevision) !== Number(currentRevision)) {
    return true
  }
  if (candidateContentHash && currentPlainTextHash
      && candidateContentHash !== currentPlainTextHash) {
    return true
  }
  return false
}

/** 放弃候选后正文应与放弃前深相等。 */
export function discardCandidateLeavesBody(beforeBody, afterDiscardBody) {
  return JSON.stringify(beforeBody ?? null) === JSON.stringify(afterDiscardBody ?? null)
}
