/**
 * ShotWorkUnit Gate 推导和状态管理。
 */
export const SHOT_GATES = Object.freeze([
  'INPUT_READY',
  'COST_CONFIRMED',
  'GENERATED',
  'QUALITY_COMPLETE',
  'ADOPTED'
])

/**
 * 根据 ShotWorkUnit 状态计算已通过的 Gate。
 * @param {{ inputsReady?: boolean, costConfirmed?: boolean, generated?: boolean, qualityComplete?: boolean, adopted?: boolean }} unit
 * @returns {string[]}
 */
export function gatesFor(unit) {
  const passed = []
  if (unit.inputsReady) passed.push('INPUT_READY')
  if (unit.costConfirmed) passed.push('COST_CONFIRMED')
  if (unit.generated) passed.push('GENERATED')
  if (unit.qualityComplete) passed.push('QUALITY_COMPLETE')
  if (unit.adopted) passed.push('ADOPTED')
  return passed
}

/**
 * 是否允许正式采用。
 * 探索模式不允许；正式模式必须已绑定分镜版本。
 */
export function canFormallyAdopt(unit) {
  return unit.mode === 'PRODUCTION' && Number.isInteger(unit.sourceShotRevision)
}

/**
 * 是否允许创建交付清单。
 */
export function canCreateManifest(project) {
  const units = project?.units || []
  return project?.mode === 'PRODUCTION'
    && units.length > 0
    && units.every(unit => unit.adopted)
}
