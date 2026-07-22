/**
 * 景点图片爬取 V2 — 更精准的搜索词 + 严格审核
 *
 * 改进：
 * 1. 搜索词更精准（景点名+风景+实拍+高清）
 * 2. 严格域名白名单：只接受旅游/摄影/百科类域名
 * 3. 过滤缩略图、小图、设计素材
 * 4. 多关键词并行 + 取最高分
 *
 * 用法：node scripts/scrape-attraction-images-v2.cjs
 * 输出：public/attraction-images.json（覆盖）
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
console.log(`从组件提取到 ${SPOTS.length} 个唯一景点\n`)

const OUTPUT = path.join(__dirname, '..', 'public', 'attraction-images.json')
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'

// ========== 严格的域名白名单（只有这些域名可以高分通过） ==========
const ELITE_DOMAINS = [
  'upload.wikimedia.org/wikipedia/commons/',  // Wikipedia 原图（非缩略图）
  'ctrip.com', 'c-ctrip.com', 'dimg',          // 携程
  'qunarzz.com', 'qunar.com',                   // 去哪儿
  '699pic.com/photo/',                          // 摄图网照片（非element/design/video_cover）
  'pixabay.com', 'pexels.com',                  // 免费图库
  'flickr.com', 'staticflickr.com',             // Flickr
  'zhimg.com',                                  // 知乎图片
]

// 中等域名（接受但分低一些）
const GOOD_DOMAINS = [
  'bcebos.com', 'baidu.com',                    // 百度系
  'shutterstock.com', 'gettyimages.com',        // 商业图库
  'unsplash.com',                               // Unsplash
  'tuchong.com', 'nipic.com',                  // 国内图库
  'photophoto.cn',
]

// ========== 绝对屏蔽 ==========
const BLOCKED_DOMAINS = [
  'etsystatic.com',                              // Etsy（商品图）
  'tukuppt.com',                                // 模板素材
  '588ku.com',                                   // 千库网素材
  'duitang.com',                                 // 堆糖（收集站）
  '21cnjy.com', '21cnjy',                        // 教育资源
  'hdslb.com',                                   // B站
  'xiaguanzhan.com',                             // 下载站
  'everyonepiano.com',                           // 钢琴
  'xuetimes.com',                                // 留学
  'lookkle.com',                                 // AI评测
  'roadaffair.com',                              // 旅行博客（质量低）
  'naukri.com',                                  // 招聘
  'nosotrosxp.com',                              // 随机
  '3dmdb.com',                                   // 3D模型
  'foxnews.com',                                 // 新闻
  'wwd.com',                                     // 时尚
  'popsugar.com', 'popsugar-assets.com',        // 娱乐
  'freepik.com',                                 // 素材
  'hexun.com',                                   // 财经
  'girlstyle.com',                               // 女性
  'zcool.com',                                   // 站酷
  'pconline.com',                                // 太平洋
  'sohu.com', 'sina.com', 'sinaimg.com',        // 门户新闻
  'ifeng.com', 'ifengimg.com',                  // 凤凰
  '163.com',                                     // 网易
  'guancha.cn',                                  // 观察者
  'thepaper.cn',                                 // 澎湃
  'redocn.com',                                  // 红动中国素材
  'ibaotu.com',                                  // 包图素材
  'v-b.com',                                    // 视觉中国（素材侧）
  'wallpapers.com',                              // 壁纸站
  'wp.com',                                      // WordPress缩略
  'cnblogs.com',                                 // 博客园
  'alicdn.com',                                  // 阿里商品图
  'taobao.com', 'tmall.com', 'jd.com',          // 电商
  'meituan.com', 'dianping.com',                 // 团购
  'xiaohongshu.com',                             // 小红书
  'douyin.com', 'tiktok.com',                   // 短视频
  'bilibili.com',                                // B站
  'youtube.com', 'ytimg.com',                   // YouTube
  'facebook.com', 'instagram.com',               // 社交
  'twitter.com', 'x.com',
  'village-v.co.jp',                             // 日本商业
  'yungyujoint.com.tw',                         // 台湾商业
  'preview.21cnjy',                              // 教育预览
  'aya02082101.com',                            // 随机博客
  '21qupu.com',                                  // 简谱
]

// 其他不可接受的URL特征
const BLOCKED_PATTERNS = [
  'thumb.', 'thumbnail', 'thumb-',              // 缩略图
  'wh300', '300.jpg', '300x', 'x300',            // 300px小图
  'wh150', '150.jpg',                            // 150px超小图
  '100x100', '200x200',                          // 极小图
  '.png_300', '.png_150',                        // 小png
  'element/', 'desgin', 'video_cover',           // 699pic素材区
  'preview', 'png_preview',                      // 预览图
  'icon', 'logo', 'avatar',                      // 图标/logo
  'chart', 'graph', 'diagram',                   // 图表
  'wallpaper-', 'wallpaper_',                    // 壁纸
  'product', 'mall', 'shop',                     // 商品
  'cat-', 'dog-', 'pet-', 'animal',              // 宠物
  'food', 'recipe', 'cooking',                   // 食物
  'meme', 'funny', 'game', 'gaming',             // 娱乐
  'music', 'piano', 'guitar',                    // 音乐
  'portrait', 'selfie', 'headshot',              // 人像
  'mapamundi', 'politico', 'nombres',            // 西语政治
  'breed', 'identifier',                         // 生物
  'barcode', 'qr',                               // 条码
  'document', 'form', 'contract',               // 文档
  'screenshot',                                  // 截图
  '/fw/', '!/fw/',                               // 压缩图标记
]

function scoreUrl(url) {
  if (!url || !url.startsWith('http')) return 0
  const l = url.toLowerCase()

  // 域名黑名单 → 直接0分
  for (const d of BLOCKED_DOMAINS) {
    if (l.includes(d)) return 0
  }

  // URL特征黑名单 → 0分
  for (const p of BLOCKED_PATTERNS) {
    if (l.includes(p)) return 0
  }

  // SVG/GIF → 0分
  if (l.endsWith('.svg') || l.endsWith('.gif')) return 0

  // PNG 需要更严格检查（很多PNG是设计素材）
  if (l.endsWith('.png')) {
    // 只接受来自可信图片源的PNG
    const trustedPng = ['wikimedia', 'wikipedia', 'pixabay', 'pexels', 'flickr', 'zhimg']
    if (!trustedPng.some(d => l.includes(d))) return 0
  }

  // 精英域名 → 95分
  for (const d of ELITE_DOMAINS) {
    if (l.includes(d)) {
      // Wikipedia 需要排除缩略图
      if (d.includes('wikimedia') && l.includes('/thumb/')) return 0
      return 95
    }
  }

  // 良好域名 → 70分
  for (const d of GOOD_DOMAINS) {
    if (l.includes(d)) return 70
  }

  // 699pic 需要细分类别检查
  if (l.includes('699pic.com')) {
    if (l.includes('/photo/')) return 75
    if (l.includes('/element/') || l.includes('/video_cover/') || l.includes('/desgin')) return 0
    return 30 // 其他699pic区域
  }

  // 未知域名，基础分低
  let score = 15
  if (url.length < 200) score += 5
  if (url.length > 500) score -= 5
  if (l.match(/\.(jpg|jpeg|webp)/)) score += 5
  if (l.includes('travel') || l.includes('scenic') || l.includes('view') || l.includes('landscape')) score += 5
  return Math.max(0, Math.min(100, score))
}

/** 为景点生成更精准的搜索词 */
function getSearchQueries(spotName) {
  return [
    `${encodeURIComponent(spotName)} 风景 实拍 高清`,
    `${encodeURIComponent(spotName)} 旅游景点`,
    `${encodeURIComponent(spotName)} scenic spot travel`,
  ]
}

