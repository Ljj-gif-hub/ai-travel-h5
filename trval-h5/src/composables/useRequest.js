/**
 * useRequest.js — 统一请求状态封装（审查报告"可补充新功能"）
 *
 * 能力：
 * - 自动管理 AbortController：组件卸载时 abort 在途请求（不再泄漏到已卸载组件）
 * - loading / error / data 三态
 * - run()/refresh()：重复调用会先 abort 上一个未完成请求（最新请求胜出，防竞态覆盖）
 * - 主动取消（AbortError）静默，不写入 error
 *
 * 约定：requestFn 的最后一个参数收到 { signal }（透传给 api/index.js request 的 options.signal）。
 * 其余视图可逐步迁移：把「ref(loading)+try/catch+finally」替换为
 *   const { data, loading, error, run } = useRequest((opts) => someApi.fetchXxx({ signal: opts.signal }))
 *
 * 注意：无需组件上下文也可使用（非组件场景自动跳过 onBeforeUnmount 清理）。
 */
import { ref, getCurrentInstance, onBeforeUnmount } from 'vue'

export function useRequest(requestFn, options = {}) {
  const { manual = false, initialData = undefined } = options

  const data = ref(initialData)
  const loading = ref(false)
  const error = ref(null)

  let controller = null
  let seq = 0

  /** 取消当前在途请求（卸载 / 新请求取代旧请求时调用） */
  const cancel = () => {
    if (controller) {
      controller.abort()
      controller = null
    }
  }

  /** 发起请求：新请求取消旧请求；AbortError 静默；竞态旧响应不覆盖新状态 */
  const run = async (...args) => {
    cancel()
    const c = new AbortController()
    controller = c
    const id = ++seq
    loading.value = true
    error.value = null
    try {
      const res = await requestFn(...args, { signal: c.signal })
      if (id === seq) data.value = res
      return res
    } catch (e) {
      if (e?.name === 'AbortError') return undefined // 主动取消/被新请求替代
      if (id === seq) error.value = e
      // BUGID COMP-3 修复：不再重抛异常，避免调用方未接 try/catch 时产生 unhandled rejection。
      // 失败结果统一收敛到 error.value，调用方按需读取。
      return undefined
    } finally {
      // 仅最新请求负责收尾（旧请求被新请求/手动 cancel 取代后不触碰状态）
      if (id === seq) {
        loading.value = false
        controller = null
      }
    }
  }

  // 组件卸载 → abort 在途请求（非组件上下文调用时静默跳过）
  if (getCurrentInstance()) {
    onBeforeUnmount(cancel)
  }

  if (!manual) run()

  return { data, loading, error, run, refresh: run, cancel }
}

export default useRequest
