/**
 * auth.js — JWT Token + 当前用户管理
 *
 * 【多账号隔离改造】
 * - Token 按账号存储（key: TOKEN）
 * - 同时记录当前登录用户名（key: CURRENT_USER）
 * - 退出登录时由 userAccountStorage.clearSession() 统一清理
 */
import { getCurrentUser } from './userAccountStorage'

const TokenKey = 'TOKEN'

export function getToken() {
  try {
    return localStorage.getItem(TokenKey)
  } catch {
    return null
  }
}

export function setToken(token) {
  try {
    return localStorage.setItem(TokenKey, token)
  } catch {
    return false
  }
}

export function removeToken() {
  try {
    return localStorage.removeItem(TokenKey)
  } catch {
    return false
  }
}

/**
 * 获取当前登录用户名
 * 优先从 localStorage CURRENT_USER 读取，兼容旧版 userInfo 解析
 */
export function getCurrentUsername() {
  try {
    // 新方案：从 CURRENT_USER 直接读取
    const user = localStorage.getItem('CURRENT_USER')
    if (user) return user
    // 兼容旧数据：从 userInfo 中解析
    const raw = localStorage.getItem('userInfo')
    if (raw) {
      const info = JSON.parse(raw)
      if (info && info.username) return info.username
    }
    return ''
  } catch {
    return ''
  }
}

export default {
  getToken,
  setToken,
  removeToken,
  getCurrentUsername,
}
