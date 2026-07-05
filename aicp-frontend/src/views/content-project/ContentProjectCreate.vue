<template>
  <div class="project-create">
    <div class="page-header">
      <h2>新建内容项目</h2>
      <p class="subtitle">选择创作模式和来源，AI 辅助生成高质量剧本</p>
    </div>

    <!-- Mode Selection -->
    <div class="section">
      <label class="section-label">创作模式</label>
      <div class="mode-grid">
        <div
          v-for="m in modes"
          :key="m.key"
          :class="['mode-card', { selected: form.creationMode === m.key }]"
          @click="form.creationMode = m.key"
          role="radio"
          :aria-checked="form.creationMode === m.key"
          tabindex="0"
          @keydown.enter="form.creationMode = m.key"
          @keydown.space.prevent="form.creationMode = m.key"
        >
          <div class="mode-icon">
            <el-icon :size="24"><component :is="m.icon" /></el-icon>
          </div>
          <div class="mode-body">
            <div class="mode-label">{{ m.label }}</div>
            <div class="mode-desc">{{ m.desc }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Source Mode -->
    <div class="section">
      <label class="section-label">来源方式</label>
      <div class="source-grid">
        <div
          :class="['source-card', { selected: form.sourceMode === 'ai_manual' }]"
          @click="form.sourceMode = 'ai_manual'"
          role="radio"
          :aria-checked="form.sourceMode === 'ai_manual'"
          tabindex="0"
          @keydown.enter="form.sourceMode = 'ai_manual'"
          @keydown.space.prevent="form.sourceMode = 'ai_manual'"
        >
          <el-icon :size="22"><MagicStick /></el-icon>
          <div class="source-body">
            <div class="source-label">AI 手动创作</div>
            <div class="source-desc">输入故事种子，AI引导创作</div>
          </div>
        </div>
        <div
          :class="['source-card', { selected: form.sourceMode === 'uploaded' }]"
          @click="form.sourceMode = 'uploaded'"
          role="radio"
          :aria-checked="form.sourceMode === 'uploaded'"
          tabindex="0"
          @keydown.enter="form.sourceMode = 'uploaded'"
          @keydown.space.prevent="form.sourceMode = 'uploaded'"
        >
          <el-icon :size="22"><UploadFilled /></el-icon>
          <div class="source-body">
            <div class="source-label">上传剧本</div>
            <div class="source-desc">上传TXT/DOCX，AI自动解析</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Start Content -->
    <div class="section">
      <label class="section-label" for="start-content">起始内容</label>
      <el-input
        id="start-content"
        v-model="form.startContent"
        type="textarea"
        :rows="4"
        placeholder="输入故事种子、人物设定或开篇内容…"
        maxlength="20000"
        show-word-limit
      />
    </div>

    <!-- Content Goal -->
    <div class="section">
      <label class="section-label" for="content-goal">内容目标</label>
      <el-input
        id="content-goal"
        v-model="form.contentGoal"
        placeholder="如：追更、完本、IP改编…"
        maxlength="50"
      />
    </div>

    <!-- Actions -->
    <div class="actions">
      <el-button type="primary" :loading="submitting" @click="handleCreate" :disabled="!canSubmit" size="large">
        <el-icon v-if="!submitting"><Promotion /></el-icon>
        创建项目
      </el-button>
      <el-button @click="$router.push('/script-gen')" size="large">取消</el-button>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick, UploadFilled, VideoCameraFilled, Reading, Film, Promotion } from '@element-plus/icons-vue'
import { contentProjectApi } from '@/api/contentProject'

const router = useRouter()
const submitting = ref(false)
const error = ref('')

const modes = [
  { key: 'short_drama', label: '短剧', icon: VideoCameraFilled, desc: '1-3分钟/集，快速产出' },
  { key: 'long_form', label: '长篇', icon: Reading, desc: '传统长篇小说创作' },
  { key: 'tvc', label: 'TVC', icon: Film, desc: '广告/宣传片脚本' }
]

const form = reactive({
  creationMode: 'short_drama',
  sourceMode: 'ai_manual',
  startContent: '',
  contentGoal: '追更'
})

const canSubmit = computed(() => form.creationMode && form.sourceMode && form.contentGoal)

async function handleCreate() {
  submitting.value = true
  error.value = ''
  try {
    const res = await contentProjectApi.create({
      name: form.startContent.slice(0, 50) || '未命名项目',
      creation_mode: form.creationMode,
      source_mode: form.sourceMode,
      start_content: form.startContent,
      content_goal: form.contentGoal,
      tenant_type: 'personal'
    })
    const project = res.data
    router.push(`/script-gen/${project.id}/workspace`)
  } catch (e) {
    error.value = e.response?.data?.message || '创建失败，请重试'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.project-create {
  max-width: 640px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 28px;
}
.page-header h2 {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 4px 0;
  letter-spacing: -.01em;
}
.subtitle {
  color: var(--text-secondary);
  font-size: 14px;
  margin: 0;
}

.section {
  margin-bottom: 24px;
}
.section-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--text-primary);
}

/* Mode grid */
.mode-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.mode-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 12px;
  border: 2px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color .2s ease, box-shadow .2s ease, transform .15s ease, background .2s ease;
  text-align: center;
  background: var(--bg-surface);
}
.mode-card:hover {
  border-color: var(--accent-border);
  box-shadow: var(--shadow-md);
}
.mode-card:active {
  transform: scale(0.97);
}
.mode-card.selected {
  border-color: var(--accent);
  background: var(--accent-bg);
  box-shadow: 0 0 0 1px var(--accent);
}
.mode-card:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.mode-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-sm);
  background: var(--accent-bg);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background .2s ease, color .2s ease;
}
.mode-card.selected .mode-icon {
  background: var(--accent);
  color: #fff;
}
.mode-label {
  font-size: 14px;
  font-weight: 600;
}
.mode-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.4;
}

/* Source grid */
.source-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.source-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border: 2px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color .2s ease, box-shadow .2s ease, transform .15s ease, background .2s ease;
  background: var(--bg-surface);
  color: var(--accent);
}
.source-card:hover {
  border-color: var(--accent-border);
  box-shadow: var(--shadow-md);
}
.source-card:active {
  transform: scale(0.97);
}
.source-card.selected {
  border-color: var(--accent);
  background: var(--accent-bg);
  box-shadow: 0 0 0 1px var(--accent);
}
.source-card:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.source-body {
  flex: 1;
  min-width: 0;
}
.source-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.source-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
}

/* Actions */
.actions {
  display: flex;
  gap: 12px;
  padding-top: 4px;
}
.error-text {
  color: var(--danger);
  font-size: 13px;
  margin-top: 12px;
}

/* Responsive */
@media (max-width: 600px) {
  .mode-grid {
    grid-template-columns: 1fr;
  }
  .source-grid {
    grid-template-columns: 1fr;
  }
  .mode-card {
    flex-direction: row;
    text-align: left;
    padding: 14px 16px;
    gap: 12px;
  }
}
</style>
