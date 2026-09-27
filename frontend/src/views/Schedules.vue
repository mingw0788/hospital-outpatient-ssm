<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { Search, Refresh, UserFilled } from '@element-plus/icons-vue'
import { useAuth } from '../stores/auth'
import { get, post, confirmAction, requestKey } from '../api'
import { ElMessage } from 'element-plus'
import { useList } from '../composables/useList'
import { money, dateTime, label, today } from '../utils'
import ListPager from '../components/ListPager.vue'
import StatusTag from '../components/StatusTag.vue'
const auth = useAuth(), departments = ref([]), doctors = ref([]), advanced = ref({}), submitting = ref(null), queueResult = ref(null), queueDialog = ref(false), range = ref([]), requestKeys = new Map()
let timer
const { rows, loading, error, query, load, page } = useList('/catalog/schedules', { departmentId:'',doctorId:auth.role==='DOCTOR'?auth.user.doctor_id:'',period:'',available:auth.isPatient?true:'',from:today(),to:'',keyword:'',title:'' })
async function doctorsLoad() { doctors.value = await get('/catalog/doctors', { departmentId:query.departmentId || undefined,size:100 }); if (auth.role !== 'DOCTOR') query.doctorId = '' }
async function search() { query.from = range.value?.[0] || today(); query.to = range.value?.[1] || ''; await load(true) }
async function register(row) {
  if (!await confirmAction(`确认预约 ${row.doctor_name} ${row.work_date} ${label(row.period)}？挂号费 ${money(row.fee)}，请在生成账单后及时缴费。`)) return
  submitting.value = row.id
  try {
    const key = requestKeys.get(row.id) || requestKey(); requestKeys.set(row.id,key)
    if (advanced.value.queue_enabled) { queueResult.value = await post('/queue/requests',{schedule_id:row.id,request_key:key}); queueDialog.value = true; sessionStorage.setItem(`queue:${auth.user.id}`,String(queueResult.value.id)); poll() }
    else { await post('/registrations',{schedule_id:row.id,request_key:key}); requestKeys.delete(row.id); ElMessage.success('挂号成功，请到「我的挂号」或「门诊缴费」完成付款'); await load() }
  } catch(e) { if (e.response?.status && e.response.status < 500) requestKeys.delete(row.id) } finally {submitting.value=null}
}
async function poll() {
  clearTimeout(timer)
  if (!queueResult.value?.id) return
  try { queueResult.value = await get(`/queue/requests/${queueResult.value.id}`); if (['QUEUED','PROCESSING','RETRY'].includes(queueResult.value.status)) timer = setTimeout(poll,2000); else {sessionStorage.removeItem(`queue:${auth.user.id}`); requestKeys.clear(); await load()} } catch { }
}
onMounted(async () => { try { [departments.value,advanced.value] = await Promise.all([get('/catalog/departments',{size:100}),get('/advanced/status')]); await doctorsLoad(); const previous=sessionStorage.getItem(`queue:${auth.user.id}`); if(previous){queueResult.value={id:previous};queueDialog.value=true;poll()} } catch {} await load() })
onUnmounted(()=>clearTimeout(timer))
</script>
<template><div><div class="page-heading"><div><div class="eyebrow">APPOINTMENTS</div><h1>{{ auth.isPatient ? '排班与挂号' : '我的出诊排班' }}</h1><p>按科室与日期查找排班；余号以提交时的实际结果为准。</p></div><el-button :icon="Refresh" @click="load()">刷新</el-button></div><el-alert v-if="advanced.queue_enabled && auth.isPatient" title="高峰挂号队列已开启：受理后请等待处理结果，入队成功不代表挂号成功。" type="info" :closable="false" show-icon/><section class="panel"><div class="filters"><el-select v-model="query.departmentId" clearable placeholder="全部科室" @change="doctorsLoad"><el-option v-for="item in departments" :key="item.id" :value="item.id" :label="item.name"/></el-select><el-select v-model="query.doctorId" :disabled="auth.role==='DOCTOR'" clearable filterable placeholder="全部医生"><el-option v-for="item in doctors" :key="item.id" :value="item.id" :label="item.doctor_name || item.display_name || item.name"/></el-select><el-input v-model="query.title" placeholder="职称" clearable/><el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期"/><el-select v-model="query.period" clearable placeholder="全部时段"><el-option label="上午" value="AM"/><el-option label="下午" value="PM"/><el-option label="演示时段" value="DEMO"/></el-select><el-checkbox v-model="query.available">仅看有余号</el-checkbox><el-button type="primary" :icon="Search" @click="search">查询</el-button></div><el-alert v-if="error" :title="error" type="error" :closable="false"/><div v-loading="loading" class="schedule-grid"><article v-for="row in rows" :key="row.id" class="schedule-card"><div class="doctor-header"><div class="doctor-avatar"><el-icon><UserFilled/></el-icon></div><div><h3>{{ row.doctor_name }} <small class="muted">{{ row.title }}</small></h3><p class="muted">{{ row.department_name }} · {{ row.location || '门诊诊室' }}</p></div></div><div class="schedule-detail"><div><small>出诊日期</small>{{ row.work_date }}</div><div><small>出诊时段</small>{{ label(row.period) }}</div><div><small>时间</small>{{ dateTime(row.start_time).slice(11,16) }}–{{ dateTime(row.end_time).slice(11,16) }}</div><div><small>可预约号源</small><strong :style="{color:row.remaining>0?'#087e8b':'#bd7272'}">{{ row.remaining }}</strong> / {{ row.total }}</div></div><div class="schedule-bottom"><strong class="money">{{ money(row.fee) }}</strong><el-button v-if="auth.isPatient" type="primary" :disabled="Number(row.remaining)<=0 || row.status!=='OPEN'" :loading="submitting===row.id" @click="register(row)">{{ advanced.queue_enabled ? '排队挂号' : '预约挂号' }}</el-button><StatusTag v-else :value="row.status"/></div></article></div><el-empty v-if="!loading && !rows.length" description="暂无符合条件的出诊排班，请调整筛选条件"/><ListPager :page="query.page" :length="rows.length" :size="query.size" @change="page"/></section><el-dialog v-model="queueDialog" title="挂号请求处理进度" width="480px"><template v-if="queueResult"><p class="muted">请求编号 #{{ queueResult.id }}。关闭此窗口不会取消已受理的请求。</p><div class="queue-result"><StatusTag :value="queueResult.status"/><p style="margin-top:15px">{{ queueResult.status==='SUCCEEDED' ? '挂号已成功，请前往我的挂号完成缴费。' : queueResult.error_message || '正在处理您的请求，请稍候…' }}</p><p v-if="queueResult.result_registration_id" class="muted" style="margin-top:10px">挂号编号 #{{ queueResult.result_registration_id }}</p></div></template><template #footer><el-button @click="poll">刷新结果</el-button><el-button type="primary" @click="$router.push('/registrations')">查看我的挂号</el-button></template></el-dialog></div></template>
