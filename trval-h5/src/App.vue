<script setup>
const tabs = [
  { path: '/', name: '首页', icon: 'home-o' },
  { path: '/chat', name: '对话', icon: 'chat-o' },
  { path: '/Profile', name: '我的', icon: 'user-o' },
]
</script>

<template>
  <div class="app">
    <transition name="slide" mode="out-in">
      <router-view />
    </transition>
    <van-tabbar route safe-area-inset-bottom>
      <van-tabbar-item
        v-for="(tab, index) in tabs"
        :key="index"
        :icon="tab.icon"
        :to="tab.path"
      >
        {{ tab.name }}
      </van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
}

:deep(.van-tabbar) {
  max-width: 500PX;
  left: 50%;
  transform: translateX(-50%);
}

:deep(.van-tabbar-item--active) {
  color: #667eea !important;
}

:deep(.van-tabbar-item--active .van-tabbar-item__icon) {
  color: #667eea !important;
}

:deep(.van-tabbar-item--active .van-tabbar-item__text) {
  color: #667eea !important;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.slide-enter-from {
  transform: translateX(100%);
  opacity: 0;
}

.slide-leave-to {
  transform: translateX(-100%);
  opacity: 0;
}
</style>
