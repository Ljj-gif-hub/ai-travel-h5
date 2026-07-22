/**
 * 港澳台城市图片专项重爬脚本
 * 问题：绝大多数港澳台图片完全错误（猫照、汽车零件、吉他网站等）
 * 策略：Wikipedia API → Pexels API → Bing 精准搜索
 *
 * 用法：node scripts/rescrape-hk-tw.cjs
 */
const axios = require('axios')
const cheerio = require('cheerio')
const fs = require('fs')
const path = require('path')

const OUTPUT = path.join(__dirname, '..', 'public', 'city-images.json')
const USER_AGENT = 'TravelApp/1.0 (image-rescrape; travel-app@example.com)'

// ==================== 港澳台城市 Wikipedia 精确映射 ====================
const WIKI_MAP = {
  // === 香港 ===
  '香港': 'Hong_Kong',
  '中西区': 'Central_and_Western_District',
  '湾仔区': 'Wan_Chai_District',
  '东区': 'Eastern_District,_Hong_Kong',
  '南区': 'Southern_District,_Hong_Kong',
  '油尖旺区': 'Yau_Tsim_Mong_District',
  '深水埗区': 'Sham_Shui_Po_District',
  '九龙城区': 'Kowloon_City_District',
  '黄大仙区': 'Wong_Tai_Sin_District',
  '观塘区': 'Kwun_Tong_District',
  '荃湾区': 'Tsuen_Wan_District',
  '屯门区': 'Tuen_Mun_District',
  '元朗区': 'Yuen_Long_District',
  '北区': 'North_District,_Hong_Kong',
  '大埔区': 'Tai_Po_District',
  '沙田区': 'Sha_Tin_District',
  '西贡区': 'Sai_Kung_District',
  '离岛区': 'Islands_District,_Hong_Kong',
  '葵青区': 'Kwai_Tsing_District',

  // === 澳门 ===
  '澳门': 'Macau',
  '花地玛堂区': 'Nossa_Senhora_de_Fátima,_Macau',
  '圣安多尼堂区': 'Santo_António,_Macau',
  '大堂区': 'Sé,_Macau',
  '望德堂区': 'São_Lázaro',
  '风顺堂区': 'São_Lourenço,_Macau',
  '嘉模堂区': 'Nossa_Senhora_do_Carmo,_Macau',
  '路氹填海区': 'Cotai',
  '圣方济各堂区': 'São_Francisco_Xavier,_Macau',

  // === 台湾 ===
  '台北': 'Taipei',
  '新北': 'New_Taipei_City',
  '高雄': 'Kaohsiung',
  '台中': 'Taichung',
  '台南': 'Tainan',
  '桃园': 'Taoyuan,_Taiwan',
  '花莲': 'Hualien_City',
  '台东': 'Taitung_City',
  '宜兰': 'Yilan_City',
  '南投': 'Nantou_City',
  '嘉义': 'Chiayi',
  '屏东': 'Pingtung_City',
  '苗栗': 'Miaoli_City',
  '新竹': 'Hsinchu',
  '彰化': 'Changhua_City',
  '云林': 'Douliu',
  '澎湖': 'Penghu',
  '金门': 'Kinmen',
  '马祖': 'Matsu_Islands',
  '基隆': 'Keelung',

  // === 台湾景点 ===
  '淡水': 'Tamsui',
  '九份': 'Jiufen',
  '十分': 'Shifen',
  '阳明山': 'Yangmingshan',
  '野柳': 'Yehliu',
  '美浓': 'Meinong_District,_Kaohsiung',
  '逢甲': 'Feng_Chia_Night_Market',
  '高美湿地': 'Gaomei_Wetlands',
  '关子岭': 'Guanziling',
  '太鲁阁': 'Taroko_National_Park',
  '七星潭': 'Qixingtan_Beach',
  '清水断崖': 'Qingshui_Cliffs',
  '绿岛': 'Green_Island,_Taiwan',
  '兰屿': 'Orchid_Island',
  '知本': 'Zhiben_Hot_Spring',
  '礁溪': 'Jiaoxi',
  '太平山': 'Taipingshan_National_Forest_Recreation_Area',
  '日月潭': 'Sun_Moon_Lake',
  '清境农场': 'Qingjing_Farm',
  '合欢山': 'Hehuanshan',
  '阿里山': 'Alishan_National_Scenic_Area',
  '奋起湖': 'Fenqihu',
  '垦丁': 'Kenting_National_Park',
  '小琉球': 'Liuqiu_Island',
  '司马库斯': 'Smangus',
  '鹿港': 'Lukang,_Changhua',
  '八卦山': 'Bagua_Mountain',
  '双心石沪': 'Double_Heart_of_Stacked_Stones',
}

