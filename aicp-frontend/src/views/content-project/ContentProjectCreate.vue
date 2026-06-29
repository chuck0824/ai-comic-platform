<template>
  <div class="content-project-create" style="max-width:640px;margin:0 auto">
    <h2 class="text-xl font-bold mb-lg">新建内容项目</h2>

    <!-- Mode Selection -->
    <div class="mb-lg">
      <label class="text-sm font-semibold mb-sm block">创作模式</label>
      <div class="flex gap-md">
        <div v-for="m in modes" :key="m.key"
             :class="['card card-hover flex-1 text-center p-md', { 'selected': form.creationMode === m.key }]"
             @click="form.creationMode = m.key">
          <div class="text-lg mb-xs">{{ m.icon }}</div>
          <div class="font-semibold">{{ m.label }}</div>
          <div class="text-xs text-muted">{{ m.desc }}</div>
        </div>
      </div>
    </div>

    <!-- Source Mode -->
    <div class="mb-lg">
      <label class="text-sm font-semibold mb-sm block">来源方式</label>
      <div class="flex gap-md">
        <div :class="['card card-hover flex-1 text-center p-md', { 'selected': form.sourceMode === 'ai_manual' }]"
             @click="form.sourceMode = 'ai_manual'">
          <div class="font-semibold">AI 手动创作</div>
          <div class="text-xs text-muted">输入故事种子，AI引导创作</div>
        </div>
        <div :class="['card card-hover flex-1 text-center p-md', { 'selected': form.sourceMode === 'uploaded' }]"
             @click="form.sourceMode = 'uploaded'">
          <div class="font-semibold">上传剧本</div>
          <div class="text-xs text-muted">上传TXT/DOCX，AI自动解析</div>
        </div>
      </div>
    </div>

    <!-- Start Content -->
    <div class="mb-lg">
      <label class="text-sm font-semibold mb-sm block">起始内容</label>
      <el-input v-model="form.startContent" type="textarea" :rows="4"
                placeholder="输入故事种子、人物设定或开篇内容…"
                maxlength="20000" show-word-limit />
    </div>

    <!-- Content Goal -->
    <div class="mb-lg">
      <label class="text-sm font-semibold mb-sm block">内容目标</label>
      <el-input v-model="form.contentGoal" placeholder="如：追更、完本、IP改编…" maxlength="50" />
    </div>

    <!-- Submit -->
    <div class="flex gap-md">
      <el-button type="primary" :loading="submitting" @click="handleCreate" :disabled="!canSubmit">
        创建项目
      </el-button>
      <el-button @click="$router.push('/script-gen')">取消</el-button>
    </div>

    <p v-if="error" class="text-danger text-sm mt-sm">{{ error }}</p>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { contentProjectApi } from '@/api/contentProject'

const router = useRouter()
const submitting = ref(false)
const error = ref('')

const modes = [
  { key: 'short_drama', label: '短剧', icon: '📺', desc: '1-3分钟/集，快速产出' },
  { key: 'long_form', label: '长篇', icon: '📖', desc: '传统长篇小说创作' },
  { key: 'tvc', label: 'TVC', icon: '🎬', desc: '广告/宣传片脚本' }
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
.selected {
  border: 2px solid var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
</style>
