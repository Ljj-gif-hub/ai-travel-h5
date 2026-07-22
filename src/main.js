import { createApp } from 'vue'
import './style.css'
/*
 * 【修复】Vant Toast/Notify/Dialog 等命令式 API 的 CSS 必须显式导入
 * 根因：unplugin-vue-components 仅按模板组件按需加载样式，
 * showToast/showDialog 是函数调用，不在 template 中 → CSS 不会被自动注入
 * 修复：显式 import Vant 全量 CSS（含 Toast/Dialog/Notify 样式）
 */
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'

function setRootFontSize() {
  const maxWidth = 500
  const fontSize = Math.min(window.innerWidth, maxWidth) / 10 + 'px'
  document.documentElement.style.fontSize = fontSize
}

setRootFontSize()
window.addEventListener('resize', setRootFontSize)

createApp(App).use(router).mount('#app')
