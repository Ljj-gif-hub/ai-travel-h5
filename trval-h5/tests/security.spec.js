// @vitest-environment jsdom
/**
 * security.js 单测 — filterXss 白名单清洗 / sanitizeHtml 转义
 * 需要 jsdom 环境提供 DOMParser / Node / document
 */
import { describe, it, expect } from 'vitest'
import { filterXss, sanitizeHtml } from '../src/utils/security'

describe('filterXss 白名单清洗', () => {
  it('保留白名单标签与安全链接', () => {
    const out = filterXss('<p>你好 <strong>世界</strong></p><a href="https://example.com">链接</a>')
    expect(out).toContain('<p>你好 <strong>世界</strong></p>')
    expect(out).toContain('href="https://example.com"')
    // a 标签自动补 rel/target，防钓鱼窗口
    expect(out).toContain('rel="noopener noreferrer"')
    expect(out).toContain('target="_blank"')
  })

  it('整块删除 script 标签及其内容', () => {
    const out = filterXss('<div>正常内容<script>alert("xss")</script></div>')
    expect(out).not.toContain('script')
    expect(out).not.toContain('alert')
    expect(out).toContain('正常内容')
  })

  it('剥离事件处理器属性', () => {
    const out = filterXss('<img src="https://a.com/x.png" onerror="alert(1)">')
    expect(out).not.toContain('onerror')
    expect(out).toContain('src="https://a.com/x.png"')
  })

  it('移除危险协议链接（javascript:）', () => {
    const out = filterXss('<a href="javascript:alert(1)">点我</a>')
    expect(out).not.toContain('javascript:')
    expect(out).toContain('点我')
  })

  it('删除 svg / iframe 等白名单外标签', () => {
    const out = filterXss('<svg onload="alert(1)"></svg><iframe src="https://evil.com"></iframe><b>ok</b>')
    expect(out).not.toContain('svg')
    expect(out).not.toContain('iframe')
    expect(out).toContain('<b>ok</b>')
  })

  it('允许位图 data:image 但拒绝 data:text/html 等危险 data 协议', () => {
    const ok = filterXss('<img src="data:image/png;base64,AAAA" alt="p">')
    expect(ok).toContain('data:image/png')
    const bad = filterXss('<a href="data:text/html,<script>alert(1)</script>">x</a>')
    expect(bad).not.toContain('data:text/html')
  })

  it('非字符串输入原样返回', () => {
    expect(filterXss(null)).toBe('')
    expect(filterXss(undefined)).toBe('')
    expect(filterXss(123)).toBe(123)
  })
})

describe('sanitizeHtml 全量转义', () => {
  it('转义 HTML 特殊字符', () => {
    expect(sanitizeHtml('<script>alert(1)</script>')).toBe('&lt;script&gt;alert(1)&lt;&#x2F;script&gt;')
  })

  it('非字符串输入安全处理', () => {
    expect(sanitizeHtml(null)).toBe('')
    expect(sanitizeHtml(123)).toBe(123)
  })
})
