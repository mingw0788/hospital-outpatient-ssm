import { defineStore } from 'pinia'
import { get, post, refreshCsrf } from '../api'
export const useAuth = defineStore('auth', {
  state: () => ({ user: null, ready: false }),
  getters: { role: state => state.user?.role || '', isPatient: state => state.user?.role === 'PATIENT' },
  actions: {
    async bootstrap() { if (this.ready) return; try { this.user = await get('/auth/me', {}, { silent: true }) } catch { this.user = null } finally { this.ready = true } },
    async login(form) { await refreshCsrf(); await post('/auth/login', form); await refreshCsrf(); this.user = await get('/auth/me'); this.ready = true },
    async logout() { await post('/auth/logout'); this.user = null; await refreshCsrf() }
  }
})
