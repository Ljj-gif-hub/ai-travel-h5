import { getToken } from './auth'

export async function streamPost(url, body, { onChunk, onDone, onError }) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  try {
    const response = await fetch(`/api${url}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    })

    if (!response.ok) {
      const errText = await response.text()
      console.error('API请求失败:', response.status, errText)
      throw new Error(`HTTP ${response.status}: ${errText}`)
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
        const trimmedLine = line.trim()
        
        if (!trimmedLine) continue
        
        if (trimmedLine === 'data: [DONE]') {
          onDone?.()
          return
        }
        
        if (!trimmedLine.startsWith('data: ')) {
          continue
        }

        try {
          const dataStr = trimmedLine.slice(6).trim()
          const parsed = JSON.parse(dataStr)
          const content = parsed?.choices?.[0]?.delta?.content || ''
          if (content) {
            onChunk?.(content)
          }
        } catch (e) {
          console.warn('SSE解析失败:', line)
        }
      }
    }
    onDone?.()
  } catch (err) {
    console.error('流式请求异常:', err)
    onError?.(err)
  }
}

export async function fetchPlan(destination, budget, days) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch('/api/travel/plan', {
    method: 'POST',
    headers,
    body: JSON.stringify({ destination, budget, days }),
  })
  
  if (!response.ok) {
    throw new Error('API请求失败')
  }
  
  const data = await response.json()
  return data
}

export async function fetchChat(messages) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch('/api/travel/chat', {
    method: 'POST',
    headers,
    body: JSON.stringify(messages),
  })
  
  if (!response.ok) {
    throw new Error('API请求失败')
  }
  
  const data = await response.json()
  return data
}
