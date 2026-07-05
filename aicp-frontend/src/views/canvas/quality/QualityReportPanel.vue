<template>
  <div class="quality-panel" v-if="report">
    <div class="qp-header">
      <span class="qp-title">质量报告</span>
      <el-tag :type="statusTagType" size="small">{{ report.overallStatus }}</el-tag>
      <span class="qp-policy">策略: {{ report.policyVersion || 'v1' }}</span>
    </div>

    <div v-if="!issues.length" class="qp-empty">
      <el-empty description="未检测到质量问题" :image-size="60" />
    </div>

    <div v-for="issue in issues" :key="issue.uuid" :class="['qp-issue', 'severity-' + issue.severity]">
      <div class="qp-issue-header">
        <el-tag :type="severityTagType(issue.severity)" size="small">{{ issue.severity }}</el-tag>
        <span class="qp-dimension">{{ issue.dimension }}</span>
        <span class="qp-time">{{ formatMs(issue.startMs) }} – {{ formatMs(issue.endMs) }}</span>
        <el-button v-if="issueTarget(issue).route" link size="small" type="primary"
          @click="$emit('navigate', issueTarget(issue))">
          定位 →
        </el-button>
      </div>
      <div v-if="issue.expected" class="qp-issue-detail">
        <span class="label">期望:</span> {{ issue.expected }}
      </div>
      <div v-if="issue.observed" class="qp-issue-detail">
        <span class="label">实际:</span> {{ issue.observed }}
      </div>
    </div>

    <div v-if="report.overallStatus === 'BLOCK'" class="qp-block-footer">
      <el-alert type="error" :closable="false" title="质量阻断" description="该候选被质量策略阻断，正式采用需要授权覆盖。" />
      <el-input v-model="overrideReason" placeholder="填写强制采用原因..." type="textarea" size="small" style="margin-top:8px" />
      <el-button type="danger" size="small" :disabled="!overrideReason" @click="$emit('override', overrideReason)" style="margin-top:4px">
        强制采用（需 canvas:quality:override 权限）
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { issueTarget, qualityStatusColor, severityColor } from './qualityState.js'

const props = defineProps({
  report: { type: Object, default: null }
})

defineEmits(['navigate', 'override'])

const overrideReason = ref('')

const issues = computed(() => props.report?.issues || [])
const statusTagType = computed(() => qualityStatusColor(props.report?.overallStatus))
const severityTagType = (s) => severityColor(s)

function formatMs(ms) { return (ms / 1000).toFixed(1) + 's' }
</script>

<style scoped>
.quality-panel { padding: 12px; }
.qp-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.qp-title { font-size: 14px; font-weight: 600; }
.qp-policy { font-size: 11px; color: #888; margin-left: auto; }
.qp-empty { padding: 20px 0; }
.qp-issue { border: 1px solid #333; border-radius: 6px; padding: 8px 12px; margin-bottom: 8px; }
.qp-issue.severity-ERROR { border-left: 3px solid #f56c6c; }
.qp-issue.severity-WARN { border-left: 3px solid #e6a23c; }
.qp-issue.severity-INFO { border-left: 3px solid #909399; }
.qp-issue-header { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.qp-dimension { color: #aaa; font-family: monospace; font-size: 11px; }
.qp-time { color: #666; font-size: 11px; margin-left: auto; }
.qp-issue-detail { font-size: 11px; color: #888; margin-top: 4px; }
.qp-issue-detail .label { color: #aaa; }
.qp-block-footer { margin-top: 12px; }
</style>
