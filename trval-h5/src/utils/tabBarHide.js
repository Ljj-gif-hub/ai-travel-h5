/**
 * 全局监听弹出层 → 控制底部 Tab 栏显隐
 * 用法：在 App.vue 的 onMounted 中调用 initTabBarHide()
 */
let observer = null
let checkTimer = null

function isAnyOverlayVisible() {
  const overlays = document.querySelectorAll('.van-overlay')
  for (const o of overlays) {
    const style = window.getComputedStyle(o)
    if (style.display !== 'none' && style.visibility !== 'hidden' && parseFloat(style.opacity) > 0) {
      return true
    }
  }
  return false
}

function check() {
  clearTimeout(checkTimer)
  // 微延迟：等 Vant 动画完成后再检测
  checkTimer = setTimeout(() => {
    if (isAnyOverlayVisible()) {
      document.documentElement.classList.add('picker-open')
    } else {
      document.documentElement.classList.remove('picker-open')
    }
  }, 100)
}

export function initTabBarHide() {
  if (observer) return

  observer = new MutationObserver(() => check())
  observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class'] })

  // 初始检测
  check()
}

export function destroyTabBarHide() {
  if (observer) {
    observer.disconnect()
    observer = null
  }
  clearTimeout(checkTimer)
  document.documentElement.classList.remove('picker-open')
}
