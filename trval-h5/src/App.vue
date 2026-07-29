<script setup>
/**
 * App.vue — 全局路由过渡动画 + 底部固定 Tab 导航
 *
 * 过渡规则：
 * - fade：底部 Tab 主页面切换（首页/对话/我的），淡入淡出
 * - slide-left：前进导航（列表→详情），页面从右侧滑入
 * - slide-right：后退导航（详情→列表），页面从左侧滑入
 *
 * 底部 Tab 栏固定在 <transition> 外部，页面切换时 Tab 不跟随滑动
 */
import { useRoute, useRouter } from 'vue-router'
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { initTabBarHide, destroyTabBarHide } from './utils/tabBarHide'

const route = useRoute()
const router = useRouter()

/* ==================== 导航方向检测 ==================== */
// 四大 Tab 页面路径
const TAB_PATHS = ['/', '/community', '/trips', '/profile']

onMounted(() => initTabBarHide())
onUnmounted(() => destroyTabBarHide())
const isTabPage = (path) => TAB_PATHS.includes(path)

// 前进（tab→detail）= true，后退（detail→tab）= false
const isForward = ref(true)

router.beforeEach((to, from) => {
  // 导航前保存当前滚动位置，供过渡动画使用
  const scrollY = window.scrollY || document.documentElement.scrollTop || 0
  document.documentElement.style.setProperty('--saved-scroll-y', `${scrollY}px`)

  if (!from.path || from.path === to.path) {
    isForward.value = true
    return
  }
  // Tab → 子页面 = 前进（slide-left）
  if (isTabPage(from.path) && !isTabPage(to.path)) {
    isForward.value = true
  }
  // 子页面 → Tab = 后退（slide-right）
  else if (!isTabPage(from.path) && isTabPage(to.path)) {
    isForward.value = false
  }
  // 子页面 → 子页面 = 保持当前方向
  else {
    isForward.value = true
  }
})

/* ==================== 底部4栏 Tab 导航 ==================== */
const tabIcons = {
  home:    { viewBox: '0 0 24 24', body: '<path d="M12 3L4 9v12h5v-7h6v7h5V9z"/>' },
  community: { viewBox: '0 0 24 24', body: '<circle cx="9" cy="9" r="4"/><circle cx="18" cy="8" r="3"/><path d="M3 19c0-2.6 2.6-5 6-5s6 2.4 6 5v1H3v-1zM17 15c-1.8 0-3.5.8-4.5 2h9c-1-1.2-2.7-2-4.5-2z"/>' },
  trips:  { viewBox: '0 0 24 24', body: '<path d="M17 3H7c-1.1 0-2 .9-2 2v16l5-3 5 3V5c0-1.1-.9-2-2-2z"/>' },
  profile: { viewBox: '0 0 24 24', body: '<circle cx="12" cy="8" r="4"/><path d="M12 14c-4.4 0-8 2-8 4.5v1.5h16v-1.5c0-2.5-3.6-4.5-8-4.5z"/>' },
}

const tabs = [
  { path: '/', name: '首页', icon: 'home' },
  { path: '/community', name: '社区', icon: 'community' },
  { path: '/trips', name: '行程', icon: 'trips' },
  { path: '/profile', name: '我的', icon: 'profile' },
]

const hideTabBar = computed(() => {
  if (route.meta?.hideTabBar) return true
  return !TAB_PATHS.includes(route.path)
})

const isActive = (path) => route.path === path
const activeIndex = computed(() => {
  const idx = tabs.findIndex(t => isActive(t.path))
  return idx >= 0 ? idx : 0
})
const isTabActive = computed(() => tabs.some(t => isActive(t.path)))

/* 【性能优化】Tab点击防抖 + replace避免路由栈堆积 */
let tabClickTimer = null
const handleTabClick = (path) => {
  if (route.path === path) return
  if (tabClickTimer) return
  tabClickTimer = setTimeout(() => { tabClickTimer = null }, 300)
  router.replace(path)
}

/**
 * 路由过渡名称决策：
 * - 前进（Tab→子页面）→ slide-left（右→左滑入）
 * - 后退（子页面→Tab）→ slide-right（左→右滑入）
 * - Tab 之间切换 → fade（淡入淡出）
 */
