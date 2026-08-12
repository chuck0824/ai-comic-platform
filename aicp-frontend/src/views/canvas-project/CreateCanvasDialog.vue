<template>
  <el-dialog
    :model-value="visible"
    title="新建画布"
    width="560px"
    destroy-on-close
    append-to-body
    @update:model-value="$emit('update:visible', $event)"
    @open="resetForm"
  >
    <el-form :model="form" label-width="120px" :rules="rules" ref="formRef">
      <el-form-item label="画布名称" prop="name">
        <el-input v-model="form.name" maxlength="200" placeholder="输入画布名称" />
      </el-form-item>
      <el-form-item label="关联内容项目">
        <el-switch v-model="linkContent" active-text="现在关联" inactive-text="稍后关联" />
      </el-form-item>
      <template v-if="linkContent">
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
        <el-alert
          v-if="form.productionUnitId && (!sourceContentVersion || !sourceStoryboardVersion)"
          type="warning"
          :closable="false"
          title="当前项目缺少可用的内容版本或分镜版本，请先补齐，或关闭关联后创建空白画布。"
        />
        <el-alert
          v-if="loadError"
          type="error"
          :closable="false"
          title="内容项目或版本信息加载失败">
          <el-button type="primary" link @click="retryLoad">重试</el-button>
        </el-alert>
        <el-form-item v-if="admissionResult && !admissionResult.passed" label="准入状态">
          <el-alert type="warning" :closable="false" show-icon>
            <template #title>生产准入未通过</template>
            <ul style="margin:4px 0;padding-left:16px">
              <li v-for="r in admissionResult.missingRequirements" :key="r.code">{{ r.label }}</li>
            </ul>
          </el-alert>
        </el-form-item>
      </template>
      <el-form-item label="用途" prop="purpose">
        <el-radio-group v-model="form.purpose" @change="onPurposeChange">
          <el-radio value="official">正式方案</el-radio>
          <el-radio value="alternative">备选方案</el-radio>
          <el-radio value="experiment">实验方案</el-radio>
        </el-radio-group>
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
import { validateCanvasDraft, buildIdempotencyKey, buildAdmissionParams } from './canvasProjectViewModel.js'

const auth = useAuthStore()
const props = defineProps({ visible: Boolean })
const emit = defineEmits(['update:visible', 'created'])

const formRef = ref(null)
const submitting = ref(false)
const linkContent = ref(false)
const projects = ref([])
const units = ref([])
const loadError = ref(false)
const admissionResult = ref(null)
const sourceContentVersion = ref(null)
const sourceStoryboardVersion = ref(null)

function blankForm() {
  return {
    name: '', contentProjectId: null, productionUnitType: null,
    productionUnitId: null, sourceContentVersionId: null,
    sourceStoryboardVersionId: null, purpose: 'experiment'
  }
}

const form = ref(blankForm())

const rules = computed(() => {
  const base = {
    name: [{ required: true, message: '请输入画布名称', trigger: 'blur' }]
  }
  if (linkContent.value) {
    base.contentProjectId = [{ required: true, message: '请选择内容项目', trigger: 'change' }]
    base.productionUnitId = [{ required: true, message: '请选择生产单元', trigger: 'change' }]
  }
  return base
})

const canSubmit = computed(() => {
  const missing = validateCanvasDraft(form.value)
  if (missing.length > 0) return false
  if (form.value.purpose === 'official' && admissionResult.value && !admissionResult.value.passed) return false
  return true
})

function resetForm() {
  linkContent.value = false
  form.value = blankForm()
  projects.value = []
  units.value = []
  loadError.value = false
  admissionResult.value = null
  sourceContentVersion.value = null
  sourceStoryboardVersion.value = null
}

watch(linkContent, enabled => {
  if (enabled) {
    form.value.productionUnitType = 'episode' // 当前仅有剧集型生产单元，后续扩展其他类型不在此改造范围
    loadProjects()
    return
  }
  const name = form.value.name
  form.value = { ...blankForm(), name }
  units.value = []
  loadError.value = false
  admissionResult.value = null
  sourceContentVersion.value = null
  sourceStoryboardVersion.value = null
})

async function loadProjects() {
  loadError.value = false
  try {
    const res = await contentProjectApi.list({ page: 1, page_size: 50 })
    projects.value = res?.data?.items || []
  } catch { projects.value = []; loadError.value = true }
}

async function retryLoad() {
  await loadProjects()
}

async function onProjectChange(projectId) {
  form.value.productionUnitId = null
  units.value = []
  loadError.value = false
  try {
    const res = await contentProjectApi.listUnits(projectId)
    units.value = res?.data || []
  } catch { units.value = []; loadError.value = true }
}

async function onUnitChange() {
  sourceContentVersion.value = null
  sourceStoryboardVersion.value = null
  loadError.value = false
  // Load approved content versions for this unit
  try {
    const cvRes = await contentProjectApi.listVersions(form.value.productionUnitId)
    const versions = cvRes?.data || []
    sourceContentVersion.value = versions.find(v => v.status === 'approved') || versions[0] || null
    if (sourceContentVersion.value) {
      form.value.sourceContentVersionId = sourceContentVersion.value.id
    }
  } catch { sourceContentVersion.value = null; loadError.value = true }
  // Load locked storyboard master for this project
  try {
    const smRes = await contentProjectApi.listStoryboardMasters(form.value.contentProjectId)
    const masters = smRes?.data || []
    sourceStoryboardVersion.value = masters.find(m => m.status === 'locked') || masters[0] || null
    if (sourceStoryboardVersion.value) {
      form.value.sourceStoryboardVersionId = sourceStoryboardVersion.value.id
    }
  } catch { sourceStoryboardVersion.value = null; loadError.value = true }
  await checkAdmission()
}

async function onPurposeChange() {
  await checkAdmission()
}

async function checkAdmission() {
  if (!form.value.contentProjectId || !form.value.productionUnitId) return
  try {
    const res = await canvasApi.checkAdmission(buildAdmissionParams(form.value))
    admissionResult.value = res?.data
  } catch { admissionResult.value = null }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请先填写画布名称')
    return
  }

  submitting.value = true
  try {
    const userId = auth.getUserId()
    if (!userId) {
      ElMessage.error('用户信息未加载，请重新登录')
      return
    }
    const key = buildIdempotencyKey(
      userId,
      linkContent.value ? form.value.contentProjectId : null,
      linkContent.value ? form.value.productionUnitId : null,
      linkContent.value ? form.value.sourceContentVersionId : null,
      linkContent.value ? form.value.sourceStoryboardVersionId : null,
      form.value.purpose
    )
    const res = await canvasApi.createProject({
      name: form.value.name,
      content_project_id: linkContent.value ? form.value.contentProjectId : null,
      production_unit_type: linkContent.value ? form.value.productionUnitType : null,
      production_unit_id: linkContent.value ? form.value.productionUnitId : null,
      source_content_version_id: linkContent.value ? form.value.sourceContentVersionId : null,
      source_storyboard_version_id: linkContent.value ? form.value.sourceStoryboardVersionId : null,
      purpose: form.value.purpose,
      owner_id: userId,
      idempotency_key: key
    })
    try {
      emit('created', res?.data)
    } catch {
      ElMessage.warning('画布已创建，请从列表进入')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>
