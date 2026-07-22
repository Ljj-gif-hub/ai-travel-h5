/**
 * 单独重新爬取指定城市图片，覆盖到 city-images.json
 * 用法: node scripts/rescrape-cities.cjs 长沙 青岛 深圳 ...
 */
const axios = require('axios')
const cheerio = require('cheerio')
const fs = require('fs')
const path = require('path')

const OUTPUT = path.join(__dirname, '..', 'public', 'city-images.json')
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36'

// 黑名单（任何匹配 → 直接0分）
const BLOCKED = [
  'icon','logo','avatar','chart','graph','barcode','qr','thumb',
  'product','mall','shop','taobao','jd.com','alibaba','aliexpress',
  'mapamundi','nombres','breed','identifier','font.','code/',
  'wallpaper-4k','desktop-wallpaper','discount','coupon','banner',
  'screenshot','diagram','infographic','passport',
  'food','recipe','game','meme',
  'box','package','carton','箱子','包装','cat-breed',
  'walmart','amazon.','ebay.','etsy.','shopify',
  'optmv','dress','clothing','shirt','shoe','sneaker',
  'watch','jewelry','perfume','cosmetic','makeup',
  'furniture','appliance','electronic','gadget',
]

// 优质域名（匹配 → 直接合格）
const GOOD = [
  'ctrip','qunar','tuniu','mafengwo',
  '699pic','nipic','tuchong','photophoto',
  'pixabay','pexels','unsplash','flickr',
  'zhimg','bcebos','baidu.com',
  'wikipedia','wikimedia',
  'gettyimages','shutterstock','alamy','500px',
  'dp.paixin','travel','scenic','sight',
]

function score(url) {
  if (!url?.startsWith('http')) return 0
  const l = url.toLowerCase()

  // 黑名单 → 0
  for (const b of BLOCKED) if (l.includes(b)) return 0

  // 优质域名 → 直接高分
  for (const g of GOOD) if (l.includes(g)) return 85

  // 未知域名 → 低基础分，必须其他方面好
  let s = 25  // 从25开始，不够格的过不了30分线
  if (url.length < 200) s += 10
  if (url.length > 600) s -= 10
  if (l.endsWith('.gif') || l.endsWith('.svg')) s -= 30
  if (l.includes('.jpg') || l.includes('.jpeg') || l.includes('.png') || l.includes('.webp')) s += 5
  return Math.max(0, Math.min(100, s))
}

async function scrape(city) {
  const queries = [
    `${encodeURIComponent(city)} 旅游景点 实拍`,
    `${encodeURIComponent(city)} 风景 高清`,
    `${encodeURIComponent(city)} city travel landmark`,
  ]
  const all = []

  for (const q of queries) {
    if (all.length >= 15) break
    for (const base of ['cn.bing.com', 'www.bing.com']) {
      try {
        const resp = await axios.get(`https://${base}/images/search?q=${q}&first=1`, {
          headers: { 'User-Agent': USER_AGENT, 'Accept': 'text/html', 'Accept-Language': 'zh-CN,zh;q=0.9' },
          timeout: 15000,
        })
        const $ = cheerio.load(resp.data)
        $('a.iusc').each((_, el) => {
          try { const m = JSON.parse($(el).attr('m') || '{}'); if (m.murl) all.push(m.murl) } catch {}
        })
        $('img.mimg').each((_, el) => {
          const s = $(el).attr('src') || $(el).attr('data-src')
          if (s?.startsWith('http') && !all.includes(s)) all.push(s)
        })
      } catch {}
    }
  }

  const best = all.map(u => ({ url: u, score: score(u) }))
    .filter(x => x.score >= 30)
    .sort((a, b) => b.score - a.score)

  return best.length > 0 ? best[0].url : null
}

async function main() {
  const targets = process.argv.slice(2)
  if (targets.length === 0) { console.log('用法: node rescrape-cities.cjs 长沙 青岛'); process.exit(1) }
  console.log(`目标城市: ${targets.join(', ')}\n`)

  const existing = fs.existsSync(OUTPUT) ? JSON.parse(fs.readFileSync(OUTPUT, 'utf-8')) : {}

  for (const city of targets) {
    const old = existing[city] || '(无)'
    console.log(`${city}: 旧→${old.substring(0, 80)}...`)
    process.stdout.write(`  爬取中... `)
    const url = await scrape(city)
    if (url) {
      existing[city] = url
      console.log(`✓ ${score(url)}分 → ${url.substring(0, 80)}...`)
    } else {
      console.log(`✗ 未找到合格图片`)
    }
    await new Promise(r => setTimeout(r, 1500))
  }

  fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, 2))
  console.log(`\n已保存到 ${OUTPUT}`)
  console.log('刷新 city-select 页面即可看到新图片')
}

main().catch(console.error)
