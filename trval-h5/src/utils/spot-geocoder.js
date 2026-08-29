/**
 * spot-geocoder.js — 通用景点坐标定位器
 *
 * 把任何 (景点名, 目的地城市) 解析成地图坐标。**核心：所有数据源返回的坐标统一
 * 转换到「当前地图瓦片坐标系」(targetCrs)，并做城市 bbox 校验，杜绝跨城市错配。**
 *
 * 优先级（越靠前越精准 / 越依赖 key）：
 *   ① 高德客户端 PlaceSearch（真实 POI GCJ-02；需浏览器已加载 AMap JS，多改名模糊匹配）
 *   ② 后端 /api/map/geocode（AMap web-key → GCJ-02；无 key 时 Nominatim → WGS-84，后端返回 crs）
 *   ③ 精选坐标库（WGS-84，离线兜底，零请求；仅限高置信名的包含匹配）
 *   ④ 全部失败返回 null，由调用方做"城市中心附近散开"兜底
 *
 * 解出后统一按 targetCrs 转换并写 localStorage 缓存（key: "city:name"，crs 随存随转）。
 * 地图为高德(GCJ-02)时 targetCrs='gcj02'；Leaflet/OSM 时='wgs84'。
 */
import { mapApi } from '../api'
import coord from './coord'

const CACHE_KEY = 'spot_geo_v6'
const CACHE_MAX = 800 // 缓存上限，防撑爆 localStorage
/** 中国境内"同一城市"景点相对城市中心的合理最远距离（米）：超过即视为跨城错配，拒绝 */
const CITY_BBOX_M = 100000

/* ------------------------------------------------------------------ *
 * 精选坐标库 (WGS-84)：纬度,经度。收录常用全称+简称，查询按"相等→包含"匹配。
 * 注：词条值为 WGS-84（GPS/OSM 基准），使用前经 coord.convert 归一化到目标坐标系。
 * ------------------------------------------------------------------ */
