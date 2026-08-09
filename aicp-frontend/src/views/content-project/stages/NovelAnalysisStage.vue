<template>
  <section class="stage-panel">
    <header><p class="eyebrow">STEP 3</p><h2>小说分析</h2><p>分析结果以 Markdown 产物版本管理；保存后展示影响范围。</p></header>

    <article class="analysis-card">
      <div class="card-heading"><h3>基础信息 · 故事梗概</h3><el-button data-action="edit-synopsis" @click="openEditor('synopsis')">编辑梗概</el-button></div>
      <p>{{ state.synopsis || '尚未生成故事梗概' }}</p>
    </article>

    <div class="two-columns">
      <article class="analysis-card">
        <div class="card-heading"><h3>主要事件列表</h3><el-button data-action="add-event" @click="openEditor('events', true)">新增事件</el-button></div>
        <ol v-if="state.events.length"><li v-for="event in state.events" :key="event.id || event.title"><strong>{{ event.title }}</strong><span>{{ event.summary }}</span></li></ol><el-empty v-else description="暂无主要事件" :image-size="64" />
        <el-button v-if="state.events.length" text @click="openEditor('events')">编辑全部事件</el-button>
      </article>
      <article class="analysis-card">
        <div class="card-heading"><h3>章节大纲</h3><el-button data-action="edit-chapter-outline" @click="openEditor('chapterOutline')">编辑章节大纲</el-button></div>
        <ol v-if="state.chapterOutline.length"><li v-for="chapter in state.chapterOutline" :key="chapter.id || chapter.title"><strong>{{ chapter.title }}</strong><span>{{ chapter.summary }}</span></li></ol><el-empty v-else description="暂无章节大纲" :image-size="64" />
      </article>
    </div>

    <article class="analysis-card">
      <div class="card-heading"><h3>世界观</h3><el-button data-action="edit-worldview" @click="openEditor('worldview')">编辑世界观</el-button></div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="整体世界类型与时间">{{ state.worldview.worldType || '—' }} / {{ state.worldview.timeSetting || '—' }}</el-descriptions-item>
        <el-descriptions-item label="力量体系 / 规则">{{ state.worldview.powerSystem || '—' }} / {{ state.worldview.rules || '—' }}</el-descriptions-item>
        <el-descriptions-item label="势力阵营" :span="2">{{ state.worldview.factions?.join('、') || '—' }}</el-descriptions-item>
      </el-descriptions>
      <h4>主要地点</h4>
      <div class="location-list">
        <div v-for="location in state.locations" :key="location.id" class="location-row">
          <div><strong>{{ location.name }}</strong><p>{{ location.description || location.spaceType || '暂无地点描述' }}</p></div>
          <div v-if="location.sceneAsset" class="asset-reference"><el-tag type="success">{{ location.sceneAsset.status }}</el-tag><span>{{ location.sceneAsset.stableId }} · v{{ location.sceneAsset.versionNo }}</span><el-button text @click="convertLocation(location)">打开资产详情</el-button></div>
          <el-button v-else data-action="convert-location-to-scene-asset" @click="convertLocation(location)">转为场景资产</el-button>
        </div>
        <el-empty v-if="!state.locations.length" description="暂无主要地点" :image-size="64" />
      </div>
    </article>

    <article class="analysis-card">
      <div class="card-heading"><h3>人物小传</h3><el-button data-action="character-detail" @click="openCharacter()">新增人物</el-button></div>
      <div class="character-grid"><button v-for="(character,index) in state.characters" :key="character.id || character.name" class="character-card" data-action="character-detail" @click="openCharacter(index)"><strong>{{ character.name }}</strong><span>{{ character.role }} · {{ character.goal }}</span><small>{{ character.personality }} / {{ character.arc }}</small></button></div>
      <el-empty v-if="!state.characters.length" description="暂无人物小传" :image-size="64" />
    </article>

    <el-alert v-if="latestVersion" type="success" :title="`已保存 ${latestVersion.section} v${latestVersion.version}`" :description="impactText(latestVersion.impact)" show-icon :closable="false" />

    <el-dialog v-model="editorVisible" :title="editorTitle" width="680px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item v-if="editorSection === 'synopsis'" label="故事梗概"><el-input v-model="editorDraft" type="textarea" :rows="12" /></el-form-item>
        <template v-if="editorSection === 'events' || editorSection === 'chapterOutline'">
          <div v-for="(item,index) in editorDraft" :key="item.id || index" class="list-editor">
            <el-input v-model="item.title" :placeholder="editorSection === 'events' ? '事件名称' : '章节标题'" /><el-input v-model="item.summary" type="textarea" :rows="3" placeholder="事件 / 章节摘要" /><el-button text type="danger" @click="editorDraft.splice(index,1)">移除</el-button>
          </div>
          <el-button @click="editorDraft.push({ id: `${editorSection}-${Date.now()}`, title:'', summary:'' })">+ 新增一项</el-button>
        </template>
        <template v-if="editorSection === 'worldview'">
          <div class="form-grid"><el-form-item label="世界类型"><el-input v-model="editorDraft.worldType" /></el-form-item><el-form-item label="时间背景"><el-input v-model="editorDraft.timeSetting" /></el-form-item></div>
          <el-form-item label="力量体系"><el-input v-model="editorDraft.powerSystem" type="textarea" :rows="3" /></el-form-item><el-form-item label="世界规则"><el-input v-model="editorDraft.rules" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="势力阵营（每行一个）"><el-input v-model="factionsText" type="textarea" :rows="4" /></el-form-item>
          <h4>主要地点</h4><div v-for="(location,index) in editorLocations" :key="location.id || index" class="location-editor"><el-input v-model="location.name" placeholder="地点名称" /><el-select v-model="location.spaceType" placeholder="空间类型"><el-option label="室内" value="INTERIOR" /><el-option label="室外" value="EXTERIOR" /><el-option label="混合" value="MIXED" /></el-select><el-input v-model="location.description" placeholder="地点说明" /><el-button text type="danger" @click="editorLocations.splice(index,1)">移除</el-button></div><el-button @click="editorLocations.push({ id:`WORLD-LOC-${Date.now()}`,name:'',spaceType:'',description:'' })">+ 新增地点</el-button>
        </template>
      </el-form>
      <template #footer><el-button @click="cancelEditor">取消</el-button><el-button type="primary" @click="saveEditor">保存并创建新版本</el-button></template>
    </el-dialog>

    <el-dialog v-model="characterVisible" title="人物详情" width="720px" :close-on-click-modal="false">
      <el-form label-position="top" class="form-grid">
        <el-form-item label="姓名"><el-input v-model="characterDraft.name" /></el-form-item><el-form-item label="剧作职能 / 角色"><el-input v-model="characterDraft.role" /></el-form-item><el-form-item label="年龄"><el-input v-model="characterDraft.age" /></el-form-item><el-form-item label="外形特征"><el-input v-model="characterDraft.appearance" /></el-form-item><el-form-item label="性格"><el-input v-model="characterDraft.personality" /></el-form-item><el-form-item label="核心目标"><el-input v-model="characterDraft.goal" /></el-form-item><el-form-item label="深层动机"><el-input v-model="characterDraft.motivation" /></el-form-item><el-form-item label="内外冲突"><el-input v-model="characterDraft.conflict" /></el-form-item><el-form-item label="人物弧光"><el-input v-model="characterDraft.arc" /></el-form-item><el-form-item label="语言风格"><el-input v-model="characterDraft.speechStyle" /></el-form-item><el-form-item label="关系网"><el-input v-model="characterDraft.relationships" type="textarea" :rows="3" /></el-form-item><el-form-item label="秘密 / 隐藏信息"><el-input v-model="characterDraft.secret" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="characterVisible=false">取消</el-button><el-button type="primary" @click="saveCharacter">保存人物版本</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { convertLocationToSceneAsset, createAnalysisState, saveAnalysisSection } from '../workbench/upstreamStageModel.js'
