/**
 * 本地头像生成器 — 替代外部 DiceBear API
 * 根据 seed 字符串生成一致的彩色 SVG 头像（首字 + 背景色）
 */
const COLORS = [
  '#8B5CF6', '#6366F1', '#3B82F6', '#0891B2', '#059669',
  '#D946EF', '#F43F5E', '#F97316', '#EAB308', '#14B8A6',
  '#EC4899', '#0EA5E9', '#84CC16', '#A855F7', '#06B6D4',
]

function hash(str) {
  let h = 0
  for (let i = 0; i < str.length; i++) {
    h = ((h << 5) - h + str.charCodeAt(i)) | 0
  }
  return Math.abs(h)
}

/**
 * @param {string} seed - 唯一标识（如 'ming', 'family' 或 userId）
 * @param {string} [text] - 显示文字，默认取 seed 第一个字
 * @returns {string} data:image/svg+xml URI
 */
export function avatarUrl(seed, text) {
  const color = COLORS[hash(seed) % COLORS.length]
  const char = (text || seed).charAt(0).toUpperCase()
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100">
  <rect width="100" height="100" rx="20" fill="${color}"/>
  <text x="50" y="50" text-anchor="middle" dy=".13em" font-family="system-ui,-apple-system,sans-serif" font-size="48" font-weight="600" fill="#fff">${char}</text>
</svg>`
  return 'data:image/svg+xml,' + encodeURIComponent(svg)
}

export default avatarUrl
