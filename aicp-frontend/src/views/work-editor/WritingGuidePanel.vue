<template>
  <div class="writing-guide-panel">
    <h3 class="text-lg font-bold mb-md">三级写作口径</h3>

    <!-- Scope tabs -->
    <el-tabs v-model="activeScope" class="mb-md">
      <el-tab-pane label="项目级口径" name="project" />
      <el-tab-pane label="角色级口吻" name="character" />
      <el-tab-pane label="单集/单章覆盖" name="content_unit" />
    </el-tabs>

    <!-- Non-overridable warning -->
    <el-alert type="warning" :closable="false" class="mb-md" show-icon>
      平台规则和合规禁区（hard_bans、platform_rules、compliance_rules）不可被角色级或单集级口径覆盖。
    </el-alert>

    <!-- Project scope -->
    <div v-if="activeScope === 'project'">
      <el-form v-if="projectGuide" label-position="top">
        <el-form-item v-for="field in PROJECT_FIELDS" :key="field" :label="FIELD_LABELS[field] || field">
          <el-input v-if="field === 'terminology'"
            v-model="projectGuide[field]"
            type="textarea" :rows="2" :placeholder="`输入${FIELD_LABELS[field]}`" />
          <el-input v-else
            v-model="projectGuide[field]"
            :placeholder="`输入${FIELD_LABELS[field]}`" />
        </el-form-item>
        <el-button type="primary" @click="saveGuide('project', 0)">保存项目级口径</el-button>
      </el-form>
      <div v-else class="empty-state">
        <p class="text-muted">尚未配置项目级写作口径</p>
        <el-button type="primary" class="mt-md" @click="initProjectGuide">创建项目级口径</el-button>
      </div>
    </div>

    <!-- Character scope -->
    <div v-if="activeScope === 'character'">
      <el-form label-position="top">
        <el-form-item label="选择角色">
          <el-select v-model="selectedCharId" placeholder="选择角色 ID" clearable>
            <el-option v-for="id in availableCharIds" :key="id" :label="`角色 ${id}`" :value="id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="selectedCharId && charGuides[selectedCharId]" class="mt-md">
        <el-form label-position="top">
          <el-form-item v-for="field in CHARACTER_FIELDS" :key="field"
            :label="FIELD_LABELS[field] || field">
            <el-input v-model="charGuides[selectedCharId][field]"
              :placeholder="`输入${FIELD_LABELS[field]}`" />
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="saveGuide('character', selectedCharId)">保存角色级口吻</el-button>
      </div>
    </div>

    <!-- Unit scope -->
    <div v-if="activeScope === 'content_unit'">
      <el-form v-if="unitGuide" label-position="top">
        <el-form-item v-for="field in UNIT_FIELDS" :key="field"
          :label="FIELD_LABELS[field] || field">
          <el-input v-if="field === 'must_include' || field === 'must_avoid'"
            v-model="unitGuide[field]"
            type="textarea" :rows="2" :placeholder="`输入${FIELD_LABELS[field]}`" />
          <el-input v-else
            v-model="unitGuide[field]"
            :placeholder="`输入${FIELD_LABELS[field]}`" />
        </el-form-item>
        <el-button type="primary" @click="saveGuide('content_unit', unitId)">保存单集口径</el-button>
      </el-form>
      <div v-else class="empty-state">
        <p class="text-muted">选择单元后配置单集/单章覆盖口径</p>
        <el-input v-model="unitId" placeholder="输入单元 ID" class="mt-md" style="max-width:200px" />
        <el-button type="primary" class="mt-md" @click="initUnitGuide">初始化单集口径</el-button>
      </div>
    </div>

    <!-- Resolution preview -->
    <div class="mt-lg">
      <el-button @click="previewResolution" :loading="resolving">解析预览</el-button>
      <div v-if="resolution" class="card mt-md">
        <h4 class="font-bold mb-sm">最终生效口径</h4>
        <div v-for="(value, key) in resolution.resolved" :key="key" class="mb-xs">
          <span class="font-bold">{{ FIELD_LABELS[key] || key }}:</span>
          <span>{{ typeof value === 'object' ? JSON.stringify(value) : value }}</span>
          <span class="text-sm text-muted ml-sm">
            (来源: {{ resolution.source_by_field?.[key] || 'project' }})
          </span>
        </div>
        <div v-if="resolution.conflicts?.length" class="mt-sm">
          <el-tag v-for="c in resolution.conflicts" :key="c" type="danger" size="small" class="mr-xs">
            {{ c }}
          </el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { contentProjectApi } from '@/api/contentProject'
import {
  PROJECT_FIELDS, CHARACTER_FIELDS, UNIT_FIELDS,
  FIELD_LABELS, NON_OVERRIDABLE
} from './creativeBibleData.js'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  bibleVersionId: { type: Number, default: null }
})

const activeScope = ref('project')
const projectGuide = ref(null)
const charGuides = reactive({})
const unitGuide = ref(null)
const selectedCharId = ref(null)
const unitId = ref(null)
const availableCharIds = ref([])

const resolution = ref(null)
const resolving = ref(false)

function initProjectGuide() {
  projectGuide.value = {}
  PROJECT_FIELDS.forEach(f => { projectGuide.value[f] = '' })
}

async function saveGuide(scopeType, scopeId) {
  const data = scopeType === 'project' ? projectGuide.value
    : scopeType === 'character' ? charGuides[scopeId]
    : unitGuide.value
  if (!data) return
  try {
    await contentProjectApi.saveWritingGuide(props.projectId, props.bibleVersionId, {
      scope_type: scopeType,
      scope_id: scopeId,
      guide: data
    })
  } catch (e) {
    // handled by interceptor
  }
}

function initUnitGuide() {
  unitGuide.value = {}
  UNIT_FIELDS.forEach(f => { unitGuide.value[f] = '' })
}

async function previewResolution() {
  resolving.value = true
  try {
    const res = await contentProjectApi.resolveWritingGuide(props.projectId, props.bibleVersionId, {
      content_unit_id: unitId.value ? Number(unitId.value) : null,
      character_ids: selectedCharId.value ? [Number(selectedCharId.value)] : []
    })
    resolution.value = res?.data ?? res
  } finally {
    resolving.value = false
  }
}
</script>

<style scoped>
.writing-guide-panel {
  padding: 16px;
}
.empty-state {
  padding: 32px 16px;
  text-align: center;
}
</style>