const props = defineProps({ modelValue:{type:Object,default:()=>({})}, persistArtifact:{type:Function,default:null}, sceneAssets:{type:Object,default:null} })
const emit = defineEmits(['update:modelValue','guidance','artifact-saved','open-scene-asset'])
const state = reactive(createAnalysisState(props.modelValue)); const editorVisible=ref(false); const editorSection=ref(''); const editorDraft=ref(null); const editorLocations=ref([]); const factionsText=ref(''); const characterVisible=ref(false); const characterIndex=ref(-1); const characterDraft=reactive({});
const latestVersion=computed(()=>state.artifactVersions.at(-1)||null); const editorTitle=computed(()=>({synopsis:'编辑故事梗概',events:'编辑主要事件',chapterOutline:'编辑章节大纲',worldview:'编辑世界观'}[editorSection.value]||'编辑'))
watch(()=>props.modelValue,value=>Object.assign(state,createAnalysisState(value)),{deep:true})
function clone(v){return JSON.parse(JSON.stringify(v))} function guidance(v){emit('guidance',v);return v} function sync(){emit('update:modelValue',clone(state))} function impactText(impact){if(!impact)return '未返回下游影响';const stale=impact.stale||[];return stale.length?`需要同步检查：${stale.join('、')}`:'未标记下游产物过期'}
function openEditor(section,add=false){editorSection.value=section;editorDraft.value=clone(state[section]);if(section==='events'&&add)editorDraft.value.push({id:`event-${Date.now()}`,title:'',summary:''});if(section==='worldview'){editorLocations.value=clone(state.locations);factionsText.value=(editorDraft.value.factions||[]).join('\n')}editorVisible.value=true}
function cancelEditor(){editorVisible.value=false;editorDraft.value=null}
async function saveEditor(){let value=editorDraft.value;if(editorSection.value==='worldview'){value={...value,factions:factionsText.value.split('\n').map(v=>v.trim()).filter(Boolean),locations:clone(editorLocations.value)};if(editorLocations.value.some(v=>!v.name?.trim()||!v.spaceType))return guidance({allowed:false,code:'WORLD_LOCATION_REQUIRED',title:'请补全主要地点',message:'地点名称和空间类型不能为空。',targetAction:'focus_worldview_editor'})}
  const saved=await saveAnalysisSection(state,editorSection.value,value,props.persistArtifact);if(!saved.ok)return guidance(saved);editorVisible.value=false;sync();emit('artifact-saved',saved);return saved}
