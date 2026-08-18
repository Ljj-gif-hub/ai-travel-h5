<script setup>
/**
 * SkeletonList.vue — 列表加载态骨架屏容器（审查报告"可补充新功能"）
 * 按 count 渲染 N 张 SkeletonCard，props 透传（rows/showAvatar/showImage）。
 * 用法：<SkeletonList :count="4" :rows="2" show-image />
 */
import SkeletonCard from './SkeletonCard.vue'

defineProps({
  count: { type: Number, default: 4 },
  rows: { type: Number, default: 2 },
  showAvatar: { type: Boolean, default: true },
  showImage: { type: Boolean, default: false },
})
</script>

<template>
  <div class="skeleton-list" aria-label="加载中" role="status">
    <SkeletonCard
      v-for="i in count"
      :key="i"
      :rows="rows"
      :show-avatar="showAvatar"
      :show-image="showImage"
    />
  </div>
</template>

<style scoped>
.skeleton-list { width: 100%; }
</style>
