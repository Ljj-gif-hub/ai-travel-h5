// @vitest-environment jsdom
/**
 * useRequest 组合式函数单测（纯逻辑，不挂载组件）：
 * - loading/error/data 三态流转
 * - AbortError 静默（主动取消不写入 error）
 * - 最新请求胜出：旧请求的迟到响应不覆盖新状态
 * - cancel() 真正 abort 在途请求（signal 传递）
 */
import { describe, it, expect, vi } from 'vitest'
import { nextTick } from 'vue'
import { useRequest } from '../src/composables/useRequest'

const deferred = () => {
  let resolve, reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

describe('useRequest 三态', () => {
  it('成功：loading → data，loading 复位', async () => {
    const d = deferred()
    const fn = vi.fn(() => d.promise)
    const { data, loading, error, run } = useRequest(fn, { manual: true })
    expect(loading.value).toBe(false)

    const p = run('arg1')
    expect(fn).toHaveBeenCalledWith('arg1', { signal: expect.any(AbortSignal) })
    expect(loading.value).toBe(true)
    expect(error.value).toBeNull()

    d.resolve({ code: 0 })
    await p
    expect(data.value).toEqual({ code: 0 })
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('失败：error 记录异常，loading 复位，异常向上抛出', async () => {
    const fn = vi.fn(() => Promise.reject(new Error('boom')))
    const { loading, error, run } = useRequest(fn, { manual: true })
    await expect(run()).rejects.toThrow('boom')
    expect(error.value).toMatchObject({ message: 'boom' })
    expect(loading.value).toBe(false)
  })

  it('AbortError 静默：返回 undefined、不写入 error', async () => {
    const fn = vi.fn(() => Promise.reject(Object.assign(new Error('aborted'), { name: 'AbortError' })))
    const { loading, error, run } = useRequest(fn, { manual: true })
    const res = await run()
    expect(res).toBeUndefined()
    expect(error.value).toBeNull()
    expect(loading.value).toBe(false)
  })

  it('manual=false 时自动发起请求', async () => {
    const fn = vi.fn(() => Promise.resolve(42))
    const { data } = useRequest(fn)
    await nextTick()
    expect(fn).toHaveBeenCalledTimes(1)
    expect(data.value).toBe(42)
  })
})

describe('竞态与取消', () => {
  it('最新请求胜出：旧请求迟到响应不覆盖新 data', async () => {
    const d1 = deferred()
    const d2 = deferred()
    const calls = [d1, d2]
    const fn = vi.fn(() => calls.shift().promise)
    const { data, loading, run } = useRequest(fn, { manual: true })

    const p1 = run() // 旧请求（挂起）
    const p2 = run() // 新请求
    d2.resolve('new')
    await p2
    expect(data.value).toBe('new')

    d1.resolve('old') // 旧请求迟到
    await p1
    expect(data.value).toBe('new') // 不被旧响应覆盖
    expect(loading.value).toBe(false)
  })

  it('cancel() 中止在途请求（signal.aborted = true）', async () => {
    const fn = vi.fn((opts) => new Promise((_, reject) => {
      opts.signal.addEventListener('abort', () => {
        reject(Object.assign(new Error('aborted'), { name: 'AbortError' }))
      })
    }))
    const { loading, error, run, cancel } = useRequest(fn, { manual: true })
    const p = run()
    expect(loading.value).toBe(true)
    cancel()
    await p
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull() // 主动取消静默
  })
})
