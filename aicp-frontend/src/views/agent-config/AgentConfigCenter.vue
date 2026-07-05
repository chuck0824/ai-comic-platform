<script setup>
import { onMounted } from 'vue'
import { useAgentConfig } from './useAgentConfig'
import AgentDefinitionList from './components/AgentDefinitionList.vue'
import AgentEditorPanel from './components/AgentEditorPanel.vue'
import CreateAgentWizard from './components/CreateAgentWizard.vue'

const {
  blueprints, definitions, selected, loading, error, filter,
  selectedVersions, selectedDraft,
  load, selectDefinition,
  createDefinition, copyDefinition, archiveDefinition,
  updateDraft, validateDraft, runTest, publishVersion
} = useAgentConfig()

const showCreate = ref(false)

onMounted(() => load())

const handleCreated = async (data) => {
  showCreate.value = false
  await createDefinition(data)
}

const handleSelect = (def) => selectDefinition(def)
const handleCopy = (id) => copyDefinition(id)
const handleArchive = (id) => archiveDefinition(id)
</script>

<template>
  <div class="config-center">
    <div class="cc-header">
      <h2>Agent 配置中心</h2>
      <el-button type="primary" @click="showCreate = true">
        <el-icon><Plus /></el-icon>新增 Agent
      </el-button>
    </div>

    <div v-if="loading" class="cc-loading">
      <el-skeleton :rows="8" animated />
    </div>

    <div v-else-if="error" class="cc-error">
      <el-empty :description="error" />
      <el-button @click="load">重试</el-button>
    </div>

    <div v-else class="cc-body">
      <div class="cc-left">
        <AgentDefinitionList
          :blueprints="blueprints"
          :definitions="definitions"
          :filter="filter"
          :selected="selected"
          @select="handleSelect"
          @copy="handleCopy"
          @archive="handleArchive"
        />
      </div>
      <div class="cc-right">
        <AgentEditorPanel
          v-if="selected"
          :definition="selected"
          :versions="selectedVersions"
          :draft="selectedDraft"
          :blueprints="blueprints"
          @update-draft="updateDraft"
          @validate="validateDraft"
          @test="runTest"
          @publish="publishVersion"
          @refresh="() => selectDefinition(selected)"
        />
        <el-empty v-else description="选择左侧 Agent 开始编辑" />
      </div>
    </div>

    <CreateAgentWizard
      v-if="showCreate"
      :blueprints="blueprints"
      @created="handleCreated"
      @close="showCreate = false"
    />
  </div>
</template>

<style scoped>
.config-center {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px 24px;
  gap: 16px;
}
.cc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.cc-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}
.cc-body {
  flex: 1;
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  overflow: hidden;
}
.cc-left {
  overflow-y: auto;
  border-right: 1px solid var(--border);
  padding-right: 16px;
}
.cc-right {
  overflow-y: auto;
}
.cc-loading, .cc-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  gap: 16px;
}
@media (max-width: 1024px) {
  .cc-body {
    grid-template-columns: 1fr;
  }
  .cc-left {
    border-right: none;
    padding-right: 0;
  }
}
</style>