const KNOWN_POI = {
  // ===== 三亚 =====
  '椰梦长廊': [18.2475, 109.4780], '三亚湾': [18.2460, 109.4530],
  '三亚湾椰梦长廊': [18.2475, 109.4780], '凤凰岛': [18.2310, 109.4730],
  '亚龙湾': [18.2215, 109.6280], '亚龙湾热带天堂森林公园': [18.2191, 109.5839],
  '亚龙湾海滩': [18.2110, 109.6440], '亚龙湾海底世界': [18.2250, 109.6350],
  '亚龙湾玫瑰谷': [18.2300, 109.6000], '大东海': [18.2450, 109.5480],
  '天涯海角': [18.2969, 109.3431], '南山': [18.3020, 109.2090],
  '南山文化旅游区': [18.2980, 109.2130], '蜈支洲岛': [18.3050, 109.7660],
  '西岛': [18.2330, 109.3670], '鹿回头': [18.2270, 109.5030],
  '鹿回头公园': [18.2270, 109.5030], '鹿回头风景区': [18.2270, 109.5030],
  '大小洞天': [18.3130, 109.1690], '第一市场': [18.2430, 109.5080],
  '第一市场夜市': [18.2430, 109.5080], '三亚千古情': [18.2700, 109.5100],
  '三亚国际免税城': [18.3098, 109.7463], '海棠湾免税城': [18.3098, 109.7463],
  '金鸡岭': [18.2460, 109.4950], '金鸡岭公园': [18.2460, 109.4950],
  '椰田古寨': [18.2010, 109.7270], '三亚湾红树林': [18.2490, 109.4730],
  '解放路步行街': [18.2480, 109.5050],
  // ===== 北京 =====
  '天安门': [39.9087, 116.3975], '故宫': [39.9163, 116.3972], '故宫博物院': [39.9163, 116.3972],
  '颐和园': [39.9998, 116.2735], '八达岭长城': [40.3540, 116.0165], '天坛': [39.8822, 116.4074],
  '南锣鼓巷': [39.9370, 116.4030], '王府井': [39.9120, 116.4110], '什刹海': [39.9400, 116.3830],
  '圆明园': [40.0080, 116.2980], '景山公园': [39.9250, 116.3970], '北海公园': [39.9270, 116.3880],
  // ===== 上海 =====
  '外滩': [31.2410, 121.4870], '东方明珠': [31.2397, 121.4998], '城隍庙': [31.2270, 121.4920],
  '豫园': [31.2270, 121.4920], '上海迪士尼': [31.1430, 121.6570], '田子坊': [31.2160, 121.4640],
  '武康路': [31.2100, 121.4400], '迪士尼乐园': [31.1430, 121.6570],
  // ===== 成都 =====
  '宽窄巷子': [30.6690, 104.0620], '锦里': [30.6470, 104.0520], '锦里古街': [30.6470, 104.0520],
  '大熊猫基地': [30.7320, 104.1450], '成都大熊猫繁育研究基地': [30.7320, 104.1450],
  '春熙路': [30.6520, 104.0810], '武侯祠': [30.6470, 104.0480], '都江堰': [31.0070, 103.6180],
  // ===== 重庆 =====
  '洪崖洞': [29.5660, 106.5820], '解放碑': [29.5570, 106.5770], '磁器口': [29.5810, 106.4490],
  '长江索道': [29.5640, 106.5850], '李子坝': [29.5520, 106.5260], '武隆天生三桥': [29.3300, 107.7700],
  // ===== 西安 =====
  '兵马俑': [34.3840, 109.2780], '秦始皇兵马俑': [34.3840, 109.2780], '大雁塔': [34.2220, 108.9640],
  '回民街': [34.2650, 108.9450], '西安城墙': [34.2590, 108.9470], '钟楼': [34.2610, 108.9430],
  '华清宫': [34.3650, 109.2650], '华山': [34.4900, 110.0850],
  // ===== 杭州 =====
  '西湖': [30.2440, 120.1540], '灵隐寺': [30.2450, 120.0910], '雷峰塔': [30.2320, 120.1490],
  '西溪湿地': [30.2680, 120.0700], '河坊街': [30.2420, 120.1700], '断桥': [30.2580, 120.1510],
  // ===== 昆明 =====
  '滇池': [24.9500, 102.6200], '翠湖': [25.0500, 102.7080], '石林': [24.8130, 103.2710],
  '西山': [24.9600, 102.6400], '金马碧鸡坊': [25.0330, 102.7130], '九乡': [24.9900, 103.3200],
  // ===== 桂林 =====
  '漓江': [24.8390, 110.4270], '象鼻山': [25.2670, 110.2990], '阳朔西街': [24.7790, 110.4900],
  '遇龙河': [24.7360, 110.4620], '兴坪古镇': [24.9050, 110.5350], '龙脊梯田': [25.7470, 110.2800],
  // ===== 厦门 =====
  '鼓浪屿': [24.4450, 118.0630], '曾厝垵': [24.4340, 118.1140], '南普陀寺': [24.4430, 118.0940],
  '环岛路': [24.4390, 118.1470], '厦门大学': [24.4410, 118.0940], '沙坡尾': [24.4440, 118.0810],
  // ===== 大理 =====
  '洱海': [25.8620, 100.2140], '大理古城': [25.6940, 100.1610], '苍山': [25.7100, 100.1000],
  '崇圣寺三塔': [25.7060, 100.1500], '喜洲古镇': [25.7750, 100.1220], '双廊': [25.9240, 100.2130],
  // ===== 丽江 =====
  '丽江古城': [26.8720, 100.2340], '玉龙雪山': [27.1000, 100.1890], '束河古镇': [26.8960, 100.1940],
  '蓝月谷': [27.0910, 100.1900], '黑龙潭': [26.8820, 100.2290], '泸沽湖': [27.6900, 100.7900],
  // ===== 张家界 =====
  '天门山': [29.1310, 110.4810], '武陵源': [29.3450, 110.5430], '张家界大峡谷': [29.5500, 110.6300],
  '张家界国家森林公园': [29.3600, 110.5100],
  // ===== 青岛 =====
  '栈桥': [36.0580, 120.3150], '八大关': [36.0530, 120.3430], '崂山': [36.2000, 120.6200],
  '五四广场': [36.0680, 120.3840], '金沙滩': [35.9580, 120.2290],
  // ===== 长沙 =====
  '橘子洲': [28.1870, 112.9590], '岳麓山': [28.1850, 112.9380], '五一广场': [28.1970, 112.9740],
  '太平老街': [28.1960, 112.9690], '湖南省博物馆': [28.2180, 112.9890],
  // ===== 广州 =====
  '广州塔': [23.1060, 113.3190], '沙面': [23.1070, 113.2420], '白云山': [23.1830, 113.3060],
  '上下九': [23.1170, 113.2450], '珠江夜游': [23.1150, 113.2600], '广州长隆': [22.9890, 113.3250],
  // ===== 深圳 =====
  '世界之窗': [22.5350, 113.9740], '大梅沙': [22.6030, 114.3050], '欢乐谷': [22.5360, 113.9810],
  '梧桐山': [22.5980, 114.1900], '市民中心': [22.5470, 114.0580], '深圳湾公园': [22.5160, 113.9760],
  '莲花山公园': [22.5490, 114.0610], '东部华侨城': [22.5860, 114.2990],
  // ===== 南京 =====
  '中山陵': [32.0600, 118.8480], '夫子庙': [32.0210, 118.7900], '总统府': [32.0440, 118.7950],
  '玄武湖': [32.0780, 118.7970], '秦淮河': [32.0210, 118.7890],
  // ===== 苏州 =====
  '拙政园': [31.3240, 120.6290], '平江路': [31.3160, 120.6300], '山塘街': [31.3070, 120.6070],
  '周庄': [31.1130, 120.8570], '同里古镇': [31.1500, 120.7200],
}

