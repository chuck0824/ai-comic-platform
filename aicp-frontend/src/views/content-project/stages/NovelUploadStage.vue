<template>
  <section class="stage-panel">
    <header><p class="eyebrow">STEP 2</p><h2>小说上传</h2><p>长文档请使用文件上传；粘贴文本按汉字数限制 2,000 字。</p></header>
    <div class="upload-card">
      <h3>文件上传</h3>
      <input ref="fileInput" type="file" accept=".txt,.md,.doc,.docx,.pdf,.epub" @change="selectFile">
      <p v-if="selectedFile">{{ selectedFile.name }} · {{ formatBytes(selectedFile.size) }}</p>
      <el-button @click="uploadSelectedFile">上传并解析文件</el-button>
    </div>
    <div class="paste-card">
      <div class="paste-title"><h3>粘贴小说文本</h3><span data-action="paste-char-counter" :class="{ overflow: pasteValidation.excess > 0 }">{{ pasteValidation.chineseCount }} / {{ pasteValidation.limit }} 个汉字</span></div>
      <el-input v-model="pastedText" type="textarea" :rows="14" placeholder="在此粘贴小说正文……" />
      <el-alert v-if="pasteValidation.excess" :title="pasteValidation.message" type="warning" show-icon :closable="false" />
      <el-button type="primary" @click="submitPastedText">保存粘贴文本</el-button>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { validatePastedNovel } from '../workbench/upstreamStageModel.js'

const props = defineProps({ uploadFile: { type: Function, default: null }, persistPastedText: { type: Function, default: null } })
const emit = defineEmits(['guidance', 'uploaded'])
const pastedText = ref('')
const selectedFile = ref(null)
const pasteValidation = computed(() => validatePastedNovel(pastedText.value))

function guidance(code, title, message, targetAction) { const value = { allowed:false, code, title, message, targetAction }; emit('guidance', value); return value }
function selectFile(event) { selectedFile.value = event.target.files?.[0] ?? null }
function formatBytes(value) { return value < 1024 * 1024 ? `${Math.ceil(value / 1024)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB` }

async function uploadSelectedFile() {
  if (!selectedFile.value) return guidance('NOVEL_FILE_REQUIRED', '请选择小说文件', '选择 TXT、Markdown、Word、PDF 或 EPUB 文件后重试。', 'focus_file_input')
  if (!props.uploadFile) return guidance('NOVEL_UPLOAD_UNAVAILABLE', '上传服务不可用', '请恢复上传服务后重试。', 'retry_file_upload')
  try {
    const response = await props.uploadFile(selectedFile.value)
    if (response?.persisted !== true && response?.uploaded !== true) return guidance('NOVEL_UPLOAD_FAILED', '小说文件未保存', response?.message || '请重试。', 'retry_file_upload')
    emit('uploaded', { source: 'file', ...response })
    return response
  } catch (error) { return guidance('NOVEL_UPLOAD_FAILED', '小说文件未保存', error?.message || '请重试。', 'retry_file_upload') }
}

async function submitPastedText() {
  const validation = pasteValidation.value
  if (!validation.allowed) return guidance(validation.code, '粘贴文本超过限制', validation.message, 'focus_pasted_novel')
  if (!pastedText.value.trim()) return guidance('NOVEL_TEXT_REQUIRED', '请粘贴小说文本', '文本不能为空。', 'focus_pasted_novel')
  if (!props.persistPastedText) return guidance('NOVEL_UPLOAD_UNAVAILABLE', '文本保存服务不可用', '请恢复保存服务后重试。', 'retry_text_upload')
  try {
    const response = await props.persistPastedText({ text: pastedText.value, chineseCount: validation.chineseCount })
    if (response?.persisted !== true) return guidance('NOVEL_UPLOAD_FAILED', '小说文本未保存', response?.message || '请重试。', 'retry_text_upload')
    emit('uploaded', { source: 'paste', ...response })
    return response
  } catch (error) { return guidance('NOVEL_UPLOAD_FAILED', '小说文本未保存', error?.message || '请重试。', 'retry_text_upload') }
}
</script>

<style scoped>
.stage-panel{display:grid;gap:18px}.eyebrow{color:var(--el-color-primary);font-weight:700;margin:0}header h2{margin:4px 0}header p{color:var(--el-text-color-secondary)}.upload-card,.paste-card{border:1px solid var(--el-border-color);border-radius:12px;padding:18px;display:grid;gap:14px}.paste-title{display:flex;justify-content:space-between;align-items:center}.paste-title h3{margin:0}.overflow{color:var(--el-color-danger);font-weight:700}
</style>
