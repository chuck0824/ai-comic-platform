import request from './request.js'
import { createSceneAssetApi } from './sceneAssetApiFactory.js'

export { createSceneAssetApi, fromSceneAssetResponse, projectWorkspaceConfig, toSceneAssetPayload } from './sceneAssetApiFactory.js'

/** Scene assets are camelCase in Vue and snake_case only at this HTTP boundary. */
export const sceneAssetApi = createSceneAssetApi(request)