/** 泛词：不做包含匹配（避免"公园/广场/海滩"这类词误命中同名景区） */
const GENERIC_WORDS = ['公园', '广场', '景区', '景点', '沙滩', '海滩', '乐园', '古城', '古镇', '步行街', '老街', '夜市', '博物馆', '大学', '风景']

/* ------------------------------------------------------------------ *
 * 内部工具
 * ------------------------------------------------------------------ */
// 去掉注记（【】（）、[]）与冗余空白，仅用于"匹配/缓存键"；不改动原始名称
function normName(name) {
  return String(name || '')
    .replace(/【[^】]*】/g, ' ')
    .replace(/（[^）]*）/g, ' ')
    .replace(/\([^)]*\)/g, ' ')
    .replace(/\[[^\]]*\]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function readCache() {
  try { return JSON.parse(localStorage.getItem(CACHE_KEY) || '{}') } catch { return {} }
}
function writeCache(cache) {
  try { localStorage.setItem(CACHE_KEY, JSON.stringify(cache)) } catch { /* 忽略 */ }
}
function trimCache(cache) {
  const keys = Object.keys(cache)
  if (keys.length <= CACHE_MAX) return
  keys.slice(0, keys.length - CACHE_MAX).forEach(k => { delete cache[k] })
}

// 常用后缀词：作为"重试变体"退一步再搜（先搜全名，再搜去后缀核心）
const TRAILING_WORDS = ['景区', '景点', '风景区', '名胜区', '旅游区', '度假区', '游览区',
  '旅游度假区', '海滨', '海滩', '沙滩', '乐园', '公园', '广场', '夜市', '夜景', '门票', '遗址']

