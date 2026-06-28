<template>
  <div class="batch-overlay">
    <div class="batch-panel">
      <h3><el-icon><component :is="mode === 'image' ? 'Picture' : 'VideoCamera'" /></el-icon> {{ mode === 'image' ? '批量生图' : '批量生视频' }}</h3>
      <p class="text-muted">已选择 {{ shotCount }} 个镜头</p>

      <div class="config-group">
        <label>AI 模型</label>
        <el-select v-model="config.modelId" size="small" style="width:100%">
          <template v-if="mode === 'image'">
            <el-option label="Seedream 5.0 (推荐)" value="seedream-5.0" />
            <el-option label="Flux 1.1 Pro" value="flux-1.1-pro" />
          </template>
          <template v-else>
            <el-option label="Seedance 2.0 (推荐)" value="seedance-2.0" />
            <el-option label="Kling 1.6" value="kling-1.6" />
          </template>
        </el-select>
      </div>

      <div class="config-group">
        <label>分辨率</label>
        <el-select v-model="config.size" size="small" style="width:100%">
          <el-option label="9:16 竖版 (1080×1920)" value="1080x1920" />
          <el-option label="16:9 横版 (1920×1080)" value="1920x1080" />
          <el-option label="1:1 方形 (1024×1024)" value="1024x1024" />
        </el-select>
      </div>

      <div v-if="mode === 'video'" class="config-group">
        <label>视频时长 (秒)</label>
        <el-input-number v-model="config.duration" :min="1" :max="10" :step="1" size="small" />
      </div>

      <div class="config-group">
        <label>多副本并行</label>
        <el-radio-group v-model="config.variants">
          <el-radio :value="1">1 个</el-radio>
          <el-radio :value="2">2 个</el-radio>
          <el-radio :value="4">4 个</el-radio>
          <el-radio :value="8">8 个</el-radio>
        </el-radio-group>
      </div>

      <div class="cost-estimate">
        <el-icon><Coin /></el-icon> 预估消耗：
        <strong>{{ estimateCredits }} 积分</strong>
        <span class="text-xs text-muted">({{ shotCount }}镜头 × {{ config.variants }}副本，实际扣费以服务端返回为准)</span>
      </div>

      <div class="flex gap-sm mt-md">
        <el-button type="primary" size="small" @click="$emit('confirm', mode, config)">
          <el-icon><Promotion /></el-icon> 开始生成
        </el-button>
        <el-button size="small" @click="$emit('close')">取消</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, computed, watch } from 'vue'

const props = defineProps({
  mode: { type: String, default: 'image' }, // 'image' | 'video'
  shotCount: { type: Number, default: 0 },
  shots: { type: Array, default: () => [] }
})
defineEmits(['confirm', 'close'])

const config = reactive({
  modelId: 'seedream-5.0',
  size: '1080x1920',
  duration: 5,
  variants: 1
})

watch(() => props.mode, (mode) => {
  config.modelId = mode === 'video' ? 'seedance-2.0' : 'seedream-5.0'
}, { immediate: true })

const estimateCredits = computed(() => {
  const base = props.mode === 'image' ? 10 : 50
  return props.shotCount * config.variants * base
})
</script>

<style scoped>
.batch-overlay {
  position: fixed; inset: 0; z-index: 950; background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center;
}
.batch-panel {
  background: #1a1a2e; border: 1px solid #333; border-radius: 12px;
  padding: 24px; width: 400px;
}
h3 { margin: 0 0 8px; }
.text-muted { color: #888; font-size: 13px; margin-bottom: 16px; }
.config-group { margin-bottom: 14px; }
.config-group label { display: block; font-size: 12px; color: #888; margin-bottom: 6px; }
.cost-estimate { padding: 10px; background: #0f172a; border-radius: 8px; font-size: 13px; color: #fbbf24; }
.cost-estimate strong { font-size: 16px; }
.flex { display: flex; } .gap-sm { gap: 8px; } .mt-md { margin-top: 16px; }
</style>
