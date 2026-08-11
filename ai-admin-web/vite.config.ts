import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    headers: {
      // Vite 开发态 source map 会用 eval；被 CSP 拦截时点击/HMR 可能静默失效
      'Content-Security-Policy':
        "script-src 'self' 'unsafe-eval' 'unsafe-inline' blob: data:; object-src 'none';",
    },
    proxy: {
      '/api/admin': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        timeout: 180000,
        proxyTimeout: 180000,
      },
    },
  },
})