const transitionName = computed(() => {
  // 子页面回退到 Tab → 从左边滑出
  if (!isForward.value) return 'slide-right'
  // 如果是子页面且有 meta.transition → 使用指定过渡
  if (route.meta?.transition && !isTabPage(route.path)) return route.meta.transition
  // Tab 页面之间切换 → fade
  if (isTabPage(route.path)) return 'fade'
  // 默认
  return route.meta?.transition || 'fade'
})

/*
 * 【Bug修复】keep-alive 缓存的白名单组件名
 * 三个 Tab 页必须缓存，否则每次切换都会销毁→重建 → 空白闪烁
 * 组件名通过各页面的 defineOptions({ name: '...' }) 显式声明
 */
const CACHED_VIEWS = ['HomeView', 'MessagesView', 'CommunityView', 'TripsView', 'ProfileView']
</script>

<template>
  <div class="app">
    <!--
      路由过渡动画容器
      - keep-alive 缓存 Tab 页面，切换不销毁
      - 过渡只用 transform，不用 absolute，不破坏文档流
    -->
    <router-view v-slot="{ Component }">
      <transition :name="transitionName" :duration="300">
        <keep-alive :include="CACHED_VIEWS" :max="5">
          <component :is="Component" :key="route.path" />
        </keep-alive>
      </transition>
    </router-view>

    <!-- 底部悬浮椭圆 Tab 导航 -->
    <div class="custom-tabbar" :class="{ 'tab-hidden': hideTabBar }">
      <div
        v-show="isTabActive"
        class="tab-indicator"
        :style="{ left: `calc(4px + (100% - 8px) / 4 * ${activeIndex})` }"
      />
      <div
        v-for="(tab, index) in tabs"
        :key="index"
        class="tab-item"
        :class="{ active: isActive(tab.path) }"
        @click="handleTabClick(tab.path)"
      >
        <svg class="tab-icon" :viewBox="tabIcons[tab.icon].viewBox" v-html="tabIcons[tab.icon].body" />
        <span class="tab-text">{{ tab.name }}</span>
      </div>
    </div>
  </div>
</template>

<style>
/* ==================== 全局 CSS 变量 ==================== */
:root {
  --tabbar-height: 56px;
  --safe-area-bottom: env(safe-area-inset-bottom, 0px);
  --transition-speed: 300ms;
}

/* ==================== 全局 Vant 组件玻璃化 ==================== */
.van-nav-bar {
  background:
    linear-gradient(160deg, rgba(255,255,255,0.65) 0%, rgba(255,255,255,0.35) 50%, rgba(255,255,255,0.55) 100%),
    rgba(255,255,255,0.6) !important;
  backdrop-filter: blur(18px) saturate(160%) !important;
  -webkit-backdrop-filter: blur(18px) saturate(160%) !important;
  border-bottom: 0.5px solid rgba(0,0,0,0.05) !important;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.5) !important;
}
/* 底部弹窗 — 悬浮卡片，四周留白 */
.van-popup--bottom {
  width: calc(100vw - 20px) !important;
  max-width: calc(100vw - 20px) !important;
  left: 10px !important;
  bottom: calc(10px + env(safe-area-inset-bottom, 0px)) !important;
  border-radius: 20px !important;
}
.van-popup {
  background:
    linear-gradient(160deg, rgba(255,255,255,0.65) 0%, rgba(255,255,255,0.3) 40%, rgba(255,255,255,0.5) 100%),
    rgba(255,255,255,0.75) !important;
  backdrop-filter: blur(22px) saturate(170%) !important;
  -webkit-backdrop-filter: blur(22px) saturate(170%) !important;
}
.van-dialog {
  background:
    linear-gradient(160deg, rgba(255,255,255,0.65) 0%, rgba(255,255,255,0.25) 40%, rgba(255,255,255,0.5) 100%),
    rgba(255,255,255,0.8) !important;
  backdrop-filter: blur(24px) saturate(170%) !important;
  -webkit-backdrop-filter: blur(24px) saturate(170%) !important;
  border-radius: 24px !important;
  overflow: hidden;
  border: 1px solid rgba(255,255,255,0.5);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.5) !important;
  color: #323233 !important;
}

