import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
export const http = axios.create({ baseURL: '/api', withCredentials: true, timeout: 20000 })
let csrf = null
export async function refreshCsrf() { const response = await http.get('/auth/csrf'); csrf = response.data.data; return csrf }
http.interceptors.request.use(async config => {
  if (!['get', 'head', 'options'].includes(config.method)) {
    if (!csrf) await refreshCsrf()
    config.headers[csrf.headerName] = csrf.token
  }
  return config
})
http.interceptors.response.use(response => response, error => {
  const status = error.response?.status
  const fallback = error.code === 'ECONNABORTED'
    ? '请求超时，请刷新确认结果后再操作'
    : !status || status >= 500
      ? '暂时无法连接服务，请确认后端已启动并稍后重试'
      : ({ 401: '登录状态已失效，请重新登录', 403: '没有操作权限或安全令牌已失效，请刷新页面', 404: '请求的服务不存在，请检查后端地址' }[status] || '请求失败，请稍后重试')
  const message = error.response?.data?.message || fallback
  if (!error.config?.silent) ElMessage.error(message)
  error.message = message
  if (error.response?.status === 401 && !error.config?.silent) window.dispatchEvent(new CustomEvent('session-expired'))
  return Promise.reject(error)
})
export async function api(method, url, data, config = {}) {
  const response = await http.request({ method, url, ...(method === 'get' ? { params: data } : { data }), ...config })
  return response.data.data
}
export const get = (url, params, config) => api('get', url, params, config)
export const post = (url, body = {}) => api('post', url, body)
export const put = (url, body) => api('put', url, body)
export const patch = (url, body) => api('patch', url, body)
export const requestKey = () => crypto.randomUUID()
export async function confirmAction(message) {
  try { await ElMessageBox.confirm(message, '确认操作', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }); return true } catch { return false }
}
export async function perform(action, refresh, success = '操作成功') {
  try { const result = await action(); ElMessage.success(success); if (refresh) await refresh(); return result } catch { return undefined }
}
