export function sanitizeHtml(str) {
  if (!str || typeof str !== 'string') {
    return str || ''
  }
  
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
    '/': '&#x2F;',
    '`': '&#x60;',
    '=': '&#x3D;'
  }
  
  return str.replace(/[&<>"'`=\/]/g, m => map[m])
}

export function filterXss(content) {
  if (!content || typeof content !== 'string') {
    return content || ''
  }
  
  let result = content
  
  result = result.replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
  result = result.replace(/<iframe[^>]*>[\s\S]*?<\/iframe>/gi, '')
  result = result.replace(/<embed[^>]*>[\s\S]*?<\/embed>/gi, '')
  result = result.replace(/<object[^>]*>[\s\S]*?<\/object>/gi, '')
  result = result.replace(/<svg[^>]*>[\s\S]*?<\/svg>/gi, '')
  result = result.replace(/javascript:/gi, '')
  result = result.replace(/on\w+\s*=\s*["']?[^"']*["']?/gi, '')
  
  return result
}

export function getProxyImageUrl(url) {
  if (!url || typeof url !== 'string') {
    return '/images/default-placeholder.png'
  }
  
  if (url.startsWith('/')) {
    return url
  }
  
  if (url.startsWith('/api/proxy/image')) {
    return url
  }
  
  try {
    const urlObj = new URL(url)
    const allowedDomains = ['api.map.baidu.com', 'map.baidu.com', 'picsum.photos', 'trae-api-cn.mchost.guru']
    if (!allowedDomains.includes(urlObj.host)) {
      return '/images/default-placeholder.png'
    }
    return `/api/proxy/image?url=${encodeURIComponent(url)}`
  } catch {
    return '/images/default-placeholder.png'
  }
}

export function validateInput(input, maxLength = 500) {
  if (!input || typeof input !== 'string') {
    return false
  }
  
  const trimmed = input.trim()
  
  if (trimmed.length === 0) {
    return false
  }
  
  if (trimmed.length > maxLength) {
    return false
  }
  
  const dangerousPatterns = [
    /<script/i,
    /javascript:/i,
    /on\w+\s*=/i,
    /<iframe/i,
    /<embed/i,
    /<object/i,
    /<svg/i
  ]
  
  for (const pattern of dangerousPatterns) {
    if (pattern.test(trimmed)) {
      return false
    }
  }
  
  return true
}
