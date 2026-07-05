<template>
  <div class="context-panel">
    <div class="panel-title">项目上下文</div>

    <!-- Selected Versions -->
    <div v-if="versions && versions.length" class="panel-section">
      <div class="panel-section-label">已选版本</div>
      <div v-for="v in versions" :key="v.key" class="panel-item">
        <span class="panel-item-key">{{ v.key }}</span>
        <span class="panel-item-value">v{{ v.versionNo }}</span>
      </div>
    </div>

    <!-- Locked Facts -->
    <div class="panel-section">
      <div class="panel-section-label">锁定事实</div>
      <div v-if="!lockedFacts || lockedFacts.length === 0" class="panel-empty">暂无</div>
      <div v-for="(f, i) in lockedFacts" :key="i" class="panel-item">
        <el-tag size="small" effect="light">{{ f.label }}</el-tag>
        <span class="panel-item-value">{{ f.value }}</span>
      </div>
    </div>

    <!-- Impact Summary -->
    <div class="panel-section">
      <div class="panel-section-label">影响范围</div>
      <div v-if="!impactSummary" class="panel-empty">—</div>
      <p v-else class="panel-text">{{ impactSummary }}</p>
    </div>

    <!-- Storyboard Intent -->
    <div v-if="storyboardIntent" class="panel-section">
      <div class="panel-section-label">分镜意向</div>
      <el-tag
        :type="storyboardIntent === 'requested' ? 'warning' : 'info'"
        size="small"
        effect="light"
      >
        {{ storyboardIntent === 'requested' ? '已请求' : storyboardIntent === 'skipped' ? '已跳过' : '未决定' }}
      </el-tag>
    </div>

    <!-- Bible Health -->
    <div class="panel-section">
      <div class="panel-section-label">创作圣经</div>
      <div v-if="!bibleHealth || bibleHealth.status === 'missing'" class="panel-empty">
        <span class="bible-warning">尚未确认</span>
      </div>
      <div v-else-if="bibleHealth.status === 'loading'" class="panel-empty">加载中…</div>
      <div v-else class="panel-item">
        <span class="panel-item-key">版本</span>
        <span class="panel-item-value">v{{ bibleHealth.current_version_no }}</span>
      </div>
      <div v-if="bibleHealth && bibleHealth.status !== 'missing'" class="panel-item">
        <span class="panel-item-key">状态</span>
        <el-tag size="small" :type="bibleHealth.ready_for_generation ? 'success' : 'warning'" effect="light">
          {{ bibleHealth.ready_for_generation ? '已确认' : bibleHealth.status }}
        </el-tag>
      </div>
    </div>

    <!-- Selected Context (from last generation) -->
    <div v-if="selectedContext" class="panel-section">
      <div class="panel-section-label">上次生成上下文</div>
      <div v-if="selectedContext.bible_version_id" class="panel-item">
        <span class="panel-item-key">圣经版本</span>
        <span class="panel-item-value">v{{ selectedContext.bible_version_id }}</span>
      </div>
      <div v-if="selectedContext.project_guide_id" class="panel-item">
        <span class="panel-item-key">项目口径</span>
        <span class="panel-item-value">已绑定</span>
      </div>
      <div v-if="selectedContext.payload_hash" class="panel-item">
        <span class="panel-item-key">快照哈希</span>
        <span class="panel-item-value mono">{{ selectedContext.payload_hash?.slice(0, 12) }}…</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  versions: { type: Array, default: () => [] },
  lockedFacts: { type: Array, default: () => [] },
  impactSummary: { type: String, default: '' },
  storyboardIntent: { type: String, default: '' },
  bibleHealth: { type: Object, default: null },
  selectedContext: { type: Object, default: null }
})
</script>

<style scoped>
.context-panel {
  width: 240px;
  border-left: 1px solid var(--border);
  padding: 16px 12px;
  overflow-y: auto;
  background: var(--bg-surface);
  flex-shrink: 0;
}
.panel-title {
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .04em;
  color: var(--text-secondary);
  margin-bottom: 16px;
  padding: 0 4px;
}

.panel-section {
  margin-bottom: 16px;
}
.panel-section-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: .06em;
  color: var(--text-tertiary);
  font-weight: 600;
  margin-bottom: 6px;
  padding: 0 4px;
}

.panel-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px;
  font-size: 12px;
  line-height: 1.5;
}
.panel-item-key {
  font-weight: 600;
  color: var(--text-primary);
}
.panel-item-value {
  color: var(--text-secondary);
}

.panel-empty {
  font-size: 12px;
  color: var(--text-tertiary);
  padding: 4px;
}
.panel-text {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
  margin: 0;
  padding: 0 4px;
}

.bible-warning {
  color: var(--warning);
  font-weight: 600;
}
.mono {
  font-family: var(--font-mono, 'SF Mono', 'Fira Code', monospace);
  font-size: 11px;
}

@media (max-width: 768px) {
  .context-panel {
    width: 100%;
    border-left: none;
    border-top: 1px solid var(--border);
    padding: 12px;
  }
}
</style>
