<template>
  <div class="drag-sheet-wrapper">
    <!-- 半透明遮罩，仅在半展开以上时显示 -->
    <div
      class="drag-sheet-overlay"
      :style="{ opacity: overlayOpacity * (currentPercent / maxHeight) }"
      @click="snapTo('min')"
      v-show="currentPercent > minHeight"
    />

    <!-- 可拖拽抽屉面板 -->
    <div
      class="drag-sheet"
      ref="sheetRef"
      :style="sheetStyle"
    >
      <!-- 拖拽手柄指示条 -->
      <div class="drag-handle-area">
        <div class="drag-handle-bar" />
      </div>

      <!-- 可滚动内容区 -->
      <div class="drag-sheet-content" ref="contentRef" @scroll="onScroll">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * DragSheet.vue — 三段式底部抽屉 (v3)
 *
 * 修复历史:
 *   v3: Touch Events + 延迟激活 + 内容滚动隔离
 *   v2: Pointer Events (移动端兼容性差，废弃)
 *   v1: 原始版本 (shouldDrag 状态机卡死)
 */
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: 'mid' },
  minHeight: { type: Number, default: 20 },
  midHeight: { type: Number, default: 65 },
  maxHeight: { type: Number, default: 95 },
  overlayOpacity: { type: Number, default: 0.5 },
})

const emit = defineEmits(['update:modelValue', 'state-change'])

const sheetRef = ref(null)
const contentRef = ref(null)

const currentPercent = ref(props.midHeight)
const isDragging = ref(false)
const contentScrollTop = ref(0)

const SNAP_POINTS = { min: props.minHeight, mid: props.midHeight, max: props.maxHeight }

function nearestSnap(pct) {
  let best = 'mid', bestDist = Infinity
  for (const [k, v] of Object.entries(SNAP_POINTS)) {
    const d = Math.abs(pct - v)
    if (d < bestDist) { bestDist = d; best = k }
  }
  return best
}

const sheetStyle = computed(() => ({
  transform: `translateY(${100 - currentPercent.value}%)`,
  transition: isDragging.value ? 'none' : 'transform 0.35s cubic-bezier(0.4, 0, 0.2, 1)',
}))

function snapTo(state) {
  const target = SNAP_POINTS[state]
  if (target === undefined) return
  isDragging.value = false
  currentPercent.value = target
  emit('update:modelValue', state)
  emit('state-change', state)
  if (state === 'min' && contentRef.value) contentRef.value.scrollTop = 0
}

/* ==================== Touch 拖拽 ==================== */
let touchId = null       // 追踪的 touch identifier
let startY = 0
let startPercent = 0
let hasMoved = false     // 本次触摸序列中是否已经产生过有效拖拽
let dragActivated = false // 拖拽模式是否已激活

function onTouchStart(e) {
  // 只在没有活跃拖拽时记录新的触摸
  if (touchId !== null) return
  const t = e.changedTouches[0]
  touchId = t.identifier
  startY = t.clientY
  startPercent = currentPercent.value
  hasMoved = false
  dragActivated = false
}

