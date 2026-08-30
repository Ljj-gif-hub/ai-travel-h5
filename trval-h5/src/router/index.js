import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken, removeToken, isTokenExpired, getRefreshToken, removeRefreshToken } from '../utils/auth'

/**
 * 4-Tab 底部导航架构路由
 * 过渡动画规则：
 * - transition: 'fade'       → 底部 Tab 切换（淡入淡出）
 * - transition: 'slide-left' → 前进导航（新页从右侧滑入）
 */
const routes = [
  /* ==================== 4个底部Tab主页面（keep-alive缓存，fade过渡） ==================== */
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/HomeView.vue'),
    meta: { transition: 'fade', tab: 0 },
  },
  {
    path: '/community',
    name: 'Community',
    component: () => import('../views/CommunityView.vue'),
    meta: { transition: 'fade', tab: 1 },
  },
  {
    path: '/trips',
    name: 'Trips',
    component: () => import('../views/TripsView.vue'),
    meta: { transition: 'fade', tab: 2 },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/Profile.vue'),
    meta: { transition: 'fade', tab: 3 },
  },

  /* ==================== 行程子页面（slide-left） ==================== */
  {
    path: '/planning',
    // 保留 query（savedPlanId/destination/days 等），否则跳转后行程信息丢失会误触发默认生成
    redirect: (to) => ({ path: '/trip-map', query: to.query }),
  },
  {
    path: '/ai-planner',
    redirect: '/agent-planner',
  },
  {
    path: '/agent-planner',
    name: 'AgentPlanner',
    component: () => import('../views/AgentPlannerView.vue'),
    meta: { transition: 'slide-left', hideTabBar: true },
  },
  {
    path: '/agent-planner-old',
    name: 'AgentPlannerOld',
    component: () => import('../views/OldPlannerView.vue'),
    meta: { transition: 'slide-left', hideTabBar: true },
  },
  {
    path: '/agent-map',
    name: 'AgentMap',
    component: () => import('../views/AgentMapView.vue'),
    meta: { transition: 'slide-left', hideTabBar: true },
  },
  {
    path: '/ai-planner/progress',
    name: 'AITripPlannerProgress',
    component: () => import('../views/AITripPlannerProgress.vue').catch(() => import('../views/HomeView.vue')),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/city-select',
    name: 'CitySelect',
    component: () => import('../views/CitySelectView.vue'),
    meta: { transition: 'slide-left', hideTabBar: true },
  },
  {
    path: '/attraction-select',
    name: 'AttractionSelect',
    component: () => import('../views/AttractionSelectView.vue'),
    meta: { transition: 'slide-left', hideTabBar: true },
  },
  {
    path: '/my-routes',
    name: 'MyRoutes',
    component: () => import('../views/MyRoutesView.vue'),
    meta: { transition: 'slide-left', hideTabBar: true },
  },
  {
    path: '/saved-plans',
    redirect: '/my-routes',
  },

  /* ==================== 社区子页面（slide-left） ==================== */
  {
    path: '/notes',
    name: 'Notes',
    component: () => import('../views/NotesView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/note-detail',
    name: 'NoteDetail',
    component: () => import('../views/NoteDetailView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/video-detail',
    name: 'VideoDetail',
    component: () => import('../views/VideoDetailView.vue'),
    meta: { transition: 'slide-left', hideTabBar: true },
  },
  {
    path: '/write-note',
    name: 'WriteNote',
    component: () => import('../views/WriteNoteView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/post',
    name: 'Post',
    component: () => import('../views/PostView.vue'),
    meta: { transition: 'slide-left' },
  },

  /* ==================== 目的地子页面（slide-left） ==================== */
  {
    path: '/destinations',
    name: 'Destinations',
    component: () => import('../views/DestinationsView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/destination-detail',
    name: 'DestinationDetail',
    component: () => import('../views/DestinationDetailView.vue'),
    meta: { transition: 'slide-left' },
  },

  /* ==================== 个人中心子页面（slide-left） ==================== */
  {
    path: '/edit-profile',
    name: 'EditProfile',
    component: () => import('../views/EditProfileView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/following',
    name: 'Following',
    component: () => import('../views/FollowingView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/followers',
    name: 'Followers',
    component: () => import('../views/FollowersView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/orders',
    name: 'Orders',
    component: () => import('../views/OrdersView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/hotel-booking',
    name: 'HotelBooking',
    component: () => import('../views/HotelBookingView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/flight-booking',
    name: 'FlightBooking',
    component: () => import('../views/FlightBookingView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/share/:token',
    name: 'ShareLanding',
    component: () => import('../views/ShareLandingView.vue'),
    meta: { transition: 'slide-left', hideTabBar: true },
  },
  {
    path: '/trip-calendar',
    name: 'TripCalendar',
    component: () => import('../views/TripCalendarView.vue'),
    meta: { transition: 'slide-left', hideTabBar: true },
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: () => import('../views/FavoritesView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/coupons',
    name: 'Coupons',
    component: () => import('../views/CouponsView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/feedback',
    name: 'Feedback',
    component: () => import('../views/FeedbackView.vue'),
    meta: { transition: 'slide-left' },
  },
  {
    path: '/about',
    name: 'About',
    component: () => import('../views/AboutView.vue'),
    meta: { transition: 'slide-left' },
  },

  /* ==================== 地图行程页（全屏地图+可拖拽抽屉） ==================== */
  {
    path: '/trip-map',
    name: 'TripMap',
    component: () => import('../views/TripMapView.vue'),
    meta: { transition: 'slide-left', hideTabBar: true },
  },
  /* ==================== 登录/注册 ==================== */
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { transition: 'fade' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/LoginView.vue'),
    meta: { transition: 'fade', initialTab: 'register' },
  },

  /* ==================== 旧路由兼容重定向 ==================== */
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('../views/ChatView.vue'),
    meta: { transition: 'slide-left', hideTabBar: true },
  },
  {
    path: '/Profile',
    redirect: '/profile',
  },

  /* ==================== 404 兜底 ==================== */
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

/*
 * 保存每个 Tab 页的滚动位置（导航离开前记录，返回时恢复）
 * 由全局 beforeEach 写入，scrollBehavior 读取
 */
const tabPaths = ['/', '/community', '/trips', '/profile']
const scrollMemory = {}

// 禁用浏览器原生滚动恢复，全部由 Vue Router 接管
if (window.history && 'scrollRestoration' in window.history) {
  window.history.scrollRestoration = 'manual'
}

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    // 浏览器前进/后退优先
    if (savedPosition) return savedPosition

    // 返回到 Tab 页 → 恢复之前保存的滚动位置
    if (tabPaths.includes(to.path) && scrollMemory[to.path] !== undefined) {
      const targetY = scrollMemory[to.path]
      delete scrollMemory[to.path]
      return { top: targetY, behavior: 'instant' }
    }

    // 前进到子页面 → 不干预
    return false
  },
})

/* ==================== 滚动位置记忆 ==================== */
router.beforeEach((to, from) => {
  // 离开 Tab 页时记住滚动位置
  if (from.path && tabPaths.includes(from.path)) {
    scrollMemory[from.path] = window.scrollY || document.documentElement.scrollTop || 0
  }
})

/* ==================== 白名单（未登录可访问） ==================== */
const whiteList = [
  // 仅公开浏览页/登录注册可匿名访问；写操作与个人数据页必须登录
  '/', '/community', '/trips', '/profile',
  '/login', '/register', '/about',
  '/planning', '/destinations', '/destination-detail',
  '/notes', '/note-detail', '/video-detail',
  '/ai-planner', // 重定向路由（→/agent-planner，目标路由受守卫保护）
  '/city-select', '/attraction-select',
  '/Profile', '/saved-plans', // 旧路由兼容（重定向）
  // 【安全】/chat 与 /ai-planner/progress 已从白名单移除：AI 接口需登录防配额滥用
]

router.beforeEach((to, from, next) => {
  const token = getToken()

  // Token 已过期（JWT exp 校验）
  if (token && isTokenExpired(token) && to.path !== '/login') {
    // AUTH-1 修复：access token 过期时优先尝试用 refresh token 续期，而非直接踢回登录页
    const refreshToken = getRefreshToken()
    if (refreshToken) {
      // 有 refresh token：不清登录态，放行导航，由 axios 401 拦截器单飞刷新
      // （refresh token 7 天有效期内可实现免登录；刷新失败时 axios 层会清登录态并跳转登录）
      next()
      return
    }
    // 无 refresh token：清 access + refresh token，回登录页
    removeToken()
    removeRefreshToken()
    localStorage.setItem('redirectUrl', to.fullPath)
    next({ path: '/login' })
    return
  }

  if (token) {
    if (to.path === '/login') {
      next({ path: '/' })
    } else {
      next()
    }
  } else {
    // 分享落地页公开访问（短链 token）
    if (whiteList.includes(to.path) || to.path.startsWith('/share/')) {
      next()
    } else {
      localStorage.setItem('redirectUrl', to.fullPath)
      next({ path: '/login' })
    }
  }
})

/* ==================== 路由预加载（审查报告"可补充新功能"） ====================
 * afterEach：导航完成后预取"即将可能访问"的路由 chunk（import() 触发分块拉取）。
 * - Tab 页 → 预取其余 3 个 Tab 兄弟路由（keep-alive 架构下命中概率最高）
 * - 支持路由 meta.preload 指定额外路径
 * - 空闲回调执行（requestIdleCallback，降级 setTimeout），不与首屏渲染抢带宽；
 *   预取失败 try/catch 静默忽略（离线/慢网不影响当前页）。
 */
/* ==================== 旧 chunk 加载失败自愈 ====================
 * 部署新版本后，浏览器缓存的旧 index.html（启发式缓存/SW 预缓存）会引用
 * 已被新构建删除的旧 hash chunk，异步路由组件加载 404 导致 router.push 静默失败
 * （用户表现：点击"出发地"等跳转入口没反应）。
 * 兜底：捕获 chunk 加载错误后清空 SW/Caches，带版本号强刷拿最新 index.html。
 * sessionStorage 30 秒防循环（新版本仍失败时不再无限刷新）。
 */
router.onError((error) => {
  const msg = String(error?.message || '')
  const isChunkError = /Failed to fetch dynamically imported module|Importing a module script failed|Loading chunk|Loading CSS chunk|error loading dynamically imported module/i.test(msg)
  if (!isChunkError) return
  const RELOAD_FLAG = 'chunk_reload_ts'
  const last = Number(sessionStorage.getItem(RELOAD_FLAG) || 0)
  if (Date.now() - last < 30000) return
  try { sessionStorage.setItem(RELOAD_FLAG, String(Date.now())) } catch {}
  try {
    if ('caches' in window) {
      caches.keys().then((keys) => keys.forEach((k) => caches.delete(k))).catch(() => {})
    }
    if (navigator.serviceWorker) {
      navigator.serviceWorker.getRegistrations().then((rs) => rs.forEach((r) => r.unregister())).catch(() => {})
    }
  } catch {}
  const url = new URL(location.href)
  url.searchParams.set('_v', String(Date.now()))
  location.replace(url.href)
})

const preloaded = new Set()
function preloadRoutePath(path) {
  if (preloaded.has(path)) return
  preloaded.add(path)
  const route = routes.find((r) => r.path === path)
  const loader = route?.component
  if (typeof loader !== 'function') return
  try {
    Promise.resolve(loader()).catch(() => { /* 预取失败忽略 */ })
  } catch (e) { /* 同步异常忽略 */ }
}

router.afterEach((to) => {
  const preloadPaths = []
  if (tabPaths.includes(to.path)) {
    for (const p of tabPaths) {
      if (p !== to.path) preloadPaths.push(p)
    }
  }
  if (Array.isArray(to.meta?.preload)) preloadPaths.push(...to.meta.preload)

  if (preloadPaths.length === 0) return
  const schedule = () => {
    for (const p of preloadPaths) preloadRoutePath(p)
  }
  if (typeof requestIdleCallback === 'function') {
    requestIdleCallback(schedule, { timeout: 2000 })
  } else {
    setTimeout(schedule, 300)
  }
})

export default router