const ALL_CITIES = Object.keys(WIKI_MAP)

// ==================== 核心函数 ====================

/** 从 Wikipedia REST API 获取页面主图（短超时，国内网络下快速失败） */
async function fetchWikiImage(wikiTitle) {
  const url = `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(wikiTitle)}`
  try {
    const resp = await axios.get(url, {
      headers: { 'User-Agent': USER_AGENT },
      timeout: 4000,
    })
    const data = resp.data
    if (data.thumbnail && data.thumbnail.source) {
      return data.thumbnail.source
    }
    if (data.originalimage && data.originalimage.source) {
      return data.originalimage.source
    }
  } catch (e) { /* 超时或404，快速失败 */ }
  return null
}

/** 从 Wikipedia action API 获取页面主图（备用，短超时） */
async function fetchWikiImageV2(wikiTitle) {
  const url = `https://en.wikipedia.org/w/api.php?action=query&titles=${encodeURIComponent(wikiTitle)}&prop=pageimages&format=json&pithumbsize=800&origin=*`
  try {
    const resp = await axios.get(url, {
      headers: { 'User-Agent': USER_AGENT },
      timeout: 4000,
    })
    const pages = resp.data.query.pages
    for (const key of Object.keys(pages)) {
      if (pages[key].thumbnail && pages[key].thumbnail.source) {
        return pages[key].thumbnail.source
      }
    }
  } catch {}
  return null
}

/** 从 Pexels API 搜索（免费，无 API Key 用网页搜索兜底） */
async function fetchPexelsImage(query) {
  // 尝试直接访问 Pexels 搜索页获取图片
  try {
    const resp = await axios.get(`https://www.pexels.com/zh-cn/search/${encodeURIComponent(query)}/`, {
      headers: {
        'User-Agent': USER_AGENT,
        'Accept': 'text/html',
      },
      timeout: 15000,
    })
    const $ = cheerio.load(resp.data)
    // 从 meta 标签或 img 标签中提取
    const imgs = []
    $('img[src*="pexels.com"]').each((_, el) => {
      const src = $(el).attr('src') || $(el).attr('data-src')
      if (src && src.includes('pexels.com/photos/') && !src.includes('profile')) {
        imgs.push(src)
      }
    })
    if (imgs.length > 0) {
      // 选最大的图
      return imgs.sort((a, b) => b.length - a.length)[0]
    }
  } catch {}
  return null
}

/** Bing 精准搜索 — 更好的关键词 */
async function fetchBingImage(cityName, englishName) {
  const queries = [
    `${encodeURIComponent(cityName)} 风景 旅游 实拍`,
    `${encodeURIComponent(englishName || cityName)} travel landscape`,
    `${encodeURIComponent(cityName)} 地标 景点`,
  ]

  const allImages = []
  for (const query of queries) {
    if (allImages.length >= 8) break
    try {
      const resp = await axios.get(`https://cn.bing.com/images/search?q=${query}&first=1`, {
        headers: {
          'User-Agent': USER_AGENT,
          'Accept': 'text/html,application/xhtml+xml',
          'Accept-Language': 'zh-CN,zh;q=0.9',
        },
        timeout: 8000,
      })
      const $ = cheerio.load(resp.data)
      $('a.iusc').each((_, el) => {
        try {
          const m = $(el).attr('m')
          if (m) {
            const parsed = JSON.parse(m)
            if (parsed.murl) allImages.push({ url: parsed.murl, title: parsed.t || '' })
          }
        } catch {}
      })
    } catch {}
  }

  if (allImages.length === 0) return null

  // 评分过滤
  const scored = allImages
    .map(img => ({ ...img, score: scoreUrl(img.url) }))
    .filter(img => img.score >= 20)
    .sort((a, b) => b.score - a.score)

  return scored.length > 0 ? scored[0].url : null
}

