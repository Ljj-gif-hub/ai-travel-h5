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
        // 相对路径，跟随部署 base（/ai-travel-h5/ 等），避免指向站点根目录
        start_url: './',
        scope: './',
        icons: [
          {
            src: './favicon.svg',
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
        // 离线兜底页：断网导航时返回 offline.html
        navigateFallback: `${mode === 'production' ? '/ai-travel-h5/' : '/'}offline.html`,
        navigateFallbackDenylist: [/^\/api\//],
        runtimeCaching: [
          {
            urlPattern: /^https?:\/\/.*\/api\/.*/i,
            handler: 'NetworkFirst',
            options: { cacheName: 'api-cache', expiration: { maxEntries: 50, maxAgeSeconds: 300 } },
          },
          // 离线地图（B5）：OSM 瓦片 CacheFirst（Leaflet 离线底图载体）
          {
            urlPattern: /^https:\/\/[abc]\.tile\.openstreetmap\.org\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'osm-tiles',
              expiration: { maxEntries: 2000, maxAgeSeconds: 30 * 24 * 3600 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          // 高德瓦片/资源：best-effort 缓存（带鉴权参数，缓存失败静默降级）
          {
            urlPattern: /^https:\/\/.*\.(amap|gaode)\.com\/.*/i,
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'amap-assets',
              expiration: { maxEntries: 500, maxAgeSeconds: 7 * 24 * 3600 },
              cacheableResponse: { statuses: [0, 200] },
            },
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
      '/api/agent': {
        // Agent SSE 流式直连 Python 服务，避免 Spring Boot 二次代理导致 resp.body 为 null
        target: 'http://localhost:3201',
        changeOrigin: true,
        timeout: 600000,
        proxyTimeout: 600000,
        ws: false,
      },
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