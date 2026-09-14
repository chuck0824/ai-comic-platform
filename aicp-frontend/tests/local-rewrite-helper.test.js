import test from 'node:test'
import assert from 'node:assert/strict'
import {
  applyBlockReplacement,
  buildAdoptPatchesRequest,
  buildLocalRewriteRequest,
  discardCandidateLeavesBody,
  findBlockRange,
  flattenScriptBodyPlainText,
  isCandidateStale,
  normalizeLocalRewriteCandidate,
  operationFromBlockAction,
  selectPartialPatches,
  sha256Hex
} from '../src/views/content-project/workbench/localRewriteHelper.js'

const sampleBody = {
  episodes: [{
    id: 'EP-001',
    scenes: [{
      id: 'SCENE-1',
      blocks: [
        { id: 'B1', type: 'action', text: '甲推门而入。' },
        { id: 'B2', type: 'dialogue', text: '你终于来了。' }
      ]
    }]
  }]
}

test('flattens script body with block ranges', () => {
  const { plainText, ranges } = flattenScriptBodyPlainText(sampleBody)
  assert.equal(plainText, '甲推门而入。\n\n你终于来了。')
  assert.equal(ranges.length, 2)
  assert.deepEqual(findBlockRange(ranges, 'B2'), {
    blockId: 'B2',
    sceneId: 'SCENE-1',
    start: '甲推门而入。\n\n'.length,
    end: plainText.length,
    text: '你终于来了。'
  })
})

test('maps UI actions to operations', () => {
  assert.equal(operationFromBlockAction('strengthen-conflict'), 'strengthen_conflict')
  assert.equal(operationFromBlockAction('unknown'), 'rewrite')
})

test('builds rewrite and adopt payloads with hashes', async () => {
  const { plainText, ranges } = flattenScriptBodyPlainText(sampleBody)
  const range = findBlockRange(ranges, 'B1')
  const req = await buildLocalRewriteRequest({
    baseVersionId: 9,
    contentUnitRevision: 2,
    plainText,
    startOffset: range.start,
    endOffset: range.end,
    operation: 'rewrite_tone'
  })
  assert.equal(req.base_version_id, 9)
  assert.equal(req.content_unit_revision, 2)
  assert.equal(req.start_offset, 0)
  assert.equal(req.end_offset, range.end)
  assert.equal(req.content_hash, await sha256Hex(plainText))
  assert.equal(req.selected_text_hash, await sha256Hex('甲推门而入。'))

  const adopt = await buildAdoptPatchesRequest({
    contentUnitRevision: 2,
    plainText,
    patches: [{ startOffset: 0, endOffset: 6, expectedTextHash: 'abc', replacement: '乙推门而入。', reason: 'tone' }]
  })
  assert.equal(adopt.patches[0].start_offset, 0)
  assert.equal(adopt.patches[0].replacement, '乙推门而入。')
})

test('applies block replacement and normalizes candidate', () => {
  const next = applyBlockReplacement(sampleBody, 'B1', '乙推门而入。')
  assert.equal(next.episodes[0].scenes[0].blocks[0].text, '乙推门而入。')
  assert.equal(sampleBody.episodes[0].scenes[0].blocks[0].text, '甲推门而入。')

  const candidate = normalizeLocalRewriteCandidate({
    candidate_version_id: 44,
    job: { id: 7 },
    diff: { before: 'a', after: 'b' },
    patches: [{ start_offset: 0, end_offset: 1, expected_text_hash: 'h', replacement: 'b', reason: 'x' }]
  })
  assert.equal(candidate.jobId, 7)
  assert.equal(candidate.candidateVersionId, 44)
  assert.equal(candidate.patches[0].startOffset, 0)
})

test('selects partial patches and detects stale candidates', () => {
  const patches = [
    { startOffset: 0, endOffset: 1, replacement: 'a' },
    { startOffset: 2, endOffset: 3, replacement: 'b' },
    { startOffset: 4, endOffset: 5, replacement: 'c' }
  ]
  assert.deepEqual(selectPartialPatches(patches, [0, 2]).map(p => p.replacement), ['a', 'c'])

  assert.equal(isCandidateStale({
    candidateRevision: 1,
    currentRevision: 2
  }), true)
  assert.equal(isCandidateStale({
    candidateContentHash: 'aaa',
    currentPlainTextHash: 'bbb'
  }), true)
  assert.equal(isCandidateStale({
    candidateRevision: 3,
    currentRevision: 3,
    candidateContentHash: 'same',
    currentPlainTextHash: 'same'
  }), false)
})

test('discard candidate leaves body unchanged', () => {
  const body = { episodes: [{ id: 'EP-001' }] }
  const snapshot = JSON.parse(JSON.stringify(body))
  assert.equal(discardCandidateLeavesBody(snapshot, body), true)
  assert.equal(discardCandidateLeavesBody(snapshot, { episodes: [] }), false)
})
