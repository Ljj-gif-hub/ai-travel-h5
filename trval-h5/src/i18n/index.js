/**
 * i18n 国际化（B5）
 * - vue-i18n v9（Composition API, legacy:false）
 * - 语言持久化 localStorage.lang，默认 zh-CN
 * - Vant 组件内置文案跟随切换（vant/es/locale/lang/xx）
 */
import { createI18n } from 'vue-i18n'
import { Locale } from 'vant'
import vantZhCN from 'vant/es/locale/lang/zh-CN'
import vantEnUS from 'vant/es/locale/lang/en-US'
import zhCN from '../locales/zh-CN'
import enUS from '../locales/en-US'

function savedLang() {
  const lang = localStorage.getItem('lang')
  return lang === 'en-US' ? 'en-US' : 'zh-CN'
}

const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: savedLang(),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, 'en-US': enUS },
})

/** 同步 Vant 内置组件文案 */
function syncVantLocale(lang) {
  Locale.use(lang, lang === 'en-US' ? vantEnUS : vantZhCN)
}

/** 切换语言：'zh-CN' | 'en-US' */
export function setLanguage(lang) {
  const target = lang === 'en-US' ? 'en-US' : 'zh-CN'
  i18n.global.locale.value = target
  try { localStorage.setItem('lang', target) } catch (e) { /* 忽略 */ }
  syncVantLocale(target)
}

// 初始化 Vant 文案
syncVantLocale(savedLang())

export default i18n
