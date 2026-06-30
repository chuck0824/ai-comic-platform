<template>
  <div class="storyboard-editor" data-testid="storyboard-editor">
    <!-- Loading -->
    <div v-if="loading" class="loading-state" data-testid="loading">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- Error -->
    <div v-else-if="error" class="error-state" data-testid="error">
      <el-result icon="error" :title="error" sub-title="请检查项目权限或刷新重试">
        <template #extra>
          <el-button type="primary" @click="load">重新加载</el-button>
          <el-button @click="$router.back()">返回</el-button>
        </template>
      </el-result>
    </div>

    <!-- Empty -->
    <div v-else-if="!activeVersion" class="empty-state" data-testid="empty">
      <el-result icon="info" title="暂无分镜版本" sub-title="请先生成或导入分镜">
        <template #extra>
          <el-button type="primary" @click="$router.back()">返回项目</el-button>
        </template>
      </el-result>
    </div>

    <!-- Main Editor -->
    <template v-else>
      <!-- Topbar -->
      <StoryboardTopbar
        :storyboard="storyboard"
        :active-version="activeVersion"
        :versions="versions"
        :save-state="saveState"
        :is-locked="isLocked"
        :total-shots="shots.length"
        :total-duration-ms="totalDurationMs"
        @switch-version="switchVersion"
        @lock="lockCurrentVersion"
        @fork="forkVersion"
        @upgrade="upgradeVersion"
      />

      <!-- Module Tabs -->
      <StoryboardModuleTabs
        :active-module="activeModule"
        :is-locked="isLocked"
        @select="setActiveModule"
      />

      <!-- Module Content -->
      <div class="editor-body">
        <!-- Shots Module: Three-column layout -->
        <template v-if="activeModule === 'shots'">
          <div class="three-column-layout" data-testid="three-column-layout">
            <SceneNavigator
              :scenes="shotsGroupedByScene"
              :selected-shot-id="selectedShotId"
              @select-shot="setSelectedShot"
              @add-shot="addShot"
            />
            <ShotGrid
              :shots="shots"
              :scenes="scenes"
              :selected-shot-id="selectedShotId"
              :is-locked="isLocked"
              :loading="loading"
              @select-shot="setSelectedShot"
              @add-shot="addShot"
              @duplicate-shot="duplicateShot"
              @remove-shot="removeShot"
              @split-shot="splitShot"
            />
            <ShotInspector
              v-if="selectedShot"
              :shot="selectedShot"
              :is-locked="isLocked"
              :save-state="saveState"
              :conflict-diffs="conflictDiffs"
              @patch="(patch) => queuePatch(selectedShot.id, patch)"
              @resolve-conflict="resolveConflict"
            />
            <div v-else class="no-selection-panel">
              <el-empty description="选择一个镜头查看详情" :image-size="80" />
            </div>
          </div>
        </template>

        <!-- Other module placeholders -->
        <EmotionRhythmPanel
          v-if="activeModule === 'emotion'"
          :project-id="projectId"
          :storyboard-id="storyboardId"
          :version-id="activeVersion.id"
          :is-locked="isLocked"
        />
        <PromptTemplatePanel
          v-if="activeModule === 'prompts'"
          :project-id="projectId"
          :storyboard-id="storyboardId"
          :version-id="activeVersion.id"
          :is-locked="isLocked"
        />
        <CreativeRulePanel
          v-if="activeModule === 'rules'"
          :project-id="projectId"
          :storyboard-id="storyboardId"
          :version-id="activeVersion.id"
          :is-locked="isLocked"
        />
        <CharacterVisualPanel
          v-if="activeModule === 'visuals'"
          :project-id="projectId"
          :storyboard-id="storyboardId"
          :version-id="activeVersion.id"
          :is-locked="isLocked"
        />
        <VersionReviewPanel
          v-if="activeModule === 'review'"
          :project-id="projectId"
          :storyboard-id="storyboardId"
          :version-id="activeVersion.id"
          :versions="versions"
          :is-locked="isLocked"
          @switch-version="switchVersion"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useStoryboardEditor } from './composables/useStoryboardEditor'
import StoryboardTopbar from './components/StoryboardTopbar.vue'
import StoryboardModuleTabs from './components/StoryboardModuleTabs.vue'
import SceneNavigator from './components/SceneNavigator.vue'
import ShotGrid from './components/ShotGrid.vue'
import ShotInspector from './components/ShotInspector.vue'
import EmotionRhythmPanel from './components/EmotionRhythmPanel.vue'
import PromptTemplatePanel from './components/PromptTemplatePanel.vue'
import CreativeRulePanel from './components/CreativeRulePanel.vue'
import CharacterVisualPanel from './components/CharacterVisualPanel.vue'
import VersionReviewPanel from './components/VersionReviewPanel.vue'

const {
  projectId, storyboardId, storyboard, versions, activeVersion,
  scenes, shots, selectedShotId, activeModule,
  loading, error, saveState, isDirty, conflictDiffs,
  isLocked, selectedShot, shotsGroupedByScene, totalDurationMs,
  load, switchVersion,
  queuePatch, resolveConflict,
  addShot, duplicateShot, removeShot, splitShot,
  lockCurrentVersion, forkVersion, upgradeVersion,
  setSelectedShot, setActiveModule
} = useStoryboardEditor()

onMounted(() => {
  load()
})
</script>

<style scoped>
.storyboard-editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color-page, #f5f7fa);
}

.loading-state,
.error-state,
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
}

.editor-body {
  flex: 1;
  overflow: hidden;
}

.three-column-layout {
  display: grid;
  grid-template-columns: 180px minmax(580px, 1fr) 300px;
  height: 100%;
  gap: 0;
}

.no-selection-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-bg-color, #fff);
  border-left: 1px solid var(--el-border-color-light, #e4e7ed);
}

@media (max-width: 1150px) {
  .three-column-layout {
    grid-template-columns: 160px 1fr;
  }
  .no-selection-panel {
    display: none;
  }
}
</style>
