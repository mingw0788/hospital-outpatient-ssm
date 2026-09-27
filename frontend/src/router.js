import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from './stores/auth'
export const navigation = [
  { path: '/', title: '工作概览', icon: 'Grid', roles: ['PATIENT','DOCTOR','CASHIER','PHARMACIST','ADMIN'], component: () => import('./views/Dashboard.vue') },
  { path: '/schedules', title: '排班与挂号', icon: 'Calendar', roles: ['PATIENT','DOCTOR'], component: () => import('./views/Schedules.vue') },
  { path: '/registrations', title: '我的挂号', icon: 'Tickets', roles: ['PATIENT'], component: () => import('./views/Registrations.vue') },
  { path: '/bills', title: '门诊缴费', icon: 'Wallet', roles: ['PATIENT','CASHIER'], component: () => import('./views/Bills.vue') },
  { path: '/visits', title: '候诊与接诊', icon: 'FirstAidKit', roles: ['DOCTOR','PATIENT'], component: () => import('./views/Visits.vue') },
  { path: '/records', title: '电子病历', icon: 'Document', roles: ['PATIENT','DOCTOR'], component: () => import('./views/Records.vue') },
  { path: '/prescriptions', title: '处方与发药', icon: 'Box', roles: ['PATIENT','DOCTOR','PHARMACIST'], component: () => import('./views/Prescriptions.vue') },
  { path: '/inventory', title: '药房库存', icon: 'Collection', roles: ['PHARMACIST','ADMIN'], component: () => import('./views/Inventory.vue') },
  ...[['users','人员账户','User'],['departments','科室管理','OfficeBuilding'],['doctors','医生管理','Avatar'],['schedules','排班号源','Calendar'],['drugs','药品目录','FirstAidKit'],['logs','操作审计','Notebook']].map(([resource,title,icon]) => ({ path: `/manage/${resource}`, title, icon, roles: ['ADMIN'], component: () => import('./views/Manage.vue'), props: { resource } })),
  { path: '/advanced', title: '队列与排班任务', icon: 'Operation', roles: ['ADMIN'], component: () => import('./views/Advanced.vue') },
  { path: '/profile', title: '个人资料', icon: 'Setting', roles: ['PATIENT','DOCTOR','CASHIER','PHARMACIST','ADMIN'], component: () => import('./views/Profile.vue') }
]
const router = createRouter({ history: createWebHistory(), routes: [{ path: '/login', component: () => import('./views/Login.vue'), meta: { public: true } }, ...navigation.map(item => ({ ...item, meta: { title: item.title, roles: item.roles } })), { path: '/:pathMatch(.*)*', redirect: '/' }] })
router.beforeEach(async to => { const auth = useAuth(); await auth.bootstrap(); if (!to.meta.public && !auth.user) return '/login'; if (to.path === '/login' && auth.user) return '/'; if (to.meta.roles && !to.meta.roles.includes(auth.role)) return '/'; document.title = `${to.meta.title || '登录'} · 杏林门诊` })
export default router
