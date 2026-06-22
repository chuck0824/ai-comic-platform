<!-- Prompt 模板管理器：12 槽位 × 5 大类，三级优先级 -->
<template>
  <el-dialog v-model="visible" width="800px" @close="$emit('close')">
    <template #header><el-icon><Tools /></el-icon> Prompt 模板管理</template>
    <!-- 分类 tab -->
    <div class="tabs mb-md">
      <span v-for="cat in categories" :key="cat.key"
            :class="['tab-item', { active: activeCategory === cat.key }]"
            @click="activeCategory = cat.key"><el-icon><component :is="cat.icon" /></el-icon> {{ cat.label }}</span>
    </div>

    <!-- 模板列表 -->
    <div v-if="loading" class="text-center py-xl text-muted">加载中...</div>
    <div v-else class="prompt-list">
      <div v-for="prompt in filteredPrompts" :key="prompt.category"
           class="prompt-card" :class="{ editing: editingCategory === prompt.category, default: prompt.is_default }">
        <div class="prompt-header" @click="editingCategory = editingCategory === prompt.category ? '' : prompt.category">
          <div>
            <strong>{{ prompt.name }}</strong>
            <span class="text-xs text-muted ml-sm">{{ prompt.category }}</span>
            <span v-if="prompt.is_default" class="badge badge-neutral text-xs ml-sm">默认</span>
            <span v-else class="badge badge-accent text-xs ml-sm">v{{ prompt.version }}</span>
          </div>
          <span class="text-xs text-muted">{{ prompt.desc }}</span>
        </div>
        <div v-if="editingCategory === prompt.category" class="prompt-body">
          <el-input v-model="prompt.content" type="textarea" :rows="8" class="mb-sm"
                    placeholder="编辑 Prompt 模板...&#10;&#10;可用变量：{{genre}}, {{plot}}, {{tone}}, {{setting}}, {{idea}}, {{title}}, {{character}}" />
          <div class="flex gap-sm">
            <el-button type="primary" size="small" @click="savePrompt(prompt)" :loading="saving">
              <el-icon><FolderAdd /></el-icon> 保存 (v{{ (prompt.version || 1) + 1 }})
            </el-button>
            <el-button size="small" @click="editingCategory = ''">取消</el-button>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button size="small" @click="$emit('close')">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const props = defineProps({ visible: Boolean })
defineEmits(['close'])

const categories = [
  { key: 'script', label: '剧本', icon: 'Document' },
  { key: 'character', label: '角色', icon: 'User' },
  { key: 'shot', label: '分镜', icon: 'Film' },
  { key: 'frame', label: '画面', icon: 'Picture' },
  { key: 'video', label: '视频', icon: 'VideoCamera' }
]

const activeCategory = ref('script')
const loading = ref(false)
const saving = ref(false)
const prompts = ref([])
const editingCategory = ref('')

const filteredPrompts = computed(() =>
  prompts.value.filter(p => p.category?.startsWith(activeCategory.value) ||
    (activeCategory.value === 'script' && ['script_generate','script_parse','script_split'].includes(p.category)) ||
    (activeCategory.value === 'character' && ['character_extract','character_image'].includes(p.category)) ||
    (activeCategory.value === 'shot' && p.category?.startsWith('shot')) ||
    (activeCategory.value === 'frame' && p.category?.startsWith('frame')) ||
    (activeCategory.value === 'video' && p.category?.startsWith('video'))
  )
)

async function loadPrompts() {
  loading.value = true
  try {
    const res = await request.get('/script/prompts')
    prompts.value = res.data?.items || []
  } catch { prompts.value = [] }
  finally { loading.value = false }
}

async function savePrompt(prompt) {
  saving.value = true
  try {
    await request.post('/script/prompts', {
      category: prompt.category,
      name: prompt.name,
      content: prompt.content
    })
    prompt.version = (prompt.version || 1) + 1
    prompt.is_default = false
    editingCategory.value = ''
    ElMessage.success('Prompt 已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally { saving.value = false }
}

onMounted(loadPrompts)
</script>

<style scoped>
.prompt-list { max-height:500px; overflow-y:auto; }
.prompt-card { background:var(--bg-surface); border:1px solid var(--border-light); border-radius:8px; margin-bottom:8px; overflow:hidden; }
.prompt-card.default { opacity:.85; }
.prompt-card.editing { border-color:var(--accent); }
.prompt-header { display:flex; justify-content:space-between; align-items:center; padding:10px 14px; cursor:pointer; color:var(--text-primary); }
.prompt-header:hover { background:var(--bg-surface-hover); }
.prompt-body { padding:10px 14px; border-top:1px solid var(--border-light); }
.tabs { display:flex; gap:4px; font-size:12px; }
.tab-item { padding:6px 12px; cursor:pointer; border-radius:6px; color:var(--text-secondary); background:var(--bg-surface-hover); }
.tab-item.active { background:var(--accent-bg); color:var(--accent); font-weight:600; }
.ml-sm { margin-left:8px; } .text-xs { font-size:11px; }
</style>