/** URL 质量评分 */
function scoreUrl(url) {
  if (!url || !url.startsWith('http')) return 0
  const l = url.toLowerCase()

  const BLOCKED = [
    'icon', 'logo', 'avatar', 'chart', 'graph', 'barcode', 'qr',
    'product', 'mall', 'shop', 'price', 'taobao', 'alibaba',
    'cat-', 'dog-', 'pet-', 'animal', 'squirrel',
    'piano', 'music', 'sheet-music', 'guitar',
    'chemistry', 'molecule', 'covalent',
    'equestrian', 'horse', 'saddle',
    'car-', 'auto-part', 'tire-', 'cv-joint', 'homocinetico',
    'pharmacy', 'drug-', 'medicine', 'drugs',
    'portrait', 'selfie', 'headshot', 'passport',
    'document', 'form', 'contract', 'invoice',
    'food', 'recipe', 'cooking', 'dish',
    'meme', 'funny', 'game', 'gaming',
    'code/', 'code.', '/code', 'font.',
    'mapamundi', 'politico', 'nombres', 'breed',
    'teenvogue', 'spicer.com', 'xinyuepu', 'wxdechang',
    'weibomingzi', 'ibaotu', 'tukuppt', 'zhulong',
  ]
  for (const p of BLOCKED) {
    if (l.includes(p)) return 0
  }

  const GOOD = [
    'wikipedia', 'wikimedia', 'commons.wikimedia',
    'pexels.com/photos', 'unsplash.com/photos',
    'pixabay.com', 'flickr.com',
    'ctrip.com', 'qunarzz.com', '699pic.com/photo',
    'zhimg.com', 'bcebos.com',
    'gettyimages', 'shutterstock',
  ]
  for (const d of GOOD) {
    if (l.includes(d)) return 90
  }

  let score = 20
  if (url.length < 200) score += 10
  if (url.length > 600) score -= 10
  if (l.endsWith('.gif') || l.endsWith('.svg')) score -= 50
  if (l.endsWith('.png') && l.includes('699pic.com/element')) score = 0 // 699元素图
  if (l.match(/\.(jpg|jpeg|png|webp)/)) score += 5
  if (l.includes('travel') || l.includes('scenic') || l.includes('view') || l.includes('landscape')) score += 10
  return Math.max(0, Math.min(100, score))
}

/** 主函数：并行获取图片 — Wikipedia + Bing 同时跑，谁先成功用谁 */
async function getCityImage(cityName) {
  const wikiTitle = WIKI_MAP[cityName]
  const enName = wikiTitle ? wikiTitle.replace(/_/g, ' ') : cityName

  // Wikipedia + Bing 并行请求
  const wikiPromise = (async () => {
    if (!wikiTitle) return null
    let url = await fetchWikiImage(wikiTitle)
    if (!url) url = await fetchWikiImageV2(wikiTitle)
    if (url && scoreUrl(url) >= 50) return { url, source: 'wikipedia' }
    return null
  })()

  const bingPromise = (async () => {
    const url = await fetchBingImage(cityName, enName)
    if (url && scoreUrl(url) >= 20) return { url, source: 'bing' }
    return null
  })()

  // 用 race：谁先返回就用谁
  const result = await Promise.race([wikiPromise, bingPromise])
  if (result) return result

  // 如果 race 返回的是 null（wiki 先超时失败），等 bing
  const bingResult = await bingPromise
  if (bingResult) return bingResult

  return null
}

// ==================== 主流程 ====================
async function main() {
  console.log('港澳台图片专项重爬\n')
  console.log(`共 ${ALL_CITIES.length} 个城市/景点\n`)

  // 加载已有数据
  const existing = {}
  if (fs.existsSync(OUTPUT)) {
    try { Object.assign(existing, JSON.parse(fs.readFileSync(OUTPUT, 'utf-8'))) } catch {}
  }

  let good = 0, wikiCount = 0, bingCount = 0
  for (let i = 0; i < ALL_CITIES.length; i++) {
    const city = ALL_CITIES[i]
    process.stdout.write(`[${i + 1}/${ALL_CITIES.length}] ${city} (${WIKI_MAP[city]}) ... `)

    const result = await getCityImage(city)
    if (result && result.url) {
      existing[city] = result.url
      good++
      if (result.source === 'wikipedia') wikiCount++
      else bingCount++
      console.log(`✓ ${result.source}`)
    } else {
      console.log(`✗`)
    }

    // 每10个保存一次
    if ((i + 1) % 10 === 0) {
      fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, '\t'))
      console.log(`  → 已保存 | 合格 ${good}/${i + 1} | Wiki:${wikiCount} Bing:${bingCount}\n`)
    }

    // 避免请求过快
    await new Promise(r => setTimeout(r, 300 + Math.random() * 200))
  }

  // 最终保存
  fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, '\t'))
  console.log(`\n====================`)
  console.log(`完成！${good}/${ALL_CITIES.length} 获取到图片`)
  console.log(`来源：Wikipedia ${wikiCount} | Bing ${bingCount}`)
  console.log(`输出: ${OUTPUT}`)
}

main().catch(console.error)
