<template>
  <div>
    <h2 class="text-xl font-bold mb-lg">剧本交易市场</h2>
    <!-- 搜索+筛选 -->
    <div class="card mb-lg" style="padding:16px">
      <el-input v-model="search" placeholder="搜索剧本、作者、标签…" style="max-width:400px" clearable />
      <div class="mt-md">
        <span class="text-sm font-semibold">4轴标签筛选</span>
      </div>
      <div class="flex gap-sm flex-wrap mt-sm">
        <span class="text-xs font-semibold" style="min-width:40px">题材：</span>
        <span v-for="g in ['全部','言情','悬疑','科幻']" :key="g" class="tag" :class="{ selected: genre === g || (g==='全部' && !genre) }" @click="genre = g==='全部' ? '' : g">{{ g }}</span>
      </div>
      <div class="flex gap-sm flex-wrap mt-sm">
        <span class="text-xs font-semibold" style="min-width:40px">情节：</span>
        <span v-for="p in ['全部','重生','先婚后爱']" :key="p" class="tag" :class="{ selected: plot === p || (p==='全部' && !plot) }" @click="plot = p==='全部' ? '' : p">{{ p }}</span>
      </div>
      <div class="flex gap-sm flex-wrap mt-sm">
        <span class="text-xs font-semibold" style="min-width:40px">情绪：</span>
        <span v-for="t in ['全部','甜宠','爽文']" :key="t" class="tag" :class="{ selected: tone === t || (t==='全部' && !tone) }" @click="tone = t==='全部' ? '' : t">{{ t }}</span>
      </div>
      <div class="flex gap-sm mt-md">
        <el-select v-model="sort" style="width:140px">
          <el-option label="热门推荐" value="popular" /><el-option label="最新上架" value="latest" />
          <el-option label="销量最高" value="sales" /><el-option label="评分最高" value="rating" />
        </el-select>
      </div>
    </div>

    <div class="grid4">
      <div v-for="item in items" :key="item.id" class="card card-hover" style="padding:16px">
        <div class="canvas-mock" style="min-height:120px;margin-bottom:12px;font-size:24px;font-weight:700;color:var(--text-tertiary)">封面图</div>
        <div class="font-semibold">{{ item.title }}</div>
        <p class="text-sm text-muted">@{{ item.author }}</p>
        <div class="flex gap-sm flex-wrap mt-sm">
          <span v-for="t in item.tags" :key="t" class="tag selected">{{ t }}</span>
        </div>
        <p class="text-sm text-muted mt-sm">
          <el-icon style="vertical-align:-1px;color:#f59e0b"><StarFilled /></el-icon> {{ item.rating }} ·
          <span :class="item.price > 0 ? 'badge badge-accent' : 'badge badge-success'">
            {{ item.price > 0 ? '¥' + item.price : '免费' }}
          </span>
          · 已售{{ item.sales }} · {{ item.episodes }}集
        </p>
        <el-button type="primary" size="small" class="w-full mt-md" @click="ElMessage.info('进入剧本详情')">查看详情</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const search = ref('')
const genre = ref('')
const plot = ref('')
const tone = ref('')
const sort = ref('popular')

const items = [
  { id: 1, title: '霸道总裁的替身新娘', author: '编剧小王', tags: ['言情','重生','甜宠'], rating: 4.8, price: 29.9, sales: 128, episodes: 40 },
  { id: 2, title: '重生之商业帝国', author: '漫剧达人', tags: ['重生','爽文'], rating: 4.5, price: 19.9, sales: 56, episodes: 20 },
  { id: 3, title: '仙途之逆天改命', author: '仙侠创作者', tags: ['仙侠','系统'], rating: 4.2, price: 49.9, sales: 23, episodes: 80 },
  { id: 4, title: '穿越之我成了王妃', author: '穿越编剧', tags: ['穿越','甜宠'], rating: 4.0, price: 0, sales: 301, episodes: 20 }
]
</script>
