// @vitest-environment jsdom
/**
 * virtualList.js 纯逻辑单测（虚拟滚动定位计算）：
 * buildOffsets — 累计偏移/总高度（含 gap，末项不加 gap）
 * findWindow — 可视窗口二分定位 + overscan 缓冲
 */
import { describe, it, expect } from 'vitest'
import { buildOffsets, findWindow } from '../src/utils/virtualList'

describe('buildOffsets', () => {
  it('累计偏移与总高度（gap 加在每项之后，末项不加）', () => {
    const { offsets, total } = buildOffsets([10, 20, 30], 5)
    expect(offsets).toEqual([0, 15, 40])
    expect(total).toBe(70)
  })

  it('gap = 0 时总高度为高度之和', () => {
    const { offsets, total } = buildOffsets([50, 50, 50], 0)
    expect(offsets).toEqual([0, 50, 100])
    expect(total).toBe(150)
  })

  it('空列表：偏移为空、总高度 0', () => {
    const { offsets, total } = buildOffsets([], 5)
    expect(offsets).toEqual([])
    expect(total).toBe(0)
  })
})

describe('findWindow', () => {
  const offsets = [0, 50, 100, 150, 200]
  const heights = [50, 50, 50, 50, 50]

  it('滚动中部：二分定位 start，含 overscan 缓冲', () => {
    // scrollTop=120, viewport=100 → 可视 [120,220) 覆盖第 3、4 项（含部分第 5 项起点）
    const w = findWindow(offsets, heights, 120, 100, 1)
    expect(w).toEqual({ start: 1, end: 5, offsetY: 50 })
  })

  it('顶部：无缓冲时不越界', () => {
    const w = findWindow(offsets, heights, 0, 100, 0)
    expect(w).toEqual({ start: 0, end: 2, offsetY: 0 })
  })

  it('底部：end 收敛到列表末尾', () => {
    const w = findWindow(offsets, heights, 200, 100, 2)
    expect(w).toEqual({ start: 2, end: 5, offsetY: 100 })
  })

  it('空列表：返回零窗口', () => {
    expect(findWindow([], [], 0, 100, 0)).toEqual({ start: 0, end: 0, offsetY: 0 })
  })
})
