<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Phone, UserFilled } from '@element-plus/icons-vue'
import { useAuth } from '../stores/auth'
import { post, refreshCsrf } from '../api'
import { ElMessage } from 'element-plus'
const auth = useAuth(), router = useRouter(), mode = ref('login'), busy = ref(false), formRef = ref()
const form = reactive({ username: '', password: '', display_name: '', phone: '' })
const rules = { username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { pattern: /^[a-zA-Z0-9_]{3,32}$/, message: '请使用 3～32 位字母、数字或下划线', trigger: 'blur' }], password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 8, max: 72, message: '密码应为 8～72 位', trigger: 'blur' }], display_name: [{ required: true, message: '请输入姓名', trigger: 'blur' }] }
async function submit() {
  if (busy.value) return
  if (!await formRef.value.validate().catch(() => false)) return
  busy.value = true
  try { if (mode.value === 'register') { await refreshCsrf(); await post('/auth/register', form); ElMessage.success('注册成功，即将进入患者工作台') }; await auth.login({ username: form.username, password: form.password }); router.replace('/') } catch {} finally { busy.value = false }
}
</script>
<template><div class="login-page"><section class="login-story"><div class="brand"><span class="brand-mark">✚</span><div><strong>杏林门诊</strong><small>OUTPATIENT CENTER</small></div></div><div><div class="eyebrow" style="color:#75d0c5">CONNECTED CARE, EVERY STEP</div><h1>让就诊更有序<br>让服务更贴心</h1><p>从预约挂号到门诊取药，连接每一个就诊环节。清晰的候诊进度，让每一次等待心中有数。</p><div class="login-visual"><span class="cross">✚</span></div></div><footer>软件体系结构综合实训 · 门诊业务一体化平台</footer></section><section class="login-form-shell"><div class="login-form"><div class="eyebrow">WELCOME TO XINGLIN</div><h2>{{ mode === 'login' ? '欢迎回来' : '创建患者账户' }}</h2><p class="muted">{{ mode === 'login' ? '登录账户，开启您的门诊服务' : '工作人员账户由管理员统一创建' }}</p><el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit"><el-form-item label="用户名" prop="username"><el-input v-model="form.username" :prefix-icon="User" autocomplete="username" placeholder="请输入用户名" maxlength="32"/></el-form-item><el-form-item v-if="mode === 'register'" label="姓名" prop="display_name"><el-input v-model="form.display_name" :prefix-icon="UserFilled" placeholder="请输入虚构演示姓名" maxlength="80"/></el-form-item><el-form-item v-if="mode === 'register'" label="联系电话" prop="phone"><el-input v-model="form.phone" :prefix-icon="Phone" placeholder="用于完善患者资料" maxlength="30"/></el-form-item><el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password :prefix-icon="Lock" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="请输入至少 8 位密码" maxlength="72"/></el-form-item><el-button type="primary" native-type="submit" :loading="busy" style="width:100%">{{ mode === 'login' ? '登 录' : '注册并登录' }}</el-button></el-form><el-button link type="primary" style="margin-top:18px" @click="mode = mode === 'login' ? 'register' : 'login'; formRef?.clearValidate()">{{ mode === 'login' ? '还没有账户？患者注册' : '已有账户？返回登录' }}</el-button><div class="login-note">本系统用于教学演示，请使用虚构资料。<br>患者、医生、收费员、药师与管理员通过各自账户登录；演示账户见项目启动说明。</div></div></section></div></template>
