<script setup>
import { onMounted, ref } from 'vue'
import { get, post, perform } from '../api'
import { useAuth } from '../stores/auth'
import StatusTag from '../components/StatusTag.vue'
const auth=useAuth(),rows=ref([]),loading=ref(false)
async function load(){loading.value=true;try{rows.value=await get('/prescriptions',{size:100})}finally{loading.value=false}}
async function action(row,op,label){await perform(()=>post(`/prescriptions/${row.id}/${op}`),load,label)}
onMounted(load)
</script>
<template><div><div class="page-heading"><div><div class="eyebrow">PHARMACY</div><h1>处方与发药</h1><p>{{ auth.role==='PHARMACIST'?'核对已付款处方后一次性发药。':'查看本次就诊处方及药费账单。' }}</p></div><el-button @click="load">刷新</el-button></div><section class="panel"><el-table :data="rows" v-loading="loading" stripe empty-text="暂无处方"><el-table-column prop="id" label="处方号" width="90"/><el-table-column prop="patient_name" label="患者"/><el-table-column prop="doctor_name" label="医生"/><el-table-column prop="amount" label="金额"><template #default="s"><span class="money">¥{{ Number(s.row.amount||0).toFixed(2) }}</span></template></el-table-column><el-table-column prop="status" label="处方状态"><template #default="s"><StatusTag :value="s.row.status"/></template></el-table-column><el-table-column label="药品"><template #default="s"><span v-for="(i,index) in s.row.items||[]" :key="i.id">{{ index?'、':'' }}{{ i.drug_name }} × {{ i.quantity }}</span></template></el-table-column><el-table-column label="操作" width="140"><template #default="s"><el-button v-if="auth.role==='PHARMACIST' && s.row.status==='PAID'" type="primary" size="small" @click="action(s.row,'dispense','发药完成')">确认发药</el-button><span v-else class="muted">—</span></template></el-table-column></el-table></section></div></template>
