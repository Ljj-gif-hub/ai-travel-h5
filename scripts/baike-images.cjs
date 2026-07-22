/**
 * 从百度百科获取城市官方图片 — 国内可访问，人工审核
 * 用法: node scripts/baike-images.cjs
 */
const axios = require('axios')
const fs = require('fs')
const path = require('path')

const JSON_PATH = path.join(__dirname, '..', 'public', 'city-images.json')
const existing = fs.existsSync(JSON_PATH) ? JSON.parse(fs.readFileSync(JSON_PATH, 'utf-8')) : {}

async function getBaikeImage(city) {
  try {
    const url = `https://baike.baidu.com/item/${encodeURIComponent(city)}`
    const resp = await axios.get(url, {
      timeout: 8000,
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Accept': 'text/html',
        'Accept-Language': 'zh-CN,zh;q=0.9',
      },
    })
    const html = resp.data

    // 百度百科摘要图在 <div class="summary-pic"> 下的 <img>
    let match = html.match(/<div[^>]*class="summary-pic"[^>]*>[\s\S]*?<img[^>]*src="([^"]+)"/)
    if (match) return match[1]

    // 兜底: 第一个大图
    match = html.match(/<img[^>]*src="(https:\/\/bkimg\.cdn\.bcebos\.com[^"]+)"/)
    if (match) return match[1]

    // 再兜底: lemma-pic
    match = html.match(/class="lemma-pic"[^>]*>[\s\S]*?<img[^>]*src="([^"]+)"/)
    if (match) return match[1]
  } catch {}
  return null
}

async function main() {
  // 只处理国内城市（百度百科有收录的）
  const cities = Object.keys(existing).filter(c => {
    // 跳过境外纯英文名和太生僻的区名
    if (/^[a-zA-Z\s]+$/.test(c)) return false
    return true
  })

  console.log(`共 ${cities.length} 个城市，用百度百科图片替换\n`)
  let ok = 0, fail = 0

  for (let i = 0; i < cities.length; i++) {
    const city = cities[i]
    process.stdout.write(`[${i+1}/${cities.length}] ${city} ... `)

    const img = await getBaikeImage(city)
    if (img) {
      existing[city] = img
      ok++
      console.log('✓')
    } else {
      fail++
      console.log('✗')
    }

    if ((i+1) % 50 === 0) {
      fs.writeFileSync(JSON_PATH, JSON.stringify(existing, null, 2))
      console.log(`  → 已保存 | ✓${ok} ✗${fail}\n`)
    }
    await new Promise(r => setTimeout(r, 500))
  }

  fs.writeFileSync(JSON_PATH, JSON.stringify(existing, null, 2))
  console.log(`\n完成！✓${ok} ✗${fail}`)
}

main().catch(console.error)
