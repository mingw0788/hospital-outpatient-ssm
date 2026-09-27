<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as Icons from '@element-plus/icons-vue'
import { useAuth } from './stores/auth'
import { navigation } from './router'
import { roles } from './utils'
const auth = useAuth(), route = useRoute(), router = useRouter(), mobile = ref(false)
const menus = computed(() => navigation.filter(x => x.roles.includes(auth.role)))
async function logout() { try { await auth.logout(); router.replace('/login') } catch {} }
function expired() { auth.user = null; router.replace('/login') }
onMounted(() => window.addEventListener('session-expired', expired))
onUnmounted(() => window.removeEventListener('session-expired', expired))
</script>
<template>
  <router-view v-if="route.path === '/login'" />
  <div v-else class="app-shell">
    <div v-if="mobile" class="sidebar-scrim" @click="mobile = false"></div>
    <aside class="sidebar" :class="{ 'mobile-open': mobile }">
      <router-link to="/" class="brand"><span class="brand-mark">✚</span><div><strong>杏林门诊</strong><small>OUTPATIENT CENTER</small></div></router-link>
      <div class="nav-caption">{{ roles[auth.role] }}工作空间</div>
      <nav><router-link v-for="item in menus" :key="item.path" :to="item.path" :class="{ active: route.path === item.path }" @click="mobile = false"><el-icon><component :is="Icons[item.icon]" /></el-icon><span>{{ item.title }}</span></router-link></nav>
      <div class="sidebar-footer"><span class="status-dot"></span>综合实训 · 模拟门诊服务</div>
    </aside>
    <div class="main-shell">
      <header class="topbar"><div class="topbar-location"><el-button class="mobile-trigger" :icon="Icons.Menu" text aria-label="打开导航" @click="mobile = !mobile"/><span>门诊服务中心</span><span class="crumb">/</span><strong>{{ route.meta.title }}</strong></div><div class="user-zone"><el-tag size="small" type="info">{{ roles[auth.role] }}</el-tag><span>{{ auth.user?.display_name }}</span><el-button text @click="logout">退出</el-button></div></header>
      <main class="content"><router-view :key="route.path" /></main>
      <footer class="page-footer">杏林门诊挂号与缴费系统 <span>教学演示 · 模拟支付 · 请使用虚构资料</span></footer>
    </div>
  </div>
</template>
