<template>
  <el-dialog :model-value="visible" title="新建画布" width="560px" @update:model-value="$emit('update:visible', $event)" @open="resetForm">
    <el-form :model="form" label-width="120px" :rules="rules" ref="formRef">
      <el-form-item label="画布名称" prop="name">
        <el-input v-model="form.name" maxlength="200" placeholder="输入画布名称" />
      </el-form-item>
      <el-form-item label="所属内容项目" prop="contentProjectId">
        <el-select v-model="form.contentProjectId" placeholder="选择内容项目" style="width:100%" @change="onProjectChange">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="生产单元" prop="productionUnitId">
        <el-select v-model="form.productionUnitId" placeholder="先选择内容项目" style="width:100%" :disabled="!form.contentProjectId" @change="onUnitChange">
          <el-option v-for="u in units" :key="u.id" :label="u.title || `单元 ${u.id}`" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源内容版本" v-if="sourceContentVersion">
        <span class="version-info">v{{ sourceContentVersion.versionNo }} — {{ sourceContentVersion.status }}</span>
      </el-form-item>
      <el-form-item label="来源分镜版本" v-if="sourceStoryboardVersion">
        <span class="version-info">修订 {{ sourceStoryboardVersion.revision || 1 }} — {{ sourceStoryboardVersion.status }}</span>
      </el-form-item>
      <el-form-item label="用途" prop="purpose">
        <el-radio-group v-model="form.purpose" @change="onPurposeChange">
          <el-radio value="official">正式方案</el-radio>
          <el-radio value="alternative">备选方案</el-radio>
          <el-radio value="experiment">实验方案</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="admissionResult && !admissionResult.passed" label="准入状态">
        <el-alert type="warning" :closable="false" show-icon>
          <template #title>生产准入未通过</template>
          <ul style="margin:4px 0;padding-left:16px">
            <li v-for="r in admissionResult.missingRequirements" :key="r.code">{{ r.label }}</li>
          </ul>
        </el-alert>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :disabled="!canSubmit" :loading="submitting" @click="submit">创建画布</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { canvasApi } from '@/api/canvas.js'
import { contentProjectApi } from '@/api/contentProject.js'
import { validateCanvasDraft, buildIdempotencyKey } from './canvasProjectViewModel.js'

const auth = useAuthStore()
const props = defineProps({ visible: Boolean })
const emit = defineEmits(['update:visible', 'created'])

const formRef = ref(null)
const submitting = ref(false)
const projects = ref([])
const units = ref([])
const admissionResult = ref(null)
const sourceContentVersion = ref(null)
const sourceStoryboardVersion = ref(null)

const form = ref({
  name: '', contentProjectId: null, productionUnitType: 'episode',
  productionUnitId: null, sourceContentVersionId: 1,
  sourceStoryboardVersionId: 1, purpose: 'official'
})

const rules = {
  name: [{ required: true, message: '请输入画布名称', trigger: 'blur' }],
  contentProjectId: [{ required: true, message: '请选择内容项目', trigger: 'change' }],
  productionUnitId: [{ required: true, message: '请选择生产单元', trigger: 'change' }]
}

const canSubmit = computed(() => {
  const missing = validateCanvasDraft(form.value)
  if (missing.length > 0) return false
  if (form.value.purpose === 'official' && admissionResult.value && !admissionResult.value.passed) return false
  return true
})

function resetForm() {
  form.value = { name: '', contentProjectId: null, productionUnitType: 'episode', productionUnitId: null, sourceContentVersionId: null, sourceStoryboardVersionId: null, purpose: 'official' }
  admissionResult.value = null
  sourceContentVersion.value = null
  sourceStoryboardVersion.value = null
  loadProjects()
}

async function loadProjects() {
  try {
    const res = await contentProjectApi.list({ page: 1, page_size: 50 })
    projects.value = res?.data?.items || []
  } catch { projects.value = [] }
}

async function onProjectChange(projectId) {
  form.value.productionUnitId = null
  units.value = []
  try {
    const res = await contentProjectApi.listUnits(projectId)
    units.value = res?.data || []
  } catch { units.value = [] }
}

async function onUnitChange() {
  sourceContentVersion.value = null
  sourceStoryboardVersion.value = null
  // Load approved content versions for this unit
  try {
    const cvRes = await contentProjectApi.listVersions(form.value.productionUnitId)
    const versions = cvRes?.data || []
    sourceContentVersion.value = versions.find(v => v.status === 'approved') || versions[0] || null
    if (sourceContentVersion.value) {
      form.value.sourceContentVersionId = sourceContentVersion.value.id
    }
  } catch { sourceContentVersion.value = null }
  // Load locked storyboard master for this project
  try {
    const smRes = await contentProjectApi.listStoryboardMasters(form.value.contentProjectId)
    const masters = smRes?.data || []
    sourceStoryboardVersion.value = masters.find(m => m.status === 'locked') || masters[0] || null
    if (sourceStoryboardVersion.value) {
      form.value.sourceStoryboardVersionId = sourceStoryboardVersion.value.id
    }
  } catch { sourceStoryboardVersion.value = null }
  await checkAdmission()
}

async function onPurposeChange() {
  await checkAdmission()
}

async function checkAdmission() {
  if (!form.value.contentProjectId || !form.value.productionUnitId) return
  try {
    const res = await canvasApi.checkAdmission({
      content_project_id: form.value.contentProjectId,
      production_unit_id: form.value.productionUnitId,
      purpose: form.value.purpose
    })
    admissionResult.value = res?.data
  } catch { admissionResult.value = null }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const userId = auth.user?.id
    if (!userId) { ElMessage.error('用户信息未加载'); return }
    const key = buildIdempotencyKey(userId, form.value.contentProjectId, form.value.productionUnitId, form.value.sourceContentVersionId, form.value.sourceStoryboardVersionId, form.value.purpose)
    const res = await canvasApi.createProject({
      name: form.value.name,
      content_project_id: form.value.contentProjectId,
      production_unit_type: form.value.productionUnitType,
      production_unit_id: form.value.productionUnitId,
      source_content_version_id: form.value.sourceContentVersionId,
      source_storyboard_version_id: form.value.sourceStoryboardVersionId,
      purpose: form.value.purpose,
      owner_id: userId,
      idempotency_key: key
    })
    emit('created', res?.data)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>
