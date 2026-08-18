// @vitest-environment jsdom
/**
 * envCheck.js 单测：
 * - 变量齐全 → 不告警、原值透传
 * - 变量缺失/空串 → 记录 missing、使用默认值、告警一次（含变量名）
 * - prod 静默（runEnvCheck 挂到 import.meta.env，此处验证 checkEnv 的 hooks 注入）
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { checkEnv, runEnvCheck, REQUIRED_ENV } from '../src/utils/envCheck'

describe('checkEnv 纯函数', () => {
  it('变量齐全：不告警，原值透传', () => {
    const warn = vi.fn()
    const { missing, resolved } = checkEnv(
      { VITE_API_BASE: 'https://api.example.com', VITE_IMAGE_API: '/img', VITE_BAIDU_MAP_AK: 'ak123' },
      { warn }
    )
    expect(missing).toEqual([])
    expect(resolved.VITE_API_BASE).toBe('https://api.example.com')
    expect(resolved.VITE_BAIDU_MAP_AK).toBe('ak123')
    expect(warn).not.toHaveBeenCalled()
  })

  it('变量缺失：missing 记录 + 默认值降级 + 告警含变量名', () => {
    const warn = vi.fn()
    const { missing, resolved } = checkEnv({ VITE_API_BASE: 'https://x.com' }, { warn })
    expect(missing).toEqual(['VITE_IMAGE_API', 'VITE_BAIDU_MAP_AK'])
    expect(resolved.VITE_API_BASE).toBe('https://x.com')
    expect(resolved.VITE_IMAGE_API).toBe('') // 使用默认值
    expect(resolved.VITE_BAIDU_MAP_AK).toBe('')
    expect(warn).toHaveBeenCalledTimes(1)
    expect(warn.mock.calls[0][0]).toContain('VITE_BAIDU_MAP_AK')
  })

  it('空字符串视为缺失；undefined/null 视为缺失', () => {
    const warn = vi.fn()
    const { missing } = checkEnv(
      { VITE_API_BASE: '  ', VITE_IMAGE_API: undefined, VITE_BAIDU_MAP_AK: null },
      { warn }
    )
    expect(missing).toEqual(REQUIRED_ENV.map((i) => i.key))
  })

  it('prod 场景静默降级（hooks.warn 注入空操作）', () => {
    const warn = vi.fn()
    const { missing } = checkEnv({}, { warn: () => {} })
    expect(missing.length).toBe(REQUIRED_ENV.length)
    expect(warn).not.toHaveBeenCalled()
  })
})

describe('runEnvCheck 启动入口', () => {
  let warnSpy
  beforeEach(() => { warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {}) })
  afterEach(() => { warnSpy.mockRestore() })

  it('不抛异常并返回结构化结果', () => {
    const result = runEnvCheck()
    expect(result).toHaveProperty('missing')
    expect(result).toHaveProperty('resolved')
    // dev 模式暴露调试结果
    expect(window.__envCheckResult).toBe(result)
  })
})
