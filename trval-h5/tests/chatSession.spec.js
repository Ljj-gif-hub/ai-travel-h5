// @vitest-environment jsdom
/**
 * chatSession.js 单测 — 会话容量控制：
 * 1) 最多 20 个会话，按 updatedAt 淘汰最旧
 * 2) 每会话最多 200 条消息，截断最早的
 * 需要 jsdom 环境提供 localStorage
 */
import { describe, it, expect, beforeEach } from 'vitest'
import {
  createNewSession,
  saveCurrentSessionMessages,
  getAllSessions,
} from '../src/utils/chatSession'

// 未登录（无 CURRENT_USER）时存储键为固定前缀，测试可直接读原始数据
const RAW_KEY = 'travel_chat_sessions'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

beforeEach(() => {
  localStorage.clear()
})

describe('会话容量控制', () => {
  it('会话超过 20 个时按 updatedAt 淘汰最旧的', async () => {
    const ids = []
    // 每创建一个会话间隔 2ms，保证 updatedAt 严格递增、淘汰结果可确定
    for (let i = 0; i < 25; i++) {
      ids.push(createNewSession())
      await sleep(2)
    }
    const raw = JSON.parse(localStorage.getItem(RAW_KEY))
    const storedIds = Object.keys(raw.sessions)
    expect(storedIds).toHaveLength(20)
    // 最早创建的 5 个被淘汰，其余保留
    ids.slice(0, 5).forEach((id) => expect(storedIds).not.toContain(id))
    ids.slice(5).forEach((id) => expect(storedIds).toContain(id))
    // 公开 API 读到的会话数同样受上限约束
    expect(getAllSessions()).toHaveLength(20)
  })

  it('单会话超过 200 条消息时截断保留最新 200 条', () => {
    const sid = createNewSession()
    const messages = Array.from({ length: 250 }, (_, i) => ({
      id: 'm' + i,
      type: i % 2 ? 'user' : 'assistant',
      content: 'msg ' + i,
    }))
    saveCurrentSessionMessages(messages)
    const raw = JSON.parse(localStorage.getItem(RAW_KEY))
    const stored = raw.sessions[sid].messages
    expect(stored).toHaveLength(200)
    // slice(-200)：保留索引 50..249
    expect(stored[0].content).toBe('msg 50')
    expect(stored[199].content).toBe('msg 249')
  })

  it('未超上限时数据完整保留', () => {
    const sid = createNewSession()
    const messages = Array.from({ length: 10 }, (_, i) => ({ id: 'm' + i, type: 'user', content: 'c' + i }))
    saveCurrentSessionMessages(messages)
    const raw = JSON.parse(localStorage.getItem(RAW_KEY))
    expect(raw.sessions[sid].messages).toHaveLength(10)
    expect(getAllSessions()).toHaveLength(1)
  })
})
