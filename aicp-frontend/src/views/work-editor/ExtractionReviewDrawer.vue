<template>
  <el-drawer v-model="visible" :title="'AI 提取审核 — 批次 #' + batchId" size="520px" direction="rtl">
    <div v-loading="loading">
      <el-alert v-if="error" type="error" :title="error" closable @close="error = ''" />

      <div v-if="batch">
        <el-descriptions :column="2" size="small" border style="margin-bottom:16px">
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(batch.status)">{{ batch.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="待审核">{{ batch.pending_count ?? 0 }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="candidates.length === 0" class="empty">暂无候选项</div>

        <div v-for="c in candidates" :key="c.id" class="candidate-card">
          <div class="candidate-header">
            <strong>{{ c.canonical_name }}</strong>
            <el-tag size="small">{{ c.setting_type }}</el-tag>
          </div>
          <div class="candidate-fields" v-if="c.field_values">
            <div v-for="(v, k) in c.field_values" :key="k" class="field-row">
              <span class="field-key">{{ k }}</span>
              <span class="field-val">{{ v }}</span>
            </div>
          </div>
          <div v-if="c.evidence_text" class="evidence">
            <strong>原文证据：</strong>{{ c.evidence_text }}
          </div>
          <div class="candidate-actions">
            <el-radio-group v-model="c._decision" size="small">
              <el-radio-button value="merge">合并</el-radio-button>
              <el-radio-button value="keep">保留原值</el-radio-button>
              <el-radio-button value="replace">采用新值</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="applying" @click="applyDecisions">确认回写</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { contentProjectApi } from '@/api/contentProject'
import { makeDecisionPayload } from './workEditorData'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: Boolean,
  projectId: Number,
  batchId: Number
})

const emit = defineEmits(['update:modelValue', 'applied'])

const visible = ref(props.modelValue)
const loading = ref(false)
const applying = ref(false)
const error = ref('')
const batch = ref(null)
const candidates = ref([])

watch(() => props.modelValue, (v) => { visible.value = v; if (v) loadBatch() })
watch(visible, (v) => emit('update:modelValue', v))

async function loadBatch() {
  if (!props.batchId) return
  loading.value = true
  error.value = ''
  try {
    const res = await contentProjectApi.getExtraction(props.projectId, props.batchId)
    const data = res?.data ?? res
    batch.value = data
    candidates.value = (data.candidates ?? []).map(c => ({ ...c, _decision: c.field_decisions ? 'merge' : 'replace' }))
  } catch (e) {
    error.value = e?.response?.data?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function applyDecisions() {
  applying.value = true
  try {
    const payload = makeDecisionPayload(candidates.value)
    await contentProjectApi.saveDecisions(props.projectId, props.batchId, payload)
    await contentProjectApi.applyExtraction(props.projectId, props.batchId)
    ElMessage.success('已回写')
    visible.value = false
    emit('applied')
  } catch (e) {
    error.value = e?.response?.data?.message || '回写失败'
  } finally {
    applying.value = false
  }
}

function statusTagType(s) {
  if (s === 'review_ready') return 'warning'
  if (s === 'applied') return 'success'
  if (s === 'failed') return 'danger'
  return 'info'
}
</script>

<style scoped>
.candidate-card { border: 1px solid var(--border); border-radius: 8px; padding: 12px; margin-bottom: 12px; }
.candidate-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.field-row { display: flex; gap: 8px; margin-bottom: 4px; font-size: 13px; }
.field-key { color: var(--text-secondary); min-width: 60px; }
.field-val { color: var(--text-primary); }
.evidence { font-size: 12px; color: var(--text-tertiary); margin-top: 8px; padding: 6px; background: var(--bg-muted); border-radius: 4px; }
.candidate-actions { margin-top: 10px; }
.empty { padding: 40px; text-align: center; color: var(--text-tertiary); }
</style>
