<script setup>
import { ref, computed } from 'vue'
import { roleTypeLabel, createWizardState, wizardStepLabel, isWizardComplete } from '@/utils/agentConfigHelpers'

const props = defineProps({
  blueprints: { type: Array, default: () => [] }
})

const emit = defineEmits(['created', 'close'])

const state = ref(createWizardState())

const currentBp = computed(() =>
  props.blueprints.find(b => b.id === state.value.blueprintId)
)

const selectBlueprint = (bp) => {
  state.value.blueprintId = bp.id
  state.value.step = 'identity'
}

const setIdentity = () => {
  state.value.step = 'configure'
}

const finishConfigure = () => {
  state.value.step = 'test'
}

const handleComplete = () => {
  emit('created', {
    blueprintId: state.value.blueprintId,
    name: state.value.identity?.name || '未命名 Agent',
    description: state.value.identity?.description || ''
  })
}
</script>

<template>
  <el-dialog
    :model-value="true"
    title="新增 Agent"
    width="640px"
    @close="emit('close')"
    destroy-on-close
  >
    <!-- Steps indicator -->
    <el-steps :active="['blueprint','identity','configure','test'].indexOf(state.step)" finish-status="success" align-center style="margin-bottom:24px">
      <el-step title="选择框架" />
      <el-step title="基本信息" />
      <el-step title="配置参数" />
      <el-step title="试跑发布" />
    </el-steps>

    <!-- Step 1: Select Blueprint -->
    <div v-if="state.step === 'blueprint'" class="wiz-step">
      <p class="wiz-desc">选择 Agent 的基础能力框架，定义其角色和权限边界。</p>
      <div class="wiz-cards">
        <div
          v-for="bp in blueprints" :key="bp.id"
          class="wiz-card"
          @click="selectBlueprint(bp)"
        >
          <h4>{{ bp.name }}</h4>
          <el-tag size="small">{{ roleTypeLabel(bp.roleType) }}</el-tag>
          <p>{{ bp.description || '系统内置角色框架' }}</p>
        </div>
      </div>
    </div>

    <!-- Step 2: Identity -->
    <div v-if="state.step === 'identity'" class="wiz-step">
      <p class="wiz-desc">定义 Agent 的名称和用途。</p>
      <el-form label-position="top">
        <el-form-item label="Agent 名称" required>
          <el-input v-model="state.identity.name" placeholder="例如：女频复仇强钩子" maxlength="120" />
        </el-form-item>
        <el-form-item label="用途描述">
          <el-input v-model="state.identity.description" type="textarea" :rows="3" placeholder="描述该 Agent 的创作风格和适用场景" maxlength="1000" />
        </el-form-item>
      </el-form>
      <div class="wiz-actions">
        <el-button @click="state.step = 'blueprint'">上一步</el-button>
        <el-button type="primary" @click="setIdentity" :disabled="!state.identity?.name">下一步</el-button>
      </div>
    </div>

    <!-- Step 3: Configure -->
    <div v-if="state.step === 'configure'" class="wiz-step">
      <p class="wiz-desc">调整 {{ currentBp?.name }} 的结构化参数。</p>
      <div v-if="currentBp?.parameterSchema?.properties" class="wiz-params">
        <div v-for="(schema, key) in currentBp.parameterSchema.properties" :key="key" class="wiz-param-row">
          <label>{{ schema.description || key }}</label>
          <el-slider
            v-if="schema.type === 'integer' || schema.type === 'number'"
            v-model="state.parameters[key]"
            :min="schema.minimum"
            :max="schema.maximum"
            :step="schema.type === 'integer' ? 1 : 0.1"
            :disabled="!currentBp.defaults"
            show-input
            size="small"
          />
          <el-select v-else-if="schema.enum" v-model="state.parameters[key]" size="small" style="width:100%">
            <el-option v-for="(opt, idx) in schema.enum" :key="opt" :label="schema.enumLabels?.[idx] || opt" :value="opt" />
          </el-select>
        </div>
      </div>
      <div class="wiz-actions">
        <el-button @click="state.step = 'identity'">上一步</el-button>
        <el-button type="primary" @click="finishConfigure">下一步</el-button>
      </div>
    </div>

    <!-- Step 4: Test & Publish -->
    <div v-if="state.step === 'test'" class="wiz-step">
      <p class="wiz-desc">Agent 创建完成！保存后可在配置中心中进行试跑和发布。</p>
      <el-alert type="success" title="Agent 已创建为 DRAFT 版本" :closable="false" show-icon style="margin-bottom:16px" />
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="框架">{{ currentBp?.name }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ state.identity?.name }}</el-descriptions-item>
        <el-descriptions-item label="用途">{{ state.identity?.description || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="wiz-actions">
        <el-button @click="state.step = 'configure'">上一步</el-button>
        <el-button type="primary" @click="handleComplete">完成创建</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.wiz-step { min-height: 200px; }
.wiz-desc { font-size: 13px; color: var(--text-secondary); margin-bottom: 16px; }
.wiz-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.wiz-card {
  padding: 16px; border: 1px solid var(--border); border-radius: var(--radius-md);
  cursor: pointer; transition: all .2s;
}
.wiz-card:hover { border-color: var(--accent); box-shadow: var(--shadow-sm); }
.wiz-card h4 { margin: 0 0 6px; font-size: 15px; }
.wiz-card p { font-size: 12px; color: var(--text-tertiary); margin: 6px 0 0; }
.wiz-params { display: flex; flex-direction: column; gap: 16px; }
.wiz-param-row { display: flex; flex-direction: column; gap: 4px; }
.wiz-param-row label { font-size: 13px; font-weight: 500; color: var(--text-secondary); }
.wiz-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
</style>
