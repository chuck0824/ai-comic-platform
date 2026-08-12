<template>
  <section class="stage-panel">
    <header><p class="eyebrow">STEP 5</p><h2>结构化文字剧本</h2><p>以稳定的单集与节拍 ID 管理结构，所有生成结果均可对比后采用。</p></header>
    <article v-for="episode in state.episodes" :key="episode.id" class="card">
      <div class="row"><div><strong>{{ episode.title || episode.id }}</strong><small>{{ episode.id }}</small></div><el-button data-action="open-episode-structure" @click="openEpisode(episode.id)">打开单集结构</el-button></div>
      <div v-if="state.openedEpisodeId===episode.id" class="beats">
        <div v-for="beat in episode.beats" :key="beat.id" class="beat"><div><strong>{{ beat.title || '未命名节拍' }}</strong><small>{{ beat.id }}</small></div><el-button data-action="regenerate-beat" @click="regenerateOne(episode.id,beat.id)">重新生成节拍</el-button></div>
        <el-button data-action="add-beat" @click="addOne(episode.id)">新增节拍</el-button>
      </div>
    </article>
    <footer><el-button data-action="regenerate-current-artifact" @click="regenerateArtifact">重新生成当前产物</el-button></footer>
  </section>
</template>
<script setup>
import { reactive, watch } from 'vue'
import { addBeat, createStructuredScriptState, openEpisodeStructure, regenerateBeat } from '../workbench/downstreamStageModel.js'
import { runArtifactRegeneration } from '../workbench/upstreamStageModel.js'
const props=defineProps({modelValue:{type:Object,default:()=>({})},openEpisodeAdapter:Function,addBeatAdapter:Function,regenerateBeatAdapter:Function,regenerateArtifactAdapter:Function,workbench:Object,generationInput:{type:Object,default:()=>({})}})
const emit=defineEmits(['update:modelValue','guidance','result']);const state=reactive(createStructuredScriptState(props.modelValue));watch(()=>props.modelValue,v=>Object.assign(state,createStructuredScriptState(v)),{deep:true})
const sync=()=>emit('update:modelValue',JSON.parse(JSON.stringify(state)));const handle=result=>{if(result?.allowed===false)emit('guidance',result);else{sync();emit('result',result)};return result}
async function openEpisode(id){return handle(await openEpisodeStructure(state,id,props.openEpisodeAdapter))}
async function addOne(id){return handle(await addBeat(state,id,{title:'新节拍'},props.addBeatAdapter))}
async function regenerateOne(episodeId,beatId){return handle(await regenerateBeat(state,episodeId,beatId,props.regenerateBeatAdapter))}
async function regenerateArtifact(){return handle(await runArtifactRegeneration({workbench:props.workbench,input:{...props.generationInput,subtask:'重新生成结构化文字剧本'},execute:props.regenerateArtifactAdapter}))}
</script>
<style scoped>.stage-panel{display:grid;gap:16px}.eyebrow{color:var(--el-color-primary);font-weight:700}.card{border:1px solid var(--el-border-color);border-radius:12px;padding:16px}.row,.beat,footer{display:flex;justify-content:space-between;align-items:center;gap:12px}.row div,.beat div{display:grid}.beats{display:grid;gap:10px;margin-top:14px}.beat{padding:10px;background:var(--el-fill-color-light);border-radius:8px}small,header p{color:var(--el-text-color-secondary)}footer{justify-content:flex-end}</style>
