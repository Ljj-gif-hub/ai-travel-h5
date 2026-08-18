/**
 * envCheck.js — 应用启动环境变量校验（审查报告"可补充新功能"）
 *
 * - dev 模式：缺失必需变量 → console.warn 明确提示（含变量名/用途/当前值）
 * - prod 模式：静默降级为默认值（业务代码各处已有兜底，如 VITE_API_BASE || '/api'）
 * - 导出校验结果（missing 列表 + 最终生效值）供调试面板/单测使用
 */

/** 必需变量清单：key / 默认值 / 用途说明 */
export const REQUIRED_ENV = [
  { key: 'VITE_API_BASE', def: '/api', desc: '后端 API 地址（默认同源 /api）' },
  { key: 'VITE_IMAGE_API', def: '', desc: 'AI 风景图生成 API（留空用内置默认地址）' },
  { key: 'VITE_BAIDU_MAP_AK', def: '', desc: '百度地图 AK（地图页密钥）' },
]

/**
 * 校验环境变量（纯函数，便于单测）。
 * @param {Record<string, string|undefined>} env import.meta.env 或测试注入对象
 * @param {{ warn?: (msg: string) => void }} [hooks] 告警钩子（默认 console.warn）
 * @returns {{ missing: string[], resolved: Record<string, string> }}
 */
export function checkEnv(env = {}, hooks = {}) {
  const warn = hooks.warn || ((msg) => console.warn(msg))
  const missing = []
  const resolved = {}

  for (const item of REQUIRED_ENV) {
    const val = env[item.key]
    if (val === undefined || val === null || String(val).trim() === '') {
      missing.push(item.key)
      resolved[item.key] = item.def
    } else {
      resolved[item.key] = val
    }
  }

  if (missing.length > 0) {
    const list = missing.map((k) => {
      const item = REQUIRED_ENV.find((i) => i.key === k)
      return `  - ${k}（${item.desc}）`
    }).join('\n')
    warn(`[envCheck] 缺少环境变量，已使用默认值降级：\n${list}`)
  }

  return { missing, resolved }
}

/** 应用启动时调用（main.js）：dev 模式 warn 提示，prod 静默降级 */
export function runEnvCheck() {
  const env = import.meta.env || {}
  const isDev = env.DEV === true || env.MODE === 'development'
  const hooks = isDev ? {} : { warn: () => {} } // prod 静默
  const result = checkEnv(env, hooks)
  // 暴露到全局供调试（仅在 dev）
  if (isDev && typeof window !== 'undefined') {
    window.__envCheckResult = result
  }
  return result
}

export default { checkEnv, runEnvCheck, REQUIRED_ENV }
