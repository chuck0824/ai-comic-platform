<template>
  <div class="shot-inspector" data-testid="shot-inspector">
    <div class="inspector-header">
      <span class="shot-code">{{ shot.shotCode }}</span>
      <el-tag :type="statusType" size="small">{{ statusLabel }}</el-tag>
    </div>

    <div class="inspector-body">
      <!-- Content group -->
      <el-collapse model-value="content">
        <el-collapse-item title="内容" name="content">
          <div class="field-group">
            <label>画面描述</label>
            <el-input
              data-testid="visual-description"
              :model-value="shot.visualDescription"
              type="textarea"
              :rows="3"
              :disabled="isLocked"
              @input="debouncePatch('visualDescription', $event)"
            />
          </div>
          <div class="field-group">
            <label>对白</label>
            <el-input
              :model-value="shot.dialogueText"
              type="textarea"
              :rows="2"
              :disabled="isLocked"
              @input="debouncePatch('dialogueText', $event)"
            />
          </div>
        </el-collapse-item>

        <!-- Director group -->
        <el-collapse-item title="导演" name="director">
          <div class="field-row">
            <label>时长(ms)</label>
            <el-input-number :model-value="shot.durationMs" :disabled="isLocked" size="small" @change="(v) => $emit('patch', { durationMs: v })" />
          </div>
          <div class="field-row">
            <label>景别</label>
            <el-select :model-value="shot.shotSize" :disabled="isLocked" size="small" @change="(v) => $emit('patch', { shotSize: v })">
              <el-option v-for="s in SHOT_SIZE_OPTIONS" :key="s" :label="s" :value="s" />
            </el-select>
          </div>
          <div class="field-group">
            <label>光影氛围</label>
            <el-input :model-value="shot.lightingAtmosphere" :disabled="isLocked" @input="debouncePatch('lightingAtmosphere', $event)" />
          </div>
          <div class="field-group">
            <label>角色动作</label>
            <el-input :model-value="shot.characterAction" type="textarea" :rows="2" :disabled="isLocked" @input="debouncePatch('characterAction', $event)" />
          </div>
          <div class="field-group">
            <label>情绪</label>
            <el-input :model-value="shot.emotionDescription" :disabled="isLocked" @input="debouncePatch('emotionDescription', $event)" />
          </div>
        </el-collapse-item>

        <!-- Sound group -->
        <el-collapse-item title="声音" name="sound">
          <div class="field-group">
            <label>音效</label>
            <el-input :model-value="shot.soundEffect" :disabled="isLocked" @input="debouncePatch('soundEffect', $event)" />
          </div>
        </el-collapse-item>

        <!-- Prompts group -->
        <el-collapse-item title="提示词" name="prompts">
          <div class="field-group">
            <label>图片提示词 <span class="char-count">{{ charCount(shot.imagePrompt) }}</span></label>
            <el-input :model-value="shot.imagePrompt" type="textarea" :rows="4" :disabled="isLocked" @input="debouncePatch('imagePrompt', $event)" />
          </div>
          <div class="field-group">
            <label>视频动作提示词 <span class="char-count">{{ charCount(shot.videoMotionPrompt) }}</span></label>
            <el-input :model-value="shot.videoMotionPrompt" type="textarea" :rows="4" :disabled="isLocked" @input="debouncePatch('videoMotionPrompt', $event)" />
          </div>
        </el-collapse-item>

        <!-- Status -->
        <el-collapse-item title="状态" name="status">
          <el-select :model-value="shot.status" :disabled="isLocked" @change="(v) => $emit('patch', { status: v })">
            <el-option v-for="s in SHOT_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-collapse-item>
      </el-collapse>

      <!-- Conflict resolution -->
      <div v-if="conflictDiffs.length" class="conflict-banner">
        <el-alert title="版本冲突" type="error" :closable="false" show-icon>
          <template #default>
            <div v-for="d in conflictDiffs" :key="d.field" class="conflict-field">
              <strong>{{ d.field }}</strong>: 本地「{{ d.local }}」 vs 服务器「{{ d.server }}」
            </div>
            <div class="conflict-actions">
              <el-button size="small" @click="$emit('resolve-conflict', true)">使用服务器版本</el-button>
              <el-button size="small" type="primary" @click="$emit('resolve-conflict', false)">保留本地版本</el-button>
            </div>
          </template>
        </el-alert>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { SHOT_SIZE_OPTIONS, SHOT_STATUS_OPTIONS } from '../storyboardData'

const props = defineProps({
  shot: Object,
  isLocked: Boolean,
  saveState: String,
  conflictDiffs: { type: Array, default: () => [] }
})

const statusType = computed(() =>
  props.shot?.status === 'confirmed' ? 'success' : props.shot?.status === 'needs_review' ? 'warning' : 'info')
const statusLabel = computed(() => ({ draft: '草稿', confirmed: '已确认', needs_review: '待检查' })[props.shot?.status] || props.shot?.status)

const emit = defineEmits(['patch', 'resolve-conflict'])

let timers = {}
function debouncePatch(field, value) {
  clearTimeout(timers[field])
  timers[field] = setTimeout(() => {
    emit('patch', { [field]: value || '' })
  }, 800)
}

function charCount(text) {
  return text ? text.length + '字' : '0字'
}
</script>

<style scoped>
.shot-inspector {
  height: 100%;
  overflow-y: auto;
  background: var(--el-bg-color, #fff);
  border-left: 1px solid var(--el-border-color-light, #e4e7ed);
}
.inspector-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
}
.shot-code { font-family: monospace; font-weight: 600; font-size: 14px; }
.inspector-body { padding: 8px; }
.field-group { margin-bottom: 12px; }
.field-group label { display: block; font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 4px; }
.field-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.field-row label { font-size: 12px; color: var(--el-text-color-secondary); min-width: 60px; }
.char-count { font-weight: normal; color: var(--el-text-color-placeholder); }
.conflict-banner { margin-top: 16px; }
.conflict-field { font-size: 12px; margin: 4px 0; }
.conflict-actions { margin-top: 8px; display: flex; gap: 8px; }
</style>
