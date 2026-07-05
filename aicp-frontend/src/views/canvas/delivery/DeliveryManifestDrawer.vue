<template>
  <el-drawer :model-value="visible" title="交付清单" size="420px" @close="$emit('close')">
    <div v-if="manifest" class="delivery-drawer">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="交付版本">v{{ manifest.revision }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="manifestStatusType(manifest.status)" size="small">{{ manifest.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="SHA-256">
          <code style="font-size:10px">{{ manifest.manifestHash }}</code>
        </el-descriptions-item>
      </el-descriptions>

      <h4 style="margin-top:16px">交付条目 ({{ manifest.items?.length || 0 }})</h4>
      <div v-for="item in manifest.items" :key="item.assetVersionId" class="delivery-item">
        <span class="item-index">#{{ item.sortOrder + 1 }}</span>
        <span>Shot {{ item.shotUnitId }}</span>
        <span class="item-frames">{{ item.durationFrames }}帧 @ {{ item.fps }}fps</span>
        <el-tag size="small" type="success">已采用 v{{ item.adoptionId }}</el-tag>
      </div>

      <h4 style="margin-top:16px">外部交换格式</h4>
      <div class="exchange-actions">
        <el-button v-for="fmt in EXCHANGE_FORMATS" :key="fmt.id" size="small"
          :loading="packagingFormat === fmt.id"
          @click="handlePackage(fmt.id)">
          {{ fmt.label }} ({{ fmt.ext }})
        </el-button>
      </div>

      <el-alert v-if="packageResult" :title="packageResult" type="success" :closable="false" style="margin-top:12px" />

      <div class="readme-box" style="margin-top:16px">
        <h4>能力边界声明</h4>
        <p>EDL 仅记录镜头顺序和入出点。变速、转场、多轨音频混音、复合镜头和调色信息不在 EDL 中保留。完整创作意图请参考导演台 revision 和 FCPXML。</p>
      </div>
    </div>

    <div v-else class="delivery-empty">
      <el-empty description="暂无交付清单">
        <el-button type="primary" :disabled="!canCreate" @click="$emit('create')">创建交付清单</el-button>
      </el-empty>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { canvasApi } from '@/api/canvas.js'
import { canCreateManifest, manifestStatusType, EXCHANGE_FORMATS } from './deliveryState.js'

const props = defineProps({
  visible: Boolean,
  manifest: Object,
  project: Object
})

defineEmits(['close', 'create'])

const packagingFormat = ref(null)
const packageResult = ref('')

const canCreate = canCreateManifest(props.project || {})

async function handlePackage(format) {
  if (!props.manifest?.uuid) return
  packagingFormat.value = format
  try {
    await canvasApi.packageDelivery(props.manifest.uuid, format)
    packageResult.value = `${format.toUpperCase()} 打包任务已创建`
    ElMessage.success('打包任务已创建')
  } catch (e) {
    ElMessage.error('打包失败: ' + (e?.response?.data?.message || e.message))
  } finally {
    packagingFormat.value = null
  }
}
</script>

<style scoped>
.delivery-drawer { font-size: 13px; }
.delivery-item { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid #333; font-size: 12px; }
.item-index { color: #888; font-weight: 600; }
.item-frames { color: #666; margin-left: auto; margin-right: 8px; }
.exchange-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.readme-box { background: #1a1a1a; border-radius: 6px; padding: 12px; }
.readme-box h4 { font-size: 12px; margin: 0 0 6px; color: #888; }
.readme-box p { font-size: 11px; color: #666; line-height: 1.5; }
.delivery-empty { padding: 40px 0; }
</style>
