/**
 * 前端端口注册表（即时拖拽反馈用）。
 * 后端 CanvasPortRegistry 是权威校验源。
 */
const CONTRACT_VERSION = 'canvas-ports-v1'

const DEFINITIONS = {
  // OUTPUT ports
  'text_out:OUTPUT':      { key: 'text_out', payloadType: 'text', roles: ['prompt', 'dialogue', 'description'] },
  'shot:OUTPUT':          { key: 'shot', payloadType: 'shot', roles: ['shot_identity'] },
  'image_ref:OUTPUT':     { key: 'image_ref', payloadType: 'image_ref', roles: ['identity', 'scene', 'prop', 'character'] },
  'motion_ref:OUTPUT':    { key: 'motion_ref', payloadType: 'motion_ref', roles: ['motion_source'] },
  'camera_ref:OUTPUT':    { key: 'camera_ref', payloadType: 'camera_ref', roles: ['camera_source'] },
  'audio_ref:OUTPUT':     { key: 'audio_ref', payloadType: 'audio_ref', roles: ['audio_source'] },
  'director_package:OUTPUT': { key: 'director_package', payloadType: 'director_package', roles: ['director_output'] },
  'video_candidate:OUTPUT':{ key: 'video_candidate', payloadType: 'video_candidate', roles: ['candidate_output'] },
  'quality_report:OUTPUT': { key: 'quality_report', payloadType: 'quality_report', roles: ['quality_output'] },

  // INPUT ports
  'image_ref:INPUT':      { key: 'image_ref', payloadType: 'image_ref', roles: ['identity', 'scene', 'composition', 'style_ref'] },
  'motion_ref:INPUT':     { key: 'motion_ref', payloadType: 'motion_ref', roles: ['motion_reference'] },
  'camera_ref:INPUT':     { key: 'camera_ref', payloadType: 'camera_ref', roles: ['camera_reference'] },
  'audio_ref:INPUT':      { key: 'audio_ref', payloadType: 'audio_ref', roles: ['audio_timing', 'audio_reference'] },
  'director_package:INPUT': { key: 'director_package', payloadType: 'director_package', roles: ['director_input'] }
}

/**
 * 前端即时校验：两个端口是否可以连线。
 * 后端 `POST /api/v1/canvas/ports/validate` 为最终权威校验。
 *
 * @param {{ nodeType: string, port: string }} source
 * @param {{ nodeType: string, port: string }} target
 * @param {string|null} role
 * @returns {boolean}
 */
export function canConnect(source, target, role = null) {
  const srcDef = DEFINITIONS[source.port + ':OUTPUT']
  const tgtDef = DEFINITIONS[target.port + ':INPUT']
  if (!srcDef || !tgtDef) return false
  if (srcDef.payloadType !== tgtDef.payloadType) return false
  if (role !== null && !tgtDef.roles.includes(role)) return false
  return true
}

export { CONTRACT_VERSION }
