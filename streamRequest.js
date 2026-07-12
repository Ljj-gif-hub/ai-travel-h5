// src/utils/streamRequest.js
const ARK_API_KEY = 'ark-8d059553-00ed-4e3b-81d3-43816b459dd2-f2466'

export async function streamPost(url, body, { onChunk, onDone, onError }) {
  try {
    const response = await fetch(`/doubao-api${url}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${ARK_API_KEY}`,
      },
      body: JSON.stringify(body),
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const dataStr = line.slice(6).trim()
          if (dataStr === '[DONE]') {
            onDone?.()
            return
          }
          try {
            const parsed = JSON.parse(dataStr)
            const content = parsed?.choices?.[0]?.delta?.content || ''
            if (content) {
              onChunk?.(content)
            }
          } catch {}
        }
      }
    }
    onDone?.()
  } catch (err) {
    onError?.(err)
  }
}