import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../utils/auth'

/**
 * 5-Tab 底部导航架构路由
 * 过渡动画规则：
 * - transition: 'fade'       → 底部 Tab 切换（淡入淡出）
 * - transition: 'slide-left' → 前进导航（新页从右侧滑入）
 */
const routes = [
  /* ==================== 5个底部Tab主页面（keep-alive缓存，fade过渡） ==================== */
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/HomeView.vue'),
    meta: { transition: 'fade', tab: 0 },
  },
  {
    path: '/messages',
    name: 'Messages',
    component: () => import('../views/MessagesView.vue'),
    meta: { transition: 'fade', tab: 1 },
  },
  {
    path: '/community',
    name: 'Community',
    component: () => import('../views/CommunityView.vue'),
    meta: { transition: 'fade', tab: 2 },
  },
  {
    path: '/trips',
    name: 'Trips',
    component: () => import('../views/TripsView.vue'),
    meta: { transition: 'fade', tab: 3 },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/Profile.vue'),
    meta: { transition: 'fade', tab: 4 },
  },

  /* ==================== 行程子页面（slide-left） ==================== */
  {
    path: '/planning',
    redirect: '/trip-map',
  },
  {
    path: '/ai-planner',
    name: 'AITripPlanner',
    component: () => import('../views/AITripPlanner.vue'),
    meta: { transition: 'slide-left' },
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
    path: '/saved-plans',
    redirect: '/trips',
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
    redirect: '/messages',
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
const tabPaths = ['/', '/messages', '/community', '/trips', '/profile']
const scrollMemory = {}

// 禁用浏览器原生滚动恢复，全部由 Vue Router 接管
if (window.history && 'scrollRestoration' in window.history) {
  window.history.scrollRestoration = 'manual'
}

const router = createRouter({
  history: createWebHistory(),
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
  '/', '/messages', '/community', '/trips', '/profile',
  '/login', '/register', '/about',
  '/planning', '/destinations', '/destination-detail',
  '/notes', '/note-detail', '/video-detail', '/write-note', '/post',
  '/trip-map', '/ai-planner', '/ai-planner/progress',
  '/city-select', '/attraction-select',
  '/edit-profile', '/following', '/followers',
  '/orders', '/favorites', '/coupons', '/feedback',
  '/chat', '/Profile', '/saved-plans', // 旧路由兼容
]

router.beforeEach((to, from, next) => {
  const token = getToken()

  if (token) {
    if (to.path === '/login') {
      next({ path: '/' })
    } else {
      next()
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      localStorage.setItem('redirectUrl', to.fullPath)
      next({ path: '/login' })
    }
  }
})

export default router
