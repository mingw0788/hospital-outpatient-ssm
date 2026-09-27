import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  // API_PROXY is read by Vite only; the browser continues to use same-origin /api requests.
  const env = loadEnv(mode, process.cwd(), '')
  const proxy = {
    '/api': {
      target: env.API_PROXY || 'http://127.0.0.1:8080',
      changeOrigin: true
    }
  }
  return {
    plugins: [vue()],
    appType: 'spa',
    server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true, proxy },
    build: { chunkSizeWarningLimit: 1100 }
  }
})
