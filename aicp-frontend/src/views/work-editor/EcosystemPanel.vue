<template>
  <div class="ecosystem-panel">
    <div class="flex items-center justify-between mb-md">
      <h3 class="text-lg font-bold">总体生态</h3>
      <el-button type="primary" size="small" @click="startCreate" :disabled="!editable">
        + 添加规则
      </el-button>
    </div>

    <!-- Type filter -->
    <div class="mb-md">
      <el-radio-group v-model="filterType" size="small" @change="loadRules">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button v-for="[key, label] in ECOSYSTEM_RULE_TYPES" :key="key" :label="key">
          {{ label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="skeleton-group">
      <div v-for="i in 3" :key="i" class="skeleton" style="height:48px;margin-top:8px" />
    </div>

    <!-- Error -->
    <el-alert v-else-if="error" type="error" :title="error" show-icon :closable="false" />

    <!-- Empty -->
    <div v-else-if="rules.length === 0" class="empty-state">
      <p class="text-muted">当前版本暂无生态规则。从类型筛选器中选择类型并添加第一条规则。</p>
    </div>

    <!-- Rule list -->
    <div v-else>
      <div v-for="rule in rules" :key="rule.id" class="card card-interactive mb-sm"
        @click="selectRule(rule)">
        <div class="flex items-center justify-between">
          <div>
            <span class="font-bold">{{ rule.name }}</span>
            <el-tag size="small" type="info" class="ml-sm">
              {{ typeLabel(rule.rule_type) }}
            </el-tag>
          </div>
          <el-button size="small" @click.stop="startEdit(rule)">编辑</el-button>
        </div>
        <p v-if="rule.summary" class="text-sm text-muted mt-xs">{{ rule.summary }}</p>
      </div>
    </div>

    <!-- Pagination -->
    <el-pagination v-if="total > pageSize" class="mt-md" layout="prev, pager, next"
      :total="total" :page-size="pageSize" :current-page="page"
      @current-change="p => { page = p; loadRules() }" />

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="showDialog" :title="editingRule ? '编辑生态规则' : '新增生态规则'" width="560px">
      <el-form v-if="form" label-position="top">
        <el-form-item label="规则类型">
          <el-select v-model="form.rule_type" placeholder="选择类型" :disabled="!!editingRule">
            <el-option v-for="[key, label] in ECOSYSTEM_RULE_TYPES" :key="key"
              :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则名称" required>
          <el-input v-model="form.name" placeholder="如：能力使用必须付出记忆代价" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="3" placeholder="简要描述规则内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRule" :loading="saving">
          {{ editingRule ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { contentProjectApi } from '@/api/contentProject'
import { ECOSYSTEM_RULE_TYPES } from './creativeBibleData.js'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  bibleVersionId: { type: Number, default: null },
  editable: { type: Boolean, default: true }
})

const loading = ref(false)
const error = ref(null)
const rules = ref([])
const filterType = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const showDialog = ref(false)
const editingRule = ref(null)
const form = ref(null)
const saving = ref(false)

function typeLabel(type) {
  const found = ECOSYSTEM_RULE_TYPES.find(([k]) => k === type)
  return found ? found[1] : type
}

async function loadRules() {
  if (!props.bibleVersionId) return
  loading.value = true
  error.value = null
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (filterType.value) params.rule_type = filterType.value
    const res = await contentProjectApi.listEcosystemRules(props.projectId, props.bibleVersionId, params)
    const data = res?.data ?? res
    rules.value = data?.records ?? data?.items ?? []
    total.value = data?.total ?? 0
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function startCreate() {
  editingRule.value = null
  form.value = { rule_type: '', name: '', summary: '' }
  showDialog.value = true
}

function startEdit(rule) {
  editingRule.value = rule
  form.value = {
    rule_type: rule.rule_type,
    name: rule.name,
    summary: rule.summary || ''
  }
  showDialog.value = true
}

function selectRule(rule) {
  startEdit(rule)
}

async function saveRule() {
  if (!form.value.name?.trim()) return
  saving.value = true
  try {
    const data = {
      rule_type: form.value.rule_type,
      name: form.value.name.trim(),
      summary: form.value.summary?.trim() || null,
      revision: editingRule.value?.revision ?? null
    }
    if (editingRule.value) {
      await contentProjectApi.updateEcosystemRule(
        props.projectId, props.bibleVersionId, editingRule.value.id, data)
    } else {
      await contentProjectApi.createEcosystemRule(
        props.projectId, props.bibleVersionId, data)
    }
    showDialog.value = false
    loadRules()
  } finally {
    saving.value = false
  }
}

// Initial load
import { watch } from 'vue'
watch(() => props.bibleVersionId, (val) => { if (val) loadRules() }, { immediate: true })
</script>

<style scoped>
.ecosystem-panel {
  padding: 16px;
}
.empty-state {
  padding: 48px 16px;
  text-align: center;
}
</style>
