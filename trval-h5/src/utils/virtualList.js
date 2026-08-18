/**
 * virtualList.js — 虚拟滚动纯逻辑（可单测）
 * 与 components/VirtualList.vue 配套：定位计算全部收敛于此，DOM 无关。
 */

/**
 * 由每项高度构建累计偏移（含间距 gap）。
 * @param {number[]} heights 每项高度（实测或估算）
 * @param {number} gap 项间距（px，加在每项之后；最后一项不追加）
 * @returns {{ offsets: number[], total: number }}
 */
export function buildOffsets(heights, gap = 0) {
  const offsets = new Array(heights.length)
  let acc = 0
  for (let i = 0; i < heights.length; i++) {
    offsets[i] = acc
    acc += heights[i] + gap
  }
  return { offsets, total: heights.length ? acc - gap : 0 }
}

/** 二分：第一个「底部越过 target」的项（offsets[mid] + heights[mid] > target） */
function lowerBound(offsets, heights, target) {
  let lo = 0
  let hi = offsets.length
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    if (offsets[mid] + heights[mid] <= target) lo = mid + 1
    else hi = mid
  }
  return lo
}

/**
 * 计算可视窗口（含 overscan 缓冲）。
 * @param {number[]} offsets 累计偏移
 * @param {number[]} heights 每项高度
 * @param {number} scrollTop 当前滚动位置
 * @param {number} viewport 视口高度
 * @param {number} overscan 上下各多渲染的项数
 * @returns {{ start: number, end: number, offsetY: number }} [start, end) 半开区间
 */
export function findWindow(offsets, heights, scrollTop, viewport, overscan = 0) {
  const n = offsets.length
  if (n === 0) return { start: 0, end: 0, offsetY: 0 }
  const endScroll = scrollTop + viewport
  let start = lowerBound(offsets, heights, scrollTop)
  let end = start
  while (end < n && offsets[end] < endScroll) end += 1
  start = Math.max(0, start - overscan)
  end = Math.min(n, end + overscan)
  return { start, end, offsetY: offsets[start] }
}
