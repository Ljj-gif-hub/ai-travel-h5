/**
 * AI对话会话持久化工具 — 多账号隔离版
 *
 * 【多账号改造】
 * - 每个账号的对话会话完全独立存储
 * - 存储键名: travel_chat_sessions:{username}
 * - 首次登录自动迁移旧数据到账号专属空间
 */

import { getCurrentUser } from './userAccountStorage'

const STORAGE_PREFIX = 'travel_chat_sessions'
let _migrated = false  // 标记是否已完成迁移

/** 获取当前账号专属的存储键名 */
function getStorageKey() {
  const user = getCurrentUser()
  return user ? `${STORAGE_PREFIX}:${user}` : STORAGE_PREFIX
}

/**
 * 【修复】首次登录时自动将旧全局会话数据迁移到账号专属空间
 * 解决"登录后之前对话记录消失"的问题
 */
function migrateIfNeeded() {
  if (_migrated) return
  const user = getCurrentUser()
  if (!user) return  // 未登录不迁移
  try {
    const oldRaw = localStorage.getItem(STORAGE_PREFIX)
    if (!oldRaw) { _migrated = true; return }
    const oldData = JSON.parse(oldRaw)
    if (!oldData.sessions || Object.keys(oldData.sessions).length === 0) {
      localStorage.removeItem(STORAGE_PREFIX)
      _migrated = true
      return
    }
    // 检查目标键是否已有数据
    const newKey = `${STORAGE_PREFIX}:${user}`
    const newRaw = localStorage.getItem(newKey)
    const newData = newRaw ? JSON.parse(newRaw) : { currentSessionId: '', sessions: {} }
    // 合并：旧会话合并到新空间（不覆盖已存在的同名session）
    Object.keys(oldData.sessions).forEach(sid => {
      if (!newData.sessions[sid]) {
        newData.sessions[sid] = oldData.sessions[sid]
      }
    })
    // 如果新空间没有当前会话，设定最后一个旧会话为当前
    if (!newData.currentSessionId || !newData.sessions[newData.currentSessionId]) {
      const lastSid = oldData.currentSessionId
      if (lastSid && newData.sessions[lastSid]) {
        newData.currentSessionId = lastSid
      }
    }
    localStorage.setItem(newKey, JSON.stringify(newData))
    localStorage.removeItem(STORAGE_PREFIX)  // 清除旧数据
  } catch { /* 迁移失败不阻塞 */ }
  _migrated = true
}

/* ==================== 内部读写 ==================== */
const readAll = () => {
  migrateIfNeeded()  // 【修复】读写前自动迁移旧数据
  try {
    const raw = localStorage.getItem(getStorageKey())
    return raw ? JSON.parse(raw) : { currentSessionId: '', sessions: {} }
  } catch { return { currentSessionId: '', sessions: {} } }
}

const writeAll = (data) => {
  migrateIfNeeded()  // 【修复】写入前自动迁移旧数据
  try { localStorage.setItem(getStorageKey(), JSON.stringify(data)) } catch { /* quota */ }
}

/* ==================== 公开API ==================== */

/** 获取当前会话ID，无则自动创建新会话 */
export function getCurrentSessionId() {
  const data = readAll()
  if (data.currentSessionId && data.sessions[data.currentSessionId]) {
    return data.currentSessionId
  }
  return createNewSession()
}

/** 设置当前活跃会话ID */
export function setCurrentSessionId(id) {
  const data = readAll()
  data.currentSessionId = id
  writeAll(data)
}

/** 创建新会话，返回新sessionId */
export function createNewSession() {
  const data = readAll()
  const id = 's_' + Date.now() + '_' + Math.random().toString(36).slice(2, 7)
  data.currentSessionId = id
  data.sessions[id] = {
    id,
    title: '新对话',
    messages: [
      { id: genMsgId(), type: 'system', content: '👋 你好！我是 AI 旅行规划师，告诉我你的旅行计划，我来帮你设计完美行程～' },
    ],
    createdAt: Date.now(),
    updatedAt: Date.now(),
  }
  writeAll(data)
  return id
}

/** 获取当前会话的全部消息 */
export function getCurrentSessionMessages() {
  const data = readAll()
  const sid = data.currentSessionId
  if (sid && data.sessions[sid]) {
    return data.sessions[sid].messages || []
  }
  return [{ id: genMsgId(), type: 'system', content: '👋 你好！我是 AI 旅行规划师，告诉我你的旅行计划，我来帮你设计完美行程～' }]
}

/** 保存当前会话消息 */
export function saveCurrentSessionMessages(messages) {
  const data = readAll()
  const sid = data.currentSessionId
  if (!sid) return
  if (!data.sessions[sid]) {
    data.sessions[sid] = { id: sid, title: '新对话', messages: [], createdAt: Date.now(), updatedAt: Date.now() }
  }
  data.sessions[sid].messages = messages
  data.sessions[sid].updatedAt = Date.now()
  const firstUser = messages.find(m => m.type === 'user')
  if (firstUser) {
    data.sessions[sid].title = firstUser.content.slice(0, 20) + (firstUser.content.length > 20 ? '...' : '')
  }
  writeAll(data)
}

/** 删除当前会话并创建新会话 */
export function clearCurrentSession() {
  const data = readAll()
  const sid = data.currentSessionId
  if (sid) delete data.sessions[sid]
  data.currentSessionId = ''
  writeAll(data)
  return createNewSession()
}

/** 获取全部历史会话列表（供消息Tab展示） */
export function getAllSessions() {
  const data = readAll()
  return Object.values(data.sessions || {}).sort((a, b) => b.updatedAt - a.updatedAt)
}

/** 切换到指定会话 */
export function switchToSession(sessionId) {
  const data = readAll()
  if (data.sessions[sessionId]) {
    setCurrentSessionId(sessionId)
    return data.sessions[sessionId].messages
  }
  return null
}

/** 删除指定会话 */
export function deleteSession(sessionId) {
  const data = readAll()
  delete data.sessions[sessionId]
  if (data.currentSessionId === sessionId) {
    data.currentSessionId = ''
  }
  writeAll(data)
}

/** 生成唯一消息ID */
export function genMsgId() {
  return 'm_' + Date.now() + '_' + Math.random().toString(36).slice(2, 7)
}
