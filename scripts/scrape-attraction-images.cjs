/**
 * 景点图片爬取脚本
 * 从 AttractionSelectView.vue 提取所有景点，Bing 搜索图片
 *
 * 用法：node scripts/scrape-attraction-images.cjs
 * 输出：public/attraction-images.json
 */
const axios = require('axios')
const cheerio = require('cheerio')
const fs = require('fs')
const path = require('path')

// 从 AttractionSelectView.vue 提取景点列表
const vueContent = fs.readFileSync(path.join(__dirname, '..', 'src', 'views', 'AttractionSelectView.vue'), 'utf-8')
const spotMatches = vueContent.match(/spots:\s*\[([^\]]+)\]/g)
const allSpots = []
spotMatches.forEach(m => {
  const names = m.match(/'([^']+)'/g)
  if (names) names.forEach(n => allSpots.push(n.replace(/'/g, '')))
})
const SPOTS = [...new Set(allSpots)]
console.log(`从 AttractionSelectView.vue 提取到 ${SPOTS.length} 个唯一景点\n`)

const OUTPUT = path.join(__dirname, '..', 'public', 'attraction-images.json')
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36'
const DELAY_MS = 500

const BLOCKED = [
  'icon', 'logo', 'avatar', 'chart', 'graph', 'barcode', 'qr',
  'product', 'mall', 'shop', 'price', 'taobao', 'alibaba',
  'cat-', 'dog-', 'pet-', 'animal', 'squirrel',
  'piano', 'music', 'guitar',
  'chemistry', 'molecule',
  'car-', 'auto-part', 'tire-',
  'pharmacy', 'drug', 'medicine',
  'food', 'recipe', 'cooking', 'dish',
  'meme', 'funny', 'game', 'gaming',
  'code/', '/code', 'font.',
  'teenvogue', 'spicer.com',
]

const GOOD_DOMAINS = [
  'wikipedia', 'wikimedia',
  'ctrip.com', 'qunarzz.com', 'mafengwo',
  '699pic.com/photo', 'tuchong.com',
  'pixabay.com', 'pexels.com', 'unsplash.com', 'flickr.com',
  'zhimg.com', 'bcebos.com',
  'gettyimages', 'shutterstock',
]

function scoreUrl(url) {
  if (!url || !url.startsWith('http')) return 0
  const l = url.toLowerCase()
  for (const p of BLOCKED) { if (l.includes(p)) return 0 }
  if (l.endsWith('.svg') || l.endsWith('.gif')) return 0
  for (const d of GOOD_DOMAINS) { if (l.includes(d)) return 85 }
  let s = 20
  if (url.length < 200) s += 10
  if (url.length > 600) s -= 10
  if (l.match(/\.(jpg|jpeg|png|webp)/)) s += 5
  if (l.includes('travel') || l.includes('scenic') || l.includes('view') || l.includes('landscape')) s += 10
  return Math.max(0, Math.min(100, s))
}

async function scrapeSpot(spotName) {
  const queries = [
    `${encodeURIComponent(spotName)} 景点 风景 实拍`,
    `${encodeURIComponent(spotName)} travel attraction landscape`,
  ]
  const allImages = []
  for (const query of queries) {
    if (allImages.length >= 8) break
    try {
      const resp = await axios.get(`https://cn.bing.com/images/search?q=${query}&first=1`, {
        headers: { 'User-Agent': USER_AGENT, 'Accept': 'text/html', 'Accept-Language': 'zh-CN,zh;q=0.9' },
        timeout: 10000,
      })
      const $ = cheerio.load(resp.data)
      $('a.iusc').each((_, el) => {
        try {
          const m = $(el).attr('m')
          if (m) { const p = JSON.parse(m); if (p.murl) allImages.push({ url: p.murl }) }
        } catch {}
      })
    } catch {}
  }

  if (allImages.length === 0) return null
  const scored = allImages.map(i => ({ ...i, score: scoreUrl(i.url) }))
    .filter(i => i.score >= 20).sort((a, b) => b.score - a.score)
  return scored.length > 0 ? scored[0].url : null
}

async function main() {
  const existing = {}
  if (fs.existsSync(OUTPUT)) {
    try { Object.assign(existing, JSON.parse(fs.readFileSync(OUTPUT, 'utf-8'))) } catch {}
  }

  const pending = SPOTS.filter(s => !existing[s] || !existing[s])
  console.log(`已有 ${Object.keys(existing).filter(k => existing[k]).length} 张图片，待爬 ${pending.length} 个\n`)

  let good = 0
  for (let i = 0; i < pending.length; i++) {
    const spot = pending[i]
    process.stdout.write(`[${i + 1}/${pending.length}] ${spot} ... `)

    const url = await scrapeSpot(spot)
    if (url) {
      existing[spot] = url
      good++
      console.log('✓')
    } else {
      existing[spot] = ''
      console.log('✗')
    }

    if ((i + 1) % 20 === 0) {
      fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, '\t'))
      console.log(`  → 已保存 | ${good}/${i + 1}\n`)
    }
    await new Promise(r => setTimeout(r, DELAY_MS + Math.random() * 300))
  }

  fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, '\t'))
  const valid = Object.values(existing).filter(Boolean).length
  console.log(`\n完成！${valid}/${SPOTS.length} 有图片`)
  console.log(`输出: ${OUTPUT}`)
}

main().catch(console.error)
