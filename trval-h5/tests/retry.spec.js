// @vitest-environment jsdom
/**
 * api/index.js request() 增强能力单测（审查报告"可补充新功能"）：
 * 1) GET 网络错误 / 5xx 自动重试（最多 2 次，退避 300/900ms）
 * 2) 非 GET 不重试；4xx 不重试；AbortError / 超时不重试
 * 3) 401 单飞刷新 refreshToken + 原请求重放一次；刷新失败清会话跳登录
 * 4) GET in-flight 去重（并发共享 promise，TTL 后允许重新发起）
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { request } from '../src/api/index.js'

const okData = (data = { ok: 1 }) => ({ status: 200, ok: true, json: async () => ({ code: 0, data }) })
const httpError = (status) => ({ status, ok: false, json: async () => { throw new Error('not json') } })
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

let fetchMock

beforeEach(() => {
  sessionStorage.clear()
  localStorage.clear()
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('GET 自动重试（网络错误 / 5xx）', () => {
  it('5xx 最多重试 2 次后成功（共 3 次请求，含退避）', async () => {
    fetchMock
      .mockResolvedValueOnce(httpError(500))
      .mockResolvedValueOnce(httpError(503))
      .mockResolvedValueOnce(okData())
    const start = Date.now()
    const res = await request('/retry-5xx')
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(res.code).toBe(0)
    // 退避 300 + 900 = 1200ms
    expect(Date.now() - start).toBeGreaterThanOrEqual(1200)
  })

  it('网络错误（TypeError）同样重试', async () => {
    fetchMock
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(okData())
    const res = await request('/retry-net')
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(res.code).toBe(0)
  })

  it('重试 2 次仍失败时抛出最后一次错误', async () => {
    fetchMock
      .mockResolvedValueOnce(httpError(500))
      .mockResolvedValueOnce(httpError(500))
      .mockResolvedValueOnce(httpError(502))
    await expect(request('/retry-fail')).rejects.toMatchObject({ status: 502 })
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('非 GET 请求不重试（幂等保护）', async () => {
    fetchMock.mockResolvedValueOnce(httpError(500))
    await expect(request('/post-fail', { method: 'POST', body: '{}' })).rejects.toMatchObject({ status: 500 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('4xx 不重试（业务错误交给调用方）', async () => {
    fetchMock.mockResolvedValueOnce(httpError(400))
    await expect(request('/bad-request')).rejects.toMatchObject({ status: 400 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('AbortError（主动取消）不重试', async () => {
    const ac = new AbortController()
    ac.abort()
    fetchMock.mockImplementation((url, init) => {
      if (init.signal?.aborted) {
        return Promise.reject(Object.assign(new Error('aborted'), { name: 'AbortError' }))
      }
      return Promise.resolve(okData())
    })
    await expect(request('/aborted', { signal: ac.signal })).rejects.toMatchObject({ name: 'AbortError' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('请求超时不重试（timeout 标记）', async () => {
    fetchMock.mockImplementation((url, init) => new Promise((_, reject) => {
      init.signal.addEventListener('abort', () => {
        reject(Object.assign(new Error('aborted'), { name: 'AbortError' }))
      })
    }))
    await expect(request('/timeout', { timeout: 30 })).rejects.toMatchObject({ message: '请求超时', timeout: true })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('401 单飞刷新 Token', () => {
  it('401 → 刷新成功 → 用新 token 重放原请求一次', async () => {
    sessionStorage.setItem('TOKEN', 't1')
    sessionStorage.setItem('REFRESH_TOKEN', 'rt1')
    fetchMock.mockImplementation((url, init) => {
      const u = String(url)
      if (u.includes('/auth/refresh')) {
        const body = JSON.parse(init.body)
        expect(body.refreshToken).toBe('rt1')
        return Promise.resolve({ status: 200, ok: true, json: async () => ({ code: 0, data: { token: 't2', refreshToken: 'rt2' } }) })
      }
      if (u.includes('/auth/data')) {
        if (init.headers.Authorization === 'Bearer t1') return Promise.resolve(httpError(401))
        expect(init.headers.Authorization).toBe('Bearer t2') // 重放时携带新 token
        return Promise.resolve(okData())
      }
      return Promise.resolve(httpError(404))
    })
    const res = await request('/auth/data')
    expect(res.code).toBe(0)
    expect(fetchMock).toHaveBeenCalledTimes(3)
    // 旋转刷新：新 token / 新 refreshToken 已入库
    expect(sessionStorage.getItem('TOKEN')).toBe('t2')
    expect(sessionStorage.getItem('REFRESH_TOKEN')).toBe('rt2')
  })

  it('刷新失败（refresh 401）→ 清会话并跳登录页', async () => {
    sessionStorage.setItem('TOKEN', 't1')
    sessionStorage.setItem('REFRESH_TOKEN', 'rt1')
    fetchMock.mockImplementation((url) => {
      const u = String(url)
      if (u.includes('/auth/refresh')) return Promise.resolve(httpError(401))
      return Promise.resolve(httpError(401))
    })
    await expect(request('/auth/data2')).rejects.toMatchObject({ status: 401 })
    // 刷新仅一次（单飞），业务请求一次，不重放
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(sessionStorage.getItem('TOKEN')).toBeNull()
    expect(sessionStorage.getItem('REFRESH_TOKEN')).toBeNull()
    expect(window.location.hash).toContain('/login')
  })

  it('登录等豁免接口自身 401 不触发刷新流程', async () => {
    fetchMock.mockResolvedValue(httpError(401))
    await expect(request('/auth/login', { method: 'POST', body: '{}' })).rejects.toMatchObject({ status: 401 })
    expect(fetchMock).toHaveBeenCalledTimes(1) // 未发起 /auth/refresh
  })
})

describe('GET in-flight 去重', () => {
  it('并发相同请求共享同一 promise，仅发一次网络请求', async () => {
    fetchMock.mockImplementation(() => new Promise((r) => setTimeout(() => r(okData()), 30)))
    const p1 = request('/dedup', { params: { page: 1 } })
    const p2 = request('/dedup', { params: { page: 1 } })
    const [r1, r2] = await Promise.all([p1, p2])
    // request 为 async 包装，外层 promise 引用不同；共享体现在：只发一次请求、结果同引用
    expect(r1.code).toBe(0)
    expect(r1).toBe(r2)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('不同参数不共享；TTL 过期后允许重新发起', async () => {
    fetchMock.mockResolvedValue(okData())
    await request('/dedup-ttl', { params: { page: 1 } })
    await request('/dedup-ttl', { params: { page: 2 } }) // 参数不同 → 新请求
    expect(fetchMock).toHaveBeenCalledTimes(2)
    await sleep(250) // 等待 DEDUP_TTL(200ms) 过期
    await request('/dedup-ttl', { params: { page: 1 } }) // 相同请求重新允许
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })
})
