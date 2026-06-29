<template>
  <div class="storyboard-panel">
    <div class="flex items-center justify-between mb-md">
      <h3 class="font-semibold">A-tier 分镜</h3>
      <div class="flex gap-sm">
        <el-button v-if="!master" type="primary" :loading="generating" @click="$emit('generate')">
          <el-icon><Cpu /></el-icon> 生成分镜
        </el-button>
        <el-button v-if="master && master.status !== 'locked'" size="small" type="warning" @click="$emit('lock', master.id)">
          锁定分镜
        </el-button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="generating" class="text-center py-md">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <p class="text-sm text-muted mt-xs">AI 正在生成分镜…</p>
      <el-progress :percentage="100" :indeterminate="true" :duration="2" />
    </div>

    <!-- No master -->
    <div v-else-if="!master" class="text-center py-lg text-muted">
      <p>暂无分镜，点击"生成分镜"开始</p>
      <p class="text-xs mt-xs">预计镜头数、耗时和费用将在生成前展示</p>
    </div>

    <!-- Master info -->
    <div v-else>
      <div class="flex items-center gap-sm mb-md text-sm">
        <el-tag :type="master.status === 'locked' ? 'success' : 'warning'" size="small">
          {{ master.status === 'locked' ? '已锁定' : '草稿' }}
        </el-tag>
        <span>共 {{ master.total_shots }} 镜头</span>
        <span>约 {{ master.estimated_duration_sec }}秒</span>
      </div>

      <!-- Scene + Shot cards -->
      <div v-if="scenes.length" class="flex flex-col gap-md" style="max-height:60vh;overflow-y:auto">
        <div v-for="scene in scenes" :key="scene.id" class="card p-md">
          <div class="flex items-center justify-between mb-sm">
            <span class="font-semibold text-sm">场景 {{ scene.scene_no }}</span>
            <span class="text-xs text-muted">{{ scene.duration_sec }}秒</span>
          </div>
          <p v-if="scene.dramatic_goal" class="text-sm mb-xs">
            <span class="text-muted">戏剧目标：</span>{{ scene.dramatic_goal }}
          </p>
          <p v-if="scene.beat_description" class="text-sm mb-sm">
            <span class="text-muted">节拍：</span>{{ scene.beat_description }}
          </p>

          <!-- Shots in scene -->
          <div class="flex flex-col gap-xs mt-sm">
            <div v-for="shot in shotsByScene[scene.id]" :key="shot.id"
                 class="flex items-center gap-sm text-xs"
                 style="padding:4px 8px;background:#fafafa;border-radius:4px">
              <span class="badge badge-primary">#{{ shot.shot_no }}</span>
              <span>{{ shot.shot_type || '镜头' }}</span>
              <span class="text-muted">{{ shot.duration_sec }}s</span>
              <span class="flex-1 text-muted">{{ ellipsis(shot.description, 40) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="text-center py-md text-muted text-sm">暂无场景数据</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Cpu, Loading } from '@element-plus/icons-vue'

const props = defineProps({
  master: { type: Object, default: null },
  scenes: { type: Array, default: () => [] },
  shots: { type: Array, default: () => [] },
  generating: { type: Boolean, default: false }
})

defineEmits(['generate', 'lock'])

const shotsByScene = computed(() => {
  const map = {}
  for (const shot of props.shots) {
    const sid = shot.scene_id
    if (!map[sid]) map[sid] = []
    map[sid].push(shot)
  }
  return map
})

function ellipsis(text, max) {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '…' : text
}
</script>
