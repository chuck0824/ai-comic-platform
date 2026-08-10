const DEFAULTS = Object.freeze({
  creationSettings: {},
  novelUpload: {},
  novelAnalysis: {},
  adaptation: { hooks: [] },
  structuredScript: { episodes: [] },
  scriptBody: { episodes: [] },
  reviewRevision: { issues: [] },
  textStoryboard: { shots: [] }
})

function clone(value) { return JSON.parse(JSON.stringify(value)) }

export function resetProjectWorkspaceData(state) {
  for (const [key, value] of Object.entries(DEFAULTS)) state[key] = clone(value)
  return state
}

export function createProjectLoadGuard() {
  let generation = 0
  let projectId = null
  return {
    begin(nextProjectId) { projectId = String(nextProjectId); generation += 1; return generation },
    accept(token, responseProjectId) { return token === generation && String(responseProjectId) === projectId },
    invalidate() { generation += 1; projectId = null }
  }
}
