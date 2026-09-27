import { ref, reactive } from 'vue'
import { get } from '../api'
export function useList(url, initial = {}) {
  const rows = ref([]), loading = ref(false), error = ref(''), query = reactive({ page: 1, size: 20, ...initial })
  async function load(reset = false) {
    if (reset === true) query.page = 1
    loading.value = true; error.value = ''
    try { const result = await get(typeof url === 'function' ? url() : url, Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v !== null && v !== undefined))); rows.value = Array.isArray(result) ? result : result?.items || result?.records || [] }
    catch (e) { error.value = e.message; rows.value = [] } finally { loading.value = false }
  }
  async function page(delta) { query.page = Math.max(1, query.page + delta); await load() }
  return { rows, loading, error, query, load, page }
}
