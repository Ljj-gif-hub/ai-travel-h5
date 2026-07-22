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
import { computed, ref } from 'vue'

const route = useRoute()
const router = useRouter()

/* ==================== 导航方向检测 ==================== */
// 五大 Tab 页面路径（根级页面）
const TAB_PATHS = ['/', '/messages', '/community', '/trips', '/profile']
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

/* ==================== 底部5栏 Tab 导航 ==================== */
const tabs = [
  { path: '/',          name: '首页', icon: 'home-o' },
  { path: '/messages',  name: '消息', icon: 'chat-o' },
  { path: '/community', name: '社区', icon: 'friends-o' },
  { path: '/trips',     name: '行程', icon: 'bookmark-o' },
  { path: '/profile',   name: '我的', icon: 'user-o' },
]

const hideTabBar = computed(() => {
  if (route.meta?.hideTabBar) return true
  const hiddenRoutes = ['/planning', '/login', '/register', '/trip-map', '/video-detail', '/note-detail']
  return hiddenRoutes.includes(route.path)
})

const isActive = (path) => route.path === path

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

    <!-- 底部固定三栏 Tab 导航 — 不参与过渡 -->
    <div v-if="!hideTabBar" class="custom-tabbar">
      <div
        v-for="(tab, index) in tabs"
        :key="index"
        class="tab-item"
        :class="{ active: isActive(tab.path) }"
        @click="handleTabClick(tab.path)"
      >
        <van-icon :name="tab.icon" size="22" />
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

/* ==================== 全局滚动条隐藏 ==================== */
::-webkit-scrollbar { width: 0; height: 0; }
* { scrollbar-width: none; -ms-overflow-style: none; }

/* ==================== 路由过渡动画 ==================== */

/* fade — 底部 Tab 切换（淡入淡出） */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/*
 * slide-left — 前进：离开元素 fixed 固定在视口，不参与文档流，进入元素正常流
 * slide-right — 后退：同上
 *
 * --saved-scroll-y 在 router.beforeEach 中设置为导航前的 scrollY
 * 用于补偿 fixed 定位的偏移，保证离开元素显示的是用户当前看到的内容
 */
.slide-left-leave-active,
.slide-right-leave-active {
  position: fixed;
  top: calc(-1 * var(--saved-scroll-y, 0px));
  left: 0;
  width: 100%;
  height: 100vh;
  transition: transform 0.3s cubic-bezier(0.25, 0.1, 0.25, 1.0), opacity 0.3s ease;
  z-index: 1;
}
.slide-left-leave-to   { transform: translateX(-30%); opacity: 0; }
.slide-right-leave-to  { transform: translateX(100%);  opacity: 0; }

.slide-left-enter-active,
.slide-right-enter-active {
  transition: transform 0.3s cubic-bezier(0.25, 0.1, 0.25, 1.0), opacity 0.3s ease;
}
.slide-left-enter-from  { transform: translateX(100%); }
.slide-left-enter-to    { transform: translateX(0); }
.slide-right-enter-from { transform: translateX(-20%); opacity: 0.6; }
.slide-right-enter-to   { transform: translateX(0); opacity: 1; }
</style>

<style scoped>
.app {
  width: 100%;
  min-height: 100vh;
  position: relative;
  overflow-x: hidden;
}

/* ==================== 底部 Tab 导航栏（固定，不参与过渡）— 薰衣草毛玻璃风格 ==================== */
/* 【性能优化】will-change + transform 开启GPU硬件加速 */
.custom-tabbar {
  position: fixed;
  will-change: transform;
  transform: translateZ(0);
  bottom: 0;
  left: 0;
  right: 0;
  width: 100%;
  height: var(--tabbar-height);
  padding-bottom: var(--safe-area-bottom);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  display: flex;
  align-items: center;
  justify-content: space-around;
  box-shadow: 0 -2px 20px rgba(139, 92, 246, 0.08);
  z-index: 9999;
  box-sizing: content-box;
  border-top: 1px solid rgba(139, 92, 246, 0.06);
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  height: 100%;
  color: #B0B8C8;
  transition: color 0.25s ease, transform 0.2s ease;
  cursor: pointer;
  position: relative;
  will-change: transform;        /* GPU加速：点击缩放动画 */
}

.tab-item.active {
  color: #8B5CF6;
}
/* 选中Tab图标持续微弱放大呼吸 */
.tab-item.active :deep(.van-icon) {
  animation: tabIconBreathe 2.8s ease-in-out infinite;
}
@keyframes tabIconBreathe {
  0%, 100% { transform: scale(1); }
  50%      { transform: scale(1.08); }
}

.tab-item.active::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 50%;
  transform: translateX(-50%);
  width: 20px;
  height: 3px;
  background: linear-gradient(135deg, #A78BFA, #8B5CF6);
  border-radius: 2px;
}

/* Tab图标弹性缩放过渡 */
.tab-item:active {
  transform: scale(0.88);
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.tab-text {
  font-size: 10px;
  margin-top: 3px;
  font-weight: 500;
  letter-spacing: 0.2px;
}
</style>
