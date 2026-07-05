import { ref, reactive } from 'vue'
import { agentApi } from '@/api/agent'
import { ElMessage } from 'element-plus'

export function useAgentConfig() {
  const blueprints = ref([])
  const definitions = ref([])
  const selected = ref(null)
  const loading = ref(false)
  const error = ref('')
  const filter = reactive({ roleType: '', status: 'ACTIVE' })

  const selectedVersions = ref([])
  const selectedDraft = ref(null)

  const load = async () => {
    loading.value = true
    error.value = ''
    try {
      const [bpRes, defsRes] = await Promise.all([
        agentApi.getBlueprints(),
        agentApi.getDefinitions()
      ])
      blueprints.value = bpRes.data || []
      definitions.value = defsRes.data || []
    } catch (e) {
      error.value = e?.message || '加载 Agent 配置失败'
      ElMessage.error(error.value)
    } finally {
      loading.value = false
    }
  }

  const selectDefinition = async (def) => {
    selected.value = def
    selectedVersions.value = []
    selectedDraft.value = null
    try {
      const res = await agentApi.getVersions(def.id)
      selectedVersions.value = res.data || []
      const draft = selectedVersions.value.find(v => v.status === 'DRAFT')
      selectedDraft.value = draft || null
    } catch (e) {
      ElMessage.error('加载版本列表失败')
    }
  }

  const createDefinition = async (data) => {
    try {
      const res = await agentApi.createDefinition(data)
      await load()
      ElMessage.success('Agent 创建成功')
      return res.data
    } catch (e) {
      ElMessage.error(e?.message || '创建失败')
      throw e
    }
  }

  const copyDefinition = async (id) => {
    try {
      await agentApi.copyDefinition(id)
      await load()
      ElMessage.success('Agent 复制成功')
    } catch (e) {
      ElMessage.error(e?.message || '复制失败')
    }
  }

  const archiveDefinition = async (id) => {
    try {
      await agentApi.archiveDefinition(id)
      await load()
      selected.value = null
      ElMessage.success('Agent 已归档')
    } catch (e) {
      ElMessage.error(e?.message || '归档失败')
    }
  }

  const updateDraft = async (versionId, data) => {
    try {
      const res = await agentApi.updateVersion(versionId, data)
      ElMessage.success('草稿已保存')
      return res.data
    } catch (e) {
      ElMessage.error(e?.message || '保存失败')
      throw e
    }
  }

  const validateDraft = async (versionId) => {
    try {
      const res = await agentApi.validateVersion(versionId)
      return res.data
    } catch (e) {
      ElMessage.error(e?.message || '校验失败')
      throw e
    }
  }

  const runTest = async (versionId, data) => {
    try {
      const res = await agentApi.testVersion(versionId, data)
      return res.data
    } catch (e) {
      ElMessage.error(e?.message || '试跑失败')
      throw e
    }
  }

  const publishVersion = async (versionId, data) => {
    try {
      const res = await agentApi.publishVersion(versionId, data)
      await selectDefinition(selected.value)
      ElMessage.success('发布成功')
      return res.data
    } catch (e) {
      ElMessage.error(e?.message || '发布失败')
      throw e
    }
  }

  return {
    blueprints, definitions, selected, loading, error, filter,
    selectedVersions, selectedDraft,
    load, selectDefinition,
    createDefinition, copyDefinition, archiveDefinition,
    updateDraft, validateDraft, runTest, publishVersion
  }
}
