<template>
  <section class="scene-asset-library">
    <header class="library-heading">
      <div><p class="eyebrow">SCENE ASSETS</p><h2>场景资产库</h2><p>母资产、变体、剧本引用与分镜快照统一在项目内管理。</p></div>
      <div class="heading-actions">
        <el-button data-action="create-from-location" @click="openCreate('location')">从世界观地点创建</el-button>
        <el-button type="primary" data-action="new-scene-asset" @click="openCreate('blank')">新建场景资产</el-button>
      </div>
    </header>

    <el-alert v-if="sceneAssets.state.value === 'readonly'" type="warning" title="正在显示最近缓存，当前仅可查看" show-icon :closable="false" />
    <div class="filters">
      <el-input v-model="sceneAssets.filters.keyword" clearable placeholder="搜索名称、来源地点、地标或标签" />
      <el-select v-model="sceneAssets.filters.spaceType" clearable placeholder="空间类型"><el-option label="室内" value="INTERIOR" /><el-option label="室外" value="EXTERIOR" /><el-option label="混合" value="MIXED" /></el-select>
      <el-select v-model="sceneAssets.filters.reusability" clearable placeholder="复用级别"><el-option label="高复用" value="HIGH" /><el-option label="中复用" value="MEDIUM" /><el-option label="低复用" value="LOW" /></el-select>
      <el-select v-model="sceneAssets.filters.status" clearable placeholder="生命周期"><el-option label="启用" value="ACTIVE" /><el-option label="已停用" value="ARCHIVED" /></el-select>
      <el-select v-model="sceneAssets.filters.referenced" clearable placeholder="引用状态"><el-option label="已引用" :value="true" /><el-option label="未引用" :value="false" /></el-select>
      <el-button @click="reload">刷新</el-button>
    </div>

    <div v-if="sceneAssets.filteredAssets.value.length" class="asset-grid">
      <article v-for="asset in sceneAssets.filteredAssets.value" :key="asset.id" class="asset-card" @click="openAsset(asset)">
        <div class="cover" :style="coverStyle(asset)"><span v-if="!asset.master?.coverUrl" class="cover-fallback">场景</span></div>
        <div class="asset-card-body">
          <div class="card-title"><div><small>{{ asset.stableId || asset.master?.stableId || asset.uuid || `SCENE-${asset.id}` }}</small><h3>{{ asset.name }}</h3></div><el-tag :type="statusType(asset.status)">{{ lifecycleLabel(asset.status) }}</el-tag></div>
          <p>{{ asset.master?.spaceType || '未分类' }} · v{{ asset.currentVersionNo || 1 }} · {{ asset.variants?.length || 0 }} 个变体</p>
          <p>分集引用 {{ asset.episodeReferenceCount || asset.episodeReferences?.length || 0 }} 处<span v-if="asset.episodeReferences?.length">：{{ asset.episodeReferences.join('、') }}</span></p>
          <div class="card-status"><el-tag size="small" :type="syncType(asset.syncStatus)">{{ asset.syncStatus || 'CURRENT' }}</el-tag><el-button text data-action="view-impact" @click.stop="viewImpact(asset)">查看影响</el-button></div>
        </div>
      </article>
    </div>
    <el-empty v-else :description="sceneAssets.state.value === 'loading' ? '正在加载场景资产' : '暂无匹配的场景资产'" />

    <section v-if="sceneAssets.actionResults.value.length" class="result-history">
      <h3>最近操作结果</h3>
      <button v-for="result in sceneAssets.actionResults.value" :key="result.id" @click="openResult(result)"><strong>{{ actionLabel(result.action) }}</strong><span>{{ result.createdAt }} · 影响 {{ result.affectedConsumers?.length || 0 }} 处</span></button>
    </section>

    <SceneAssetDetailDrawer v-model="drawerVisible" :scene-assets="sceneAssets" @guidance="guide" @open-result="openResult" />
    <el-dialog v-model="createVisible" :title="createMode === 'location' ? '从世界观地点创建' : '新建场景资产'" width="620px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item v-if="createMode === 'location'" label="地点稳定 ID" :error="createErrors.worldLocationRef"><el-input v-model="createDraft.worldLocationRef" placeholder="WORLD-LOC-001" /></el-form-item>
        <el-form-item label="场景名称" :error="createErrors.name"><el-input v-model="createDraft.name" /></el-form-item>
        <div class="form-grid"><el-form-item label="空间类型" :error="createErrors.spaceType"><el-select v-model="createDraft.spaceType"><el-option label="室内" value="INTERIOR" /><el-option label="室外" value="EXTERIOR" /><el-option label="混合" value="MIXED" /></el-select></el-form-item><el-form-item label="复用级别" :error="createErrors.reusability"><el-select v-model="createDraft.reusability"><el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" /></el-select></el-form-item><el-form-item label="现实类型" :error="createErrors.realityType"><el-input v-model="createDraft.realityType" placeholder="REALISTIC / FANTASY" /></el-form-item></div>
      </el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" @click="saveCreate">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import SceneAssetDetailDrawer from './SceneAssetDetailDrawer.vue'

