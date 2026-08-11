import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createSceneAssetApi } from '../src/api/sceneAssetApiFactory.js'

test('script scene application sends the protected request with its content project workspace', async () => {
  const calls = []
  const client = {
    post: async (...args) => { calls.push(args); return { data: { application_id: 91 } } }
  }
  const api = createSceneAssetApi(client)
  const result = await api.apply(41, 7, {
    targetType: 'SCRIPT_SCENE', targetId: null, targetKey: 'EP-001-SCENE-001', idempotencyKey: 'bind-1'
  })
  assert.equal(result.applicationId, 91)
  assert.equal(calls[0][0], '/asset/library/7/applications')
  assert.equal(calls[0][1].project_id, 41)
  assert.deepEqual(calls[0][2], { headers: { 'X-Workspace-Id': 'project_41' } })
})

test('global request interceptor preserves an explicit project workspace header', () => {
  const source = readFileSync(new URL('../src/api/request.js', import.meta.url), 'utf8')
  assert.match(source, /!config\.headers\['X-Workspace-Id'\]/)
})
