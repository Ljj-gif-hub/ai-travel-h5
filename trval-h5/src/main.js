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
import i18n from './i18n'
import { initTheme } from './utils/theme'

// 深色模式：挂载前应用初始主题，避免深色首屏闪烁
initTheme()

function setRootFontSize() {
  const maxWidth = 500
  const fontSize = Math.min(window.innerWidth, maxWidth) / 10 + 'px'
  document.documentElement.style.fontSize = fontSize
}

setRootFontSize()
window.addEventListener('resize', setRootFontSize);

/* ==================== 低内存设备内存保护（对高端机零影响） ====================
 * backdrop-filter 会实时模糊背后整页，而 position:fixed/sticky 的栏与全屏弹层
 * 在滚动/打开时每帧重合成，是 GPU 内存的最大头（低端机 → 标签页 out of memory）。
 * 仅当设备内存 ≤4GB 或 ≤4 核时启用：去掉这些常驻栏/弹层的磨砂，
 * 装饰性卡片的磨砂质感完整保留。高端机（不支持/高于阈值）不启用，视觉完全不变。
 * ⚠️ 前面加 `;`：本 IIFE 以 ( 开头，若不加分号会被 ASI 续接到上一句
 * window.addEventListener(...) 变成对调用结果的二次调用 → TypeError。
 */
;(function () {
  const dm = navigator.deviceMemory // Chrome/Android 才有；iOS/Firefox 无 → 视为可负担
  const hc = navigator.hardwareConcurrency
  const isLowMem = (dm !== undefined && dm <= 4) || (hc !== undefined && hc <= 4)
  if (!isLowMem) return
  document.documentElement.classList.add('low-mem')
  let pending = false
  const strip = () => {
    for (const el of document.querySelectorAll('*')) {
      if (el.dataset.lmStripped) continue
      const cs = getComputedStyle(el)
      const pos = cs.position
      if ((pos === 'fixed' || pos === 'sticky') && (cs.backdropFilter !== 'none' || cs.webkitBackdropFilter !== 'none')) {
        el.style.webkitBackdropFilter = 'none'
        el.style.backdropFilter = 'none'
        el.dataset.lmStripped = '1'
      }
    }
  }
  const schedule = () => { if (!pending) { pending = true; setTimeout(() => { pending = false; strip() }, 60) } }
  // 监听视图/弹层挂载，动态剥离（路由切换、van-popup 打开都会触发）
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true })
  strip()
})();

createApp(App).use(router).use(i18n).mount('#app')
