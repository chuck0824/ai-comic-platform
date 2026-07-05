/**
 * 时间线数学工具。
 * 关键帧插值：LINEAR、HOLD、EASE_IN_OUT、CUBIC_BEZIER。
 */

const EPSILON = 1e-8

/** 标量线性插值 */
export function interpolateScalar(keyframes, timeMs, mode = 'LINEAR') {
  if (!keyframes.length) return 0
  if (keyframes.length === 1) return keyframes[0].value

  const sorted = [...keyframes].sort((a, b) => a.timeMs - b.timeMs)
  if (timeMs <= sorted[0].timeMs) return sorted[0].value
  if (timeMs >= sorted[sorted.length - 1].timeMs) return sorted[sorted.length - 1].value

  const next = sorted.findIndex(k => k.timeMs > timeMs)
  const k0 = sorted[next - 1]
  const k1 = sorted[next]

  const t = (timeMs - k0.timeMs) / (k1.timeMs - k0.timeMs)

  switch (mode) {
    case 'HOLD': return k0.value
    case 'EASE_IN_OUT':
      return k0.value + (k1.value - k0.value) * easeInOutCubic(t)
    case 'CUBIC_BEZIER':
      return k0.value + (k1.value - k0.value) * cubicBezier(t)
    case 'LINEAR':
    default:
      return k0.value + (k1.value - k0.value) * t
  }
}

/** Vector3 线性插值 */
export function interpolateVector3(keyframes, timeMs, mode = 'LINEAR') {
  return {
    x: interpolateScalar(keyframes.map(k => ({ timeMs: k.timeMs, value: k.value.x })), timeMs, mode),
    y: interpolateScalar(keyframes.map(k => ({ timeMs: k.timeMs, value: k.value.y })), timeMs, mode),
    z: interpolateScalar(keyframes.map(k => ({ timeMs: k.timeMs, value: k.value.z })), timeMs, mode)
  }
}

/** Quaternion Slerp */
export function slerpQuaternion(q0, q1, t) {
  let cosOmega = q0.x * q1.x + q0.y * q1.y + q0.z * q1.z + q0.w * q1.w
  let flip = false
  if (cosOmega < 0) { cosOmega = -cosOmega; flip = true }

  let s0, s1
  if (cosOmega > 1 - EPSILON) {
    s0 = 1 - t
    s1 = flip ? t - 1 : t
  } else {
    const omega = Math.acos(cosOmega)
    const sinOmega = Math.sin(omega)
    s0 = Math.sin((1 - t) * omega) / sinOmega
    s1 = flip ? -Math.sin(t * omega) / sinOmega : Math.sin(t * omega) / sinOmega
  }

  return {
    x: s0 * q0.x + s1 * q1.x,
    y: s0 * q0.y + s1 * q1.y,
    z: s0 * q0.z + s1 * q1.z,
    w: s0 * q0.w + s1 * q1.w
  }
}

function easeInOutCubic(t) {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2
}

function cubicBezier(t) {
  return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t
}
