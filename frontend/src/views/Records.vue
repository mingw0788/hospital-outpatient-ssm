<script setup>
import { onMounted, ref } from 'vue'
import { get } from '../api'
const rows=ref([]), detail=ref(null), dialog=ref(false), loading=ref(false)
async function load(){loading.value=true;try{rows.value=await get('/records',{size:100})}finally{loading.value=false}}
async function open(row){try{detail.value=await get(`/records/${row.visit_id}`);dialog.value=true}catch{}}
onMounted(load)
</script>
<template><div><div class="page-heading"><div><div class="eyebrow">MEDICAL RECORD</div><h1>电子病历</h1><p>已提交病历按权限展示，患者只能查看本人记录。</p></div><el-button @click="load">刷新</el-button></div><section class="panel"><el-table :data="rows" v-loading="loading" stripe empty-text="暂无已提交病历"><el-table-column prop="submitted_at" label="提交时间"/><el-table-column prop="patient_name" label="患者"/><el-table-column prop="doctor_name" label="医生"/><el-table-column prop="department_name" label="科室"/><el-table-column prop="diagnosis" label="诊断"/><el-table-column label="操作"><template #default="s"><el-button size="small" @click="open(s.row)">查看详情</el-button></template></el-table-column></el-table></section><el-dialog v-model="dialog" title="病历详情" width="720px"><template v-if="detail"><div class="record-section"><h3>主诉</h3><p class="record-text">{{ detail.record?.chief_complaint }}</p></div><div class="record-section"><h3>诊断</h3><p class="record-text">{{ detail.record?.diagnosis }}</p></div><div class="record-section"><h3>病史与处理意见</h3><p class="record-text">{{ detail.record?.history }}{{ detail.record?.advice ? `\n${detail.record.advice}` : '' }}</p></div><div class="record-section"><h3>处方</h3><p v-for="p in detail.prescriptions || []" :key="p.id">处方 #{{ p.id }} · {{ p.status }}</p></div></template></el-dialog></div></template>
