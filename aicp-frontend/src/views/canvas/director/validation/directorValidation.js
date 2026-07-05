/**
 * 导演台冻结前自动检查。
 * 错误阻止冻结；警告可确认后继续。
 */

/**
 * @param {object} doc - DirectorDocument
 * @returns {{ errors: Array<{code:string,message:string}>, warnings: Array<{code:string,message:string}> }}
 */
export function validateDirectorDocument(doc) {
  const errors = []
  const warnings = []

  // 坐标系统
  if (doc.coordinateSystem !== 'RH_Y_UP_METERS') {
    errors.push({ code: 'COORD_SYSTEM', message: '坐标系统必须为 RH_Y_UP_METERS' })
  }

  // 时长/帧率
  if (!doc.durationMs || doc.durationMs <= 0) errors.push({ code: 'DURATION_ZERO', message: '时长必须 > 0' })
  if (!doc.fps || doc.fps < 1 || doc.fps > 120) errors.push({ code: 'FPS_RANGE', message: '帧率必须在 1–120' })

  // 至少一个相机
  if (!doc.cameras || doc.cameras.length === 0) {
    errors.push({ code: 'NO_CAMERA', message: '至少需要一个相机' })
  }

  // 动作重叠检测
  for (const obj of doc.objects || []) {
    if (obj.actions && obj.actions.length > 1) {
      const sorted = [...obj.actions].sort((a, b) => a.inMs - b.inMs)
      for (let i = 1; i < sorted.length; i++) {
        if (sorted[i].inMs < sorted[i - 1].outMs) {
          warnings.push({ code: 'ACTION_OVERLAP', message: `对象 ${obj.name || obj.id} 动作重叠` })
        }
      }
    }
    // 动作时间越界
    for (const action of (obj.actions || [])) {
      if (action.outMs <= action.inMs) errors.push({ code: 'ACTION_INVALID', message: `对象 ${obj.name} out_ms <= in_ms` })
      if (action.inMs < 0 || action.outMs > doc.durationMs) errors.push({ code: 'ACTION_BOUNDS', message: `对象 ${obj.name} 动作超出时间范围` })
    }
    // 关键帧越界
    for (const kf of (obj.keyframes || [])) {
      if (kf.timeMs < 0 || kf.timeMs >= doc.durationMs) {
        errors.push({ code: 'KEYFRAME_BOUNDS', message: `对象 ${obj.name} 关键帧 ${kf.timeMs}ms 越界` })
      }
    }
  }

  // 空场景
  if (!doc.objects || doc.objects.length === 0) {
    warnings.push({ code: 'EMPTY_SCENE', message: '场景没有任何对象' })
  }

  return { errors, warnings }
}
