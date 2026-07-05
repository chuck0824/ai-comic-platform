<script setup>
import { ref } from 'vue'
import { roleTypeLabel, versionStatusLabel } from '@/utils/agentConfigHelpers'
import AgentVersionPanel from './AgentVersionPanel.vue'
import AgentTestRunPanel from './AgentTestRunPanel.vue'

const props = defineProps({
  definition: { type: Object, default: null },
  versions: { type: Array, default: () => [] },
  draft: { type: Object, default: null },
  blueprints: { type: Array, default: () => [] }
})

const emit = defineEmits(['update-draft', 'validate', 'test', 'publish', 'refresh'])

const activeTab = ref('params')

const bp = computed(() =>
  props.blueprints.find(b => b.roleType === props.definition?.roleType)
)

const editablePrompt = ref('')
const parameters = ref({})

watch(() => props.draft, (d) => {
  if (d) {
    editablePrompt.value = d.editablePrompt || ''
    parameters.value = { ...(d.parameters || {}) }
  }
}, { immediate: true })

const handleSaveDraft = async () => {
  if (!props.draft) return
  await emit('update-draft', props.draft.id, {
    rowVersion: props.draft.rowVersion,
    parameters: parameters.value,
    editablePrompt: editablePrompt.value,
    examples: [],
    modelPolicy: {}
  })
  emit('refresh')
}

const handleValidate = async () => {
  if (!props.draft) return
  await emit('validate', props.draft.id)
}
</script>

<template>
  <div class="editor-panel" v-if="definition">
    <div class="ep-header">
      <h3>{{ definition.name }}</h3>
      <el-tag>{{ roleTypeLabel(definition.roleType) }}</el-tag>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="基础设置" name="base">
        <div class="ep-section">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="名称">{{ definition.name }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ roleTypeLabel(definition.roleType) }}</el-descriptions-item>
            <el-descriptions-item label="框架">{{ bp?.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ definition.lifecycleStatus }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ definition.createdAt }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-tab-pane>

      <el-tab-pane label="方法参数" name="params" v-if="draft">
        <div class="ep-section">
          <div v-if="bp?.parameterSchema?.properties" class="ep-params">
            <div v-for="(schema, key) in bp.parameterSchema.properties" :key="key" class="ep-param-row">
              <label>{{ schema.description || key }}</label>
              <el-slider
                v-if="schema.type === 'integer' || schema.type === 'number'"
                v-model="parameters[key]"
                :min="schema.minimum"
                :max="schema.maximum"
                :step="schema.type === 'integer' ? 1 : 0.1"
                show-input
                size="small"
              />
              <el-select
                v-else-if="schema.enum"
                v-model="parameters[key]"
                size="small"
                style="width:100%"
              >
                <el-option v-for="(opt, idx) in schema.enum" :key="opt" :label="schema.enumLabels?.[idx] || opt" :value="opt" />
              </el-select>
            </div>
          </div>
          <el-empty v-else description="该 Blueprint 无可配置参数" :image-size="40" />
          <el-button type="primary" size="small" @click="handleSaveDraft" style="margin-top:12px">保存参数</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="高级 Prompt" name="prompt" v-if="draft">
        <div class="ep-section">
          <div class="ep-locked">
            <h4>平台锁定 Prompt（只读）</h4>
            <pre>{{ bp?.lockedSystemPrompt || bp?.editablePromptTemplate || '无' }}</pre>
          </div>
          <div class="ep-editable">
            <h4>用户可编辑 Prompt</h4>
            <el-input
              v-model="editablePrompt"
              type="textarea"
              :rows="12"
              placeholder="在此编辑你的创作方法 Prompt...&#10;&#10;可用变量：{{user_method}}、{{task_input}}、{{project_context}}"
            />
          </div>
          <div style="display:flex;gap:8px;margin-top:12px">
            <el-button type="primary" size="small" @click="handleSaveDraft">保存 Prompt</el-button>
            <el-button size="small" @click="handleValidate">校验</el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="版本记录" name="versions">
        <AgentVersionPanel
          :versions="versions"
          :draft="draft"
          @publish="(data) => emit('publish', draft.id, data)"
          @refresh="emit('refresh')"
        />
      </el-tab-pane>

      <el-tab-pane label="试跑对比" name="test" v-if="draft">
        <AgentTestRunPanel
          :version-id="draft.id"
          @test="(data) => emit('test', draft.id, data)"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.editor-panel { }
.ep-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.ep-header h3 { margin: 0; font-size: 18px; font-weight: 600; color: var(--text-primary); }
.ep-section { padding: 12px 0; }
.ep-params { display: flex; flex-direction: column; gap: 16px; }
.ep-param-row { display: flex; flex-direction: column; gap: 4px; }
.ep-param-row label { font-size: 13px; font-weight: 500; color: var(--text-secondary); }
.ep-locked { margin-bottom: 20px; }
.ep-locked h4, .ep-editable h4 { font-size: 14px; font-weight: 600; color: var(--text-secondary); margin: 0 0 8px; }
.ep-locked pre {
  background: var(--bg-surface);
  padding: 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-tertiary);
  white-space: pre-wrap;
  max-height: 200px;
  overflow-y: auto;
}
</style>
