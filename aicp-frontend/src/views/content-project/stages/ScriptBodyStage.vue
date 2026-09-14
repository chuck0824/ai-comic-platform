<template>
  <section class="stage-panel">
    <header>
      <p class="eyebrow">STEP 6</p>
      <h2>剧本正文</h2>
      <p>选择正文块后执行 AI 修订；场景资产版本在绑定时固定。</p>
    </header>
    <el-alert
      v-for="warning in state.preStoryboardWarnings"
      :key="warning.sceneId"
      type="warning"
      :title="`${warning.sceneId}：${warning.message}`"
      :closable="false"
      show-icon
    />
    <article v-for="episode in state.episodes" :key="episode.id" class="episode">
      <div class="toolbar">
        <strong>{{ episode.title || episode.id }}</strong>
        <el-button data-action="add-scene" @click="addScene(episode.id)">新增场景</el-button>
      </div>
      <section v-for="scene in episode.scenes" :key="scene.id" class="scene">
        <div class="toolbar">
          <div>
            <strong>{{ scene.heading || scene.id }}</strong>
            <el-tag :type="sceneAssetStatus(scene) === 'STALE' ? 'warning' : (scene.assetBinding ? 'success' : 'warning')">
              {{ scene.assetBinding ? sceneAssetStatus(scene) : '待绑定资产' }}
            </el-tag>
          </div>
          <div>
            <el-button data-action="change-scene-space" @click="openSpaceChange(scene)">修改空间</el-button>
            <el-button @click="scene.assetBinding ? openSceneAsset(scene) : openPicker(scene.id)">
              {{ scene.assetBinding ? '打开资产详情' : '场景资产' }}
            </el-button>
            <el-button data-action="add-script-block" @click="addBlock(scene.id)">新增正文块</el-button>
          </div>
        </div>
        <button
          v-for="block in scene.blocks"
          :key="block.id"
          class="block"
          :class="{ selected: state.selectedBlockId === block.id }"
          @click="chooseBlock(block.id)"
        >
          <small>{{ block.type }} · {{ block.id }}</small>
          <span>{{ block.text || '空正文块' }}</span>
        </button>
      </section>
    </article>
    <div class="action-grid">
      <el-button data-action="continue-selected-block" @click="ai('continue-selected-block')">续写选中段落</el-button>
      <el-button data-action="strengthen-conflict" @click="ai('strengthen-conflict')">增强冲突</el-button>
      <el-button data-action="condense-dialogue" @click="ai('condense-dialogue')">精简对白</el-button>
      <el-button data-action="rewrite-tone" @click="ai('rewrite-tone')">改写语气</el-button>
      <el-button data-action="check-character-consistency" @click="ai('check-character-consistency')">检查角色一致性</el-button>
      <el-button data-action="run-script-check" @click="check">运行正文检查</el-button>
      <el-button data-action="export-script" @click="exportBody">导出正文</el-button>
      <el-button data-action="regenerate-current-artifact" @click="regenerate">重新生成当前产物</el-button>
    </div>
    <el-button v-if="sceneAssetState?.actionResult?.value" text @click="openSceneActionResult">查看场景资产最近操作结果</el-button>
    <SceneAssetPicker
      v-model="pickerVisible"
      :assets="assetList"
      :degraded="sceneAssetsDegraded"
      @bind-existing="bindExisting"
      @create-new="createAndBind"
      @defer="deferBinding"
      @guidance="guide"
    />
    <el-dialog v-model="spaceVisible" title="修改场景空间" width="520px">
      <el-form label-position="top">
        <el-form-item label="空间"><el-input v-model="spaceDraft.value" /></el-form-item>
        <el-form-item label="修改范围">
          <el-radio-group v-model="spaceDraft.scope">
            <el-radio value="CURRENT_SCENE">仅当前场景</el-radio>
            <el-radio value="MASTER_ASSET">更新母资产</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="spaceVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSpaceChange">确认修改</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="rewriteVisible" title="局部改写候选" width="640px" :close-on-click-modal="false">
      <div class="rewrite-diff">
        <section>
          <h4>改写前</h4>
          <pre>{{ rewriteCandidate?.before || '—' }}</pre>
        </section>
        <section>
          <h4>改写后</h4>
          <pre>{{ rewriteCandidate?.after || '—' }}</pre>
        </section>
      </div>
      <div v-if="(rewriteCandidate?.patches || []).length > 1" class="rewrite-patches">
        <h4>选择要采用的改动</h4>
        <el-checkbox-group v-model="selectedPatchIndexes">
          <el-checkbox
            v-for="(patch, index) in rewriteCandidate.patches"
            :key="index"
            :label="index"
          >
            #{{ index + 1 }} {{ patch.reason || '改写' }}：{{ previewPatch(patch) }}
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button :disabled="rewriteBusy" @click="discardRewrite">放弃</el-button>
        <el-button
          type="primary"
          :loading="rewriteBusy"
          :disabled="!canAdoptSelectedPatches"
          @click="adoptRewrite"
        >
          采用并写入草稿
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import SceneAssetPicker from '../components/SceneAssetPicker.vue'
import {
  addScriptBlock,
  addScriptScene,
  applyBlockAiAction,
  bindScriptSceneAsset,
  changeSceneSpace,
  createScriptBodyState,
  deferSceneAssetBinding,
  exportScript,
  runScriptCheck,
  selectScriptBlock
} from '../workbench/downstreamStageModel.js'
import { runArtifactRegeneration } from '../workbench/upstreamStageModel.js'
import { sceneConsumerStatus } from '../workbench/sceneAssetUiModel.js'
import { flattenScriptBodyPlainText } from '../workbench/localRewriteHelper.js'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  sceneAssets: { type: Array, default: () => [] },
  sceneAssetState: { type: Object, default: null },
  sceneAssetsDegraded: Boolean,
  blockActionAdapter: Function,
  localRewriteAdoptAdapter: Function,
  bindSceneAssetAdapter: Function,
  createSceneAssetAdapter: Function,
  spaceChangeAdapter: Function,
  scriptCheckAdapter: Function,
  exportAdapter: Function,
  regenerateArtifactAdapter: Function,
  workbench: Object,
  generationInput: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue', 'guidance', 'result', 'open-scene-asset', 'open-scene-action-result'])

