/**
 * 审核全部城市图片 → 删除低分/错误图片 → 重新爬取
 * 用法: node scripts/audit-images.cjs
 */
const axios = require('axios')
const cheerio = require('cheerio')
const fs = require('fs')
const path = require('path')

const API = 'http://localhost:3200/api/city'
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'

// 黑名单
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
  'seo/','-seo','spider','crawl','bot.',
  'desktop-wallpaper','-wallpaper','wallpaper-',
  'chartfarosh','cat-breed','mapamundi','politico',
  'nombres','identifier','breed',
  'fantaslook','ruffle','floral','sundress',
]

const GOOD = [
  'ctrip','qunar','tuniu','mafengwo',
  '699pic','nipic','tuchong','photophoto',
  'pixabay','pexels','unsplash','flickr',
  'zhimg','bcebos','baidu.com',
  'wikipedia','wikimedia',
  'gettyimages','shutterstock','alamy','500px',
  'hdslb','bilibili','douyin',
  'travel','scenic','sight','landscape',
]

function score(url) {
  if (!url?.startsWith('http')) return 0
  const l = url.toLowerCase()
  for (const b of BLOCKED) if (l.includes(b)) return 0
  for (const g of GOOD) if (l.includes(g)) return 85
  let s = 25
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
  return all.map(u => ({ url: u, score: score(u) }))
    .filter(x => x.score >= 30)
    .sort((a, b) => b.score - a.score)
}

async function main() {
  // 1. 拉取全部图片
  console.log('拉取数据库图片映射...')
  const resp = await axios.get(`${API}/images/map`)
  const all = resp.data.data || {}
  const entries = Object.entries(all)
  console.log(`共 ${entries.length} 条记录\n`)

  // 2. 审核打分
  const bad = []
  for (const [city, url] of entries) {
    const s = score(url)
    if (s < 40) {
      bad.push({ city, url, score: s })
      console.log(`❌ ${city} (${s}分) → ${url.substring(0, 80)}...`)
    }
  }
  console.log(`\n不合格: ${bad.length}/${entries.length}`)

  if (bad.length === 0) { console.log('全部合格！'); return }

  // 3. 删除低分记录
  console.log('\n删除低分记录并重新爬取...\n')
  for (const item of bad) {
    process.stdout.write(`[${bad.indexOf(item)+1}/${bad.length}] ${item.city} (旧${item.score}分) → `)
    const results = await scrape(item.city)
    if (results.length > 0 && results[0].score >= 50) {
      const good = results[0]
      console.log(`✓ 新${good.score}分 → ${good.url.substring(0, 70)}...`)
      // 更新到数据库
      try {
        await axios.post(`${API}/images/batch`, [{ cityName: item.city, imageUrl: good.url, source: 'bing' }])
      } catch {}
    } else {
      console.log(`✗ 未找到替代图片`)
    }
    await new Promise(r => setTimeout(r, 1200))
  }

  console.log('\n审核修复完成！刷新页面查看效果')
}

main().catch(console.error)
