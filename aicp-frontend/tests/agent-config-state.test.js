import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createWizardState, canPublish, bindingSourceLabel,
  filterDefinitions, versionStatusLabel, lifecycleStatusLabel,
  roleTypeLabel, testRunStatusLabel, nextWizardStep,
  isWizardComplete, wizardStepLabel
} from '../src/utils/agentConfigHelpers.js'

test('new agent wizard starts by selecting a blueprint', () => {
  assert.deepEqual(createWizardState(), {
    step: 'blueprint', blueprintId: null, identity: null,
    parameters: {}, editablePrompt: '', successfulTestRunId: null
  })
})

test('draft cannot publish without valid config and successful test', () => {
  assert.equal(canPublish({ valid: true, successfulTestRunId: null }), false)
  assert.equal(canPublish({ valid: true, successfulTestRunId: 'run_1' }), true)
  assert.equal(canPublish({ valid: false, successfulTestRunId: 'run_1' }), false)
  assert.equal(canPublish(null), false)
})

test('binding labels are explicit', () => {
  assert.equal(bindingSourceLabel('PROJECT'), '项目默认')
  assert.equal(bindingSourceLabel('USER'), '用户默认')
  assert.equal(bindingSourceLabel('SYSTEM'), '系统默认')
  assert.equal(bindingSourceLabel('TEMPORARY'), '单次调整')
  assert.equal(bindingSourceLabel('UNKNOWN'), '未解析')
})

test('filterDefinitions matches role and lifecycle status', () => {
  const rows = [
    { id: 'a', roleType: 'HOOK', lifecycleStatus: 'ACTIVE' },
    { id: 'b', roleType: 'DIRECTOR', lifecycleStatus: 'ARCHIVED' },
    { id: 'c', roleType: 'HOOK', lifecycleStatus: 'ARCHIVED' }
  ]
  assert.deepEqual(
    filterDefinitions(rows, { roleType: 'HOOK', status: 'ACTIVE' }).map(x => x.id),
    ['a']
  )
  assert.deepEqual(
    filterDefinitions(rows, { roleType: null, status: 'ACTIVE' }).map(x => x.id),
    ['a']
  )
  assert.deepEqual(
    filterDefinitions(rows, { roleType: 'DIRECTOR', status: null }).map(x => x.id),
    ['b']
  )
})

test('version status labels in Chinese', () => {
  assert.equal(versionStatusLabel('DRAFT'), '草稿')
  assert.equal(versionStatusLabel('PUBLISHED'), '已发布')
  assert.equal(versionStatusLabel('ARCHIVED'), '已归档')
  assert.equal(versionStatusLabel('UNKNOWN'), 'UNKNOWN')
})

test('lifecycle status labels in Chinese', () => {
  assert.equal(lifecycleStatusLabel('ACTIVE'), '启用')
  assert.equal(lifecycleStatusLabel('ARCHIVED'), '已归档')
})

test('role type labels in Chinese', () => {
  assert.equal(roleTypeLabel('HOOK'), '钩子')
  assert.equal(roleTypeLabel('SCREENWRITER'), '编剧')
  assert.equal(roleTypeLabel('STORYBOARD'), '分镜')
  assert.equal(roleTypeLabel('DIRECTOR'), '导演')
})

test('test run status labels in Chinese', () => {
  assert.equal(testRunStatusLabel('SUCCEEDED'), '成功')
  assert.equal(testRunStatusLabel('FAILED'), '失败')
  assert.equal(testRunStatusLabel('RUNNING'), '运行中')
  assert.equal(testRunStatusLabel('PENDING'), '等待中')
})

test('wizard step progression', () => {
  assert.equal(nextWizardStep('blueprint'), 'identity')
  assert.equal(nextWizardStep('identity'), 'configure')
  assert.equal(nextWizardStep('configure'), 'test')
  assert.equal(nextWizardStep('test'), null)
  assert.equal(nextWizardStep('invalid'), null)
})

test('wizard is complete only on test step with successful run', () => {
  assert.equal(isWizardComplete({ step: 'test', successfulTestRunId: 'run_1' }), true)
  assert.equal(isWizardComplete({ step: 'test', successfulTestRunId: null }), false)
  assert.equal(isWizardComplete({ step: 'configure', successfulTestRunId: 'run_1' }), false)
})

test('wizard step labels in Chinese', () => {
  assert.equal(wizardStepLabel('blueprint'), '选择框架')
  assert.equal(wizardStepLabel('identity'), '基本信息')
  assert.equal(wizardStepLabel('configure'), '配置参数')
  assert.equal(wizardStepLabel('test'), '试跑与发布')
})
