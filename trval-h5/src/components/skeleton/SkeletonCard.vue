<script setup>
/**
 * SkeletonCard.vue — 通用卡片骨架屏（审查报告"可补充新功能"）
 * 灰色占位块 + 闪烁动画，样式与现有磨砂玻璃卡片体系一致。
 * 用法：列表加载态逐条渲染 <SkeletonCard :rows="2" :show-avatar="true" :show-image="true" />
 */
defineProps({
  rows: { type: Number, default: 2 },
  showAvatar: { type: Boolean, default: true },
  showImage: { type: Boolean, default: false },
})
</script>

<template>
  <div class="skeleton-card" aria-hidden="true">
    <div class="sk-header">
      <div v-if="showAvatar" class="sk sk-avatar" />
      <div class="sk sk-title" />
    </div>
    <div class="sk sk-line" v-for="i in rows" :key="i" :style="{ width: i === rows ? '62%' : '100%' }" />
    <div v-if="showImage" class="sk sk-image" />
  </div>
</template>

<style scoped>
.skeleton-card {
  background: var(--bg-card);
  backdrop-filter: blur(var(--glass-blur-light));
  -webkit-backdrop-filter: blur(var(--glass-blur-light));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  padding: 16px;
  box-shadow: var(--shadow-card);
  margin-bottom: 12px;
}
.sk-header { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.sk {
  background: linear-gradient(90deg, rgba(148,163,184,0.14) 25%, rgba(148,163,184,0.28) 50%, rgba(148,163,184,0.14) 75%);
  background-size: 200% 100%;
  animation: sk-shimmer 1.6s ease-in-out infinite;
  border-radius: 6px;
}
.sk-avatar { width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0; }
.sk-title { flex: 1; height: 14px; }
.sk-line { height: 12px; margin-bottom: 8px; }
.sk-image { height: 120px; margin-top: 4px; border-radius: 10px; }
@keyframes sk-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
/* 低内存设备：骨架屏也剥掉磨砂，降低合成开销 */
.low-mem .skeleton-card { backdrop-filter: none; -webkit-backdrop-filter: none; background: var(--bg-card-solid, #fff); }
html[data-theme='dark'] .sk {
  background: linear-gradient(90deg, rgba(148,163,184,0.12) 25%, rgba(148,163,184,0.22) 50%, rgba(148,163,184,0.12) 75%);
  background-size: 200% 100%;
}
</style>