async function scrapeSpot(spotName) {
  const queries = getSearchQueries(spotName)
  const allImages = []

  for (const query of queries) {
    if (allImages.length >= 15) break
    try {
      const resp = await axios.get(`https://cn.bing.com/images/search?q=${query}&first=1`, {
        headers: {
          'User-Agent': UA,
          'Accept': 'text/html,application/xhtml+xml',
          'Accept-Language': 'zh-CN,zh;q=0.9',
        },
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

  const scored = allImages
    .map(i => ({ ...i, score: scoreUrl(i.url) }))
    .filter(i => i.score >= 40)  // 提高门槛：从20提到40
    .sort((a, b) => b.score - a.score)

  return scored.length > 0 ? scored[0].url : null
}

async function main() {
  // 加载已有数据
  const existing = {}
  if (fs.existsSync(OUTPUT)) {
    try { Object.assign(existing, JSON.parse(fs.readFileSync(OUTPUT, 'utf-8'))) } catch {}
  }

  // 筛选出需要重爬的：无图 或 低分（<40）
  const needRescue = []
  for (const spot of SPOTS) {
    const url = existing[spot]
    if (!url || scoreUrl(url) < 40) {
      needRescue.push(spot)
    }
  }

  console.log(`已有 ${SPOTS.length - needRescue.length} 张合格图片，需重爬 ${needRescue.length} 个\n`)

  let good = 0
  for (let i = 0; i < needRescue.length; i++) {
    const spot = needRescue[i]
    process.stdout.write(`[${i + 1}/${needRescue.length}] ${spot} ... `)

    const url = await scrapeSpot(spot)
    if (url) {
      existing[spot] = url
      good++
      console.log(`✓ (${scoreUrl(url)}分)`)
    } else {
      console.log('✗')
    }

    if ((i + 1) % 20 === 0) {
      fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, '\t'))
      console.log(`  → 已保存 | ${good}/${i + 1}\n`)
    }
    await new Promise(r => setTimeout(r, 400 + Math.random() * 300))
  }

  fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, '\t'))
  const valid = Object.values(existing).filter(u => u && scoreUrl(u) >= 40).length
  console.log(`\n完成！${valid}/${SPOTS.length} 有合格图片（≥40分）`)
  console.log(`输出: ${OUTPUT}`)
}

main().catch(console.error)