// 从一段 LLM 生成的名称里提取"候选地标词"，按长短/精确度排序，供多轮搜索
function queryVariants(name) {
  const core = normName(name).replace(/[。.!！?？]+$/g, '')
  const set = []
  const push = (s) => { s = (s || '').trim(); if (s && !set.includes(s)) set.push(s) }
  push(core)
  // "大东海或三亚湾自由活动" → 取或/和/与、 分隔的第一个地标（通常为具体景点）
  const firstPart = core.split(/[或和与/,，、]/)[0]
  if (firstPart) push(firstPart)
  // 去掉一类通用后缀再试（如"蜈支洲岛景区" → "蜈支洲岛"）
  for (const w of TRAILING_WORDS) {
    if (core.length > w.length && core.endsWith(w)) push(core.slice(0, -w.length))
  }
  return set
}

// 名称相似度打分（0~100），用于在多条 PlaceSearch 结果里挑最像目标的那一个
function similarity(query, resultName) {
  const a = normName(query).toLowerCase()
  const b = normName(resultName).toLowerCase()
  if (!a || !b) return 0
  if (a === b) return 100
  if (a.includes(b) || b.includes(a)) return 60 + Math.min(a.length, b.length)
  // 公共汉字 token 占比
  const toks = (s) => new Set(s.match(/[一-龥]+/g) || [])
  const ta = toks(a), tb = toks(b)
  let c = 0; ta.forEach(t => { if (tb.has(t)) c++ })
  return c * 5
}

/** 城市 bbox 校验：候选点超过目标城市中心一定距离即视为跨城错配，拒绝 */
function cityPlausible(lat, lng, cityCenter) {
  if (!cityCenter || !isFinite(cityCenter.lat) || !isFinite(cityCenter.lng)) return true
  // 城市中心在中国境外/未知时不做 bbox 约束（境外景点允许）
  if (!coord.isInChina(cityCenter.lat, cityCenter.lng)) return true
  return coord.distanceMeters(lat, lng, cityCenter.lat, cityCenter.lng) <= CITY_BBOX_M
}

/* ------------------------------------------------------------------ *
 * 各数据源解析（全部返回「原生坐标系 crs」，由主流程统一转换 + 校验）
 * ------------------------------------------------------------------ */

/** ① 精选坐标库：归一化后按"完全相等 → 名称互相包含（取更长者 + 排除泛词）"匹配，WGS-84 */
function curatedCoord(name) {
  const n = normName(name)
  if (!n) return null
  if (KNOWN_POI[n]) { const v = KNOWN_POI[n]; return { lat: v[0], lng: v[1], source: 'curated', crs: 'wgs84' } }
  // 仅允许"较具体"的名称做包含匹配；泛词直接拒绝，防止"公园/海滩"误命中同名景区
  if (n.length < 3 || GENERIC_WORDS.includes(n)) return null
  let best = null
  for (const k in KNOWN_POI) {
    if ((n.includes(k) || k.includes(n)) && k.length >= 3) {
      if (!best || k.length > best.len) best = { len: k.length, v: KNOWN_POI[k] }
    }
  }
  return (best && best.len >= 3) ? { lat: best.v[0], lng: best.v[1], source: 'curated', crs: 'wgs84' } : null
}

/** ② 高德客户端 PlaceSearch：多改名 + 最优匹配，GCJ-02（需浏览器已加载 AMap JS） */
function amapGeocode(name, city) {
  return new Promise(resolve => {
    const map = window.AMap
    if (!map || typeof map.plugin !== 'function') return resolve(null)
    try {
      map.plugin(['AMap.PlaceSearch'], () => {
        const cityShort = String(city || '').replace(/[市省]$/, '') || ''
        const ps = new map.PlaceSearch({ pageSize: 10, citylimit: !!cityShort, extensions: 'base' })
        if (cityShort) ps.setCity(cityShort)
        const tried = queryVariants(name)
        const attempt = (i) => {
          if (i >= tried.length) return resolve(null)
          const q = tried[i]
          try {
            ps.search(q, (status, result) => {
              const pois = (status === 'complete' && result && result.poiList && result.poiList.pois) || []
              if (!pois.length) return attempt(i + 1)
              // 在结果里挑跟查询最像、且带坐标的
              let best = null, bestScore = -1
              for (const p of pois) {
                if (!p.location || !isFinite(p.location.lat) || !isFinite(p.location.lng)) continue
                const sc = similarity(q, p.name)
                if (sc > bestScore) { bestScore = sc; best = p }
              }
              // 太不像（如城市级泛匹配）则换下一个改名
              if (best && bestScore >= 30) resolve({ lat: best.location.lat, lng: best.location.lng, source: 'amap', crs: 'gcj02' })
              else attempt(i + 1)
            })
          } catch (e) { return attempt(i + 1) }
        }
        attempt(0)
      })
    } catch (e) { resolve(null) }
  })
}

