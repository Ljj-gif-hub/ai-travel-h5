import { getToken } from './auth'

export async function streamPost(url, body, { onChunk, onDone, onError }) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(token && { Authorization: `Bearer ${token}` }),
  }

  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    const err = new Error(`SSE 请求失败 (${response.status})`)
    err.status = response.status
    err.body = text
    onError?.(err)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      onDone?.()
      break
    }

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (!line.trim()) continue
      let data = line
      if (line.startsWith('data:')) {
        data = line.slice(5).trim()
      }
      if (data === '[DONE]') {
        onDone?.()
        return
      }
      try {
        const parsed = JSON.parse(data)
        onChunk?.(parsed)
      } catch {
        onChunk?.(data)
      }
    }
  }
}
