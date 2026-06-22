<template>
  <div>
    <h2 class="text-xl font-bold mb-lg">AI资产与风格模型市场</h2>
    <div class="tabs">
      <div v-for="tab in tabs" :key="tab" class="tab-item" :class="{ active: activeTab === tab }" @click="activeTab = tab">{{ tab }}</div>
    </div>
    <div class="grid4">
      <div v-for="item in assets" :key="item.id" class="card card-hover" style="padding:16px">
        <div class="canvas-mock" style="min-height:100px;margin-bottom:12px;border-radius:8px">Preview</div>
        <div class="font-semibold">{{ item.name }}</div>
        <p class="text-sm text-muted">@{{ item.author }} · <el-icon style="vertical-align:-1px;color:#f59e0b"><StarFilled /></el-icon> {{ item.rating }} · {{ item.uses }}使用</p>
        <p class="text-xs text-muted mt-sm">触发词: {{ item.trigger }}</p>
        <span :class="item.price > 0 ? 'badge badge-accent' : 'badge badge-success'">¥{{ item.price || '免费' }}</span>
        <span class="badge badge-neutral" style="margin-left:4px">{{ item.type }}</span>
        <el-button type="primary" size="small" class="w-full mt-md" @click="applyToCanvas(item)">
          应用到画布
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('风格模型')
const tabs = ['风格模型', '角色资产', '场景资产', '提示词', '音色 / BGM']

const assets = [
  { id: 1, name: '韩漫风格 — 都市言情', author: 'AI视觉师', rating: 4.9, uses: '2.3k', trigger: 'korean manhwa style', price: 9.9, type: 'Checkpoint' },
  { id: 2, name: '写实风格 — 现代都市', author: '写实派', rating: 4.7, uses: '5.1k', trigger: 'realistic modern', price: 0, type: 'Checkpoint' },
  { id: 3, name: '二次元 — 日系动漫', author: '二次元画师', rating: 4.6, uses: '1.8k', trigger: 'anime style', price: 19.9, type: 'LoRA' },
  { id: 4, name: '水墨国风 — 仙侠古装', author: '国风画师', rating: 4.8, uses: '890', trigger: 'ink wash painting', price: 0, type: 'Style Pack' }
]

function applyToCanvas(item) {
  ElMessage.success(`"${item.name}" 已应用到画布`)
}
</script>
