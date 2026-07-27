/**
 * userAccountStorage.js — 多账号独立数据隔离工具
 *
 * 设计原则：
 * - 每个账号以用户名作为唯一标识，拥有完全独立的 localStorage 存储空间
 * - 切换账号后数据完全隔离，互不可见
 * - 退出登录仅清空会话缓存，持久化数据完整保留
 *
 * 存储结构（localStorage）:
 *   TOKEN            → 当前登录账号的 JWT Token
 *   CURRENT_USER     → 当前登录账号的用户名
 *   account:{user}   → 该账号的全部持久化数据（JSON 对象）:
 *     {
 *       userInfo: { ... },           // 用户基础信息
 *       chatSessions: { ... },       // AI 对话会话
 *       chatHistory: [ ... ],        // AI 对话历史
 *       savedPlans: [ ... ],         // 已保存行程
 *       favorites: [ ... ],          // 收藏列表
 *       browsingHistory: [ ... ],    // 浏览记录
 *     }
 *
 * 公开 API：
 *   getCurrentUser()                  → 获取当前登录用户名
 *   initAccountData(username)         → 为新注册账号创建空白数据空间
 *   accountExists(username)           → 检查账号是否已存在
 *   getAccountData(username, key)     → 读取指定账号的指定数据字段
 *   setAccountData(username, key, val)→ 写入指定账号的指定数据字段
 *   getMyData(key)                    → 读取当前登录账号的数据（便捷方法）
 *   setMyData(key, val)               → 写入当前登录账号的数据（便捷方法）
 *   clearSession()                    → 退出登录：清除 Token + 当前用户标记
 *   deleteAccount(username)           → 彻底删除账号及所有数据
 */

const CURRENT_USER_KEY = 'CURRENT_USER'

/* ==================== 当前登录账号管理 ==================== */

/** 获取当前登录用户名 */
export function getCurrentUser() {
  try {
    return localStorage.getItem(CURRENT_USER_KEY) || ''
  } catch {
    return ''
  }
}

/** 设置当前登录用户名 */
export function setCurrentUser(username) {
  try {
    if (username) {
      localStorage.setItem(CURRENT_USER_KEY, username)
    } else {
      localStorage.removeItem(CURRENT_USER_KEY)
    }
  } catch { /* quota */ }
}

/** 检查该账号是否已有数据 */
export function accountExists(username) {
  if (!username) return false
  try {
    return localStorage.getItem(buildKey(username)) !== null
  } catch {
    return false
  }
}

/* ==================== 账号数据读写 ==================== */

/** 构建存储键名 */
function buildKey(username) {
  return `account:${username}`
}

/** 读取账号完整数据 */
function readAccount(username) {
  if (!username) return {}
  try {
    const raw = localStorage.getItem(buildKey(username))
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

/** 写入账号完整数据 */
function writeAccount(username, data) {
  if (!username) return
  try {
    localStorage.setItem(buildKey(username), JSON.stringify(data))
  } catch (e) {
    console.warn('账号数据写入失败（存储空间可能已满）:', e)
  }
}

/**
 * 为新注册账号初始化空白数据空间
 * 如果账号已存在，返回 false 表示用户名已被占用
 */
export function initAccountData(username) {
  if (!username) return false
  if (accountExists(username)) return false // 账号已存在，拒绝覆盖
  writeAccount(username, {
    userInfo: {
      nickname: username,
      username: username,
      avatar: '',
      level: '普通会员',
      points: 0,
      following: 0,
      followers: 0,
      travelNotes: 0,
      bio: '',
      citiesVisited: 0,
      totalDays: 0,
      totalSpent: 0,
      totalPhotos: 0,
    },
    chatSessions: null,    // 由 chatSession.js 按需初始化
    chatHistory: [],
    savedPlans: [],
    favorites: [],
    browsingHistory: [],
    createdAt: Date.now(),
  })
  return true
}

/**
 * 读取指定账号的指定数据字段
 * @param {string} username - 用户名
 * @param {string} key - 数据字段名（如 'userInfo', 'savedPlans' 等）
 * @returns {*} 对应数据，不存在时返回 undefined
 */
export function getAccountData(username, key) {
  if (!username || !key) return undefined
  const data = readAccount(username)
  return data[key]
}

/**
 * 写入指定账号的指定数据字段
 * @param {string} username - 用户名
 * @param {string} key - 数据字段名
 * @param {*} value - 要存储的值
 */
export function setAccountData(username, key, value) {
  if (!username || !key) return
  const data = readAccount(username)
  data[key] = value
  writeAccount(username, data)
}

/* ==================== 当前账号便捷方法 ==================== */

/**
 * 读取当前登录账号的指定数据
 * @param {string} key - 数据字段名
 */
export function getMyData(key) {
  return getAccountData(getCurrentUser(), key)
}

/**
 * 写入当前登录账号的指定数据
 * @param {string} key - 数据字段名
 * @param {*} value - 要存储的值
 */
export function setMyData(key, value) {
  setAccountData(getCurrentUser(), key, value)
}

/* ==================== 会话管理 ==================== */

/**
 * 退出登录：清除 Token + 当前用户标记
 * 账号持久化数据完整保留，切换账号时数据不受影响
 */
export function clearSession() {
  try {
    localStorage.removeItem(CURRENT_USER_KEY)
    localStorage.removeItem('TOKEN')
    // 清除临时页面缓存
    localStorage.removeItem('redirectUrl')
  } catch { /* noop */ }
}

/**
 * 彻底删除账号及所有数据（谨慎使用）
 */
export function deleteAccount(username) {
  if (!username) return
  try {
    localStorage.removeItem(buildKey(username))
    // 如果是当前登录账号，同时清除会话
    if (getCurrentUser() === username) {
      clearSession()
    }
  } catch { /* noop */ }
}

export default {
  getCurrentUser,
  setCurrentUser,
  accountExists,
  initAccountData,
  getAccountData,
  setAccountData,
  getMyData,
  setMyData,
  clearSession,
  deleteAccount,
}
