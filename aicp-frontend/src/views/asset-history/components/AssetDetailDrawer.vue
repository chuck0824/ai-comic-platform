<template>
  <el-drawer v-model="visible" :title="detail?.name || '资产详情'" size="480px" @close="$emit('close')">
    <div v-if="loading" class="center py-xl"><el-icon class="is-loading" :size="24"><Loading /></el-icon></div>
    <div v-else-if="error" class="center py-xl text-muted">{{ error }}</div>
    <div v-else-if="detail" class="detail-body">
      <div class="detail-section">
        <h4>概览</h4>
        <div class="detail-row"><span>名称</span><span>{{ detail.name }}</span></div>
        <div class="detail-row"><span>分类</span><span>{{ detail.assetTypeLabel }}</span></div>
        <div class="detail-row"><span>媒体类型</span><span>{{ detail.mediaType || '—' }}</span></div>
        <div class="detail-row"><span>状态</span>
          <span :class="'status-tag status-' + (detail.status || '').toLowerCase()">{{ detail.statusLabel }}</span>
        </div>
        <div class="detail-row"><span>创建时间</span><span>{{ formatDate(detail.createdAt) }}</span></div>
        <div v-if="detail.fileSize" class="detail-row"><span>大小</span><span>{{ formatSize(detail.fileSize) }}</span></div>
        <div v-if="detail.width" class="detail-row"><span>分辨率</span><span>{{ detail.width }}×{{ detail.height }}</span></div>
        <div v-if="detail.modelId" class="detail-row"><span>模型</span><span>{{ detail.modelId }}</span></div>
      </div>
      <div v-if="detail.errorSummary" class="detail-section">
        <h4>错误信息</h4>
        <div class="error-box">{{ detail.errorSummary }}</div>
      </div>
      <div class="detail-section">
        <h4>操作</h4>
        <div class="action-buttons">
          <el-button v-if="detail.canDownload" size="small" @click="$emit('download')"><el-icon><Download /></el-icon>下载</el-button>
          <el-button v-if="detail.canSendToCanvas" size="small" type="primary" @click="$emit('send-to-canvas')"><el-icon><Position /></el-icon>发送到画布</el-button>
          <el-button v-if="detail.canRegenerate" size="small" @click="$emit('regenerate')"><el-icon><Refresh /></el-icon>再次生成</el-button>
          <el-button v-if="detail.canPublish" size="small" type="success" @click="$emit('publish')"><el-icon><Upload /></el-icon>发布市场</el-button>
          <el-button v-if="detail.canTrash" size="small" type="danger" @click="$emit('trash')"><el-icon><Delete /></el-icon>删除</el-button>
          <el-button v-if="detail.canRestore" size="small" type="primary" @click="$emit('restore')"><el-icon><RefreshLeft /></el-icon>恢复</el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
defineProps({ visible: Boolean, detail: Object, loading: Boolean, error: String })
defineEmits(['close', 'download', 'send-to-canvas', 'regenerate', 'publish', 'trash', 'restore'])
function formatDate(d) { return d ? new Date(d).toLocaleString('zh-CN') : '—' }
function formatSize(b) { return b > 1048576 ? (b/1048576).toFixed(1)+'MB' : b > 1024 ? (b/1024).toFixed(1)+'KB' : b+'B' }
</script>

<style scoped>
.detail-body { padding: 0 8px; }
.detail-section { margin-bottom: 20px; }
.detail-section h4 { font-size: 14px; margin-bottom: 8px; color: #94a3b8; }
.detail-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 13px; border-bottom: 1px solid #1e293b; }
.action-buttons { display: flex; gap: 8px; flex-wrap: wrap; }
.error-box { background: #3b1e1e; color: #f87171; padding: 8px 12px; border-radius: 6px; font-size: 12px; }
.status-tag { padding: 2px 8px; border-radius: 4px; font-size: 11px; background: #1e293b; }
.status-tag.status-running { background: #1e3a5f; color: #60a5fa; }
.status-tag.status-failed { background: #3b1e1e; color: #f87171; }
.status-tag.status-succeeded, .status-tag.status-active { background: #1e3b1e; color: #4ade80; }
.center { text-align: center; } .py-xl { padding: 60px 0; } .text-muted { color: #a1a1aa; }
</style>
