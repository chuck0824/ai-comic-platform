<template>
  <div class="agent-page">
    <div class="agent-sidebar">
      <div class="sidebar-header"><el-icon><Cpu /></el-icon> Agent 会话</div>
      <el-button size="small" type="primary" class="new-session-btn" @click="createNewSession">
        + 新建会话
      </el-button>
      <div v-for="s in sessions" :key="s.id || s.uuid"
           :class="['session-item', { active: activeSessionId === (s.id || s.uuid) }]"
           @click="selectSession(s)">
        <div class="session-title">{{ s.title || '新会话' }}</div>
        <div class="session-meta">{{ s.status }} · {{ formatDate(s.created_at) }}</div>
      </div>
    </div>

    <div class="agent-main">
      <div v-if="!activeSessionId" class="text-center py-xl text-muted">
        <el-icon :size="32" color="var(--text-tertiary)"><Cpu /></el-icon>
        <p class="mt-sm">选择一个会话或创建新会话开始</p>
      </div>

      <template v-else>
        <!-- Messages -->
        <div ref="messageListRef" class="message-list">
          <div v-for="(msg, i) in messages" :key="i"
               :class="['message', 'msg-' + msg.role]">
            <div class="msg-content">{{ msg.content }}</div>
            <div v-if="msg.tool_calls" class="msg-tools">
              <el-icon><Tools /></el-icon> 工具调用: {{ msg.tool_calls }}
            </div>
            <div v-if="msg.confidence" class="msg-confidence">
              置信度: {{ (msg.confidence * 100).toFixed(0) }}%
            </div>
          </div>

          <!-- Execution Plan -->
          <div v-if="executionPlan" class="execution-plan">
            <h4><el-icon><List /></el-icon> 执行计划</h4>
            <div v-for="(step, i) in executionPlan.steps" :key="i" class="plan-step">
              <span :class="stepStatus(step)"><el-icon :size="14"><component :is="stepStatusIcon(step)" /></el-icon></span>
              {{ step.title || step.tool_name }}
              <span class="text-xs text-muted">{{ step.estimated_seconds || 0 }}s</span>
            </div>
          </div>
        </div>

        <!-- Input -->
        <div class="message-input">
          <el-input v-model="inputText" type="textarea" :rows="2"
                    placeholder="输入自然语言指令... 例如：为第1集生成所有分镜图"
                    @keydown.enter.ctrl="sendMessage" />
          <el-button type="primary" size="small" @click="sendMessage" :loading="sending">
            <el-icon><Promotion /></el-icon> 发送 (Ctrl+Enter)
          </el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { agentApi } from '@/api/agent'

const sessions = ref([])
const activeSessionId = ref(null)
const messages = ref([])
const executionPlan = ref(null)
const inputText = ref('')
const sending = ref(false)
const messageListRef = ref(null)

async function createNewSession() {
  try {
    const res = await agentApi.createSession({ title: '新会话', project_id: null })
    const session = res.data
    sessions.value.unshift(session)
    selectSession(session)
  } catch (e) { console.error(e) }
}

function selectSession(s) {
  activeSessionId.value = s.id || s.uuid
  messages.value = []
  executionPlan.value = null
}

async function sendMessage() {
  if (!inputText.value.trim() || !activeSessionId.value) return
  sending.value = true
  const userMsg = { role: 'user', content: inputText.value }
  messages.value.push(userMsg)
  inputText.value = ''

  try {
    const res = await agentApi.sendMessage(activeSessionId.value, {
      content: userMsg.content,
      project_id: null
    })
    if (res.data) {
      if (res.data.plan) executionPlan.value = res.data.plan
      if (res.data.messages) messages.value.push(...res.data.messages)
      else if (res.data.content) messages.value.push({ role: 'assistant', content: res.data.content,
        confidence: res.data.confidence, tool_calls: res.data.tool_calls })
    }
  } catch (e) { messages.value.push({ role: 'system', content: '请求失败: ' + e.message }) }
  finally { sending.value = false }

  await nextTick()
  if (messageListRef.value) messageListRef.value.scrollTop = messageListRef.value.scrollHeight
}

function stepStatusIcon(step) {
  const s = step.status || 'pending'
  return { pending: 'Clock', running: 'Refresh', succeeded: 'CircleCheck', failed: 'CircleClose' }[s] || 'Clock'
}
function stepStatus(step) {
  return 'step-' + (step.status || 'pending')
}
function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : ''
}
</script>

<style scoped>
.agent-page { display: flex; height: calc(100vh - 60px); background: #0f172a; color: #e0e0e0; --text-secondary:#a1a1aa; }
.agent-sidebar { width: 260px; border-right: 1px solid #1e293b; display: flex; flex-direction: column; }
.sidebar-header { padding: 16px; font-weight: 600; font-size: 15px; border-bottom: 1px solid #1e293b; }
.new-session-btn { margin: 12px; }
.session-item { padding: 12px 16px; cursor: pointer; border-bottom: 1px solid #1e293b; }
.session-item:hover, .session-item.active { background: rgba(79,70,229,0.15); }
.session-title { font-size: 13px; font-weight: 500; }
.session-meta { font-size: 11px; color: #a1a1aa; margin-top: 4px; }
.agent-main { flex: 1; display: flex; flex-direction: column; }
.message-list { flex: 1; overflow-y: auto; padding: 20px; }
.message { margin-bottom: 16px; padding: 12px 16px; border-radius: 10px; max-width: 80%; }
.msg-user { background: #4f46e5; margin-left: auto; }
.msg-assistant { background: #1e293b; }
.msg-system { background: #7f1d1d; color: #fca5a5; font-size: 12px; }
.msg-content { font-size: 14px; line-height: 1.5; white-space: pre-wrap; }
.msg-tools { margin-top: 6px; font-size: 11px; color: #fbbf24; }
.msg-confidence { margin-top: 4px; font-size: 11px; color: #a1a1aa; }
.execution-plan { background: #1a1a2e; border: 1px solid #2a2a3e; border-radius: 10px; padding: 16px; margin-top: 16px; color:#e0e0e0; }
.execution-plan h4 { margin: 0 0 12px; }
.plan-step { padding: 6px 0; font-size: 13px; display: flex; gap: 8px; align-items: center; }
.plan-step span:first-child { width: 20px; }
.message-input { display: flex; gap: 12px; padding: 16px 20px; border-top: 1px solid #1e293b; align-items: flex-end; }
.text-center { text-align: center; } .py-xl { padding: 60px 0; }
.text-muted { color: #a1a1aa; } .text-xs { font-size: 11px; }
</style>
