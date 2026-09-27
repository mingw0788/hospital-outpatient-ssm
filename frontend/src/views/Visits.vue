<script setup>
import { onMounted, ref } from 'vue'
import { get, post, put, requestKey, perform } from '../api'
import { useAuth } from '../stores/auth'
import StatusTag from '../components/StatusTag.vue'
const auth=useAuth(), rows=ref([]), loading=ref(false), editing=ref(null), dialog=ref(false), saving=ref(false), opening=ref(false), drugs=ref([])
const emptyRecord=()=>({chief_complaint:'',history:'',past_history:'',allergies:'',diagnosis:'',advice:'',items:[]})
const form=ref(emptyRecord()),requeueKeys=new Map()
async function load(){loading.value=true;try{rows.value=await get('/visits',{size:100});if(auth.role==='DOCTOR')drugs.value=await get('/catalog/drugs',{size:100})}finally{loading.value=false}}
async function action(path,label,body={}){return perform(()=>post(path,body),load,label)}
async function requeue(row){
  if(!requeueKeys.has(row.id))requeueKeys.set(row.id,requestKey())
  const result=await action(`/visits/${row.id}/requeue`,'已重新排队',{request_key:requeueKeys.get(row.id)})
  if(result)requeueKeys.delete(row.id)
}
async function edit(row){
  if(opening.value)return
  opening.value=true
  try{
    const detail=await get(`/records/${row.id}`)
    form.value={...emptyRecord(),...detail.record,items:(detail.items||[]).map(item=>({...item}))}
    editing.value=row
    dialog.value=true
  }catch{}finally{opening.value=false}
}
async function save(done){
  if(!editing.value||saving.value)return
  saving.value=true
  try{await perform(()=>done?post(`/visits/${editing.value.id}/complete`,form.value):put(`/visits/${editing.value.id}/record`,form.value),async()=>{dialog.value=false;editing.value=null;await load()},done?'病历已提交':'草稿已保存')}finally{saving.value=false}
}
onMounted(load)
</script>
<template><div><div class="page-heading"><div><div class="eyebrow">CLINIC</div><h1>候诊与接诊</h1><p>{{ auth.role==='DOCTOR'?'按候诊顺序叫号并完成病历。':'查看本人报到后的候诊状态。' }}</p></div><el-button @click="load">刷新</el-button></div><section class="panel"><el-table :data="rows" v-loading="loading" stripe empty-text="暂无候诊记录"><el-table-column prop="queue_no" label="候诊号" width="90"/><el-table-column prop="patient_name" label="患者"/><el-table-column prop="doctor_name" label="医生"/><el-table-column prop="work_date" label="日期"/><el-table-column prop="status" label="状态"><template #default="s"><StatusTag :value="s.row.status"/></template></el-table-column><el-table-column prop="ahead_count" label="前方人数"/><el-table-column label="操作" width="260"><template #default="s"><div class="table-actions"><template v-if="auth.role==='DOCTOR'"><el-button v-if="s.row.status==='WAITING'" size="small" type="primary" @click="action(`/visits/${s.row.id}/call`,'已叫号')">叫号</el-button><el-button v-if="s.row.status==='CALLED'" size="small" type="primary" @click="action(`/visits/${s.row.id}/start`,'开始接诊')">开始接诊</el-button><el-button v-if="s.row.status==='CALLED'" size="small" @click="action(`/visits/${s.row.id}/skip`,'已标记过号')">过号</el-button><el-button v-if="s.row.status==='IN_PROGRESS'" size="small" type="success" @click="edit(s.row)" :loading="opening">填写病历</el-button></template><el-button v-if="auth.role==='PATIENT' && s.row.status==='SKIPPED'" size="small" @click="requeue(s.row)">重新排队</el-button></div></template></el-table-column></el-table></section><el-dialog v-model="dialog" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" title="提交电子病历" width="720px" destroy-on-close><el-form label-width="95px"><el-form-item label="主诉"><el-input v-model="form.chief_complaint" type="textarea"/></el-form-item><el-form-item label="诊断"><el-input v-model="form.diagnosis" type="textarea"/></el-form-item><el-form-item label="病史"><el-input v-model="form.history" type="textarea"/></el-form-item><el-form-item label="既往史"><el-input v-model="form.past_history" type="textarea"/></el-form-item><el-form-item label="过敏史"><el-input v-model="form.allergies" type="textarea"/></el-form-item><el-form-item label="处理意见"><el-input v-model="form.advice" type="textarea"/></el-form-item><el-form-item label="处方药品"><el-select v-model="form.items" multiple value-key="drug_id" placeholder="可选药品"><el-option v-for="d in drugs" :key="d.id" :label="`${d.name} ¥${d.price}`" :value="{drug_id:d.id,quantity:1,usage_text:'遵医嘱'}"/></el-select></el-form-item><el-form-item v-for="item in form.items" :key="item.drug_id" :label="drugs.find(d=>d.id===item.drug_id)?.name || `药品 #${item.drug_id}`"><el-input-number v-model="item.quantity" :min="1" :max="1000" :step="1" step-strictly aria-label="药品数量"/><el-input v-model="item.usage_text" placeholder="用法用量" maxlength="500" style="margin-top:8px"/></el-form-item></el-form><template #footer><el-button @click="dialog=false" :disabled="saving">取消</el-button><el-button @click="save(false)" :disabled="saving">保存草稿</el-button><el-button type="primary" @click="save(true)" :loading="saving">提交并完成接诊</el-button></template></el-dialog></div></template>
