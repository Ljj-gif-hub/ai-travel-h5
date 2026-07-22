import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [VantResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    proxy: {
      '/uploads': { target: 'http://localhost:3200', changeOrigin: true },
      '/api': {
        target: 'http://localhost:3200',
        changeOrigin: true,
        timeout: 1800000,
        proxyTimeout: 1800000,
        ws: false,
        // SSE 流式代理：禁用缓冲，确保实时推送
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            // 告诉后端这是一条长连接流式请求
            if (req.url.includes('/stream') || req.url.includes('/planner/stream')) {
              proxyReq.setHeader('Connection', 'keep-alive')
              proxyReq.setHeader('Cache-Control', 'no-cache')
            }
          })
        },
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash].[ext]',
      },
    },
  },
})