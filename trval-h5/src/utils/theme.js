/**
 * theme.js — 深色模式主题管理（B4）
 *
 * 三种模式：
 *   'light'  — 强制浅色
 *   'dark'   — 强制深色
 *   'system' — 跟随系统 prefers-color-scheme（默认）
 *
 * 优先级：localStorage.theme → 系统设置。
 * 切换后设置 <html data-theme="dark|light">，style.css 的 [data-theme] 覆盖块生效；
 * Vant 组件深色由 App.vue 的 <van-config-provider :theme="theme"> 接管。
 */
import { ref } from 'vue'

const themeMode = ref(localStorage.getItem('theme') || 'system')
const theme = ref('light')

function resolve() {
  if (themeMode.value === 'dark') return 'dark'
  if (themeMode.value === 'light') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function apply() {
  const t = resolve()
  theme.value = t
  document.documentElement.setAttribute('data-theme', t)
  // 原生控件（滚动条、日期选择器）跟随
  document.documentElement.style.colorScheme = t
}

/** 应用初始主题 + 注册系统跟随（main.js 启动时调用，避免深色首屏闪烁） */
export function initTheme() {
  apply()
  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  if (mq.addEventListener) {
    mq.addEventListener('change', () => {
      if (themeMode.value === 'system') apply()
    })
  }
}

/** 切换主题模式：'light' | 'dark' | 'system' */
export function setTheme(mode) {
  if (!['light', 'dark', 'system'].includes(mode)) return
  themeMode.value = mode
  try { localStorage.setItem('theme', mode) } catch (e) { /* 忽略 */ }
  apply()
}

export function useTheme() {
  return { theme, themeMode, setTheme }
}

export default useTheme
