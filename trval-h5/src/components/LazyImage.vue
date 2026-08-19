<script setup>
/**
 * LazyImage.vue — 图片懒加载统一组件（审查报告"可补充新功能"）
 *
 * - IntersectionObserver：进入视口（默认提前 200px）才加载真实 src
 * - 加载前显示占位背景（浅色渐变 + 微光闪烁，与全局图片骨架体系一致）
 * - 加载失败显示内置默认错误图（data URI SVG）
 * - 单根 <img>，class/style/@click 等属性自动透传，直接替换既有 <img> 零布局改动
 * - 不支持 IntersectionObserver 的环境降级为立即加载
 */
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
  /** 提前加载距离（px），默认 200px 提前量避免滚动露白 */
  rootMargin: { type: String, default: '200px' },
})

const elRef = ref(null)
const inView = ref(false)
const loaded = ref(false)
const failed = ref(false)

// 内置错误占位图（浅灰底 + 图片图标），避免依赖外部静态资源
const ERROR_SRC = 'data:image/svg+xml,' + encodeURIComponent(
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 240"><rect fill="#eef1f6" width="400" height="240"/><g fill="none" stroke="#c3ccd9" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"><rect x="150" y="80" width="100" height="80" rx="10"/><circle cx="172" cy="102" r="8"/><path d="M160 152l26-26 20 20 14-14 30 30"/></g><text x="200" y="212" text-anchor="middle" fill="#94a3b8" font-size="13" font-family="sans-serif">图片加载失败</text></svg>`
)

// 共享单例 observer（列表大量图片时只建一个，降低内存）
let sharedObserver = null
const observeMap = new Map()

function ensureObserver() {
  if (!sharedObserver && typeof IntersectionObserver !== 'undefined') {
    sharedObserver = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            const cb = observeMap.get(entry.target)
            if (cb) {
              observeMap.delete(entry.target)
              sharedObserver.unobserve(entry.target)
              cb()
            }
          }
        }
      },
      { rootMargin: props.rootMargin }
    )
  }
  return sharedObserver
}

onMounted(() => {
  const el = elRef.value
  if (!el || !props.src) return
  const observer = ensureObserver()
  if (observer) {
    observeMap.set(el, () => { inView.value = true })
    observer.observe(el)
  } else {
    inView.value = true // 降级：无 IO 支持直接加载
  }
})

// BUGID COMP-1 修复：src 变化时重置加载/失败状态并重新走 observer 加载新图；
// 同时修复 failed 状态无 reset——换 src 后旧错误图不再残留
watch(() => props.src, () => {
  failed.value = false
  loaded.value = false
  const el = elRef.value
  if (!el || !props.src) return
  // 重置 observer：解除旧监听，重新观察等待新 src 进入视口后加载
  if (sharedObserver) {
    sharedObserver.unobserve(el)
    observeMap.delete(el)
  }
  inView.value = false
  const observer = ensureObserver()
  if (observer) {
    observeMap.set(el, () => { inView.value = true })
    observer.observe(el)
  } else {
    inView.value = true // 降级：无 IO 支持直接加载
  }
})

onBeforeUnmount(() => {
  if (elRef.value && sharedObserver) sharedObserver.unobserve(elRef.value)
  observeMap.delete(elRef.value)
})

const computedSrc = () => {
  // 未进视口：返回 undefined 让 Vue 移除 src 属性。
  // 千万不能用 ''：Chrome 对 <img src=""> 会立即触发 error 事件，
  // 把 failed 提前置位 -> 进视口后直接显示错误占位图，真实图片请求永远发不出去
  if (!inView.value) return undefined
  if (failed.value) return ERROR_SRC
  return props.src
}

const onLoad = () => { loaded.value = true }
const onError = () => { failed.value = true }
</script>

<template>
  <img
    ref="elRef"
    :src="computedSrc()"
    :alt="alt"
    class="lazy-img"
    :class="{ 'lazy-img--loaded': loaded && !failed, 'lazy-img--error': failed }"
    @load="onLoad"
    @error="onError"
  />
</template>

<style scoped>
.lazy-img {
  /* 占位背景：未进视口 / 未加载完成时显示（青山绿水浅色渐变） */
  background:
    linear-gradient(90deg, #e8f4f8 25%, #d4ecf2 50%, #e8f4f8 75%);
  background-size: 200% 100%;
  animation: lazy-shimmer 2s ease-in-out infinite;
}

.lazy-img--loaded {
  background: transparent;
  opacity: 0;
  animation: lazy-fade-in 0.5s ease-out forwards;
}

.lazy-img--error {
  animation: none;
  background: #eef1f6;
}

@keyframes lazy-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@keyframes lazy-fade-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 深色模式下占位/错误底更柔和 */
html[data-theme='dark'] .lazy-img {
  background: linear-gradient(90deg, #1d2030 25%, #252a3f 50%, #1d2030 75%);
  background-size: 200% 100%;
}
html[data-theme='dark'] .lazy-img--loaded { background: transparent; }
html[data-theme='dark'] .lazy-img--error { background: #1d2030; }
</style>