/* ==================== 全局滚动条隐藏 ==================== */
::-webkit-scrollbar { width: 0; height: 0; }
* { scrollbar-width: none; -ms-overflow-style: none; }

/* ==================== 路由过渡动画（GPU 加速，防闪烁） ==================== */

/* fade — Tab 切换 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
  will-change: opacity;
}
.fade-enter-from,
.fade-leave-to { opacity: 0; }

/* slide — 子页面进出 */
.slide-left-enter-active,
.slide-left-leave-active,
.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.28s cubic-bezier(0.25, 0.1, 0.25, 1.0), opacity 0.25s ease;
  will-change: transform, opacity;
  backface-visibility: hidden;
}

.slide-left-leave-active,
.slide-right-leave-active {
  position: fixed;
  top: calc(-1 * var(--saved-scroll-y, 0px));
  left: 0;
  width: 100%;
  height: 100vh;
  z-index: 1;
  pointer-events: none;
}

.slide-left-enter-from  { transform: translateX(100%); opacity: 0.8; }
.slide-left-leave-to    { transform: translateX(-30%); opacity: 0; }
.slide-right-enter-from { transform: translateX(-20%); opacity: 0.8; }
.slide-right-leave-to   { transform: translateX(100%); opacity: 0; }
.slide-left-enter-to,
.slide-left-leave-from,
.slide-right-enter-to,
.slide-right-leave-from { transform: translateX(0); opacity: 1; }

/* 非 Tab 页 / 弹出层打开 — Tab 栏向下滑出 */
.custom-tabbar.tab-hidden,
.picker-open .custom-tabbar {
  transform: translateX(-50%) translateY(120%) translateZ(0) !important;
  opacity: 0;
  pointer-events: none;
}
</style>

<style scoped>
.app {
  width: 100%;
  min-height: 100vh;
  position: relative;
  overflow-x: hidden;
  background: transparent;
  -webkit-font-smoothing: antialiased;
}

/*
 * ==================== 底部 Tab — 椭圆胶囊 + 滑动指示器 ====================
 */
.custom-tabbar {
  position: fixed;
  bottom: calc(10px + var(--safe-area-bottom, 0px));
  left: 50%;
  transform: translateX(-50%) translateZ(0);
  width: auto;
  min-width: 240px;
  max-width: calc(100vw - 36px);
  height: 48px;
  border-radius: 24px;
  padding: 0 4px;
  background:
    linear-gradient(160deg, rgba(255,255,255,0.6) 0%, rgba(255,255,255,0.28) 35%, rgba(255,255,255,0.05) 60%, rgba(255,255,255,0.5) 100%),
    rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(20px) saturate(160%);
  -webkit-backdrop-filter: blur(20px) saturate(160%);
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.55),
    0 1px 3px rgba(0,0,0,0.04),
    0 4px 14px rgba(0,0,0,0.05);
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  align-items: center;
  justify-items: center;
  z-index: 9999;
  box-sizing: border-box;
  border: 0.5px solid rgba(0,0,0,0.06);
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s ease;
}

/* 滑动指示器 */
.tab-indicator {
  position: absolute;
  top: 7px;
  width: calc((100% - 8px) / 4);
  height: 34px;
  border-radius: 17px;
  background:
    linear-gradient(160deg, rgba(139,92,246,0.15) 0%, rgba(139,92,246,0.05) 50%, rgba(139,92,246,0.12) 100%),
    rgba(139,92,246,0.06);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  border: 0.5px solid rgba(139,92,246,0.12);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.4);
  transition: left 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  pointer-events: none;
  z-index: 0;
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 34px;
  color: #999;
  transition: color 0.25s ease;
  cursor: pointer;
  position: relative;
  z-index: 1;
}

.tab-item.active { color: #7C3AED; }

.tab-item:active { transform: scale(0.88); }

.tab-text {
  font-size: 8px;
  margin-top: 1px;
  font-weight: 500;
  line-height: 1;
  transform: scaleX(0.88);
}

.tab-icon {
  width: 19px;
  height: 19px;
  fill: currentColor;
  display: block;
}
</style>
