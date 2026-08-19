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

/**
 * 白名单式 XSS 过滤 — 用浏览器原生 DOMParser 解析后按白名单清洗 DOM。
 * 只保留安全标签/属性，剥离所有事件处理器与危险协议（javascript:/data:text 等），
 * 从根上杜绝脚本注入。原实现为黑名单正则，可被 <svg onload>、实体编码 javascript: 等绕过。
 */
export function filterXss(content) {
  if (!content || typeof content !== 'string') {
    return content || ''
  }

  // 非浏览器环境（SSR/单测）无 DOMParser：降级为「去标签 + 全量转义」，宁缺毋滥
  if (typeof DOMParser === 'undefined') {
    return sanitizeHtml(content.replace(/<[^>]*>/g, ''))
  }

  const ALLOWED_TAGS = new Set([
    'p', 'br', 'hr', 'div', 'span', 'b', 'strong', 'i', 'em', 'u', 's', 'del', 'ins',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 'blockquote', 'pre', 'code',
    'a', 'img', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'caption',
    'figure', 'figcaption', 'mark', 'sub', 'sup', 'small', 'q', 'cite'
  ])
  const ALLOWED_ATTRS = new Set(['href', 'src', 'alt', 'title', 'class', 'width', 'height'])
  // 只放行安全协议；data: 仅允许位图图片，排除 data:image/svg+xml（可内嵌脚本）与 data:text/html
  const SAFE_PROTOCOL = /^(https?:|mailto:|tel:|#|\/|data:image\/(png|jpe?g|gif|webp))/i

  const doc = new DOMParser().parseFromString(content, 'text/html')

  const clean = (node) => {
    for (const child of Array.from(node.childNodes)) {
      if (child.nodeType === Node.COMMENT_NODE) {
        child.remove()
        continue
      }
      if (child.nodeType === Node.TEXT_NODE) {
        continue
      }
      const tag = child.tagName ? child.tagName.toLowerCase() : ''
      if (!ALLOWED_TAGS.has(tag)) {
        // 白名单之外的标签（script/iframe/svg/style/object…）连同内容整块删除，杜绝隐藏载荷
        child.remove()
        continue
      }
      for (const attr of Array.from(child.attributes)) {
        const name = attr.name.toLowerCase()
        const value = attr.value
        if (name.startsWith('on') || !ALLOWED_ATTRS.has(name)) {
          child.removeAttribute(attr.name)
          continue
        }
        if ((name === 'href' || name === 'src') && !SAFE_PROTOCOL.test(value.trim())) {
          child.removeAttribute(attr.name)
          continue
        }
      }
      if (tag === 'a') {
        child.setAttribute('rel', 'noopener noreferrer')
        child.setAttribute('target', '_blank')
      }
      clean(child)
    }
  }

  clean(doc.body)
  return doc.body.innerHTML
}

export function getProxyImageUrl(url) {
  if (!url || typeof url !== 'string' || url.trim() === '') {
    return '/images/default-placeholder.png'
  }

  // BUGID L-COMP-3 修复：/api/proxy/image 判断需在 / 判断之前——
  // 否则该路径也会命中 url.startsWith('/')，下面分支永远不可达（死分支）
  if (url.startsWith('/api/proxy/image')) {
    return url
  }

  if (url.startsWith('/')) {
    return url
  }

  try {
    const urlObj = new URL(url)
    const allowedDomains = ['api.map.baidu.com', 'map.baidu.com', 'restapi.amap.com', 'webapi.amap.com']
    // 地图域走服务端代理（解决跨域/防盗链）；其余域由浏览器直接加载，
    // 不再替换成占位图（否则所有第三方/AI 生成图片都会变成默认占位图）
    if (allowedDomains.includes(urlObj.host)) {
      return `/api/proxy/image?url=${encodeURIComponent(url)}`
    }
    return url
  } catch {
    return '/images/default-placeholder.png'
  }
}

/*
 * 【已删除】validateInput 黑名单正则校验 — 全项目零调用方。
 * 黑名单式过滤可被 <svg onload>、实体编码等绕过，XSS 防护统一走上方 filterXss 白名单清洗。
 */
