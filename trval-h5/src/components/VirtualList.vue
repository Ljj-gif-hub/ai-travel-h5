<script setup>
/**
 * VirtualList.vue — 通用虚拟滚动组件（审查报告"可补充新功能"）
 *
 * 仅渲染可视区 + overscan 的项，用绝对定位幻影高度 + translateY 定位窗口。
 * - 固定高度：传 itemHeight，零测量开销
 * - 动态高度：传 estimatedItemHeight，渲染后用 ResizeObserver 实测修正偏移
 *   （高度变化 → rAF 批量重建偏移 → emit('height-change') 供父级做滚动锚定）
 *
 * 用法：
 *   <VirtualList :items="list" :item-key="'id'" :estimated-item-height="72" :gap="18"
 *                :scroll-top="scrollTop" :viewport-height="viewportH" @height-change="...">
 *     <template #default="{ item, index }"> ...渲染单项... </template>
 *   </VirtualList>
 *
 * 滚动由外层滚动容器负责（组件只渲染占位），scrollTop/viewportHeight 由父级透传。
 */
import { ref, shallowRef, computed, watch, onBeforeUnmount } from 'vue'
import { buildOffsets, findWindow } from '../utils/virtualList'

const props = defineProps({
  items: { type: Array, required: true },
  /** 固定行高（提供后关闭 ResizeObserver 测量） */
  itemHeight: { type: Number, default: null },
  /** 估算行高（动态高度模式用，实测后修正） */
  estimatedItemHeight: { type: Number, default: 72 },
  /** 项间距（px） */
  gap: { type: Number, default: 0 },
  /** 上下各多渲染的项数 */
  overscan: { type: Number, default: 5 },
  /** 取 item 唯一 key 的字段名（如 'id'）；缺省用下标 */
  itemKey: { type: String, default: '' },
  /** 外层滚动容器的 scrollTop（父级透传，响应式） */
  scrollTop: { type: Number, default: 0 },
  /** 外层滚动容器视口高度 */
  viewportHeight: { type: Number, default: 0 },
})

const emit = defineEmits(['height-change'])

/* ---------------- 高度表与偏移 ---------------- */
const heights = shallowRef([])
// 偏移重建版本号：heights 是 plain 数组，用版本号驱动 computed 重算
const layoutVersion = ref(0)
const offsets = ref([])
const totalHeight = ref(0)

let rafId = null
function rebuildLayout() {
  cancelAnimationFrame(rafId)
  rafId = requestAnimationFrame(() => {
    const { offsets: offs, total } = buildOffsets(heights.value, props.gap)
    offsets.value = offs
    totalHeight.value = total
    layoutVersion.value += 1
    emit('height-change', total)
  })
}

function initHeights(n) {
  const fixed = props.itemHeight
  const arr = new Array(n)
  for (let i = 0; i < n; i++) arr[i] = fixed != null ? fixed : props.estimatedItemHeight
  heights.value = arr
  rebuildLayout()
}

// items 数组换引用（整体替换，如从存储恢复）→ 高度表整体重置
watch(
  () => props.items,
  (list) => {
    if (list !== prevItems) {
      prevItems = list
      initHeights(list.length)
    }
  }
)
// 原地变更（push 新消息）→ 高度表向后追加；截断（清空对话）→ 重置
watch(
  () => props.items.length,
  (len) => {
    const h = heights.value
    if (len > h.length) {
      const fixed = props.itemHeight
      for (let i = h.length; i < len; i++) {
        h[i] = fixed != null ? fixed : props.estimatedItemHeight
      }
      rebuildLayout()
    } else if (len < h.length) {
      initHeights(len)
    }
  }
)
let prevItems = props.items
initHeights(props.items.length)

// 固定/估算高度切换 → 重建
watch(() => props.itemHeight, () => initHeights(props.items.length))

/* ---------------- ResizeObserver 实测修正（仅动态高度模式） ---------------- */
let ro = null
const measureMap = new Map() // el -> index

function ensureRO() {
  if (ro || props.itemHeight != null || typeof ResizeObserver === 'undefined') return
  ro = new ResizeObserver((entries) => {
    let dirty = false
    for (const entry of entries) {
      const idx = measureMap.get(entry.target)
      if (idx == null) continue
      const h = Math.round(entry.borderBoxSize?.[0]?.blockSize || entry.contentRect.height)
      if (h > 0 && heights.value[idx] !== h) {
        heights.value[idx] = h
        dirty = true
      }
    }
    if (dirty) rebuildLayout()
  })
}

function setItemRef(el, index) {
  if (!el) {
    // 卸载：vue 会给 null 调用清理——需要先从 map 找 index
    for (const [k, v] of measureMap) { if (v === index) { measureMap.delete(k); ro?.unobserve(k) } }
    return
  }
  ensureRO()
  if (ro && !measureMap.has(el)) {
    measureMap.set(el, index)
    ro.observe(el)
  }
}

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  ro?.disconnect()
  ro = null
  measureMap.clear()
})

/* ---------------- 可视窗口计算 ---------------- */
const window = computed(() => {
  layoutVersion.value // 依赖：偏移重建后重算
  const top = props.scrollTop
  const vh = props.viewportHeight
  if (vh <= 0) return { start: 0, end: 0, offsetY: 0, list: [] }
  const { start, end, offsetY } = findWindow(offsets.value, heights.value, top, vh, props.overscan)
  const list = []
  for (let i = start; i < end; i++) {
    const item = props.items[i]
    if (item === undefined) break
    list.push({
      key: props.itemKey && item != null ? item[props.itemKey] : i,
      index: i,
      data: item,
    })
  }
  return { start, end, offsetY, list }
})
</script>

<template>
  <div
    class="virtual-list"
    :style="{ height: totalHeight + 'px', position: 'relative' }"
    aria-hidden="true"
  >
    <div
      class="virtual-list-window"
      :style="{ transform: `translateY(${window.offsetY}px)` }"
    >
      <div
        v-for="item in window.list"
        :key="item.key"
        class="virtual-list-item"
        :ref="(el) => setItemRef(el, item.index)"
        :style="gap > 0 ? { marginBottom: gap + 'px' } : undefined"
      >
        <slot :item="item.data" :index="item.index" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.virtual-list { width: 100%; }
.virtual-list-window { position: relative; width: 100%; }
.virtual-list-item { width: 100%; }
</style>
