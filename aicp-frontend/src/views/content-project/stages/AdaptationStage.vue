<template>
  <section class="stage-panel">
    <header class="stage-heading"><div><p class="eyebrow">STEP 4</p><h2>改编方案</h2><p>先持久化高压开场，再确认改编规则和方案版本。</p></div><el-tag v-if="state.confirmed" type="success">已确认 v{{ state.version }}</el-tag><el-tag v-else type="warning">草稿</el-tag></header>

    <article class="adaptation-card">
      <div class="card-heading"><h3>选择高压开场</h3><span v-if="state.hookVersion">已保存 v{{ state.hookVersion }}</span></div>
      <div class="hook-grid">
        <button v-for="hook in state.hooks" :key="hook.id" data-action="select-high-pressure-hook" class="hook-card" :class="{ selected: state.selectedHookId === hook.id }" @click="selectHook(hook.id)"><strong>{{ hook.title }}</strong><span>{{ hook.description || hook.summary || '点击选择并保存此开场' }}</span><small>{{ state.selectedHookId === hook.id ? '已保存' : '选择此方案' }}</small></button>
      </div>
      <el-empty v-if="!state.hooks.length" description="暂无高压开场候选" :image-size="64" />
    </article>

    <article class="adaptation-card">
      <div class="card-heading"><h3>改编规则</h3><el-button data-action="add-adaptation-rule" @click="openRule()">新增改编规则</el-button></div>
      <div class="rule-list"><div v-for="rule in state.rules" :key="rule.id" class="rule-row"><div><strong>{{ rule.title }}</strong><p>{{ rule.instruction }}</p></div><div><el-button text @click="openRule(rule)">编辑</el-button><el-button text type="danger" @click="removeRule(rule.id)">删除</el-button></div></div></div>
      <el-empty v-if="!state.rules.length" description="暂无附加改编规则" :image-size="64" />
    </article>

    <article class="adaptation-card summary">
      <h3>确认前检查</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="高压开场">{{ selectedHook?.title || '未选择' }}</el-descriptions-item><el-descriptions-item label="改编强度">{{ creationSettings.adaptationStrength || '未设置' }}</el-descriptions-item><el-descriptions-item label="输出格式">{{ creationSettings.outputFormat || '未设置' }}</el-descriptions-item><el-descriptions-item label="创作模型">{{ creationSettings.model?.name || creationSettings.model?.id || '未选择' }}</el-descriptions-item><el-descriptions-item label="预估积分">{{ creationSettings.model?.demo ? '0（演示）' : (creationSettings.estimatedPoints ?? '未获取') }}</el-descriptions-item><el-descriptions-item label="规则数">{{ state.rules.length }}</el-descriptions-item>
      </el-descriptions>
    </article>

    <footer class="stage-actions"><el-button data-action="regenerate-current-artifact" @click="regenerate">重新生成当前产物</el-button><el-button type="primary" data-action="confirm-adaptation" @click="confirmPlan">确认改编方案</el-button></footer>

    <el-dialog v-model="ruleVisible" :title="editingRuleId ? '编辑改编规则' : '新增改编规则'" width="520px" :close-on-click-modal="false"><el-form label-position="top"><el-form-item label="规则名称"><el-input v-model="ruleDraft.title" /></el-form-item><el-form-item label="执行说明"><el-input v-model="ruleDraft.instruction" type="textarea" :rows="5" /></el-form-item></el-form><template #footer><el-button @click="ruleVisible=false">取消</el-button><el-button type="primary" @click="saveRule">保存规则</el-button></template></el-dialog>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { addAdaptationRule, confirmAdaptationPlan, createAdaptationState, persistHookSelection, removeAdaptationRule, runArtifactRegeneration, updateAdaptationRule } from '../workbench/upstreamStageModel.js'
const props=defineProps({modelValue:{type:Object,default:()=>({})},creationSettings:{type:Object,default:()=>({})},persistHook:{type:Function,default:null},persistPlan:{type:Function,default:null},regenerateArtifact:{type:Function,default:null},workbench:{type:Object,default:null}})
const emit=defineEmits(['update:modelValue','guidance','confirmed','regenerated']);const state=reactive(createAdaptationState(props.modelValue));const ruleVisible=ref(false);const editingRuleId=ref(null);const ruleDraft=reactive({title:'',instruction:''});const selectedHook=computed(()=>state.hooks.find(h=>h.id===state.selectedHookId)||null)
watch(()=>props.modelValue,value=>Object.assign(state,createAdaptationState(value)),{deep:true});function clone(v){return JSON.parse(JSON.stringify(v))}function sync(){emit('update:modelValue',clone(state))}function guidance(v){emit('guidance',v);return v}
async function selectHook(id){const result=await persistHookSelection(state,id,props.persistHook);if(!result.ok)return guidance(result);sync();return result}
function openRule(rule=null){editingRuleId.value=rule?.id||null;Object.assign(ruleDraft,{title:rule?.title||'',instruction:rule?.instruction||''});ruleVisible.value=true}
function saveRule(){const result=editingRuleId.value?updateAdaptationRule(state,editingRuleId.value,ruleDraft):addAdaptationRule(state,ruleDraft);if(result?.allowed===false)return guidance(result);ruleVisible.value=false;sync();return result}
function removeRule(id){const result=removeAdaptationRule(state,id);if(!result.ok)return guidance(result);sync();return result}
async function confirmPlan(){const result=await confirmAdaptationPlan(state,props.creationSettings,props.persistPlan);if(!result.ok)return guidance(result);sync();emit('confirmed',result);return result}
async function regenerate(){const result=await runArtifactRegeneration({workbench:props.workbench,input:{model:props.creationSettings.model,estimatedPoints:props.creationSettings.estimatedPoints,subtask:'重新生成改编方案'},execute:props.regenerateArtifact});if(result?.allowed===false)return guidance(result);emit('regenerated',result);return result}
</script>

<style scoped>
.stage-panel{display:grid;gap:18px}.eyebrow{color:var(--el-color-primary);font-weight:700;margin:0}.stage-heading,.card-heading,.stage-actions,.rule-row{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.stage-heading h2{margin:4px 0}.stage-heading p,.rule-row p{color:var(--el-text-color-secondary)}.adaptation-card{border:1px solid var(--el-border-color);border-radius:12px;padding:18px}.card-heading h3{margin:0}.hook-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:14px}.hook-card{border:1px solid var(--el-border-color);background:var(--el-fill-color-blank);color:inherit;border-radius:10px;padding:14px;text-align:left;cursor:pointer;display:grid;gap:8px}.hook-card.selected{border-color:var(--el-color-primary);box-shadow:0 0 0 1px var(--el-color-primary)}.hook-card span,.hook-card small{color:var(--el-text-color-secondary)}.rule-list{display:grid}.rule-row{padding:12px 0;border-bottom:1px solid var(--el-border-color-lighter)}.rule-row p{margin:5px 0}.stage-actions{justify-content:flex-end}@media(max-width:800px){.hook-grid{grid-template-columns:1fr}}
</style>
