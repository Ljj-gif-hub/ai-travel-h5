/**
 * 百度百科图片 — 每座城市人工精选的摘要图
 * 提取 og:image / lemma-pic / summary-pic
 */
const axios = require('axios')
const fs = require('fs')
const path = require('path')

const OUT = path.join(__dirname, '..', 'public', 'city-images.json')
const all = fs.existsSync(OUT) ? JSON.parse(fs.readFileSync(OUT, 'utf-8')) : {}
const UA = 'Mozilla/5.0 (Linux; Android 10; Pixel 3) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36'

async function getBaikeImg(city) {
  const url = `https://baike.baidu.com/item/${encodeURIComponent(city)}`
  try {
    const r = await axios.get(url, { timeout: 10000, headers: { 'User-Agent': UA, 'Accept': 'text/html', 'Accept-Language': 'zh-CN' } })
    const html = r.data

    // 1) og:image meta
    let m = html.match(/<meta[^>]*property="og:image"[^>]*content="([^"]+)"/)
    if (m) return m[1]

    // 2) summary-pic 内的 img
    m = html.match(/class="summary-pic"[^>]*>[\s\S]*?<img[^>]*src="([^"]+)"/)
    if (m) return m[1]

    // 3) 第一个 bkimg 图片
    m = html.match(/src="(https:\/\/bkimg\.cdn\.bcebos\.com\/[^"]+)"/)
    if (m) return m[1]

    // 4) 任何 1200x 大图
    m = html.match(/src="(https:\/\/[^"]+\.(?:jpg|jpeg|png)[^"]*)"/i)
    if (m && m[1].length < 300) return m[1]
  } catch {}
  return null
}

async function main() {
  const cities = Object.keys(all)
  let ok = 0, skip = 0, fail = 0

  for (let i = 0; i < cities.length; i++) {
    const city = cities[i]
    // 跳过已有百度百科图片的
    if (all[city] && (all[city].includes('bkimg') || all[city].includes('baike'))) { skip++; continue }

    process.stdout.write(`[${i+1}/${cities.length}] ${city} `)
    const img = await getBaikeImg(city)
    if (img) {
      all[city] = img
      ok++
      console.log(`✓ ${img.substring(0,60)}...`)
    } else {
      fail++
      console.log('✗')
    }
    if ((i+1) % 50 === 0) { fs.writeFileSync(OUT, JSON.stringify(all, null, 2)); console.log(`  → 存 ✓${ok} ✗${fail} 跳${skip}\n`) }
    await new Promise(r => setTimeout(r, 600))
  }
  fs.writeFileSync(OUT, JSON.stringify(all, null, 2))
  console.log(`\n完成 ✓${ok} ✗${fail} 跳${skip}`)
}
main().catch(console.error)
