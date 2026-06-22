<template>
  <div v-if="visible" class="shot-table-overlay">
    <div class="shot-table-container">
      <!-- Toolbar -->
      <div class="table-toolbar">
        <div class="flex items-center gap-md">
          <span class="font-bold"><el-icon><Film /></el-icon> 分镜表 · {{ shots.length }} 镜头</span>
          <el-select v-model="statusFilter" size="small" placeholder="状态筛选" clearable style="width:120px">
            <el-option label="全部" value="" />
            <el-option label="待编辑" value="pending" />
            <el-option label="生成中" value="generating" />
            <el-option label="已完成" value="completed" />
            <el-option label="失败" value="failed" />
          </el-select>
          <span class="text-sm text-muted">已选: {{ checkedShots.length }} / {{ filteredShots.length }}</span>
        </div>
        <div class="flex gap-sm">
          <el-button size="small" @click="checkAll">全选</el-button>
          <el-button size="small" type="primary" @click="showBatchImage = true" :disabled="!checkedShots.length">
            <el-icon><Picture /></el-icon> 批量生图 ({{ checkedShots.length }})
          </el-button>
          <el-button size="small" type="success" @click="showBatchVideo = true"
                     :disabled="!checkedShots.filter(s => s.image_status === 'completed').length">
            <el-icon><VideoCamera /></el-icon> 批量生视频
          </el-button>
          <el-button size="small" @click="composePrompts" :disabled="!checkedShots.length">合成提示词</el-button>
          <el-button size="small" @click="exportCSV"><el-icon><Download /></el-icon> 导出CSV</el-button>
          <el-button size="small" text @click="$emit('close')"><el-icon><Close /></el-icon></el-button>
        </div>
      </div>

      <!-- Table -->
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th style="width:40px"><input type="checkbox" @change="toggleAll" :checked="allChecked" /></th>
              <th style="width:50px">#</th>
              <th style="width:60px">场次</th>
              <th style="width:120px">景别</th>
              <th style="width:100px">运镜</th>
              <th style="width:80px">时长ms</th>
              <th style="min-width:140px">角色</th>
              <th style="min-width:120px">道具</th>
              <th style="min-width:140px">动作</th>
              <th style="min-width:200px">画面描述</th>
              <th style="min-width:150px">对白</th>
              <th style="min-width:150px">旁白</th>
              <th style="min-width:200px">生图Prompt</th>
              <th style="min-width:200px">生视频Prompt</th>
              <th style="width:80px">色标</th>
              <th style="width:80px">图片状态</th>
              <th style="width:80px">视频状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="shot in filteredShots" :key="shot.id || shot.shot_id || shot.uuid"
                :class="{ selected: checkedShots.includes(shot.id || shot.uuid) }"
                @click="toggleShot(shot.id || shot.uuid)">
              <td><input type="checkbox" :checked="checkedShots.includes(shot.id || shot.uuid)" /></td>
              <td>{{ shot.shot_no || shot.order }}</td>
              <td><input v-model="shot.scene_no" @change="updateShotField(shot, 'scene_no')"
                         style="width:45px;text-align:center" /></td>
              <td>
                <select v-model="shot.shot_size" @change="updateShotField(shot, 'shot_size')" style="width:110px">
                  <option value="">-</option>
                  <option value="CU">CU 特写</option>
                  <option value="MCU">MCU 近景</option>
                  <option value="MS">MS 中景</option>
                  <option value="LS">LS 远景</option>
                  <option value="ELS">ELS 极远</option>
                </select>
              </td>
              <td>
                <select v-model="shot.camera_motion" @change="updateShotField(shot, 'camera_motion')" style="width:90px">
                  <option value="">-</option>
                  <option value="fixed">固定</option>
                  <option value="push">推</option>
                  <option value="pull">拉</option>
                  <option value="pan">摇</option>
                  <option value="tilt">移</option>
                </select>
              </td>
              <td><input v-model="shot.duration" @change="updateShotField(shot, 'duration')"
                         style="width:60px;text-align:center" type="number" /></td>
              <td><textarea v-model="shot.characters" @change="updateShotField(shot, 'characters')"
                            rows="2" style="width:100%" placeholder="@角色资产" /></td>
              <td><textarea v-model="shot.props" @change="updateShotField(shot, 'props')"
                            rows="2" style="width:100%" placeholder="@道具资产" /></td>
              <td><textarea v-model="shot.action" @change="updateShotField(shot, 'action')"
                            rows="2" style="width:100%" /></td>
              <td><textarea v-model="shot.visual_description" @change="updateShotField(shot, 'visual_description')"
                            rows="2" style="width:100%" /></td>
              <td><textarea v-model="shot.dialogue_text" @change="updateShotField(shot, 'dialogue_text')"
                            rows="2" style="width:100%" placeholder="角色: 台词" /></td>
              <td><textarea v-model="shot.voiceover" @change="updateShotField(shot, 'voiceover')"
                            rows="2" style="width:100%" placeholder="旁白/内心独白" /></td>
              <td><textarea v-model="shot.image_prompt" @change="updateShotField(shot, 'image_prompt')"
                            rows="2" style="width:100%" placeholder="生图Prompt..." /></td>
              <td><textarea v-model="shot.video_prompt" @change="updateShotField(shot, 'video_prompt')"
                            rows="2" style="width:100%" placeholder="生视频Prompt..." /></td>
              <td><input v-model="shot.color_mark" @change="updateShotField(shot, 'color_mark')"
                         type="color" style="width:42px;height:28px;padding:0" /></td>
              <td><span :class="statusBadge(shot.image_status)">{{ shot.image_status || 'pending' }}</span></td>
              <td><span :class="statusBadge(shot.video_status)">{{ shot.video_status || 'pending' }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Batch Generate Overlay -->
    <BatchGeneratePanel v-if="showBatchImage || showBatchVideo"
                        :mode="showBatchImage ? 'image' : 'video'"
                        :shot-count="checkedShots.length"
                        :shots="checkedShots"
                        @confirm="onBatchConfirm"
                        @close="showBatchImage = false; showBatchVideo = false" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import BatchGeneratePanel from './BatchGeneratePanel.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  shots: { type: Array, default: () => [] },
  nodeId: { type: String, default: '' }
})
const emit = defineEmits(['close', 'updateShot', 'batchGenerateImages', 'batchGenerateVideos'])

