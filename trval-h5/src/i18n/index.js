/**
 * i18n 国际化（B5）
 * - vue-i18n v9（Composition API, legacy:false）
 * - 语言持久化 localStorage.lang，默认 zh-CN
 * - Vant 组件内置文案跟随切换（vant/es/locale/lang/xx）
 * - 【懒加载】zh 默认同步加载（首屏无闪烁）；en 语言包按需动态 import，
 *   仅首次切换到英文时拉取 chunk，切换后 setLocaleMessage 注册
 */
import { createI18n } from 'vue-i18n'
import { Locale } from 'vant'
import vantZhCN from 'vant/es/locale/lang/zh-CN'
import vantEnUS from 'vant/es/locale/lang/en-US'
import zhCN from '../locales/zh-CN'

/** 英文语言包按需加载（失败回退中文并允许重试一次） */
let enMessagesPromise = null
function loadEnMessages() {
  if (!enMessagesPromise) {
    enMessagesPromise = import('../locales/en-US')
      .then((m) => m.default)
      .catch((e) => {
        console.warn('[i18n] 英文语言包加载失败，回退中文:', e)
        enMessagesPromise = null
        return null
      })
  }
  return enMessagesPromise
}

function savedLang() {
  const lang = localStorage.getItem('lang')
  return lang === 'en-US' ? 'en-US' : 'zh-CN'
}

const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: savedLang(),
  fallbackLocale: 'zh-CN',
  // 仅注册 zh：en 未加载期间 fallbackLocale 兜底，无闪烁
  messages: { 'zh-CN': zhCN },
})

/** 同步 Vant 内置组件文案 */
function syncVantLocale(lang) {
  Locale.use(lang, lang === 'en-US' ? vantEnUS : vantZhCN)
}

function applyLang(lang) {
  i18n.global.locale.value = lang
  try { localStorage.setItem('lang', lang) } catch (e) { /* 忽略 */ }
  syncVantLocale(lang)
}

/** 切换语言：'zh-CN' | 'en-US'（英文为异步加载，加载完成后才切换） */
export function setLanguage(lang) {
  const target = lang === 'en-US' ? 'en-US' : 'zh-CN'
  if (target === 'en-US') {
    loadEnMessages().then((enUS) => {
      if (enUS) i18n.global.setLocaleMessage('en-US', enUS)
      applyLang('en-US')
    })
  } else {
    applyLang('zh-CN')
  }
}

// 初始化 Vant 文案
syncVantLocale(savedLang())
// 持久化语言为 en-US → 启动时异步补全语言包
if (savedLang() === 'en-US') setLanguage('en-US')

export default i18n