function onTouchMove(e) {
  // 找到我们追踪的 touch
  let t = null
  for (let i = 0; i < e.changedTouches.length; i++) {
    if (e.changedTouches[i].identifier === touchId) { t = e.changedTouches[i]; break }
  }
  if (!t) return

  const deltaY = startY - t.clientY   // 手指向上 → 正, 向下 → 负
  hasMoved = true

  // ====== 判断是否应该激活拖拽 ======
  if (!dragActivated) {
    const absDelta = Math.abs(startY - t.clientY)

    // 移动太少 → 不激活
    if (absDelta < 6) return

    // 场景1: 抽屉在最大档(95%)，内容还能向上滚(scrollTop>2) → 不激活拖拽，让内容滚动
    if (contentScrollTop.value > 2 && currentPercent.value >= props.maxHeight - 1) {
      return
    }

    // 场景2: 抽屉在最小档(20%)，手指往上推 → 激活拖拽（展开抽屉）
    if (currentPercent.value <= props.minHeight + 0.5) {
      if (deltaY > 0) {
        // 往上推 → 激活，展开抽屉
        dragActivated = true
      } else {
        // 往下拉但已最小 → 不激活
        return
      }
    }
    // 场景3: 其他情况 → 激活拖拽
    else {
      dragActivated = true
    }

    // 首次激活：重新校准起点
    if (dragActivated) {
      startY = t.clientY
      startPercent = currentPercent.value
      isDragging.value = true
      return
    }
  }

  // ====== 拖拽进行中 ======
  if (!dragActivated) return

  // 阻止浏览器默认滚动（仅在事件可取消时）
  if (e.cancelable) {
    e.preventDefault()
  }

  const moveDelta = startY - t.clientY
  let newPercent = startPercent + (moveDelta / window.innerHeight) * 100
  // 边界钳制
  newPercent = Math.max(props.minHeight, Math.min(props.maxHeight, newPercent))
  currentPercent.value = Math.round(newPercent)
}

function onTouchEnd(e) {
  // 找到我们追踪的 touch
  let found = false
  for (let i = 0; i < e.changedTouches.length; i++) {
    if (e.changedTouches[i].identifier === touchId) { found = true; break }
  }
  if (!found) return

  const wasDragging = dragActivated

  // 清理状态
  touchId = null
  isDragging.value = false
  dragActivated = false
  hasMoved = false

  if (wasDragging) {
    snapTo(nearestSnap(currentPercent.value))
  }
}

function onScroll() {
  if (contentRef.value) {
    contentScrollTop.value = contentRef.value.scrollTop
  }
}

/* ==================== 外部同步 ==================== */
watch(() => props.modelValue, (val) => {
  const target = SNAP_POINTS[val]
  if (target !== undefined && Math.abs(currentPercent.value - target) > 1) {
    isDragging.value = false
    currentPercent.value = target
  }
})

onMounted(() => {
  const target = SNAP_POINTS[props.modelValue]
  if (target !== undefined) currentPercent.value = target

  // 手动绑定 touch 事件（需要 passive:false 才能在 drag 时 preventDefault）
  const el = sheetRef.value
  if (el) {
    el.addEventListener('touchstart', onTouchStart, { passive: true })
    el.addEventListener('touchmove', onTouchMove, { passive: false })
    el.addEventListener('touchend', onTouchEnd, { passive: true })
    el.addEventListener('touchcancel', onTouchEnd, { passive: true })
  }
})

onUnmounted(() => {
  touchId = null
  isDragging.value = false

  const el = sheetRef.value
  if (el) {
    el.removeEventListener('touchstart', onTouchStart)
    el.removeEventListener('touchmove', onTouchMove)
    el.removeEventListener('touchend', onTouchEnd)
    el.removeEventListener('touchcancel', onTouchEnd)
  }
})

defineExpose({ snapTo, setState: snapTo })
</script>

<style scoped>
.drag-sheet-wrapper {
  position: fixed;
  inset: 0;
  z-index: 100;
  pointer-events: none;
}

.drag-sheet-overlay {
  position: absolute;
  inset: 0;
  background: #000;
  transition: opacity 0.35s ease;
  pointer-events: auto;
}

.drag-sheet {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 100%;
  background: #fff;
  border-top-left-radius: 20px;
  border-top-right-radius: 20px;
  box-shadow: 0 -6px 30px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  pointer-events: auto;
  will-change: transform;
}

.drag-handle-area {
  height: 36px;
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  /* 只禁止手柄区域的浏览器手势，内容区保持可滚动 */
  touch-action: none;
}

.drag-handle-bar {
  width: 36px;
  height: 5px;
  background: #d1d5db;
  border-radius: 3px;
}

.drag-sheet-content {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
}
</style>
