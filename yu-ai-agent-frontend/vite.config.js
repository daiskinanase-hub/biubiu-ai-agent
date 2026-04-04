import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import http from 'http'
import https from 'https'

// 创建自定义 agent，保持长连接
const httpAgent = new http.Agent({
  keepAlive: true,
  keepAliveMsecs: 60000,
  maxSockets: 10
})

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue()
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    cors: true,
    proxy: {
      // AI 服务代理（AI恋爱大师、AI超级智能体）-> 8123 端口
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true,
        agent: httpAgent,
        timeout: 600000, // 10分钟
        proxyTimeout: 600000
      },
      // 知识库服务代理 -> 10201 端口
      '/kb-api': {
        target: 'http://localhost:10201',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/kb-api/, '/api'),
        agent: httpAgent,
        timeout: 600000, // 10分钟
        proxyTimeout: 600000,
        // 配置 WebSocket 支持（如果需要）
        ws: false
      }
    }
  }
})
