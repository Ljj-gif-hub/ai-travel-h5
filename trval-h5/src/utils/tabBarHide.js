/**
 * 全局监听弹出层 → 控制底部 Tab 栏显隐
 * 用法：在 App.vue 的 onMounted 中调用 initTabBarHide()
 *
 * 【性能改造】原实现用 MutationObserver(subtree:true) 监听 body 全树 class/style 变化，
 * 聊天流式渲染等高频 DOM 变更会持续触发回调（每次回调还遍历 .van-overlay 计算样式），
 * 造成明显性能损耗。现改为「事件委托 + 窄范围观察」混合方案：
 *
 *   1. transitionend（capture）：Vant Popup/Dialog 打开/关闭动画结束必然触发，
 *      是最可靠的显隐信号，且只在动画期间产生事件，开销极低。
 *   2. click（capture）：打开弹窗、点遮罩关闭都伴随点击，兜底补一次检测。
 *   3. van-popup 的 open/close/opened/closed 自定义事件（capture）：Vant 组件 emit
 *      是 Vue 3 的 props 回调，不会派发 DOM 事件，document 层无法捕获；
 *      这里作为 forward-compat 兜底注册（若未来改为原生 CustomEvent 即可直接生效）。
 *   4. MutationObserver 仅观察 document.body 的直接子节点增删（subtree:false），
 *      捕获弹窗首次挂载/卸载，不再扫描整棵 DOM 树。
 *
 * 所有信号统一走 100ms 节流的 check()，最终判定仍以 .van-overlay 实际可见性为准。
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

// 事件委托句柄（保留引用便于销毁）
const onTransitionEnd = (e) => {
  const target = e.target
  if (!target || !(target.classList && target.classList.contains)) return
  if (target.classList.contains('van-overlay') || target.classList.contains('van-popup')) check()
}
const onClick = () => check()
const onPopupEvent = () => check()

// van-popup 自定义事件名（见文件头注释第 3 点：document 层大概率收不到，作兜底）
const POPUP_EVENTS = ['open', 'close', 'opened', 'closed']

export function initTabBarHide() {
  if (observer) return

  document.addEventListener('transitionend', onTransitionEnd, true)
  document.addEventListener('click', onClick, true)
  POPUP_EVENTS.forEach((name) => document.addEventListener(name, onPopupEvent, true))

  // 窄范围观察：只关心 body 直接子节点（弹窗/遮罩 teleport 挂载点）的增删
  observer = new MutationObserver(() => check())
  observer.observe(document.body, { childList: true, subtree: false })

  // 初始检测
  check()
}

export function destroyTabBarHide() {
  document.removeEventListener('transitionend', onTransitionEnd, true)
  document.removeEventListener('click', onClick, true)
  POPUP_EVENTS.forEach((name) => document.removeEventListener(name, onPopupEvent, true))

  if (observer) {
    observer.disconnect()
    observer = null
  }
  clearTimeout(checkTimer)
  document.documentElement.classList.remove('picker-open')
}
