/**
 * agent.js — Agent 旅游规划 API 封装
 * SSE 走 Vite 代理 /api/agent → localhost:3200 (Spring Boot) → localhost:3201 (Python)
 */
import { getToken } from '../utils/auth'

const BASE = '/api'

/**
 * SSE 流式 Agent 规划 — XMLHttpRequest + onprogress
 */
export function agentPlanStream(params, callbacks) {
  const { onProgress, onComplete, onError } = callbacks
  const xhr = new XMLHttpRequest()
  let lastIdx = 0
  let buf = ''
  let receivedAny = false
  const token = getToken()

  xhr.open('POST', `${BASE}/agent/plan/stream`, true)
  xhr.setRequestHeader('Content-Type', 'application/json')
  xhr.setRequestHeader('Accept', 'text/event-stream')
  if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

  xhr.onprogress = function () {
    const add = xhr.responseText.substring(lastIdx)
    lastIdx = xhr.responseText.length
    if (!add) return
    buf += add
    const lines = buf.split('\n')
    buf = lines.pop() || ''
    for (const line of lines) {
      const t = line.trim()
      if (!t.startsWith('data:')) continue
      receivedAny = true
      const json = t.substring(5).trim()
      if (!json) continue
      try {
        const ev = JSON.parse(json)
        const type = ev.event_type || ''
        if (type === 'complete') { if (onComplete) onComplete(ev) }
        else if (type === 'error') { if (onError) onError(ev.message || 'Agent 服务异常') }
        else { if (onProgress) onProgress(ev) }
      } catch {}
    }
  }

  // 兜底：非 2xx 或从未收到任何 data: 事件（如校验失败返回 422 JSON）→ 报错，避免永久卡在"生成中"
  xhr.onload = function () {
    if (xhr.status < 200 || xhr.status >= 300 || !receivedAny) {
      let msg = 'Agent 请求失败 (' + xhr.status + ')'
      if (!receivedAny && xhr.responseText) {
        try {
          const body = JSON.parse(xhr.responseText)
          if (body && body.message) msg = body.message
        } catch {}
      }
      if (onError) onError(msg)
    }
  }

  xhr.onerror = function () { if (onError) onError('无法连接 Agent 服务，请确认已启动 python main.py') }
  xhr.ontimeout = function () { if (onError) onError('请求超时') }
  xhr.timeout = 300000
  xhr.send(JSON.stringify(params))
  return () => xhr.abort()
}

export async function agentPlanSync(params) {
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`
  const r = await fetch(`${BASE}/agent/plan`, { method: 'POST', headers, body: JSON.stringify(params) })
  return r.json()
}

export async function agentHealth() {
  const r = await fetch(`${BASE}/agent/health`)
  return r.json()
}