const state = reactive(createScriptBodyState(props.modelValue))
const pickerVisible = ref(false)
const spaceVisible = ref(false)
const spaceDraft = reactive({ sceneId: null, value: '', scope: null })
const rewriteVisible = ref(false)
const rewriteBusy = ref(false)
const rewriteCandidate = ref(null)
const rewriteBlockId = ref(null)
const rewritePlainSnapshot = ref('')
const selectedPatchIndexes = ref([])

const assetList = computed(() => props.sceneAssetState?.assets?.value || props.sceneAssets)
const canAdoptSelectedPatches = computed(() => {
  const patches = rewriteCandidate.value?.patches || []
  if (patches.length <= 1) return true
  return selectedPatchIndexes.value.length > 0
})
watch(() => props.modelValue, v => Object.assign(state, createScriptBodyState(v)), { deep: true })

const sync = () => emit('update:modelValue', JSON.parse(JSON.stringify(state)))
function guide(v) { emit('guidance', v); return v }
function done(v) {
  if (v?.allowed === false || v?.ok === false) return guide(v)
  sync()
  emit('result', v)
  return v
}
function previewPatch(patch) {
  const text = String(patch?.replacement || '')
  return text.length > 36 ? `${text.slice(0, 36)}…` : text
}

function addScene(ep) {
  const value = addScriptScene(state, ep, { heading: '新场景' })
  pickerVisible.value = true
  return done(value)
}
function addBlock(scene) { return done(addScriptBlock(state, scene, { type: 'action', text: '' })) }
function chooseBlock(id) { selectScriptBlock(state, id); sync() }
function openPicker(id) { state.sceneAssetPicker = { open: true, sceneId: id }; pickerVisible.value = true }

async function ai(action) {
  const value = await applyBlockAiAction(state, action, props.blockActionAdapter)
  if (value?.localRewrite && value.candidate) {
    rewriteBlockId.value = value.block?.id || state.selectedBlockId
    rewriteCandidate.value = value.candidate
    rewritePlainSnapshot.value = flattenScriptBodyPlainText(state).plainText
    selectedPatchIndexes.value = (value.candidate.patches || []).map((_, index) => index)
    rewriteVisible.value = true
  }
  return done(value)
}

async function adoptRewrite() {
  if (typeof props.localRewriteAdoptAdapter !== 'function') {
    return guide({ allowed: false, code: 'LOCAL_REWRITE_ADOPT_REQUIRED', title: '采用服务不可用', message: '开启阶段事实链后才能采用局部改写。' })
  }
  if (!canAdoptSelectedPatches.value) {
    return guide({ allowed: false, code: 'LOCAL_REWRITE_PATCH_REQUIRED', title: '请选择改动', message: '至少选择一处 Patch 后再采用。' })
  }
  rewriteBusy.value = true
  try {
    const result = await props.localRewriteAdoptAdapter({
      blockId: rewriteBlockId.value,
      candidate: rewriteCandidate.value,
      plainText: rewritePlainSnapshot.value,
      selectedPatchIndexes: selectedPatchIndexes.value
    })
    if (result?.allowed === false || result?.ok === false) return guide(result)
    if (result?.block?.text != null) {
      for (const episode of state.episodes || []) {
        for (const scene of episode.scenes || []) {
          const block = (scene.blocks || []).find(item => item.id === rewriteBlockId.value)
          if (block) block.text = result.block.text
        }
      }
    }
    rewriteVisible.value = false
    rewriteCandidate.value = null
    selectedPatchIndexes.value = []
    return done(result)
  } catch (caught) {
    return guide({
      allowed: false,
      code: 'LOCAL_REWRITE_ADOPT_FAILED',
      title: '采用失败',
      message: caught?.response?.data?.message || caught?.message || '正文已变化或 revision 冲突，请重试。'
    })
  } finally {
    rewriteBusy.value = false
  }
}