const props = defineProps({ sceneAssets: { type: Object, required: true }, autoLoad: { type: Boolean, default: true } })
const emit = defineEmits(['guidance', 'open-result'])
const sceneAssets = props.sceneAssets
const drawerVisible = ref(false); const createVisible = ref(false); const createMode = ref('blank')
const createDraft = reactive({ worldLocationRef: '', name: '', spaceType: '', reusability: 'MEDIUM', realityType: 'REALISTIC' })
const createErrors = reactive({})
onMounted(() => { if (props.autoLoad) reload() })
function guide(value) { emit('guidance', value); return value }
async function reload() { const result = await sceneAssets.load(); if (!result.ok) guide(result); return result }
function openAsset(asset) { sceneAssets.selectAsset(asset); drawerVisible.value = true }
async function viewImpact(asset) { openAsset(asset); const result = await sceneAssets.loadImpact(asset.id); if (!result.ok) guide(result); return result }
function openCreate(mode) { createMode.value = mode; Object.assign(createDraft, { worldLocationRef: '', name: '', spaceType: '', reusability: 'MEDIUM', realityType: 'REALISTIC' }); Object.keys(createErrors).forEach(key => delete createErrors[key]); createVisible.value = true }
async function saveCreate() {
  Object.keys(createErrors).forEach(key => delete createErrors[key])
  if (createMode.value === 'location' && !createDraft.worldLocationRef.trim()) createErrors.worldLocationRef = '请选择世界观中的地点'
  if (Object.keys(createErrors).length) return guide({ ok: false, code: 'WORLD_LOCATION_REQUIRED', message: createErrors.worldLocationRef })
  const operation = createMode.value === 'location' ? sceneAssets.createFromLocation : sceneAssets.create
  const result = await operation({ ...createDraft })
  if (!result.ok) { Object.assign(createErrors, result.fieldErrors || {}); return guide(result) }
  createVisible.value = false; drawerVisible.value = true; return result
}
function openResult(result) { const opened = sceneAssets.openActionResult(result); if (!opened.ok) return guide(opened); emit('open-result', result); drawerVisible.value = true }
function coverStyle(asset) { return asset.master?.coverUrl ? { backgroundImage: `url(${asset.master.coverUrl})` } : {} }
function statusType(status) { return String(status).toUpperCase() === 'ARCHIVED' ? 'info' : 'success' }
function lifecycleLabel(status) { return String(status).toUpperCase() === 'ARCHIVED' ? '已停用' : '启用中' }
function syncType(status) { return String(status).toUpperCase() === 'STALE' || String(status).toUpperCase() === 'NEEDS_SYNC' ? 'warning' : 'success' }
function actionLabel(action) { return ({ 'restore-version': '恢复历史版本', 'replace-reference': '迁移引用', 'update-scene-asset': '更新场景资产', 'deactivate-scene-asset': '停用场景资产' })[action] || action }
</script>

<style scoped>
.scene-asset-library{display:grid;gap:18px}.library-heading,.heading-actions,.filters,.card-title,.card-status{display:flex;align-items:center;justify-content:space-between;gap:12px}.eyebrow{color:var(--el-color-primary);font-weight:700;margin:0}.library-heading h2{margin:4px 0}.library-heading p,.asset-card p{color:var(--el-text-color-secondary)}.filters{display:grid;grid-template-columns:minmax(240px,2fr) repeat(4,minmax(120px,1fr)) auto}.asset-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:14px}.asset-card{overflow:hidden;border:1px solid var(--el-border-color);border-radius:14px;background:var(--el-fill-color-blank);cursor:pointer}.cover{height:132px;background:linear-gradient(135deg,var(--el-color-primary-light-7),var(--el-fill-color-dark));background-size:cover;background-position:center;display:grid;place-items:center}.cover-fallback{font-size:22px;font-weight:700;color:var(--el-text-color-secondary)}.asset-card-body{padding:14px}.card-title h3{margin:4px 0}.card-title small{color:var(--el-text-color-placeholder)}.result-history{display:grid;gap:8px}.result-history button{display:flex;justify-content:space-between;padding:12px;border:1px solid var(--el-border-color);background:var(--el-fill-color-blank);border-radius:8px;cursor:pointer}.form-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}@media(max-width:1000px){.filters{grid-template-columns:1fr 1fr}.library-heading{align-items:flex-start;flex-direction:column}}@media(max-width:600px){.filters,.form-grid{grid-template-columns:1fr}}
</style>
