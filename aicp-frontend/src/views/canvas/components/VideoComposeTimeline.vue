<template>
  <div v-if="visible" class="timeline-overlay">
    <div class="timeline-container">
      <div class="timeline-toolbar">
        <span class="font-bold"><el-icon><Film /></el-icon> 视频合成时间线</span>
        <div class="flex gap-sm">
          <el-button size="small" circle @click="$emit('play')"><el-icon><VideoPlay /></el-icon></el-button>
          <span class="text-sm text-muted">{{ currentTime }}s / {{ totalDuration }}s</span>
          <el-button size="small" type="primary" @click="startExport">
            <el-icon><Promotion /></el-icon> 合成导出
          </el-button>
          <el-button size="small" text @click="$emit('close')"><el-icon><Close /></el-icon></el-button>
        </div>
      </div>

      <!-- 7 Tracks -->
      <div class="tracks-area">
        <TrackRow v-for="track in tracks" :key="track.id"
                  :track="track"
                  :total-duration="totalDuration"
                  @update="onTrackUpdate(track.id, $event)" />
      </div>

      <!-- Export Dialog -->
      <div v-if="exportDialog" class="export-dialog">
        <div class="export-panel">
          <h3>导出设置</h3>
          <div class="config-group">
            <label>格式</label>
            <el-select v-model="exportConfig.format" size="small">
              <el-option label="MP4 (H.264)" value="mp4" />
              <el-option label="MOV" value="mov" />
            </el-select>
          </div>
          <div class="config-group">
            <label>分辨率</label>
            <el-select v-model="exportConfig.resolution" size="small">
              <el-option label="1080p (1920×1080)" value="1080p" />
              <el-option label="720p (1280×720)" value="720p" />
            </el-select>
          </div>
          <div class="config-group">
            <label>帧率</label>
            <el-select v-model="exportConfig.fps" size="small">
              <el-option label="25 FPS" :value="25" />
              <el-option label="30 FPS" :value="30" />
            </el-select>
          </div>
          <div class="flex gap-sm mt-md">
            <el-button type="primary" size="small" @click="confirmExport">确认导出</el-button>
            <el-button size="small" @click="exportDialog = false">取消</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  timeline: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['close', 'export', 'update', 'play'])

const currentTime = ref(0)
const exportDialog = ref(false)
const exportConfig = reactive({ format: 'mp4', resolution: '1080p', fps: 25 })

const totalDuration = computed(() => {
  const videoTrack = props.timeline?.video_track || []
  return Math.max(30, ...videoTrack.map(c => (c.end || c.start || 0) + (c.duration || 0)))
})

const tracks = computed(() => [
  { id: 'video', label: '视频轨', items: props.timeline?.video_track || [], color: '#4f46e5' },
  { id: 'audio', label: '配音轨', items: props.timeline?.audio_track || [], color: '#059669' },
  { id: 'subtitle', label: '字幕轨', items: props.timeline?.subtitle_track || [], color: '#d97706' },
  { id: 'bgm', label: 'BGM轨', items: props.timeline?.bgm_track || [], color: '#7c3aed' },
  { id: 'sfx', label: '音效轨', items: props.timeline?.sfx_track || [], color: '#dc2626' },
  { id: 'effect', label: '特效轨', items: props.timeline?.effect_track || [], color: '#2563eb' },
  { id: 'overlay', label: '叠加轨', items: props.timeline?.overlay_track || [], color: '#0891b2' }
])

function onTrackUpdate(trackId, data) {
  emit('update', { track: trackId, ...data })
}

function startExport() { exportDialog.value = true }
function confirmExport() {
  emit('export', exportConfig)
  exportDialog.value = false
}
</script>

<script>
// TrackRow sub-component
const TrackRow = {
  props: { track: Object, totalDuration: Number },
  template: `
    <div class="track-row">
      <div class="track-label" :style="{borderLeftColor: track.color}">{{ track.label }}</div>
      <div class="track-content">
        <div v-for="(item, i) in track.items" :key="i"
             class="track-item" :style="{
               left: ((item.start || 0) / totalDuration * 100) + '%',
               width: ((item.duration || 3) / totalDuration * 100) + '%',
               background: track.color
             }"
             :title="item.name || item.text || '片段 ' + (i+1)">
          {{ item.name || item.text || '#' + (i+1) }}
        </div>
      </div>
    </div>
  `
}
</script>

<style scoped>
.timeline-overlay {
  position: fixed; inset: 0; z-index: 900; background: rgba(0,0,0,0.85);
  display: flex; align-items: flex-end; justify-content: center; padding-bottom: 0;
}
.timeline-container {
  width: 100%; height: 60vh; background: #1a1a2e; border-top: 1px solid #333;
  border-radius: 12px 12px 0 0; display: flex; flex-direction: column; overflow: hidden;
}
.timeline-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 20px; border-bottom: 1px solid #333;
}
.tracks-area { flex: 1; overflow-y: auto; padding: 8px; }
.track-row { display: flex; align-items: center; margin-bottom: 4px; }
.track-label {
  width: 100px; font-size: 11px; color: #aaa; padding: 4px 8px;
  border-left: 3px solid #555; flex-shrink: 0;
}
.track-content {
  flex: 1; height: 32px; background: #0f172a; border-radius: 4px;
  position: relative; overflow: hidden;
}
.track-item {
  position: absolute; height: 100%; border-radius: 4px;
  font-size: 10px; color: #fff; padding: 2px 6px;
  overflow: hidden; white-space: nowrap; text-overflow: ellipsis;
  display: flex; align-items: center; cursor: pointer; opacity: 0.85;
}
.track-item:hover { opacity: 1; }
.export-dialog { position: fixed; inset: 0; z-index: 1000; background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center; }
.export-panel { background: #1a1a2e; border: 1px solid #333; border-radius: 12px;
  padding: 24px; width: 360px; }
.flex { display: flex; } .gap-sm { gap: 8px; } .mt-md { margin-top: 16px; }
.font-bold { font-weight: 600; } .text-sm { font-size: 12px; } .text-muted { color: #888; }
.config-group { margin-bottom: 12px; }
.config-group label { display: block; font-size: 12px; color: #888; margin-bottom: 4px; }
</style>