/** ③ 后端 /api/map/geocode：国内 AMap web-key → GCJ-02；无 key 时 Nominatim → WGS-84。后端返回 crs */
async function backendGeocode(name, city) {
  try {
    const address = city && !/[市省]/.test(name) ? `${city} ${name}` : name
    const res = await mapApi.geocode(address, city)
    const d = res && res.code === 0 ? res.data : null
    if (d && isFinite(+d.lat) && isFinite(+d.lng)) {
      return { lat: +d.lat, lng: +d.lng, source: 'backend', crs: d.crs || 'gcj02' }
    }
  } catch (e) { /* 忽略 */ }
  return null
}

/** 本地缓存键值 */
function cacheKey(city, name) { return `${(city || '').replace(/\s/g, '')}:${normName(name)}` }

/**
 * 主入口：把 (景点名, 城市) 解析为坐标，统一转换到 targetCrs。
 * @param {string} name 景点名
 * @param {string} city 目的地城市
 * @param {string} [targetCrs='gcj02'] 目标坐标系：高德图=gcj02，OSM/Leaflet=wgs84
 * @param {{lat:number,lng:number}|null} [cityCenter] 城市中心（targetCrs），用于 bbox 校验
 * @returns {Promise<{lat,lng,source,crs}|null>} 全部失败返回 null（由调用方散开兜底）
 */
export async function spotCoord(name, city, targetCrs = 'gcj02', cityCenter = null) {
  if (!name) return null
  const n = normName(name)
  if (!n) return null

  const k = cacheKey(city, n)
  const cache = readCache()
  const hit = cache[k]
  if (hit && isFinite(hit.lat) && isFinite(hit.lng)) {
    // 缓存随存坐标系；命中时若 CRS 与当前地图不一致，先转换再返回
    let lat = hit.lat, lng = hit.lng
    if (hit.crs && hit.crs !== targetCrs) { const c = coord.convert(lat, lng, hit.crs, targetCrs); lat = c[0]; lng = c[1] }
    return { lat, lng, source: hit.source || 'cache', crs: targetCrs }
  }

  /** 取候选 → 统一转换到 targetCrs → 城市 bbox 校验 → 通过则返回 */
  const process = async (candidate) => {
    if (!candidate) return null
    let lat = candidate.lat, lng = candidate.lng
    if (candidate.crs && candidate.crs !== targetCrs) {
      const c = coord.convert(lat, lng, candidate.crs, targetCrs); lat = c[0]; lng = c[1]
    }
    if (!isFinite(lat) || !isFinite(lng)) return null
    if (!cityPlausible(lat, lng, cityCenter)) return null
    return { lat, lng, source: candidate.source, crs: targetCrs }
  }

  // ① 高德客户端（精确真实 POI）→ ② 后端 geocode（AMap web-key / Nominatim）③ 精选库（离线兜底）
  let result = await process(await amapGeocode(n, city))
  if (!result) result = await process(await backendGeocode(n, city))
  if (!result) result = await process(curatedCoord(n))

  if (result && isFinite(result.lat) && isFinite(result.lng)) {
    cache[k] = { lat: result.lat, lng: result.lng, source: result.source, crs: targetCrs }
    trimCache(cache)
    writeCache(cache)
  }
  return result
}

export { cityPlausible }
export default spotCoord