function openCharacter(index=-1){characterIndex.value=index;Object.keys(characterDraft).forEach(k=>delete characterDraft[k]);Object.assign(characterDraft,index>=0?clone(state.characters[index]):{id:`CHAR-${Date.now()}`,name:'',role:'',age:'',appearance:'',personality:'',goal:'',motivation:'',conflict:'',arc:'',speechStyle:'',relationships:'',secret:''});characterVisible.value=true}
async function saveCharacter(){const list=clone(state.characters);if(characterIndex.value>=0)list[characterIndex.value]=clone(characterDraft);else list.push(clone(characterDraft));const saved=await saveAnalysisSection(state,'characters',list,props.persistArtifact);if(!saved.ok)return guidance(saved);characterVisible.value=false;sync();emit('artifact-saved',saved)}
async function convertLocation(location){const adapter={createFromLocation:props.sceneAssets?.createFromLocation,openAsset:async asset=>{if(props.sceneAssets?.selectAsset)props.sceneAssets.selectAsset(asset);emit('open-scene-asset',asset)}};const result=await convertLocationToSceneAsset(state,location.id,adapter);if(!result.ok)return guidance(result);sync();if(result.created)emit('artifact-saved',{section:'sceneAsset',asset:result.asset});return result}
</script>

<style scoped>
.stage-panel{display:grid;gap:18px}.eyebrow{color:var(--el-color-primary);font-weight:700;margin:0}header h2{margin:4px 0}header p,.analysis-card p{color:var(--el-text-color-secondary)}.analysis-card{border:1px solid var(--el-border-color);border-radius:12px;padding:18px}.card-heading,.location-row,.asset-reference{display:flex;justify-content:space-between;align-items:center;gap:12px}.card-heading h3{margin:0}.two-columns{display:grid;grid-template-columns:1fr 1fr;gap:18px}ol{display:grid;gap:10px;padding-left:20px}li span{display:block;color:var(--el-text-color-secondary)}.location-list,.list-editor{display:grid;gap:12px}.location-row{border-top:1px solid var(--el-border-color-lighter);padding-top:12px}.location-row p{margin:4px 0}.asset-reference{justify-content:flex-end}.character-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.character-card{border:1px solid var(--el-border-color);background:var(--el-fill-color-blank);border-radius:10px;padding:14px;text-align:left;cursor:pointer;display:grid;gap:6px;color:inherit}.character-card span,.character-card small{color:var(--el-text-color-secondary)}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.location-editor{display:grid;grid-template-columns:1fr 130px 1fr auto;gap:8px;margin-bottom:8px}@media(max-width:800px){.two-columns,.character-grid,.form-grid{grid-template-columns:1fr}.location-editor{grid-template-columns:1fr}}
</style>
