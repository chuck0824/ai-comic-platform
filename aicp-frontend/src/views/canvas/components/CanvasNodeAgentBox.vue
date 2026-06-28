<template>
  <div :class="['node-agent-box', { embedded }]" @mousedown.stop>
    <textarea
      v-model="instruction"
      class="agent-input"
      placeholder="写下你想讲的故事、场景或角色设定。例如：一个来自未来的机器人，在城市屋顶看星星。"
      @keydown.enter.meta.prevent="submitPlan"
      @keydown.enter.ctrl.prevent="submitPlan"
    />

    <div class="agent-controls">
      <div class="model-picker" @click="modelOpen = !modelOpen">
        <span class="model-icon">✦</span>
        <div>
          <strong>{{ selectedModel?.model_name || '选择模型' }}</strong>
          <small>{{ selectedModel?.description || '模型数据来自平台 AI API' }}</small>
        </div>
        <em>⌃</em>
      </div>

      <div v-if="modelOpen" class="model-menu">
        <button
          v-for="model in models"
          :key="model.model_id"
          :class="{ active: selectedModel?.model_id === model.model_id, disabled: model.status !== 'available' }"
          :disabled="model.status !== 'available'"
          @click.stop="selectModel(model)"
        >
          <span>✦</span>
          <div>
            <strong>{{ model.model_name }}</strong>
            <small>{{ model.description }}</small>
          </div>
          <em>{{ model.estimated_latency }}</em>
        </button>
      </div>

      <div class="agent-tool-icons">
        <button title="翻译">文A</button>
        <button title="快速模式">✦</button>
        <button title="收起" @click="$emit('close')">-</button>
      </div>

      <div class="usage-tip">
        <span v-if="plan">
          预计 {{ plan.usage_estimate.input_tokens_estimated }} / {{ plan.usage_estimate.output_tokens_estimated }} tokens · {{ plan.usage_estimate.estimated_credits }} 积分
        </span>
      </div>

      <button class="agent-send" :disabled="loading" @click="submitPlan">
        {{ loading ? '...' : '↑' }}
      </button>
    </div>

    <div v-if="plan" class="agent-result">
      <div class="result-grid">
        <section>
          <strong>原内容</strong>
          <p>{{ plan.original_content || '空文本节点' }}</p>
        </section>
        <section>
          <strong>修改后</strong>
          <p>{{ plan.revised_content }}</p>
        </section>
      </div>
      <div class="result-actions">
        <button @click="submitPlan">重新生成</button>
        <button @click="plan = null">取消</button>
        <button class="primary" :disabled="applying" @click="applyResult">
          {{ applying ? '应用中' : '应用到文本节点' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { canvasAgentApi } from '@/api/canvas'

const props = defineProps({
  projectId: { type: String, required: true },
  node: { type: Object, required: true },
  localMode: { type: Boolean, default: false },
  embedded: { type: Boolean, default: false }
})

const emit = defineEmits(['applied', 'close'])

const models = ref([])
const selectedModel = ref(null)
const modelOpen = ref(false)
const instruction = ref('')
const plan = ref(null)
const loading = ref(false)
const applying = ref(false)

const currentContent = computed(() => {
  const data = readNodeData(props.node)
  return data.prompt || data.content || ''
})

watch(() => props.node?.uuid || props.node?.id, () => {
  instruction.value = ''
  plan.value = null
  loadModels()
})

onMounted(loadModels)

async function loadModels() {
  try {
    const res = await canvasAgentApi.getModels({ node_type: 'text', agent_type: 'text_agent' })
    models.value = res.data.models || []
    selectedModel.value = models.value.find(item => item.status === 'available') || models.value[0] || null
  } catch (e) {
    if (props.localMode) {
      models.value = [{
        model_id: 'local-text-agent',
        model_name: '本地文本代理',
        description: '后端不可用时的本地文本编辑预览',
        estimated_latency: '0s',
        status: 'available'
      }]
      selectedModel.value = models.value[0]
      return
    }
    ElMessage.error('模型列表加载失败，请稍后重试')
  }
}

function selectModel(model) {
  selectedModel.value = model
  modelOpen.value = false
}

async function submitPlan() {
  if (!instruction.value.trim()) {
    ElMessage.warning('请输入你想如何修改当前文本')
    return
  }
  if (!selectedModel.value) {
    ElMessage.warning('暂无可用文本模型')
    return
  }
  loading.value = true
  try {
    if (props.localMode) {
      const revised = reviseTextLocally(instruction.value.trim(), currentContent.value)
      plan.value = {
        agent_type: 'text_agent',
        selected_model: selectedModel.value,
        usage_estimate: estimateLocalUsage(instruction.value.trim(), currentContent.value),
        original_content: currentContent.value,
        revised_content: revised,
        need_confirm: true
      }
      return
    }
    const res = await canvasAgentApi.planTextNode(props.projectId, {
      node_id: nodeKey(props.node),
      model_id: selectedModel.value.model_id,
      instruction: instruction.value.trim(),
      current_content: currentContent.value,
      billing_mode: 'token'
    })
    plan.value = res.data
  } catch (e) {
    ElMessage.error('文本修改失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function applyResult() {
  if (!plan.value) return
  applying.value = true
  try {
    if (props.localMode) {
      emit('applied', {
        ...props.node,
        status: 'ready',
        input_data: JSON.stringify({
          text_mode: 'prompt',
          prompt: plan.value.revised_content,
          content: plan.value.revised_content,
          source: 'text_node_agent',
          agent_type: 'text_agent',
          model_id: selectedModel.value?.model_id
        })
      })
      plan.value = null
      instruction.value = ''
      ElMessage.success('已应用到文本节点')
      return
    }
    const res = await canvasAgentApi.applyTextNode(props.projectId, {
      node_id: nodeKey(props.node),
      model_id: selectedModel.value?.model_id,
      revised_content: plan.value.revised_content
    })
    emit('applied', res.data)
    plan.value = null
    instruction.value = ''
    ElMessage.success('已应用到文本节点')
  } catch (e) {
    ElMessage.error('应用失败，请稍后重试')
  } finally {
    applying.value = false
  }
}

function nodeKey(node) {
  return node?.uuid || String(node?.id || '')
}

function readNodeData(node) {
  if (!node) return {}
  try {
    const raw = node.input_data ?? node.inputData ?? node.data ?? {}
    return typeof raw === 'string' ? JSON.parse(raw || '{}') : raw
  } catch {
    return {}
  }
}

function reviseTextLocally(command, content) {
  const base = (content || '').trim()
  if (!base) return command
  if (command.includes('精简') || command.includes('简短') || command.includes('缩写')) {
    return base.length > 120 ? base.slice(0, 120) + '。' : base
  }
  if (command.includes('扩写') || command.includes('丰富') || command.includes('详细')) {
    return `${base}\n\n补充方向：${command}。在保留原有设定的基础上，增加场景细节、人物动作、情绪变化和叙事节奏。`
  }
  return `${base}\n\n修改要求：${command}`
}

function estimateLocalUsage(command, content) {
  const inputTokens = Math.max(1, Math.ceil(((command || '') + (content || '')).length / 1.8))
  const outputTokens = Math.max(180, Math.ceil((content || command || '').length / 1.8) + 240)
  return {
    input_tokens_estimated: inputTokens,
    output_tokens_estimated: outputTokens,
    estimated_cost: 0,
    estimated_credits: 0,
    billing_mode: 'local'
  }
}
</script>

<style scoped>
.node-agent-box {
  position: absolute;
  z-index: 80;
  box-sizing: border-box;
  min-height: 192px;
  border: 1px solid #3f3f46;
  border-radius: 18px;
  background: rgba(38, 38, 38, .98);
  box-shadow: 0 22px 70px rgba(0,0,0,.45);
  color: #e5e7eb;
  padding: 22px;
}
.node-agent-box.embedded {
  position: static;
  z-index: auto;
  width: 100%;
  min-height: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  padding: 0;
}
.embedded .agent-input {
  min-height: 104px;
  padding: 12px;
  border: 1px solid #3f4658;
  border-radius: 10px;
  background: #111827;
  font-size: 14px;
}
.embedded .agent-controls { margin-top: 10px; flex-wrap: wrap; }
.embedded .model-picker { min-width: 156px; padding: 7px 10px; }
.embedded .model-menu { top: 46px; bottom: auto; width: min(420px, 100%); }
.embedded .model-menu button { padding: 10px; gap: 10px; }
.embedded .model-menu button > span { width: 34px; height: 34px; font-size: 16px; }
.embedded .model-menu strong { font-size: 14px; }
.embedded .model-menu small { font-size: 12px; }
.embedded .agent-tool-icons { display: none; }
.embedded .result-grid { grid-template-columns: 1fr; }
.agent-input {
  width:100%;
  min-height:118px;
  resize:none;
  border:0;
  outline:none;
  background:transparent;
  color:#f4f4f5;
  font-size:24px;
  line-height:1.6;
}
.agent-input::placeholder { color:#8b8b90; }
.result-actions button {
  border:1px solid #3f3f46;
  background:#27272a;
  color:#d4d4d8;
  border-radius:8px;
  padding:8px 14px;
  min-height:36px;
  cursor:pointer;
}
.result-actions button:hover { border-color:#71717a; color:#fff; }
.agent-controls { position:relative; display:flex; align-items:center; gap:10px; }
.model-picker {
  display:flex;
  align-items:center;
  gap:10px;
  min-width:170px;
  max-width:260px;
  border-radius:12px;
  background:#3f3f46;
  padding:9px 12px;
  cursor:pointer;
}
.model-picker strong, .model-menu strong { display:block; color:#fff; font-size:14px; }
.model-picker small { display:none; }
.model-menu small { display:block; color:#a1a1aa; font-size:16px; margin-top:4px; }
.model-menu em {
  margin-left:auto;
  font-style:normal;
  color:#d4d4d8;
  background:#52525b;
  border-radius:999px;
  padding:3px 8px;
  font-size:12px;
}
.model-picker em {
  margin-left:auto;
  font-style:normal;
  color:#a1a1aa;
  font-size:18px;
  line-height:1;
}
.model-icon { color:#fff; }
.model-menu {
  position:absolute;
  left:0;
  bottom:50px;
  width:520px;
  max-height:330px;
  overflow:auto;
  border:1px solid #3f3f46;
  border-radius:16px;
  background:#262626;
  padding:8px;
  box-shadow:0 18px 42px rgba(0,0,0,.5);
}
.model-menu button {
  width:100%;
  display:flex;
  align-items:center;
  gap:18px;
  border:0;
  border-radius:12px;
  background:transparent;
  color:#fff;
  padding:16px;
  cursor:pointer;
  text-align:left;
}
.model-menu button > span {
  width:48px;
  height:48px;
  border-radius:12px;
  display:flex;
  align-items:center;
  justify-content:center;
  background:#3f3f46;
  font-size:24px;
  flex-shrink:0;
}
.model-menu strong { font-size:24px; }
.model-menu button.active, .model-menu button:hover { background:#4b5563; }
.model-menu button.disabled { opacity:.4; cursor:not-allowed; }
.agent-tool-icons { display:flex; align-items:center; gap:10px; margin-left:auto; }
.agent-tool-icons button {
  border:0;
  background:transparent;
  color:#d4d4d8;
  min-width:36px;
  min-height:36px;
  height:36px;
  border-radius:8px;
  font-size:18px;
  cursor:pointer;
}
.agent-tool-icons button:hover { background:#3f3f46; color:#fff; }
.usage-tip { color:#a1a1aa; font-size:12px; white-space:nowrap; }
.agent-send, .result-actions .primary {
  border:0;
  border-radius:14px;
  background:#d4d4d8;
  color:#111;
  font-weight:800;
  width:44px;
  height:44px;
  padding:0;
  font-size:28px;
  line-height:1;
  cursor:pointer;
}
.agent-send:disabled, .result-actions .primary:disabled { opacity:.55; cursor:not-allowed; }
.agent-result { margin-top:14px; border-top:1px solid #3f3f46; padding-top:12px; }
.result-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
.result-grid section { background:#18181b; border:1px solid #333; border-radius:12px; padding:12px; min-height:120px; }
.result-grid strong { color:#d4d4d8; font-size:12px; }
.result-grid p { color:#f4f4f5; white-space:pre-wrap; line-height:1.7; margin:8px 0 0; }
.result-actions { display:flex; justify-content:flex-end; gap:8px; margin-top:10px; }
</style>
