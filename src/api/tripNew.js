/**
 * tripNew.js — 行程生成 API 封装（SSE 流式 + REST）
 *
 * 使用方式:
 *   const { startSSE } = tripNewApi
 *   const abort = startSSE(taskId, {
 *     onProgress: (data) => updateProgress(data),
 *     onComplete: (data) => showTrip(data),
 *     onError: (err) => showToast(err),
 *   })
 *   // 取消: abort()
 */
import { getToken } from '../utils/auth'

const BASE = '/api'

function headers() {
  const h = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) h['Authorization'] = `Bearer ${token}`
  return h
}

export const tripNewApi = {
  /* ==================== 创建生成任务 ==================== */
  /**
   * 创建行程生成任务，返回 taskId
   * 前端拿到 taskId 后调用 startSSE 订阅进度
   */
  generateTrip(params) {
    return fetch(`${BASE}/travel/trip/generate`, {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify({
        destination: params.destination,
        days: params.days,
        people: params.people,
        budget: params.budget,
      }),
    }).then(r => r.json())
  },

  /* ==================== SSE 流式订阅 ==================== */
  /**
   * 【推荐】单端点 SSE 行程生成 — POST 后直接在本连接接收流式进度
   * 一条 HTTP 请求完成全部流程，无竞态条件
   */
  generateAndStream(params, callbacks) {
    const { onProgress, onComplete, onError, onStop, onDayUpdate, onHotelUpdate, onTipsUpdate, onCostUpdate, onTransportUpdate, onTextUpdate } = callbacks
    const controller = new AbortController()
    let reader = null
    let lastProgress = 0

    console.log('[SSE] 单端点POST开始:', params.destination)
    fetch(`${BASE}/travel/trip/generate/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
        ...(getToken() ? { 'Authorization': `Bearer ${getToken()}` } : {}),
      },
      body: JSON.stringify(params),
      signal: controller.signal,
    })
      .then(async (resp) => {
        console.log('[SSE] 响应:', resp.status, resp.headers.get('content-type'))
        if (!resp.ok) {
          const text = await resp.text().catch(() => '')
          throw new Error(`HTTP ${resp.status}: ${text}`)
        }

        reader = resp.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) {
            console.log('[SSE] 流结束')
            // 流提前关闭 — 如果进度已达100则视为完成，否则报错
            if (lastProgress >= 100) {
              if (onComplete) onComplete({ progress: 100, stepName: '全部完成' })
            } else {
              if (onError) onError('服务器连接中断，进度: ' + lastProgress + '%')
            }
            break
          }

          const chunk = decoder.decode(value, { stream: true })
          console.log('[SSE] chunk(' + chunk.length + 'B):', chunk.substring(0, 150))
          buffer += chunk
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            const trimmed = line.trim()
            if (!trimmed || trimmed.startsWith(':')) continue

            let jsonStr = trimmed
            if (trimmed.startsWith('data:')) {
              jsonStr = trimmed.substring(5).trim()
            }

            if (!jsonStr || jsonStr === '[DONE]') continue

            try {
              const data = JSON.parse(jsonStr)
              console.log('[SSE] event:', data.eventType, data.progress != null ? data.progress + '%' : '')
              if (!data.eventType) continue

              switch (data.eventType) {
                case 'progress-update':
                  lastProgress = data.progress || lastProgress
                  if (onProgress) onProgress(data); break
                case 'day-update':
                  lastProgress = Math.max(lastProgress, 95)
                  if (onDayUpdate) onDayUpdate(data); break
                case 'hotel-update':
                  if (onHotelUpdate) onHotelUpdate(data); break
                case 'tips-update':
                  if (onTipsUpdate) onTipsUpdate(data); break
                case 'transport-update':
                  if (onTransportUpdate) onTransportUpdate(data); break
                case 'text-update':
                  if (onTextUpdate) onTextUpdate(data); break
                case 'cost-update':
                  if (onCostUpdate) onCostUpdate(data); break
                case 'generate-finish': if (onComplete) onComplete(data); cleanup(); break
                case 'stream-error': console.error('[SSE] err:', data.message); if (onError) onError(data.message); cleanup(); break
                case 'task-stop': if (onStop) onStop(data); cleanup(); break
              }
            } catch (e) {
              console.warn('[SSE] JSON parse fail:', jsonStr.substring(0, 100))
            }
          }
        }
      })
      .catch((err) => {
        if (err.name === 'AbortError') { console.log('[SSE] 用户取消'); return }
        console.error('[SSE] 连接失败:', err.message)
        if (onError) onError(err.message || '连接失败')
      })

    const cleanup = () => {
      if (reader) { reader.cancel().catch(() => {}); reader = null }
      try { controller.abort() } catch (e) {}
    }

    return () => cleanup()
  },

  /**
   * 连接 SSE 进度流（两阶段方式，支持重连）
   * @deprecated 推荐使用 generateAndStream 单端点方式
   */
  startSSE(taskId, callbacks) {
    const { onProgress, onComplete, onError, onStop } = callbacks
    const url = `${BASE}/travel/trip/progress/${taskId}`
    const controller = new AbortController()
    let reader = null
    let retryCount = 0
    const MAX_RETRIES = 3

    const connect = () => {
      console.log('[SSE] 重连:', url)
      fetch(url, {
        method: 'GET',
        headers: { 'Accept': 'text/event-stream', 'Cache-Control': 'no-cache' },
        signal: controller.signal,
      })
        .then(async (resp) => {
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
          retryCount = 0
          reader = resp.body.getReader()
          const decoder = new TextDecoder('utf-8')
          let buffer = ''
          while (true) {
            const { done, value } = await reader.read()
            if (done) break
            buffer += decoder.decode(value, { stream: true })
            const lines = buffer.split('\n')
            buffer = lines.pop() || ''
            for (const line of lines) {
              const trimmed = line.trim()
              if (!trimmed || !trimmed.startsWith('data:')) continue
              const jsonStr = trimmed.substring(5).trim()
              if (!jsonStr || jsonStr === '[DONE]') continue
              try {
                const data = JSON.parse(jsonStr)
                if (!data.eventType) continue
                switch (data.eventType) {
                  case 'progress-update': if (onProgress) onProgress(data); break
                  case 'generate-finish': if (onComplete) onComplete(data); cleanup(); break
                  case 'stream-error': if (onError) onError(data.message); cleanup(); break
                  case 'task-stop': if (onStop) onStop(data); cleanup(); break
                }
              } catch (e) {}
            }
          }
        })
        .catch((err) => {
          if (err.name === 'AbortError') return
          if (retryCount < MAX_RETRIES && !controller.signal.aborted) {
            retryCount++
            setTimeout(connect, Math.min(3000 * retryCount, 10000))
          } else if (onError) onError('连接失败')
        })
    }

    const cleanup = () => {
      if (reader) { reader.cancel().catch(() => {}); reader = null }
      try { controller.abort() } catch (e) {}
    }

    setTimeout(connect, 200)
    return () => { cleanup(); fetch(`${BASE}/travel/trip/stop/${taskId}`, { method: 'POST', headers: headers() }).catch(() => {}) }
  },

  /* ==================== REST 接口 ==================== */
  stopTrip(taskId) {
    return fetch(`${BASE}/travel/trip/stop/${taskId}`, {
      method: 'POST', headers: headers(),
    }).then(r => r.json())
  },

  searchHotels(city, params = {}) {
    const qs = new URLSearchParams({ city, ...params }).toString()
    return fetch(`${BASE}/hotel/search?${qs}`, { headers: headers() }).then(r => r.json())
  },

  getCostBreakdown(params) {
    return fetch(`${BASE}/cost/breakdown`, {
      method: 'POST', headers: headers(),
      body: JSON.stringify(params),
    }).then(r => r.json())
  },

  getMapMarkers(city) {
    return fetch(`${BASE}/map/landmarks?city=${encodeURIComponent(city)}`, {
      headers: headers(),
    }).then(r => r.json())
  },

  getMetroStations(city) {
    return fetch(`${BASE}/map/metro-stations?city=${encodeURIComponent(city)}`, {
      headers: headers(),
    }).then(r => r.json())
  },

  voiceToText(audioBlob) {
    const formData = new FormData()
    formData.append('audio', audioBlob, 'recording.wav')
    return fetch(`${BASE}/voice/transcribe`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${getToken()}` },
      body: formData,
    }).then(r => r.json())
  },
}