function discardRewrite() {
  rewriteVisible.value = false
  rewriteCandidate.value = null
  rewriteBlockId.value = null
  selectedPatchIndexes.value = []
}

async function check() { return done(await runScriptCheck(state, props.scriptCheckAdapter)) }
async function exportBody() { return done(await exportScript(state, props.exportAdapter)) }

async function bindExisting({ asset, variant }) {
  const result = await bindScriptSceneAsset(state, state.sceneAssetPicker.sceneId, asset, variant, props.bindSceneAssetAdapter, { degraded: props.sceneAssetsDegraded })
  if (result?.allowed !== false) pickerVisible.value = false
  return done(result)
}
async function createAndBind(draft) {
  if (typeof props.createSceneAssetAdapter !== 'function') {
    return guide({ allowed: false, code: 'SCENE_ASSET_CREATE_REQUIRED', title: '创建服务不可用', message: '恢复场景资产服务后重试。', targetAction: 'retry_scene_assets' })
  }
  const created = await props.createSceneAssetAdapter(draft)
  if (!created?.persisted) return guide(created)
  return bindExisting({ asset: created.asset, variant: created.variant })
}
function deferBinding() {
  deferSceneAssetBinding(state, state.sceneAssetPicker.sceneId)
  pickerVisible.value = false
  sync()
}
function openSpaceChange(scene) {
  Object.assign(spaceDraft, { sceneId: scene.id, value: scene.space || '', scope: null })
  spaceVisible.value = true
}
async function saveSpaceChange() {
  const result = await changeSceneSpace(state, spaceDraft.sceneId, spaceDraft.value, spaceDraft.scope, props.spaceChangeAdapter)
  if (result?.allowed !== false) spaceVisible.value = false
  return done(result)
}
async function regenerate() {
  return done(await runArtifactRegeneration({
    workbench: props.workbench,
    input: { ...props.generationInput, subtask: '重新生成剧本正文' },
    execute: props.regenerateArtifactAdapter
  }))
}
function sceneAssetFor(scene) {
  const id = scene.assetBinding?.sceneAssetId
  return assetList.value.find(asset => asset.id === id) || null
}
function sceneAssetStatus(scene) {
  const asset = sceneAssetFor(scene)
  return sceneConsumerStatus({
    assetId: asset?.id,
    type: 'SCRIPT_SCENE',
    id: scene.id,
    consumerKey: String(scene.id),
    result: props.sceneAssetState?.actionResult?.value?.data,
    fallback: asset?.syncStatus || asset?.status || 'CURRENT'
  })
}
function openSceneAsset(scene) {
  const asset = sceneAssetFor(scene)
  if (!asset) return guide({ ok: false, code: 'SCENE_ASSET_NOT_FOUND', message: '已绑定的场景资产不在当前项目列表中' })
  props.sceneAssetState?.selectAsset?.(asset)
  emit('open-scene-asset', asset)
}
function openSceneActionResult() {
  const result = props.sceneAssetState?.actionResult?.value?.data
  if (!result) return guide({ ok: false, code: 'SCENE_ACTION_RESULT_NOT_FOUND', message: '暂无可查看的场景资产结果' })
  props.sceneAssetState?.openActionResult?.(result)
  emit('open-scene-action-result', result)
}
</script>

<style scoped>
.stage-panel { display: grid; gap: 14px; }
.eyebrow { color: var(--el-color-primary); font-weight: 700; }
.episode, .scene { border: 1px solid var(--el-border-color); border-radius: 12px; padding: 14px; display: grid; gap: 10px; }
.scene { margin-top: 12px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.toolbar > div { display: flex; align-items: center; gap: 8px; }
.block {
  display: grid; width: 100%; text-align: left; gap: 4px; padding: 10px;
  border: 1px solid var(--el-border-color); background: var(--el-fill-color-blank); border-radius: 8px; cursor: pointer;
}
.block.selected { border-color: var(--el-color-primary); }
.block small, header p { color: var(--el-text-color-secondary); }
.action-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.rewrite-diff { display: grid; gap: 12px; }
.rewrite-diff pre {
  margin: 0; white-space: pre-wrap; word-break: break-word;
  padding: 12px; border-radius: 8px; background: var(--el-fill-color-light);
}
.rewrite-patches { margin-top: 12px; display: grid; gap: 8px; }
.rewrite-patches h4 { margin: 0; font-size: 14px; }
</style>
