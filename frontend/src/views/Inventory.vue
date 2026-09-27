<script setup>
import { onMounted, ref } from 'vue'
import { get, post, perform } from '../api'
import { useAuth } from '../stores/auth'
const auth=useAuth(),rows=ref([]),loading=ref(false),saving=ref(false),dialog=ref(false),form=ref({id:null,quantity:1,reason:'日常库存调整'})
async function load(){loading.value=true;try{rows.value=await get('/pharmacy/stock',{size:100})}finally{loading.value=false}}
function open(row){if(auth.role!=='PHARMACIST')return;form.value={id:row.id,quantity:1,reason:'日常库存调整'};dialog.value=true}
async function save(){if(auth.role!=='PHARMACIST'||saving.value)return;saving.value=true;try{await perform(()=>post(`/pharmacy/stock/${form.value.id}/adjust`,form.value),async()=>{dialog.value=false;await load()},'库存已调整')}finally{saving.value=false}}
onMounted(load)
</script>
<template><div><div class="page-heading"><div><div class="eyebrow">INVENTORY</div><h1>药房库存</h1><p>库存调整会记录操作人、数量和原因；系统不允许库存低于预占量。</p></div><el-button @click="load">刷新</el-button></div><section class="panel"><el-table :data="rows" v-loading="loading" stripe><el-table-column prop="code" label="编码"/><el-table-column prop="name" label="药品"/><el-table-column prop="spec" label="规格"/><el-table-column prop="stock" label="实物库存"/><el-table-column prop="reserved" label="预占库存"/><el-table-column prop="available" label="可售库存"/><el-table-column v-if="auth.role==='PHARMACIST'" label="操作"><template #default="s"><el-button size="small" @click="open(s.row)">调整库存</el-button></template></el-table-column></el-table></section><el-dialog v-if="auth.role==='PHARMACIST'" v-model="dialog" title="库存调整" width="450px"><el-form label-width="90px"><el-form-item label="调整数量"><el-input-number v-model="form.quantity" :min="-100000" :max="100000"/></el-form-item><el-form-item label="原因"><el-input v-model="form.reason" type="textarea" maxlength="500"/></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save" :loading="saving">保存</el-button></template></el-dialog></div></template>