const statusFilter = ref('')
const checkedShots = ref([])
const showBatchImage = ref(false)
const showBatchVideo = ref(false)

const filteredShots = computed(() => {
  if (!statusFilter.value) return props.shots
  return props.shots.filter(s =>
    s.image_status === statusFilter.value || s.video_status === statusFilter.value)
})

const allChecked = computed(() =>
  filteredShots.value.length > 0 && checkedShots.value.length === filteredShots.value.length)

function toggleShot(id) {
  const idx = checkedShots.value.indexOf(id)
  if (idx >= 0) checkedShots.value.splice(idx, 1)
  else checkedShots.value.push(id)
}

function toggleAll(e) {
  if (e.target.checked) checkedShots.value = filteredShots.value.map(s => s.id || s.uuid)
  else checkedShots.value = []
}

function checkAll() { checkedShots.value = filteredShots.value.map(s => s.id || s.uuid) }

function updateShotField(shot, field) {
  emit('updateShot', shot, { [field]: shot[field] })
}

function onBatchConfirm(mode, config) {
  const payload = { ...config, mode }
  if (mode === 'image') emit('batchGenerateImages', checkedShots.value, payload)
  else emit('batchGenerateVideos', checkedShots.value, payload)
  showBatchImage.value = false
  showBatchVideo.value = false
}

function composePrompts() {
  props.shots
    .filter(s => checkedShots.value.includes(s.id || s.uuid))
    .forEach((shot) => {
      const imagePrompt = [
        shot.scene_no && `场次:${shot.scene_no}`,
        shot.shot_size && `景别:${shot.shot_size}`,
        shot.characters && `角色:${shot.characters}`,
        shot.props && `道具:${shot.props}`,
        shot.action && `动作:${shot.action}`,
        shot.visual_description
      ].filter(Boolean).join('，')
      const videoPrompt = [
        imagePrompt,
        shot.camera_motion && `运镜:${shot.camera_motion}`,
        shot.duration && `时长:${shot.duration}ms`
      ].filter(Boolean).join('，')
      shot.image_prompt = shot.image_prompt || imagePrompt
      shot.video_prompt = shot.video_prompt || videoPrompt
      emit('updateShot', shot, {
        image_prompt: shot.image_prompt,
        video_prompt: shot.video_prompt
      })
    })
}

function exportCSV() {
  const headers = ['shot_no','scene_no','shot_size','camera_motion','duration','characters','props','action','visual_description','dialogue_text','voiceover','image_prompt','video_prompt','color_mark','image_status','video_status']
  const rows = props.shots.map(s => headers.map(h => JSON.stringify(s[h] || '').replace(/"/g,'""')).join(','))
  const csv = '﻿' + headers.join(',') + '\n' + rows.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = url; a.download = 'storyboard.csv'; a.click()
}

function statusBadge(status) {
  return {
    'badge': true,
    'badge-success': status === 'completed',
    'badge-warning': status === 'generating',
    'badge-danger': status === 'failed',
    'badge-default': status === 'pending'
  }
}
</script>

<style scoped>
.shot-table-overlay {
  position: fixed; inset: 0; z-index: 900;
  background: rgba(0,0,0,0.85); display: flex; align-items: center; justify-content: center;
}
.shot-table-container {
  width: 95vw; height: 90vh; background: #1a1a2e; border-radius: 12px;
  display: flex; flex-direction: column; overflow: hidden;
}
.table-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 20px; border-bottom: 1px solid #333;
}
.table-scroll { flex: 1; overflow: auto; padding: 0; }
table { width: 100%; border-collapse: collapse; font-size: 12px; }
th { background: #222; color: #aaa; padding: 8px 6px; text-align: left; position: sticky; top: 0; z-index: 1; }
td { padding: 4px 6px; border-bottom: 1px solid #2a2a3e; vertical-align: top; }
tr:hover { background: rgba(79,70,229,0.1); }
tr.selected { background: rgba(79,70,229,0.2); }
input, select, textarea { background: #222; border: 1px solid #444; color: #ddd; padding: 2px 4px;
  border-radius: 4px; font-size: 12px; }
td input[type="checkbox"] { width: 16px; height: 16px; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; }
.badge-success { background: #064e3b; color: #34d399; }
.badge-warning { background: #78350f; color: #fbbf24; }
.badge-danger { background: #7f1d1d; color: #f87171; }
.badge-default { background: #1e293b; color: #94a3b8; }
.flex { display: flex; } .gap-sm { gap: 8px; } .gap-md { gap: 12px; }
.items-center { align-items: center; }
</style>
