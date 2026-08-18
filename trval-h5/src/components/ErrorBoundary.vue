<script setup>
/**
 * ErrorBoundary.vue — 组件级错误边界（审查报告"可补充新功能"）
 *
 * onErrorCaptured 捕获子树渲染/生命周期错误，显示重试界面代替白屏。
 * 返回 false 阻止错误继续向上冒泡（全局兜底 app.config.errorHandler 仅处理未捕获错误）。
 * 用法：App.vue 中包在 router-view 外层；重试通过重建子树实现。
 */
import { ref, onErrorCaptured } from 'vue'

const errorInfo = ref(null)

onErrorCaptured((err, instance, info) => {
  console.error('[ErrorBoundary] 捕获页面异常:', err, info)
  errorInfo.value = err?.message || String(err)
  return false // 阻止向全局 errorHandler 传播，避免重复提示
})

const retry = () => {
  errorInfo.value = null
}
</script>

<template>
  <slot v-if="!errorInfo" />
  <div v-else class="error-boundary">
    <div class="eb-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 8v5" /><circle cx="12" cy="16.5" r="0.6" fill="currentColor" stroke="none" />
      </svg>
    </div>
    <p class="eb-title">页面出现异常</p>
    <p class="eb-desc">{{ errorInfo }}</p>
    <button class="eb-retry" @click="retry">重试</button>
  </div>
</template>

<style scoped>
.error-boundary {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  text-align: center;
}
.eb-icon { color: var(--text-hint); }
.eb-title { font-size: 17px; font-weight: 600; color: var(--text-primary); margin: 0; }
.eb-desc {
  font-size: 13px; color: var(--text-hint); margin: 0;
  max-width: 320px; word-break: break-word; line-height: 1.6;
}
.eb-retry {
  margin-top: 8px;
  border: none;
  border-radius: 20px;
  padding: 10px 32px;
  background: var(--gradient-btn);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-purple);
}
.eb-retry:active { transform: scale(0.96); }
</style>
