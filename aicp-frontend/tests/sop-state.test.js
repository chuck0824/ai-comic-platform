import test from 'node:test'
import assert from 'node:assert/strict'
import {
  RESULT_LABELS, RESULT_COLORS,
  SEVERITY_LABELS, SEVERITY_COLORS,
  OVERALL_STATUS_LABELS, OVERALL_STATUS_COLORS,
  WORK_ORDER_STATUS_LABELS,
  canTransitionWorkOrder,
  mapSopReport,
  serializeSopScope,
  overallStatusLabel, overallStatusColor,
  resultLabel, resultColor,
  severityLabel, severityColor,
  workOrderStatusLabel, workOrderStatusColor,
} from '../src/views/sop/sopState.js'

test('RESULT_LABELS maps all five results', () => {
  assert.equal(RESULT_LABELS.PASS, '通过')
  assert.equal(RESULT_LABELS.WARNING, '告警')
  assert.equal(RESULT_LABELS.BLOCKED, '阻断')
  assert.equal(RESULT_LABELS.NOT_READY, '待配置')
  assert.equal(RESULT_LABELS.ERROR, '检查异常')
})

test('RESULT_COLORS maps blocking results to danger', () => {
  assert.equal(RESULT_COLORS.BLOCKED, 'danger')
  assert.equal(RESULT_COLORS.ERROR, 'danger')
  assert.equal(RESULT_COLORS.PASS, 'success')
  assert.equal(RESULT_COLORS.WARNING, 'warning')
  assert.equal(RESULT_COLORS.NOT_READY, 'info')
})

test('SEVERITY_LABELS maps all four levels', () => {
  assert.equal(SEVERITY_LABELS.P0, 'P0-阻断')
  assert.equal(SEVERITY_LABELS.P1, 'P1-严重')
  assert.equal(SEVERITY_LABELS.P2, 'P2-告警')
  assert.equal(SEVERITY_LABELS.P3, 'P3-提示')
})

test('OVERALL_STATUS_LABELS maps green/yellow/red', () => {
  assert.equal(OVERALL_STATUS_LABELS.GREEN, '已通过')
  assert.equal(OVERALL_STATUS_LABELS.YELLOW, '有告警')
  assert.equal(OVERALL_STATUS_LABELS.RED, '已阻断')
})

test('WORK_ORDER_STATUS_LABELS maps all states', () => {
  assert.equal(WORK_ORDER_STATUS_LABELS.OPEN, '待分配')
  assert.equal(WORK_ORDER_STATUS_LABELS.ASSIGNED, '已分配')
  assert.equal(WORK_ORDER_STATUS_LABELS.FIXING, '修复中')
  assert.equal(WORK_ORDER_STATUS_LABELS.PENDING_REVIEW, '待审核')
  assert.equal(WORK_ORDER_STATUS_LABELS.PASSED, '已通过')
  assert.equal(WORK_ORDER_STATUS_LABELS.REOPENED, '已重开')
  assert.equal(WORK_ORDER_STATUS_LABELS.CANCELED, '已取消')
})

test('canTransitionWorkOrder validates state machine', () => {
  assert.equal(canTransitionWorkOrder('OPEN', 'ASSIGNED'), true)
  assert.equal(canTransitionWorkOrder('OPEN', 'CANCELED'), true)
  assert.equal(canTransitionWorkOrder('OPEN', 'FIXING'), false)
  assert.equal(canTransitionWorkOrder('ASSIGNED', 'FIXING'), true)
  assert.equal(canTransitionWorkOrder('FIXING', 'PENDING_REVIEW'), true)
  assert.equal(canTransitionWorkOrder('FIXING', 'PASSED'), false)
  assert.equal(canTransitionWorkOrder('PENDING_REVIEW', 'PASSED'), true)
  assert.equal(canTransitionWorkOrder('PENDING_REVIEW', 'REOPENED'), true)
  assert.equal(canTransitionWorkOrder('PASSED', 'FIXING'), false)
  assert.equal(canTransitionWorkOrder('CANCELED', 'OPEN'), false)
  assert.equal(canTransitionWorkOrder('REOPENED', 'FIXING'), true)
})

test('mapSopReport groups results by type', () => {
  const report = {
    overallStatus: 'RED',
    status: 'COMPLETED',
    results: [
      { ruleCode: 'SCENE_GOAL', result: 'PASS', severity: 'P1', critical: true },
      { ruleCode: 'ASSET_BINDING', result: 'BLOCKED', severity: 'P1', critical: true },
      { ruleCode: 'VOICE_BINDING', result: 'NOT_READY', severity: 'P1', critical: true },
    ],
  }
  const model = mapSopReport(report)
  assert.equal(model.canEnterProduction, false)
  assert.equal(model.groups.passed.length, 1)
  assert.equal(model.groups.blocked.length, 1)
  assert.equal(model.groups.notReady.length, 1)
  assert.equal(model.groups.warnings.length, 0)
  assert.equal(model.groups.errors.length, 0)
})

test('mapSopReport allows production when gateAllowed and not stale', () => {
  const model = mapSopReport({ overallStatus: 'GREEN', status: 'COMPLETED', gateAllowed: true, results: [] })
  assert.equal(model.canEnterProduction, true)
})

test('mapSopReport denies production when stale', () => {
  const model = mapSopReport({ overallStatus: 'GREEN', status: 'STALE', gateAllowed: true, results: [] })
  assert.equal(model.canEnterProduction, false)
})

test('serializeSopScope strips null filters', () => {
  assert.deepEqual(serializeSopScope({ contentUnitId: 8, canvasProjectId: null }), { content_unit_id: 8 })
  assert.deepEqual(serializeSopScope({}), {})
})

test('label functions return -- for unknown values', () => {
  assert.equal(resultLabel('UNKNOWN'), '--')
  assert.equal(severityLabel('X5'), '--')
  assert.equal(overallStatusLabel(null), '--')
  assert.equal(workOrderStatusLabel(''), '--')
})

test('color functions return info for unknown values', () => {
  assert.equal(resultColor('UNKNOWN'), 'info')
  assert.equal(severityColor('X5'), 'info')
  assert.equal(overallStatusColor(null), 'info')
  assert.equal(workOrderStatusColor(''), 'info')
})
