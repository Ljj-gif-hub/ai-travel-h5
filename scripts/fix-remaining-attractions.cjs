/**
 * 针对 V2 后剩余的 34 个低质量图片做最后一次修复
 * 策略：更宽泛的搜索词 + 接受 Wikipedia 带尺寸的图片
 */
const axios = require('axios')
const cheerio = require('cheerio')
const fs = require('fs')
const path = require('path')

const TARGETS = [
  '鸟巢水立方','东方明珠','杨柳青古镇','解放碑','西樵山','腾龙洞','白水洋','龙亭',
  '棒棰岛','梅里雪山','德天瀑布','蜈支洲岛','分界洲岛','太行山大峡谷','扎尕那',
  '茶卡盐湖','六盘山','富士山','圣淘沙岛','加勒古堡','霍尔顿平原','雅典卫城',
  '帕特农神庙','米克诺斯风车','北角','布拉格城堡','赫瓦尔岛','克里姆林宫',
  '奇琴伊察','基督救世主雕像','大堡礁','楠迪沙滩','帕皮提市场','恩戈罗恩戈罗火山口',
]

const OUTPUT = path.join(__dirname, '..', 'public', 'attraction-images.json')
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'

// 对这些顽固景点，接受更多域名
const ACCEPT_DOMAINS = [
  'upload.wikimedia.org', 'wikipedia',
  'ctrip.com', 'c-ctrip.com',
  'qunarzz.com', 'qunar.com',
  '699pic.com/photo',
  'pixabay.com', 'pexels.com',
  'flickr.com', 'staticflickr.com',
  'zhimg.com',
  'bcebos.com',
  'gettyimages.com',
  'shutterstock.com',
  'unsplash.com',
  'mafengwo.net', 'mafengwo.cn',
  'tuniu.com',
  'lvmama.com',
  'travel.qunar.com',
]

const BLOCKED = [
  'etsystatic', 'tukuppt', 'duitang', '21cnjy', 'hdslb', 'xiaguanzhan',
  'everyonepiano', 'xuetimes', 'lookkle', 'nosotrosxp', '3dmdb', 'foxnews',
  'wwd.com', 'popsugar', 'freepik', 'hexun', 'girlstyle', 'redocn', 'ibaotu',
  'wallpapers.com', 'naukri', 'roadaffair', 'publive', '21qupu', 'aya02082101',
  'village-v', 'yungyujoint', 'zhhainiao', 'free3d', 'redd.it',
]

function scoreUrl(url) {
  if (!url || !url.startsWith('http')) return 0
  const l = url.toLowerCase()
  for (const d of BLOCKED) { if (l.includes(d)) return 0 }
  if (l.endsWith('.svg') || l.endsWith('.gif')) return 0
  if (l.includes('thumb') || l.includes('wh300') || l.includes('300.jpg')) return 0
  if (l.includes('preview') && l.includes('tukuppt')) return 0
  if (l.includes('element/') || l.includes('video_cover')) return 0
  for (const d of ACCEPT_DOMAINS) { if (l.includes(d)) return 80 }
  // 额外接受一些
  if (l.includes('699pic.com/photo')) return 70
  if (l.match(/\.(jpg|jpeg|png|webp)/)) return 30
  return 15
}

async function scrapeSpot(spotName) {
  // 更激进的搜索词
  const queries = [
    `${encodeURIComponent(spotName)} 风光 摄影`,
    `${encodeURIComponent(spotName)} scenic photography`,
    `${encodeURIComponent(spotName)} tourism`,
  ]
  const allImages = []
  for (const query of queries) {
    if (allImages.length >= 10) break
    try {
      const resp = await axios.get(`https://cn.bing.com/images/search?q=${query}&first=1`, {
        headers: { 'User-Agent': UA, 'Accept': 'text/html', 'Accept-Language': 'zh-CN,zh;q=0.9' },
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
    .filter(i => i.score >= 25).sort((a, b) => b.score - a.score)
  return scored.length > 0 ? scored[0].url : null
}

async function main() {
  const existing = JSON.parse(fs.readFileSync(OUTPUT, 'utf-8'))
  let fixed = 0
  for (let i = 0; i < TARGETS.length; i++) {
    const spot = TARGETS[i]
    process.stdout.write(`[${i + 1}/${TARGETS.length}] ${spot} ... `)
    const url = await scrapeSpot(spot)
    if (url) {
      existing[spot] = url
      fixed++
      console.log(`✓`)
    } else {
      console.log(`✗`)
    }
    await new Promise(r => setTimeout(r, 300 + Math.random() * 200))
  }
  fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, '\t'))
  console.log(`\n修复 ${fixed}/${TARGETS.length}`)
}

main().catch(console.error)
