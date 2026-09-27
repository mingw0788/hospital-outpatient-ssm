<script setup>
import { onMounted, ref } from 'vue'
import { get, put, perform } from '../api'
const form=ref({display_name:'',phone:'',birth_date:'',allergies:''}),loading=ref(false)
async function load(){form.value=await get('/profile')}
async function save(){await perform(()=>put('/profile',form.value),load,'资料已保存')}
onMounted(load)
</script>
<template><div><div class="page-heading"><div><div class="eyebrow">PROFILE</div><h1>个人资料</h1><p>只维护当前登录账户的基本资料。</p></div></div><section class="panel profile-panel"><el-form label-width="100px" v-loading="loading"><el-form-item label="姓名"><el-input v-model="form.display_name"/></el-form-item><el-form-item label="联系电话"><el-input v-model="form.phone"/></el-form-item><el-form-item label="出生日期"><el-date-picker v-model="form.birth_date" value-format="YYYY-MM-DD" type="date"/></el-form-item><el-form-item label="过敏史" v-if="form.role==='PATIENT'"><el-input v-model="form.allergies" type="textarea"/></el-form-item><el-button type="primary" @click="save">保存资料</el-button></el-form></section></div></template>
