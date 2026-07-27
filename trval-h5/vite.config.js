import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'
import { VitePWA } from 'vite-plugin-pwa'
import { resolve } from 'path'

export default defineConfig(({ mode }) => ({
  base: mode === 'production' ? '/ai-travel-h5/' : '/',
  plugins: [
    vue(),
    Components({
      resolvers: [VantResolver()],
    }),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'icons.svg'],
      manifest: {
        name: '智能旅游助手',
        short_name: '旅游助手',
        description: 'AI 智能旅游规划助手 - 探索世界，从这里出发',
        theme_color: '#8B5CF6',
        background_color: '#F8F7FF',
        display: 'standalone',
        orientation: 'portrait',
        start_url: '/',
        scope: '/',
        icons: [
          {
            src: '/favicon.svg',
            sizes: '48x48 72x72 96x96 128x128 144x144 192x192 256x256 512x512',
            type: 'image/svg+xml',
            purpose: 'any maskable',
          },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,jpg,json}'],
        globIgnores: ['**/demos/**'],
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024,
        runtimeCaching: [
          {
            urlPattern: /^https?:\/\/.*\/api\/.*/i,
            handler: 'NetworkFirst',
            options: { cacheName: 'api-cache', expiration: { maxEntries: 50, maxAgeSeconds: 300 } },
          },
        ],
      },
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
    target: 'es2020',
    chunkSizeWarningLimit: 300,
    rollupOptions: {
      output: {
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash].[ext]',
        manualChunks(id) {
          if (id.includes('highlight.js')) return 'hljs'
          if (id.includes('markdown-it')) return 'mdit'
          if (id.includes('node_modules/vant')) return 'vant-ui'
        },
      },
    },
  },
}))