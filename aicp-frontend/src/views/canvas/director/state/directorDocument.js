/**
 * 导演台文档协议（前端侧）。
 * 坐标：RH_Y_UP_METERS。旋转：归一化 Quaternion。时间：半开 [0, duration_ms)。
 */

export const COORDINATE_SYSTEM = 'RH_Y_UP_METERS'

/** 帧索引计算：frame_count = ceil(duration_ms / 1000 * fps) */
export function frameCount(durationMs, fps) {
  return Math.ceil(durationMs / 1000 * fps)
}

/** 有效帧范围：{ first: 0, last: frameCount - 1 } */
export function validFrameRange(durationMs, fps) {
  const count = frameCount(durationMs, fps)
  return { first: 0, last: count - 1 }
}

/** 时间(ms) → 帧索引 */
export function timeToFrame(timeMs, fps) {
  return Math.floor(timeMs / 1000 * fps)
}

/** 帧索引 → 时间(ms) */
export function frameToTime(frame, fps) {
  return Math.round(frame * 1000 / fps)
}

/** 创建空的 DirectorDocument */
export function createEmptyDocument(durationMs = 5000, fps = 24, aspectRatio = '16:9') {
  return {
    coordinateSystem: COORDINATE_SYSTEM,
    durationMs,
    fps,
    cameras: [{
      id: 'camera_1',
      name: '主相机',
      focalLengthMm: 50,
      sensorWidthMm: 36,
      aperture: 2.8,
      nearClip: 0.1,
      farClip: 1000,
      aspectRatioOverride: aspectRatio
    }],
    activeCameraId: 'camera_1',
    objects: [],
    timeline: { tracks: [] }
  }
}

/** 检查是否应自动保存 */
export function shouldAutosave(state) {
  return state.dirty && !state.validating && !state.frozen
}

/** 从文档获取活跃相机 */
export function activeCamera(doc) {
  return doc.cameras?.find(c => c.id === doc.activeCameraId) || doc.cameras?.[0] || null
}
