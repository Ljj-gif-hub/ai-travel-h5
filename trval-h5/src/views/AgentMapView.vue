<script setup>
/**
 * AgentMapView.vue — Agent 行程规划地图页 v5
 * 可拖拽抽屉 + 百度地图 + 景点图片 + 携程同款动画
 */
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showToast } from 'vant'
import { agentPlanStream } from '../api/agent'
import { planApi } from '../api/index.js'
import { getToken } from '../utils/auth'
import { spotCoord } from '../utils/spot-geocoder'
// MAPFAIL-1 修复：AMap 不可用时回退 Leaflet（本地打包 + OSM 瓦片，离线由 SW 缓存）
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

defineOptions({ name: 'AgentMapView' })
const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const destCity = ref(route.query.destination || '')
const originCity = ref(route.query.origin || t('map.departure'))
const tripDays = ref(Number(route.query.days) || 3)
const tripPeople = ref(Number(route.query.people) || 2)
const phase = ref('generating')
const activeTab = ref('itinerary') // itinerary | inspiration
const activeDay = ref(0) // 当前查看的天索引
const agentProgress = ref(0)
const agentStep = ref(t('map.connectingAgent'))
const planData = ref(null)
const costBreakdown = ref(null)
const hotelList = ref([])
const attractionImages = ref({})
let attractionImageMap = null // 静态景点图映射 /attraction-images.json（同源必然能加载），懒加载一次
const markerPositions = ref({}) // 景点名 → {lat,lng}，点击卡片定位用
const markerEls = ref({})        // 景点名 → 标记 DOM，高亮用
const activeSpot = ref('')       // 当前选中的景点
const adjustText = ref('')       // 调整行程输入
const markdownContent = ref('')  // 旧版 markdown 型保存行程的兜底展示
const budgetLabel = (k) => ({ transport: t('map.budgetTransport'), accommodation: t('map.budgetAccommodation'), food: t('map.budgetFood'), tickets: t('map.budgetTickets'), shopping: t('map.budgetShopping'), total: t('map.budgetTotal') }[k] || k)

let streamAbort = null
const isSaving = ref(false)

/** 保存当前 Agent 规划到「我的行程」 */
async function savePlan() {
  if (isSaving.value || !planData.value) return
  isSaving.value = true
  try {
    const res = await planApi.savePlan({
      destination: destCity.value,
      days: tripDays.value,
      budget: Number(route.query.budget) || 5000,
      people: tripPeople.value,
      planData: {
        ...planData.value,
        hotels: hotelList.value,
        budgetDetail: costBreakdown.value,
        totalBudget: Number(route.query.budget) || 5000,
      },
      source: 'agent',
    })
    if (res.code === 0) {
      showToast(t('map.savedViewInTrips'))
    } else {
      showToast(res.message || t('map.saveFailed'))
    }
  } catch (e) {
    showToast(t('map.saveFailedRetry'))
  } finally {
    isSaving.value = false
  }
}

const stepList = ref([
  { name: t('map.stepAnalyze'), status: 'wait' },
  { name: t('map.stepPlan'), status: 'wait' },
  { name: t('map.stepBudget'), status: 'wait' },
  { name: t('map.stepOptimize'), status: 'wait' },
  { name: t('map.stepGenerate'), status: 'wait' },
])
const phaseIdx = { research: 0, plan: 1, verify: 2, adjust: 3, finalize: 4 }
const agentLogs = ref([]) // 实时思考日志

const totalCost = computed(() => {
  if (!planData.value) return 0
  let s = 0
  for (const d of (planData.value.dayPlans || [])) {
    for (const sl of (d.timeSlots || [])) { s += parseInt(sl.cost) || 0 }
  }
  return s
})
// ====== 地图覆盖层（抽屉收起时显示）与行程标题 ======
const OVERLAY_MAX_PCT = 30 // 抽屉位置高于此值（展开）→ 隐藏地图覆盖层与顶部标题
const showMapOverlay = computed(() => phase.value === 'completed' && !!planData.value && drawerPct.value <= OVERLAY_MAX_PCT)
// 顶部标题：仅当"已完成态且抽屉展开"（标题已移入抽屉）才隐藏；生成中/收起/兜底态都显示
const showTopTitle = computed(() => !(phase.value === 'completed' && !!planData.value && drawerPct.value > OVERLAY_MAX_PCT))

// 标题副题：取整程最有代表性的前 2 个真实景点（跳过"稍作休整/自由活动"等废话与空项），要精简且重点
const FILLER_TITLE_WORDS = ['休整','休息','休憩','自由活动','自由安排','闲逛','漫步','回酒店','放松','调整']
function isFillerTitle(name) {
  if (!name) return true
  const low = String(name).toLowerCase()
  return FILLER_TITLE_WORDS.some(w => low.includes(w))
}
// 景点名只保留主体，去掉后面的注记（如"（免费）"“（5A级）”"【需预约】"）
function cleanSpotName(name) {
  if (!name) return ''
  return String(name)
    .replace(/【[^】]*】/g, '')
    .replace(/（[^）]*）/g, '')
    .replace(/\([^)]*\)/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}
function topHighlights() {
  const names = []
  for (const dp of planData.value?.dayPlans || []) {
    for (const s of dp.timeSlots || []) {
      const n = cleanSpotName(s.attraction)
      if (n && !isFillerTitle(n) && !names.includes(n)) names.push(n)
      if (names.length === 2) return names
    }
  }
  return names
}
// 默认标题（优美）：{目的地}{天数}天 · {主题词} · {前2真实景点}；去废话、去"稍作休整"
const DEST_THEME = {
  三亚:'海岛慢享', 大理:'苍山洱海', 丽江:'雪山古城', 昆明:'春城花事', 桂林:'山水甲天下',
  张家界:'奇峰秘境', 厦门:'鹭岛漫游', 深圳:'向海而生', 广州:'食在广州', 成都:'巴适慢游',
  重庆:'山城烟火', 西安:'古都风华', 北京:'皇城古韵', 上海:'魔都风采', 杭州:'江南画意',
  苏州:'园林雅韵', 南京:'金陵风韵', 青岛:'碧海红瓦', 长沙:'星城烟火', 武汉:'江城烟火',
}
function defaultTripTitle() {
  const dest = planData.value?.destination || destCity.value
  const days = planData.value?.days || tripDays.value
  const hs = topHighlights()
  const theme = DEST_THEME[dest] || (days >= 6 ? '深度漫游' : days >= 4 ? '悠享畅游' : '周末微度假')
  const hl = hs.length ? hs.slice(0, 2).join('+') : ''
  const base = `${dest}${days}天 · ${theme}`
  return hl ? `${base} · ${hl}` : base
}
// 标题可编辑：用户可覆盖默认标题（空则回退到自动生成）；可取消、可保存，状态机清晰
const customTitle = ref('')
const editingTitle = ref(false)
const titleDraft = ref('')
const defaultTitle = computed(defaultTripTitle)
const displayTitle = computed(() => customTitle.value || defaultTitle.value)
function startEditTitle() { titleDraft.value = displayTitle.value; editingTitle.value = true }
function saveEditTitle() {
  const v = titleDraft.value.trim()
  customTitle.value = v
  if (planData.value) planData.value.title = v // 随保存行程带出
  editingTitle.value = false
}
function cancelEditTitle() { titleDraft.value = displayTitle.value; editingTitle.value = false }
function onTitleBlur() { if (editingTitle.value) saveEditTitle() }

// 筛选胶囊：一期仅视觉呈现（切换选中态+提示），TODO 二期接真实过滤
const filterChips = [
  { key: 'spots',    labelKey: 'map.filterSpots',    hintKey: 'map.filterSpotsHint' },
  { key: 'hotels',   labelKey: 'map.filterHotels',   hintKey: 'map.filterHotelsHint' },
  { key: 'food',     labelKey: 'map.filterFood',     hintKey: 'map.filterFoodHint' },
  { key: 'shopping', labelKey: 'map.filterShopping', hintKey: 'map.filterShoppingHint' },
]
const activeFilter = ref('')
// 数据无类别字段，按内容关键字启发式归类（景点/美食/购物/住宿，默认景点）。只取名称+活动，避免 tips 里"推荐美食"等噪声误判
const FOOD_KW = ['餐厅','美食街','美食','火锅','烤肉','烧烤','串串','米粉','面馆','海鲜','咖啡','奶茶','茶楼','菜馆','菜','小吃','夜市','大排档','甜品','烘焙','农家菜','饭','煎饼','饺子']
const SHOP_KW = ['购物','商场','商城','免税','步行街','百货','商圈','奥特莱斯','旗舰店','商业街','购物中心','市集','商贸','银泰']
const HOTEL_KW = ['酒店','民宿','客栈','宾馆','住宿','旅馆','度假村','青旅']
function categoryOf(item) {
  const src = ((item.attraction || '') + (item.activity || '')).toLowerCase()
  const has = kws => kws.some(k => src.includes(k.toLowerCase()))
  if (has(HOTEL_KW)) return 'hotels'
  if (has(SHOP_KW)) return 'shopping'
  if (has(FOOD_KW)) return 'food'
  return 'spots'
}
/** 景点名 → 类别（所有天共用） */
const slotCats = computed(() => {
  const m = {}
  for (const dp of planData.value?.dayPlans || []) for (const s of dp.timeSlots || []) if (s.attraction) m[s.attraction] = categoryOf(s)
  return m
})
/** 某类别在整份行程里的条目数：美食=含当天用餐项，住宿=含住宿推荐 */
function countOfCategory(cat) {
  let n = 0
  for (const dp of planData.value?.dayPlans || []) for (const s of dp.timeSlots || []) if (categoryOf(s) === cat) n++
  if (cat === 'food') n += (planData.value?.dayPlans || []).reduce((a, dp) => a + (dp.meals?.length || 0), 0)
  if (cat === 'hotels') n += (hotelList.value || []).length
  return n
}
/** 实际生效的过滤类别：选了但行程里没有该类（或重新生成后已变空）→ 视为未选，避免整图/整列表全暗 */
const effFilter = computed(() => {
  if (!activeFilter.value) return ''
  return countOfCategory(activeFilter.value) > 0 ? activeFilter.value : ''
})
const slotCat = s => (s ? (slotCats.value[s.attraction] || 'spots') : 'spots')
/** 单个行程卡的过滤类：命中→高亮，未命中→压暗 */
const filterClassOf = s => {
  if (!effFilter.value) return ''
  return slotCat(s) === effFilter.value ? 'filter-hl' : 'filter-dim'
}
// 非景点条目固定所属类别：用餐→food、住宿→hotels
const foodFilterClass = () => effFilter.value ? (effFilter.value === 'food' ? 'filter-hl' : 'filter-dim') : ''
const hotelFilterClass = () => effFilter.value ? (effFilter.value === 'hotels' ? 'filter-hl' : 'filter-dim') : ''
const noteFilterClass = computed(() => !effFilter.value || !noteSpot.value ? '' : (slotCat(noteSpot.value) === effFilter.value ? 'filter-hl' : 'filter-dim'))
function onFilter(key) {
  if (activeFilter.value === key) { activeFilter.value = ''; return }
  if (countOfCategory(key) <= 0) { showToast(t('map.filterEmpty')); return }
  activeFilter.value = key
}
/** 把当前过滤状态同步到地图钉标：命中类别高亮、其余压暗 */
function applyFilterToMap() {
  const f = effFilter.value
  for (const name of Object.keys(markerEls.value)) {
    const el = markerEls.value[name]
    if (!el) continue
    const cat = slotCats.value[name] || 'spots'
    el.classList.toggle('filter-dim', !!f && cat !== f)
    el.classList.toggle('filter-hl', !!f && cat === f)
  }
}
watch(effFilter, applyFilterToMap)

// 选中景点便签卡：优先 activeSpot，否则默认第一天第一个景点
const slotByName = computed(() => {
  const map = {}
  for (const dp of planData.value?.dayPlans || []) for (const s of dp.timeSlots || []) if (s.attraction && !map[s.attraction]) map[s.attraction] = s
  return map
})
const firstSpot = computed(() => planData.value?.dayPlans?.[0]?.timeSlots?.[0] || null)
const noteSpot = computed(() => slotByName.value[activeSpot.value] || firstSpot.value)
const noteImgStyle = computed(() => {
  const urls = noteSpot.value && attractionImages.value[noteSpot.value.attraction]
  const u = urls && urls[0]
  return u ? { backgroundImage: `url(${u})` } : {}
})

// ====== 行程详情：所有天连续滚动，天数胶囊做滚动定位锚点 ======
const dayEls = {}       // 各天区块 DOM（v-for 函数 ref 填充）
const bodyEl = ref(null) // 行程滚动容器
/** 吸顶头部底缘 = 判定线：某天顶部越过它即算"当前天"。实测吸顶头高，避免硬编码 90/100 这类拍脑袋值 */
let stickyHeadH = -1
function revealLine() {
  // 抽屉收起时 .body 被 display:none，offsetHeight=0 不可用 → 不缓存 0，落到兜底值，等可见后再实测
  if (stickyHeadH < 0) {
    const head = bodyEl.value && bodyEl.value.querySelector('.sticky-head')
    const h = head ? head.offsetHeight : 0
    if (h > 0) stickyHeadH = h
  }
  return (stickyHeadH > 0 ? stickyHeadH : 96) + 4
}
let pendingDayIdx = -1   // 点 Day 后的程序化滚动锁定：滚动监测在用户手动滚动前不接管，避免把胶囊抢回前一天
const REVEAL_SLACK = 8   // 滚动落点再往上多留 8px：标题稳稳露在吸顶头下方，给平滑滚动的残差留余地
const REVEAL_DRIFT = 26  // 「当前天」判定留 26px 容差：自然滚动差几像素也不突兀
/** 用户开始手动滚动（触摸拖动/滚轮）→ 交还滚动监测，这才允许胶囊跟随真实阅读位置 */
function releaseDayLock() { pendingDayIdx = -1 }
/** 点天数胶囊：滚动到该天；抽屉收起则先上滑展示再滚 */
function scrollToDay(idx) {
  activeDay.value = idx
  pendingDayIdx = idx
  const doScroll = () => {
    const body = bodyEl.value
    const el = dayEls[idx]
    if (!body || !el) return
    const reveal = revealLine()
    const bodyTop = body.getBoundingClientRect().top
    const dist = el.getBoundingClientRect().top - bodyTop - (reveal - REVEAL_SLACK)  // 该天距落点（判定线上方 8px）的距离
    const maxScroll = body.scrollHeight - body.clientHeight            // 最后一段内容不足以把当天顶到落点线时绷到底
    const target = Math.max(0, Math.min(maxScroll, body.scrollTop + dist))
    body.scrollTo({ top: target, behavior: 'smooth' })
  }
  if (drawerPct.value <= OVERLAY_MAX_PCT) { snapTo(MID); setTimeout(doScroll, 500) }
  else doScroll()
}
/** 点天数胶囊（收起条）——同上，滚动定位 */
function pickDay(idx) { scrollToDay(idx) }
/** 滚动时同步高亮当前到达的天（取最靠上、已越过顶部判定线的那天） */
function onBodyScroll() {
  const body = bodyEl.value
  if (!body || !planData.value?.dayPlans) return
  // 点 Day 后的程序化滚动期间不接管：胶囊维持用户点的天，直到用户手动滚动（touchmove/wheel 释放锁）
  if (pendingDayIdx >= 0) return
  const bodyTop = body.getBoundingClientRect().top
  const reveal = revealLine()
  const windowLine = reveal + REVEAL_DRIFT
  let cur = 0
  planData.value.dayPlans.forEach((_, idx) => {
    const el = dayEls[idx]
    if (el && el.getBoundingClientRect().top - bodyTop <= windowLine) cur = idx
  })
  // 滚到底后没有内容再支撑判定线，直接认最后一阅为当前天
  if (body.scrollHeight - body.scrollTop - body.clientHeight < 8) cur = planData.value.dayPlans.length - 1
  if (activeDay.value !== cur) activeDay.value = cur
}
/** 天数标题：去掉"第N天："前缀；若是"稍作休整/自由活动"等废话，则回退为该天真实景点简介 */
function dayTitleOf(idx) {
  const dp = planData.value?.dayPlans?.[idx]
  if (!dp) return ''
  const d = dp.day
  const raw = String(dp.dayTitle || '').replace(`第${d}天：`, '').replace(`第${d}天:`, '').replace(`第${d}天`, '').trim()
  if (raw && !isFillerTitle(raw)) return cleanSpotName(raw)
  const hls = []
  for (const s of dp.timeSlots || []) {
    const n = cleanSpotName(s.attraction)
    if (n && !isFillerTitle(n) && !hls.includes(n)) hls.push(n)
    if (hls.length === 2) break
  }
  return hls.join('+')
}
const spotCount = computed(() => {
  if (!planData.value) return 0
  const set = new Set()
  for (const dp of planData.value.dayPlans || []) for (const s of dp.timeSlots || []) if (s.attraction) set.add(s.attraction)
  return set.size
})
const itinPreview = computed(() => {
  if (!planData.value) return ''
  const days = planData.value.days || tripDays.value
  return `${destCity.value} · ${days}${t('common.days')}${Math.max(days - 1, 0)}${t('common.night')} · ${spotCount.value}${t('map.spotUnit')}`
})
// ====== 可拖拽抽屉（仅手柄区域可拖拽，内容区自由滚动） ======
const MIN = 13, MID = 55, MAX = 97
const drawerPct = ref(MID)
const isDragging = ref(false)
let handleTouchId = null, hStartY = 0, hStartPct = 0, hDragOn = false

function snapTo(target) {
  drawerPct.value = Math.max(MIN, Math.min(MAX, target))
}

function onHandleTouchStart(e) {
  cancelMapZoom() // 用户再次按住抽屉时，中止未完成的缩放动画
  handleTouchId = e.changedTouches[0].identifier
  hStartY = e.changedTouches[0].clientY
  hStartPct = drawerPct.value
  hDragOn = false
}

function onHandleTouchMove(e) {
  let t = null
  for (let i = 0; i < e.changedTouches.length; i++) {
    if (e.changedTouches[i].identifier === handleTouchId) { t = e.changedTouches[i]; break }
  }
  if (!t) return
  const dy = hStartY - t.clientY
  if (!hDragOn) {
    if (Math.abs(dy) < 5) return
    hDragOn = true; hStartY = t.clientY; hStartPct = drawerPct.value; isDragging.value = true
  }
  if (e.cancelable) e.preventDefault()
  drawerPct.value = Math.round(Math.max(MIN, Math.min(MAX, hStartPct + (hStartY - t.clientY) / window.innerHeight * 100)))
}

function onHandleTouchEnd(e) {
  let found = false
  for (let i = 0; i < e.changedTouches.length; i++) {
    if (e.changedTouches[i].identifier === handleTouchId) { found = true; break }
  }
  if (!found) return
  handleTouchId = null; isDragging.value = false
  if (hDragOn) {
    const pct = drawerPct.value
    const dMid = Math.abs(pct - MID), dMax = Math.abs(pct - MAX), dMin = Math.abs(pct - MIN)
    if (dMin < dMid && dMin < dMax) snapTo(MIN)
    else if (dMax < dMid) snapTo(MAX)
    else snapTo(MID)
    // 落点可能与最后一次拖动位置相同（drawerPct 没变、watch 不触发），松手必须显式缩放，否则会"缩放失效"
    fireDrawerZoom(drawerPct.value)
  }
  hDragOn = false
}

// ====== 抽屉 ↔ 地图互动：识别当前缩放做相对增量（抽屉上滑=放大、下滑=缩小，不受当前级别影响） ======
// 拖动中不连续缩放；抽屉快停止运动（收尾滑动/吸附）时沿缓动曲线平滑缩放，更从容灵动
const ZOOM_RANGE = 2.5          // 抽屉 MIN→MAX 全程对应放大 2.5 级
const MIN_PCT = MIN, MAX_PCT = MAX
const SNAP_DURATION_MS = 720    // 收尾缩放总时长（ms）：稍慢 + 缓动曲线，避免生硬
let lastPct = drawerPct.value   // 上次应用缩放时的抽屉位置
let zoomRafId = null            // 缓动缩放中的 rAF 句柄（新缩放/拖动/卸载时取消）

/** 缓动曲线：easeOutCubic（先快后慢，收尾优雅减速，更灵动） */
function easeOutCubic(t) { return 1 - Math.pow(1 - t, 3) }

/** 取消进行中的缩放动画（避免与新的手势/缩放打架） */
function cancelMapZoom() {
  if (zoomRafId) { cancelAnimationFrame(zoomRafId); zoomRafId = null }
}

/** 灵动缩放：rAF 每帧沿缓动曲线 setZoom 到当前插值级别（AMap 连续缩放支持小数级别），
 *  60fps 平滑无极；只持续 SNAP_DURATION_MS 一次、帧数有界，不会像持续拖动那样压垮渲染进程 */
function animateMapZoom(target, duration = SNAP_DURATION_MS) {
  if (!mapInstance || !window.AMap) return
  const start = mapInstance.getZoom()
  if (Math.abs(start - target) < 0.02) return
  cancelMapZoom() // 仅当确实要缩放时才打断上一次动画，no-op 不打断
  const t0 = performance.now()
  const tick = (ts) => {
    if (!mapInstance || !window.AMap) return
    const t = Math.min((ts - t0) / duration, 1)
    const zoom = start + (target - start) * easeOutCubic(t)
    if (Math.abs(mapInstance.getZoom() - zoom) > 0.001) mapInstance.setZoom(zoom, true) // 立即渲染到小数级别
    if (t < 1) zoomRafId = requestAnimationFrame(tick)
    else zoomRafId = null
  }
  zoomRafId = requestAnimationFrame(tick)
}

/** 抽屉快停止运动时，执行一次相对缩放（delta 相对上次实际应用的位置） */
function fireDrawerZoom(pct) {
  if (!mapInstance || !window.AMap || pct == null) return
  // 识别当前真实缩放（含用户手动缩放过的高级别），只做相对增量
  const currentZoom = mapInstance.getZoom()
  const deltaPct = pct - lastPct
  // 缩放方向与抽屉相反：抽屉上滑（pct↑）→ 地图缩小；抽屉下滑（pct↓）→ 地图放大
  const deltaZoom = -((deltaPct / (MAX_PCT - MIN_PCT)) * ZOOM_RANGE)
  const target = currentZoom + deltaZoom
  lastPct = pct
  animateMapZoom(target, SNAP_DURATION_MS)
}

watch(drawerPct, (val) => {
  // 拖动中不缩放；抽屉停止/吸附（快停止运动）时才缩放
  if (isDragging.value) return
  fireDrawerZoom(val)
})

// ====== 地图 ======
let mapInstance = null
// 当前地图瓦片坐标系：高德=gcj02，Leaflet/OSM 兜底=wgs84。定位器按此把各数据源坐标统一归一化。
let activeMapCrs = 'gcj02'
let markerInstances = [] // 已添加的地图标记实例（重新生成时清除）
const markerByName = {}  // 景点名 → AMap.Marker 实例（扇形展开用）
const markerPrimary = new Set() // 同地点合并后的代表景点名；declutter 只按代表名排布，避免把已合并的同一图标重新堆开
// 路线连线（从哪到哪）：按行程顺序连接各景点，直观展示当日/全程行进路线
let routeLine = null // AMap.Polyline 实例
/** 按行程顺序收集去重后的景点坐标（先到先得），作为路线折线点 */
function routePoints() {
  const pts = [], seen = new Set()
  for (const dp of planData.value?.dayPlans || []) for (const s of dp.timeSlots || []) {
    if (!s.attraction) continue
    const p = markerPositions.value[s.attraction]
    if (p && !seen.has(s.attraction)) { seen.add(s.attraction); pts.push([p.lng, p.lat]) }
  }
  return pts
}
/** 绘制路线：优先用高德驾车路算（多站点按顺序分段搜索，贴合真实道路）；任一腿失败则回退为景点直连折线。 */
function drawRoute() {
  if (!mapInstance || !window.AMap) return
  if (routeLine) { try { routeLine.setMap(null) } catch {} ; routeLine = null }
  const pts = routePoints()
  if (pts.length < 2) return
  const lnglats = pts.map(p => new window.AMap.LngLat(p[0], p[1]))
  // 兜底：景点直连折线（保证一定能看到"从哪到哪"）
  const doStraight = () => {
    if (routeLine) { try { routeLine.setMap(null) } catch {} ; routeLine = null }
    routeLine = new window.AMap.Polyline({ path: lnglats, strokeColor: '#2E7CFF', strokeWeight: 6, strokeOpacity: .9, strokeStyle: 'solid', lineJoin: 'round', lineCap: 'round', zIndex: 40, showDir: true })
    routeLine.setMap(mapInstance)
  }
  const drawPolyline = (flat) => {
    if (!flat || flat.length < 2) { doStraight(); return }
    if (routeLine) { try { routeLine.setMap(null) } catch {} ; routeLine = null }
    routeLine = new window.AMap.Polyline({ path: flat, strokeColor: '#2E7CFF', strokeWeight: 6, strokeOpacity: .85, lineJoin: 'round', lineCap: 'round', zIndex: 40, showDir: true, borderWeight: 2 })
    routeLine.setMap(mapInstance)
  }
  window.AMap.plugin('AMap.Driving', () => {
    let driving
    try { driving = new window.AMap.Driving({ policy: window.AMap.DrivingPolicy.LEAST_TIME }) }
    catch (e) { doStraight(); return }
    const legs = []
    let failed = false, pending = lnglats.length - 1
    const finish = () => {
      if (pending > 0) return
      if (failed) { doStraight(); return }
      const ordered = legs.sort((a, b) => a.i - b.i)
      drawPolyline(ordered.reduce((acc, leg) => acc.concat(leg.path), []))
    }
    // 分段顺序搜索：origin → 第1段 → … → destination；每段用真实道路 polyline
    const searchLeg = (i) => {
      if (i >= lnglats.length - 1) { finish(); return }
      try {
        driving.search(lnglats[i], lnglats[i + 1], (status, result) => {
          const steps = result?.routes?.[0]?.steps
          if (status === 'complete' && Array.isArray(steps) && steps.length) {
            const path = []
            steps.forEach(st => { if (Array.isArray(st.path)) path.push(...st.path) })
            if (path.length) legs.push({ i, path })
            else failed = true
          } else failed = true
          pending--
          searchLeg(i + 1)
        })
      } catch (e) { failed = true; pending--; searchLeg(i + 1) }
    }
    searchLeg(0)
  })
}
const cityCoords = {
  北京:[39.915,116.404],上海:[31.23,121.474],成都:[30.573,104.067],杭州:[30.274,120.155],
  大理:[25.607,100.233],三亚:[18.253,109.504],西安:[34.263,108.948],重庆:[29.565,106.551],
  长沙:[28.194,112.97],厦门:[24.48,118.089],深圳:[22.543,113.958],广州:[23.129,113.264],
  南京:[32.06,118.797],武汉:[30.593,114.305],苏州:[31.299,120.585],昆明:[25.044,102.710],
  桂林:[25.274,110.290],张家界:[29.117,110.478],丽江:[26.872,100.230],拉萨:[29.650,91.100],
  青岛:[36.067,120.383],大连:[38.914,121.615],哈尔滨:[45.803,126.535],乌鲁木齐:[43.826,87.617],
}
async function getCenter(c) {
  for (const [k, v] of Object.entries(cityCoords)) { if (c.includes(k)) return { lat: v[0], lng: v[1] } }
  // 高德地理编码兜底（只认与目标城市相关的命中，避免 mock 固定城市造成错位）
  try {
    const r = await fetch(`/api/map/suggestion?keyword=${encodeURIComponent(c)}`).then(r => r.json())
    if (r.code === 0 && Array.isArray(r.data)) {
      const hit = r.data.find(d => suggestionMatches(c, d))
      if (hit && isFinite(+hit.lat) && isFinite(+hit.lng)) return { lat: +hit.lat, lng: +hit.lng }
    }
  } catch {}
  // 最终兜底：中国大陆中心
  return { lat: 35.86, lng: 104.19 }
}

let mapLoaded = false

async function initMap() {
  if (window.AMap) { mapLoaded = true; await initAmapMap(); return }
  // MAPFAIL-1 修复：优先 AMap，SDK 加载后仍不可用则回退 Leaflet，两者都不可用才 toast 失败
  await new Promise(r => {
    const s = document.createElement('script'); s.src = '/api/map/script'
    s.onload = () => { let n = 0; const c = setInterval(() => { if (window.AMap) { clearInterval(c); r(true) } else if (n++ > 20) { clearInterval(c); r(false) } }, 200) }
    s.onerror = () => r(false); document.head.appendChild(s)
  })
  if (window.AMap) { mapLoaded = true; await initAmapMap(); return }
  const Lf = await loadLeaflet()
  if (Lf) { mapLoaded = true; await initLeafletMap(Lf) }
  else showToast(t('map.mapLoadFailed'))
}

async function initAmapMap() {
  activeMapCrs = 'gcj02' // 高德瓦片 = GCJ-02，定位器据此归一化
  const center = await getCenter(destCity.value)
  const el = document.getElementById('agent-bmap'); if (!el) return
  mapInstance = new window.AMap.Map('agent-bmap', {
    center: [center.lng, center.lat], zoom: 13,
    viewMode: '2D', resizeEnable: true,
  })
  // 缩放/平移后重排标记，防重叠
  mapInstance.on('moveend', scheduleDeclutter)
  mapInstance.on('zoomend', scheduleDeclutter)
  // 城市标签（自定义样式，替代丑的默认 label）
  const cityEl = document.createElement('div')
  cityEl.className = 'city-marker'
  cityEl.textContent = destCity.value
  const marker = new window.AMap.Marker({
    position: [center.lng, center.lat],
    content: cityEl,
    anchor: 'center',
    zIndex: 100,
  })
  mapInstance.add(marker)
}

/** MAPFAIL-1 修复：加载 Leaflet（AMap 不可用时的兜底，本地打包 + OSM 瓦片，离线由 SW 缓存） */
let leafletLoaded = false
const loadLeaflet = () => {
  return new Promise((resolve) => {
    if (L) { leafletLoaded = true; resolve(L); return }
    if (window.L) { leafletLoaded = true; resolve(window.L); return }
    const script = document.createElement('script')
    script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
    script.onload = () => { leafletLoaded = true; resolve(window.L) }
    script.onerror = () => resolve(null)
    document.head.appendChild(script)
  })
}

/** MAPFAIL-1 修复：Leaflet 兜底地图（瓦片 + 城市标签；标记/缩放等 AMap 专属逻辑在 window.AMap 缺失时自动降级为 no-op） */
async function initLeafletMap(Lf) {
  activeMapCrs = 'wgs84' // OSM 瓦片 = WGS-84
  const center = await getCenter(destCity.value)
  if (mapInstance) {
    try { if (typeof mapInstance.destroy === 'function') mapInstance.destroy() } catch (e) {}
    try { if (typeof mapInstance.remove === 'function') mapInstance.remove() } catch (e) {}
    mapInstance = null
  }
  const el = document.getElementById('agent-bmap'); if (!el) return
  mapInstance = Lf.map('agent-bmap', {
    center: [center.lat, center.lng], zoom: 13,
    zoomControl: true, attributionControl: false,
  })
  Lf.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(mapInstance)
  // 城市标签（复用全局 .city-marker 样式；destCity 来自 route.query，转义防 XSS）
  const icon = Lf.divIcon({
    className: 'leaflet-city-marker',
    html: `<div class="city-marker">${escapeHtml(destCity.value)}</div>`,
    iconSize: [0, 0],
    iconAnchor: [0, 0],
  })
  Lf.marker([center.lat, center.lng], { icon, interactive: false, zIndexOffset: 1000 }).addTo(mapInstance)
}

/** 地理编码：把景点名解析为真实坐标。
 *  委托通用定位器 spot-geocoder（高德客户端→后端→精选库，统一归一化到地图坐标系并做城市校验），
 *  全部 miss 才用"目的地中心 + 按名散开"兜底，并标记 approx 表示"位置为近似估算"。 */
async function geocodeName(name, center) {
  const hit = await spotCoord(name, destCity.value, activeMapCrs, center)
  if (hit) return { lat: hit.lat, lng: hit.lng, approx: false }
  const seed = nameSeed(name || 'spot')
  const ang = (seed % 360) * Math.PI / 180
  const dist = 0.012 + (seed % 100) / 100 * 0.021
  return { lat: center.lat + Math.sin(ang) * dist, lng: center.lng + Math.cos(ang) * dist, approx: true }
}

/** 同地点合并阈值（米）：坐标距离小于该值的推荐视为同一个地点，合并为一个定位图标。
 *  例：LLM 常把"武侯祠（含锦里古街）"和"锦里古街"同时推荐出来，两点紧邻，地图上应只显示一个 pin。 */
const SAME_PLACE_M = 150
/** 按坐标相近程度把景点分组成"同一地点"，每组保持行程出现顺序（names[0] 为最早出现者，即标签最上一行）。
 *  携带 approx：仅当整组都来自"近似估算兜底"时标记为近似（避免把真实 POI 误标为近似）。 */
function groupBySamePlace(markers) {
  const groups = []
  for (const m of markers) {
    let host = null
    for (const g of groups) {
      const dy = (g.lat - m.lat) * 111000
      const dx = (g.lng - m.lng) * 111000 * Math.cos(g.lat * Math.PI / 180)
      if (Math.sqrt(dx * dx + dy * dy) < SAME_PLACE_M) { host = g; break }
    }
    if (host) { host.names.push(m.name); if (m.approx) host.approxCount++ }
    else groups.push({ lat: m.lat, lng: m.lng, names: [m.name], approxCount: m.approx ? 1 : 0 })
  }
  groups.forEach(g => { g.approx = g.approxCount === g.names.length })
  return groups
}
/** 转义 HTML，防止景点名里的特殊字符破坏标签 DOM */
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]))
}

/** 景点名稳定的 seed（JS 字符串哈希），用于把"无坐标兜底"的景点按名字散开在同一城市，避免全部堆在市中心 */
function nameSeed(name) {
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0
  return h
}

/** 联想结果是否与查询景点名相关。
 *  高德有 key 时返回真实 POI（名称命中）；无 key 时后端返回固定城市 mock（北京/上海…），
 *  名称不命中则拒绝，防止把三亚的景点全部定位到北京。 */
function suggestionMatches(name, s) {
  if (!s || !s.name) return false
  const a = String(name || '').toLowerCase().trim()
  const b = String(s.name).toLowerCase().trim()
  if (!a || !b) return false
  if (b.includes(a) || a.includes(b)) return true
  const addr = String(s.address || '').toLowerCase()
  return a.length >= 2 && addr.includes(a)
}

async function addMarkers(names) {
  if (!mapInstance || !window.AMap) return
  // 并行地理编码真实坐标，避免全部随机堆在市中心。
  // 先解析一次目的地中心（供定位器城市 bbox 校验 + 兜底散点），再对每个"纯净"景区名并行编码。
  const center = await getCenter(destCity.value)
  const markers = await Promise.all(names.map(async (name) => {
    const c = await geocodeName(cleanSpotName(name), center)
    return { name, lat: c.lat, lng: c.lng, approx: !!c.approx }
  }))
  // 同一地点去重：相邻的推荐合并为一个定位图标，避免地图上出现重复定位针
  const groups = groupBySamePlace(markers)
  groups.forEach(group => {
    // MAPRACE-1 修复：await geocodeName 后地图实例可能已随切换目的地被销毁/重建，add 前再次校验
    if (!mapInstance || !window.AMap) return
    // 标准定位针图标（teardrop pin）+ 干净文字气泡；近似估算的加 ≈ 角标 + 淡色虚线边框
    const el = document.createElement('div')
    el.className = 'spot-marker' + (group.approx ? ' approx' : '')
    el.innerHTML =
      (group.approx ? '<span class="spot-marker-approx" title="位置为近似估算">≈</span>' : '') +
      '<span class="spot-marker-name"></span>' +
      '<svg class="spot-marker-pin" viewBox="0 0 24 36" width="18" height="27" aria-hidden="true">' +
        '<path d="M12 0C5.4 0 0 5.4 0 12c0 9 12 24 12 24s12-15 12-24C24 5.4 18.6 0 12 0z" fill="#7C3AED" stroke="#fff" stroke-width="1.6"/>' +
        '<circle cx="12" cy="11.5" r="5.2" fill="#fff"/>' +
      '</svg>'
    // 合并的标签：同地点多个景点名从上到下按行程顺序分行排列
    el.querySelector('.spot-marker-name').innerHTML =
      group.names.map(escapeHtml).join('<br>')
    const mk = new window.AMap.Marker({
      position: [group.lng, group.lat],
      content: el,
      anchor: 'bottom-center',
      offset: new window.AMap.Pixel(0, -2),
      zIndex: 60,
    })
    mapInstance.add(mk)
    // 点击定位针 → 高亮该组代表景点（写 activeSpot，驱动便签卡；不自动上滑避免争抢缩放）
    mk.on('click', () => highlightMarker(group.names[0]))
    markerInstances.push(mk)
    // 组内所有名字都指向同一个图标（点击任意行程卡片都能定位/高亮它）
    group.names.forEach(name => {
      markerPositions.value[name] = { lat: group.lat, lng: group.lng }
      markerEls.value[name] = el
      markerByName[name] = mk
    })
    markerPrimary.add(group.names[0]) // 代表名：declutter 只按代表名排布
  })
  // 地图渲染稳定后执行展开（防重叠）
  setTimeout(declutterMarkers, 350)
  // 缩放到覆盖全部景点（行程横跨多个方位时避免只看到市中心一小块）
  fitToMarkers()
  // 新增钉标后同步当前过滤态（高亮命中类别 / 压暗未命中）+ 重画「从哪到哪」路线
  applyFilterToMap()
  drawRoute()
}

/** 视图缩放到全部景点：行程常横跨一个城市多个方位（如三亚的天涯海角→亚龙湾约 30km），缩到能同时看到 */
function fitToMarkers() {
  if (!mapInstance || !window.AMap || !markerInstances.length) return
  try { mapInstance.setFitView(markerInstances, false, [80, 40, 40, 80], 15) } catch (e) {}
}

/** 上下排列：重叠的景点标记竖向堆叠（不挤成一团），全部可见可点 */
const OVERLAP_PX = 48     // 屏幕像素：间距小于此值视为重叠
const STACK_STEP = 46     // 上下排列的垂直间距（px）
let declutterTimer = null

function declutterMarkers() {
  if (!mapInstance || !window.AMap || markerPrimary.size < 2) return
  try {
    // 只排布"同地点合并后"的代表名；同一图标的其它名字共享同一坐标，不能参与排布（否则会把合并的图标重新堆开）
    const names = [...markerPrimary]
    // 原始坐标 → 当前屏幕像素；地图视图未就绪/正在变换时可能返回 NaN，跳过本轮
    const screen = {}
    for (const n of names) {
      const p = markerPositions.value[n]
      const px = mapInstance.lngLatToContainer([p.lng, p.lat])
      if (!px || !isFinite(px.x) || !isFinite(px.y)) return
      screen[n] = { x: px.x, y: px.y }
    }
    // 贪心分组：互相在阈值内的归为一组
    const assigned = new Set()
    const groups = []
    names.forEach(n => {
      if (assigned.has(n)) return
      const group = [n]
      assigned.add(n)
      names.forEach(m => {
        if (assigned.has(m)) return
        const dx = screen[n].x - screen[m].x
        const dy = screen[n].y - screen[m].y
        if (Math.sqrt(dx * dx + dy * dy) < OVERLAP_PX) { group.push(m); assigned.add(m) }
      })
      groups.push(group)
    })
    // 单点归位到精确坐标；重叠组上下排列（竖向堆叠）
    groups.forEach(group => {
      if (group.length === 1) {
        const p = markerPositions.value[group[0]]
        if (isFinite(p.lat) && isFinite(p.lng)) markerByName[group[0]]?.setPosition([p.lng, p.lat])
        return
      }
      const cx = group.reduce((s, n) => s + screen[n].x, 0) / group.length
      const cy = group.reduce((s, n) => s + screen[n].y, 0) / group.length
      group.forEach((n, i) => {
        // 围绕中心垂直堆叠：第 i 个放在 cy - 偏移（居中分布），同 x
        const offsetY = (i - (group.length - 1) / 2) * STACK_STEP
        const ll = mapInstance.containerToLngLat(cx, cy + offsetY)
        if (ll && isFinite(ll.lng) && isFinite(ll.lat)) {
          markerByName[n]?.setPosition([ll.lng, ll.lat])
        }
      })
    })
  } catch (e) {
    // 地图视图未就绪（containerToLngLat 可能抛 LngLat(NaN)），静默跳过本轮，等 moveend/zoomend 稳定后再排
  }
}

/** 缩放/平移结束后防抖重排，保证不重叠 */
function scheduleDeclutter() {
  clearTimeout(declutterTimer)
  declutterTimer = setTimeout(declutterMarkers, 150)
}

/** 高亮地图上的指定景点标记 */
function highlightMarker(name) {
  activeSpot.value = name
  Object.values(markerEls.value).forEach(el => el && el.classList.remove('active'))
  const el = markerEls.value[name]
  if (el) el.classList.add('active')
}

/** 点击行程卡片：定位到该景点的精确坐标（居中 + 放大 + 高亮标记） */
function goToSpot(name) {
  const pos = markerPositions.value[name]
  if (!pos || !mapInstance || !window.AMap) return
  mapInstance.setCenter([pos.lng, pos.lat])
  // 放大到至少 16 级，看清精确位置（若已更近则保持）
  animateMapZoom(Math.max(mapInstance.getZoom(), 16))
  highlightMarker(name)
}

/** 把 LLM 生成的景点名（常带【x】/（x）等元数据后缀）与静态图映射做模糊匹配 */
function matchLocalImage(name, map) {
  if (!map) return ''
  if (map[name]) return map[name]
  const clean = String(name).replace(/【[^】]*】/g, '').replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim()
  if (clean && map[clean]) return map[clean]
  for (const k of Object.keys(map)) {
    if (clean && (clean.includes(k) || k.includes(clean))) return map[k]
  }
  return ''
}

async function loadImages(dayPlans) {
  // 优先用预拉取的本地静态图（同源、必然能加载），作为 slot0（可靠兜底）。
  // 再调后端 /api/map/attraction-images 聚合高德 POI 真实照片，最多 3 张。
  // 不复用 /scene/image：线上百度 AK 为空，恒返回不可达的 picsum 占位图。
  if (attractionImageMap === null) {
    try { const r = await fetch('/attraction-images.json').then(res => res.json()); attractionImageMap = r || {} } catch { attractionImageMap = {} }
  }
  const names = new Set()
  for (const dp of dayPlans) for (const s of (dp.timeSlots||[])) { if (s.attraction) names.add(s.attraction) }
  if (!names.size) return

  // 本地静态图 → slot0
  for (const name of names) {
    const local = matchLocalImage(name, attractionImageMap)
    attractionImages.value[name] = local ? [local] : []
  }

  // 后端高德真实图片（合入，最多 3 张，去重；本地图保底不被覆盖）
  const city = planData.value?.destination || destCity.value || ''
  try {
    const qs = new URLSearchParams()
    if (city) qs.set('city', city)
    qs.set('names', [...names].join(','))
    const r = await fetch(`/api/map/attraction-images?${qs.toString()}`)
    const body = await r.json()
    const imap = body && body.code === 0 ? (body.data || {}) : {}
    for (const name of names) {
      const urls = imap[name] || []
      const arr = attractionImages.value[name] || []
      for (const u of urls) { if (arr.length >= 3) break; if (!arr.includes(u)) arr.push(u) }
      attractionImages.value[name] = arr
    }
  } catch { /* 后端不可达则仅保留本地/占位 */ }
}

/** 单个景点当前的图片列表（最多 3 张） */
function imagesOf(name) {
  return attractionImages.value[name] || []
}

// ====== 记忆层标识：长期偏好(user_id) + 会话上下文(session_id) ======
function getUserShortId() {
  try {
    const token = getToken()
    if (!token) return ''
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = payload.padEnd(Math.ceil(payload.length / 4) * 4, '=')
    const u = JSON.parse(atob(padded))
    return u.userId ? String(u.userId) : ''
  } catch { return '' }
}
function getSessionId() {
  let sid = localStorage.getItem('agent_session_id')
  if (!sid) {
    sid = 'sess_' + Math.random().toString(36).slice(2) + Date.now().toString(36)
    localStorage.setItem('agent_session_id', sid)
  }
  return sid
}

// ====== SSE 流式生成 ======
/** 提交调整需求：带新需求重新生成行程 */
function submitAdjust() {
  const text = adjustText.value.trim()
  if (!text) { showToast(t('map.askAIEmpty')); return }
  adjustText.value = ''
  phase.value = 'generating'
  startGeneration(text)
}

// ====== 问AI栏「按住说话」：Web Speech API 语音转文字，识别后填入并自动提交（复用 components.* 文案） ======
const voiceRecording = ref(false)
let voiceReco = null
function voiceStart() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SR) { showToast(t('components.voiceUnsupportedBrowser')); return }
  try {
    if (voiceReco) { try { voiceReco.abort() } catch {} }
    voiceReco = new SR()
    voiceReco.lang = 'zh-CN'
    voiceReco.continuous = false
    voiceReco.interimResults = false
    voiceReco.onresult = (e) => {
      let text = ''
      for (let i = e.resultIndex; i < e.results.length; i++) { if (e.results[i].isFinal) text += e.results[i][0].transcript }
      text = text.trim()
      voiceRecording.value = false
      if (text) { adjustText.value = text; submitAdjust() }
      else showToast(t('components.noVoiceContent'))
    }
    voiceReco.onerror = () => { voiceRecording.value = false; showToast(t('components.recognitionFailedRetry')) }
    voiceReco.onend = () => { voiceRecording.value = false }
    voiceReco.start()
    voiceRecording.value = true
  } catch (e) { voiceRecording.value = false; showToast(t('components.voiceStartFailed')) }
}
function voiceStop() { if (voiceReco) { try { voiceReco.stop() } catch {} } }

async function startGeneration(adjustment = '') {
  // SSERACE-1 修复：新起 SSE 前先中止旧流（agentPlanStream 返回中止函数），避免旧流回调污染新方案（submitAdjust 复用此入口一并覆盖）
  if (streamAbort) {
    try { if (typeof streamAbort === 'function') streamAbort(); else if (streamAbort.abort) streamAbort.abort() } catch (e) {}
    streamAbort = null
  }
  // 重新生成前清空旧标记与旧方案
  if (mapInstance && window.AMap) {
    markerInstances.forEach(mk => { try { mapInstance.remove(mk) } catch {} })
  }
  markerInstances = []
  markerPositions.value = {}
  markerEls.value = {}
  markerPrimary.clear()
  activeSpot.value = ''
  activeFilter.value = ''
  if (routeLine) { try { routeLine.setMap(null) } catch {} ; routeLine = null }
  planData.value = null
  customTitle.value = '' // 新行程重置用户自定义标题
  const steps = stepList.value
  steps.forEach(s => s.status = 'wait')
  agentLogs.value = []
  agentProgress.value = 0
  agentStep.value = t('map.connectingAgent')

  streamAbort = agentPlanStream({
    destination: destCity.value, origin: originCity.value, days: tripDays.value,
    budget: Number(route.query.budget) || 5000, people: tripPeople.value,
    companion: route.query.companion || '',
    styles: route.query.styles ? route.query.styles.split(',') : [],
    hotel_level: route.query.hotel_level || '舒适型', pace: route.query.pace || '适中',
    schedule: route.query.schedule || '', cabin: route.query.cabin || '',
    months: route.query.months ? route.query.months.split(',').map(Number).filter(Boolean) : [],
    // 记忆层标识：Agent 据此读取长期偏好 + 会话上下文
    user_id: getUserShortId(), session_id: getSessionId(),
    // 行程调整需求：Agent 在规划时应用
    adjustment: adjustment || undefined,
  }, {
    onProgress(event) {
      const idx = phaseIdx[event.phase] ?? -1
      const msg = event.message || ''
      if (msg) {
        agentLogs.value.push(msg)
        if (agentLogs.value.length > 8) agentLogs.value.shift()
      }
      if (idx >= 0) {
        steps.forEach((s, i) => { s.status = i < idx ? 'done' : i === idx ? 'doing' : 'wait' })
        agentStep.value = steps[idx]?.name || ''
        agentProgress.value = [10, 30, 55, 80, 95][idx] || agentProgress.value
      }
      if (event.event_type === 'phase_end' && idx >= 0) {
        steps[idx].status = 'done'
        agentProgress.value = [20, 45, 70, 95, 100][idx] || agentProgress.value
      }
    },
    onComplete: async (event) => {
      const d = event.data || {}
      agentProgress.value = 100
      steps.forEach(s => s.status = 'done')
      planData.value = {
        destination: d.destination || destCity.value, days: d.days || tripDays.value,
        people: d.people || tripPeople.value, overview: d.overview || '',
        dayPlans: (d.day_plans || []).map(dp => ({
          day: dp.day, dayTitle: dp.day_title || '',
          timeSlots: (dp.time_slots || []).map(s => ({
            timeOfDay: s.time_of_day || '', time: s.time || '', attraction: s.attraction || '',
            activity: s.activity || '', duration: s.duration || '', cost: `${s.cost || 0}${t('common.yuan')}`,
            transport: s.transport || '', tips: s.tips || '', hours: s.hours || '',
          })), meals: dp.meals || [],
        })), tips: d.tips || [],
      }
      costBreakdown.value = d.budget_detail || null
      hotelList.value = (d.hotels || []).map(h => ({
        name: h.name, district: h.district, pricePerNight: h.price_per_night, rating: h.rating, highlights: h.highlights,
      }))
      const markerNames = []
      ;(d.day_plans || []).forEach(dp => { (dp.time_slots || []).forEach(s => { if (s.attraction && !markerNames.includes(s.attraction)) markerNames.push(s.attraction) }) })
      addMarkers(markerNames); loadImages(d.day_plans || [])
      phase.value = 'completed'; snapTo(MAX)
    },
    onError(msg) { showToast(msg || t('map.planFailed')); goBackToPrev() },
  })
}

/** 加载已保存的行程（兼容旧/新结构；任何异常都不重定向，保证能打开） */
async function loadSavedPlan(planId) {
  try {
    const result = await planApi.getPlanById(planId)
    if (result.code !== 0 || !result.data) { showToast(t('map.loadFailed')); return }
    const data = result.data
    destCity.value = data.destination || ''
    tripDays.value = data.days || 3
    tripPeople.value = data.people || 2

    let pd = data.planData || data.planJson
    if (typeof pd === 'string') { try { pd = JSON.parse(pd) } catch { pd = null } }

    const isObj = !!pd && typeof pd === 'object'
    const dayPlansArr = isObj && Array.isArray(pd.dayPlans) ? pd.dayPlans : []
    const hasContent = isObj && typeof pd.content === 'string' && pd.content.trim().length > 0

    if (dayPlansArr.length > 0) {
      // 结构化方案（新 Agent / 旧结构化）
      planData.value = {
        destination: pd.destination || destCity.value,
        days: pd.days || tripDays.value,
        people: pd.people || tripPeople.value,
        overview: pd.overview || '',
        dayPlans: dayPlansArr.map((dpItem) => {
          const dp = dpItem || {}
          return {
            day: dp.day,
            dayTitle: dp.day_title || dp.dayTitle || '',
            timeSlots: (dp.time_slots || dp.timeSlots || []).map((sItem) => {
              const s = sItem || {}
              return {
                timeOfDay: s.time_of_day || s.timeOfDay || '',
                time: s.time || '',
                attraction: s.attraction || '',
                activity: s.activity || '',
                duration: s.duration || '',
                cost: s.cost != null ? `${s.cost}${t('common.yuan')}` : `0${t('common.yuan')}`,
                transport: s.transport || '',
                tips: s.tips || '',
                hours: s.hours || '',
              }
            }),
            meals: dp.meals || [],
          }
        }),
        tips: pd.tips || [],
      }
      hotelList.value = Array.isArray(pd.hotels) ? pd.hotels : []
      costBreakdown.value = pd.budgetDetail || pd.budget_detail || null
      markdownContent.value = ''
    } else {
      // markdown 型旧方案（聊天保存 / 旧文本流，dayPlans 为空但有 content）
      const md = hasContent ? pd.content : ''
      planData.value = null
      hotelList.value = []
      costBreakdown.value = null
      markdownContent.value = md || (isObj ? JSON.stringify(pd, null, 2) : t('map.planNoContent'))
    }

    // 先让内容显示出来，地图后台初始化（不阻塞）
    phase.value = 'completed'
    agentProgress.value = 100
    stepList.value.forEach(s => s.status = 'done')
    snapTo(MAX)
    showToast(t('map.savedPlanLoaded'))
    initMap().then(() => {
      if (planData.value) {
        const names = []
        ;(planData.value.dayPlans || []).forEach(dp => (dp.timeSlots || []).forEach(s => { if (s.attraction && !names.includes(s.attraction)) names.push(s.attraction) }))
        addMarkers(names)
        loadImages(planData.value.dayPlans || [])
      }
    })
  } catch (e) {
    console.error('加载保存规划失败:', e)
    showToast(t('map.planOpenFailed'))
  }
}

onMounted(() => {
  const savedId = route.query.savedPlanId
  if (!savedId && !destCity.value) { router.replace('/trips'); return }
  if (savedId) { loadSavedPlan(savedId) } else { initMap(); startGeneration() }
})
/** 销毁地图实例释放内存：AMap 不销毁会保留 WebGL 上下文/瓦片缓存/事件监听，
 *  每次进入地图页都泄漏几十 MB，逛几页后标签页被系统杀掉显示 out of memory */
function destroyMap() {
  // MAPFAIL-1 修复：Leaflet 兜底实例只有 remove() 无 destroy()，双判断销毁防泄漏
  try { if (mapInstance && typeof mapInstance.destroy === 'function') mapInstance.destroy() } catch {}
  try { if (mapInstance && typeof mapInstance.remove === 'function') mapInstance.remove() } catch {}
  mapInstance = null
  markerInstances.forEach(mk => { try { if (mk && mk.setMap) mk.setMap(null) } catch {} })
  markerInstances = []
  for (const k of Object.keys(markerByName)) delete markerByName[k]
  markerPrimary.clear()
  if (routeLine) { try { routeLine.setMap(null) } catch {} ; routeLine = null }
}

onBeforeUnmount(() => { if (streamAbort) streamAbort(); if (voiceReco) { try { voiceReco.abort() } catch {} } cancelMapZoom(); if (declutterTimer) clearTimeout(declutterTimer); destroyMap() })
/** 回退到上一个界面（无历史时兜底回 /trips，避免空白页） */
function goBackToPrev() {
  if (window.history.length <= 1) router.replace('/trips')
  else router.back()
}
function goBack() { goBackToPrev() }
function handleStop() { if (streamAbort) streamAbort(); goBackToPrev() }
</script>

<template>
  <div class="page">
    <!-- 地图 -->
    <div class="map-wrap">
      <div id="agent-bmap" class="bmap"></div>
      <div class="fb" v-if="!mapLoaded">
        <div class="fb-grad"></div>
        <div class="fb-route">
          <div class="fb-city"><div class="fb-dot p"></div><span>{{ originCity }}</span></div>
          <div class="fb-line"><div class="fb-li"></div></div>
          <div class="fb-city"><div class="fb-dot o"></div><span>{{ destCity }}</span></div>
        </div>
      </div>
    </div>

    <!-- 地图覆盖层：抽屉收起时（State A）显示左上筛选胶囊 + 底部选中景点便签卡；容器 pointer-events:none 放行地图手势 -->
    <transition name="ov">
      <div class="map-overlay" v-show="showMapOverlay">
        <div class="filter-chips">
          <div v-for="c in filterChips" :key="c.key" class="chip" :class="{ on: activeFilter === c.key }" @click="onFilter(c.key)">
            <span class="chip-label">{{ t(c.labelKey) }}</span>
            <span class="chip-hint">{{ t(c.hintKey) }}</span>
            <svg class="chip-arrow" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path d="M6 9l6 6 6-6" fill="none" stroke="#888" stroke-width="2"/></svg>
          </div>
        </div>
        <div class="spot-note-card" :class="noteFilterClass" v-if="noteSpot" @click="goToSpot(noteSpot.attraction)">
          <div class="snc-img" :class="{ plc: !noteImgStyle.backgroundImage }" :style="noteImgStyle">
            <svg v-if="!noteImgStyle.backgroundImage" viewBox="0 0 200 130"><rect width="200" height="130" rx="10" fill="#ede9fe"/><circle cx="100" cy="55" r="30" fill="rgba(255,255,255,0.5)"/></svg>
          </div>
          <div class="snc-info">
            <div class="snc-title">{{ noteSpot.attraction }}</div>
            <div class="snc-meta">
              <span>🕐 {{ noteSpot.hours || t('map.seeNotes') }}</span>
              <span>⏱ {{ t('map.suggestPlay', { duration: noteSpot.duration }) }}</span>
            </div>
            <div class="snc-cost" v-if="noteSpot.cost">💰 {{ noteSpot.cost }}</div>
          </div>
        </div>
      </div>
    </transition>

    <!-- 顶部 -->
    <div class="top">
      <div class="top-btn" @click="goBack">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#fff" stroke-width="2.2"><polyline points="15 18 9 12 15 6"/></svg>
      </div>
      <span class="top-title" v-show="showTopTitle">{{ destCity }} · {{ tripDays }}{{ t('common.days') }}</span>
    </div>

    <!-- 悬浮保存：抽屉外，地图上方，生成完成后显示 -->
    <div class="save-float" v-if="phase==='completed'&&planData" :class="{ saving: isSaving }" @click="savePlan">
      <span class="save-float-icon">💾</span><span>{{ isSaving ? t('map.saving') : t('map.saveTrip') }}</span>
    </div>

    <!-- 可拖拽抽屉 -->
    <div class="drawer"
      :class="{ collapsed: showMapOverlay }"
      :style="{transform:`translateY(${100-drawerPct}%)`,transition:isDragging?'none':'transform 0.5s cubic-bezier(0.34,1.56,0.64,1)'}">
      <div class="handle" @click="snapTo(drawerPct>50?MIN:MAX)"
        @touchstart.passive="onHandleTouchStart" @touchmove="onHandleTouchMove"
        @touchend="onHandleTouchEnd" @touchcancel="onHandleTouchEnd">
        <div class="bar"></div>
      </div>

      <!-- 底部天数胶囊条（State A 收起态）：地图上方那一行，点某天自动上滑展示 -->
      <div class="day-pills collapsed" v-if="showMapOverlay">
        <span v-for="(dp, idx) in planData.dayPlans" :key="dp.day" class="dpill" :class="{ on: activeDay === idx }" @click.stop="pickDay(idx)">{{ t('map.dayN', { day: dp.day }) }}</span>
      </div>

      <div class="body" ref="bodyEl" @scroll.passive="onBodyScroll" @touchmove.passive="releaseDayLock" @wheel.passive="releaseDayLock">

        <!-- 携程同款生成动画 -->
        <div v-if="phase==='generating'" class="gen">
          <!-- 顶部状态条 -->
          <div class="gen-status-bar">
            <div class="gsb-left">
              <span class="gsb-spinner"></span>
              <span class="gsb-text">{{agentStep}}</span>
            </div>
            <span class="gsb-pct">{{agentProgress}}%</span>
          </div>

          <!-- 骨架卡片（携程同款 shimmer） -->
          <div class="skeleton-list">
            <div v-for="i in 3" :key="i" class="sk-card" :style="{animationDelay: (i-1)*0.15+'s'}">
              <!-- 标题行骨架 -->
              <div class="sk-row sk-title"></div>
              <!-- 标签行 -->
              <div class="sk-row sk-tags"><span></span><span></span></div>
              <!-- 双图骨架 -->
              <div class="sk-imgs"><div class="sk-img"></div><div class="sk-img"></div></div>
              <!-- 文本行骨架 x3 -->
              <div class="sk-row sk-text"></div>
              <div class="sk-row sk-text short"></div>
              <div class="sk-row sk-text shorter"></div>
              <!-- 底部元信息 -->
              <div class="sk-row sk-meta"></div>
            </div>
          </div>

          <button class="stop-btn" @click="handleStop">{{ t('agent.stopGenerate') }}</button>
        </div>

        <!-- 旧版 markdown 保存行程兜底展示 -->
        <div v-else-if="phase==='completed' && markdownContent" class="done">
          <div class="md-title">📄 {{ t('map.planContent') }}</div>
          <div class="md-content">{{ markdownContent }}</div>
        </div>

        <!-- 完成 -->
        <div v-else-if="phase==='completed'&&planData" class="done">
          <!-- 行程标题（State B，可编辑：铅笔进入编辑态；Enter/失焦保存，Esc/✕ 取消，双击标题也可编辑） -->
          <div class="ih-title-row">
            <input v-if="editingTitle" v-model="titleDraft" class="ih-title-input"
                   :placeholder="defaultTitle" maxlength="30"
                   @keyup.enter="saveEditTitle" @keyup.esc="cancelEditTitle" @blur="onTitleBlur" />
            <span v-else class="ih-title" @dblclick="startEditTitle">{{ displayTitle }}</span>
            <span v-if="!editingTitle" class="ih-edit" :title="t('map.editTitle')" @click="startEditTitle">✏️</span>
            <span v-else class="ih-title-actions">
              <span class="ih-btn cancel" :title="t('map.cancelEdit')" @mousedown.prevent="cancelEditTitle">✕</span>
              <span class="ih-btn ok" :title="t('map.saveTitle')" @mousedown.prevent="saveEditTitle">✓</span>
            </span>
          </div>
          <div class="ih-sub-row">
            <span class="ih-date" :title="t('map.setDepartureDate')">📅 {{ t('map.setDepartureDate') }}</span>
            <span class="ih-summary">{{ itinPreview }}</span>
          </div>

          <!-- 吸顶头部：双 Tab + 天数胶囊，滚动时一起保持在顶部不隐藏 -->
          <div class="sticky-head">
            <!-- 双 Tab（行程详情 / 旅行灵感） -->
            <div class="tab-row">
              <div class="tab" :class="{on:activeTab==='itinerary'}" @click="activeTab='itinerary'">📅 {{ t('map.itineraryDetailTab') }}</div>
              <div class="tab" :class="{on:activeTab==='inspiration'}" @click="activeTab='inspiration'">💡 {{ t('map.inspirationTab') }}</div>
            </div>
            <div class="day-pills" v-if="activeTab==='itinerary'">
              <span v-for="(dp, idx) in planData.dayPlans" :key="dp.day" class="dpill" :class="{ on: activeDay === idx }" @click="scrollToDay(idx)">{{ t('map.dayN', { day: dp.day }) }}</span>
            </div>
          </div>

          <!-- 行程详情 Tab：所有天连续渲染，往下翻即可看完每一天；各天有独立标题分隔（同参考图） -->
          <template v-if="activeTab==='itinerary'">

            <div v-for="(dp, idx) in planData.dayPlans" :key="dp.day" :ref="el => dayEls[idx] = el" class="day-section">
              <div class="day-heading">
                <div class="day-heading-title">
                  <span class="day-badge">{{ t('map.dayN', { day: dp.day }) }}</span>
                  <span class="day-title" v-if="dayTitleOf(idx)">{{ dayTitleOf(idx) }}</span>
                </div>
                <span class="opt-chip">🧭 {{ t('map.optimizeChip') }}</span>
              </div>

              <!-- 第1天：住宿推荐置顶（对齐参考图：酒店卡位于第一天内容上方） -->
              <template v-if="idx === 0 && hotelList.length">
                <div class="hotels-section">
                  <div v-for="(h, hi) in hotelList" :key="hi" class="hotel-card" :class="hotelFilterClass()">
                    <div class="hotel-img-plc">
                      <svg viewBox="0 0 200 140"><rect width="200" height="140" rx="12" :fill="['#ede9fe','#fef3e8','#e8f4f8'][hi%3]"/><rect x="60" y="45" width="80" height="50" rx="6" fill="rgba(255,255,255,0.6)"/></svg>
                    </div>
                    <div class="hotel-info">
                      <div class="hotel-name">{{h.name}}</div>
                      <div class="hotel-meta">📍 {{h.district}} · ⭐{{h.rating}}</div>
                      <div class="hotel-desc" v-if="h.highlights">{{h.highlights}}</div>
                      <div class="hotel-price-row">
                        <span class="hotel-price">¥{{h.pricePerNight?.toLocaleString()}}</span><span class="hotel-unit">/{{ t('common.night') }}</span>
                        <span class="hotel-total">{{ t('map.totalNights', { n: tripDays }) }} ¥{{Number(h.pricePerNight||0)>0 ? (h.pricePerNight*tripDays).toLocaleString() : '--'}}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </template>

              <div class="day-content tl">
                <div v-for="(slot, si) in dp.timeSlots" :key="si" class="tl-item">
                  <div class="spot-card"
                       :class="[{ active: activeSpot === slot.attraction }, filterClassOf(slot)]"
                       @click="goToSpot(slot.attraction)"
                       :style="{ animationDelay: (si * 0.04) + 's' }">
                  <div class="spot-header">
                    <span class="spot-num">{{si+1}}</span>
                    <div class="spot-title-row">
                      <span class="spot-title">{{slot.attraction}}</span>
                      <span v-if="si<=1" class="spot-badges">
                        <span class="hot-badge" v-if="si===0">🔥 {{ t('map.hotBadge10') }}</span>
                        <span class="level-badge" v-if="si===0">5A</span>
                        <span class="rank-badge">🏆 {{ t('map.mustVisitRank') }}</span>
                      </span>
                    </div>
                  </div>
                  <div class="spot-hours" v-if="slot.hours||slot.tips">
                    <span class="hours-icon">🕐</span>
                    <span>{{slot.hours||t('map.seeNotes')}}</span>
                  </div>
                  <div class="spot-imgs">
                    <template v-for="(img, ii) in imagesOf(slot.attraction)" :key="'i'+ii">
                      <div class="spot-img" :style="{backgroundImage:'url('+img+')'}"></div>
                    </template>
                    <template v-for="n in (3 - imagesOf(slot.attraction).length)" :key="'p'+n">
                      <div class="spot-img spot-img-plc">
                        <svg viewBox="0 0 400 240"><rect width="400" height="240" :fill="['#e8f4f8','#fef3e8','#ede9fe'][n-1]"/><circle cx="200" cy="100" r="50" fill="rgba(255,255,255,0.5)"/></svg>
                      </div>
                    </template>
                  </div>
                  <div class="spot-intro">{{slot.activity}}</div>
                  <div class="spot-intro spot-tip-inline" v-if="slot.tips" v-text="slot.tips"></div>
                  <div class="spot-transport">
                    <span v-if="slot.transport">🚗 {{slot.transport}}</span>
                    <span>⏱ {{slot.duration}}</span>
                    <span class="spot-cost">💰 {{slot.cost}}</span>
                  </div>
                  </div>
                  <span class="tl-node"></span>
                </div>
                <div class="tl-item" v-if="dp.meals?.length">
                  <span class="tl-node tl-food"></span>
                  <div class="meals-bar" :class="foodFilterClass()">
                    <span class="meals-tag">🍽</span>
                    <span v-for="(m, mi) in dp.meals" :key="m" class="meal" :style="{ animationDelay: (mi * 0.04) + 's' }">{{m}}</span>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- 旅行灵感 Tab -->
          <template v-if="activeTab==='inspiration'">
            <div class="insp-overview" v-if="planData.overview">{{ planData.overview }}</div>
            <div class="tips-list" v-if="planData.tips && planData.tips.length">
              <div v-for="(tip, ti) in planData.tips" :key="ti" class="tip-item">💡 {{ tip }}</div>
            </div>
            <div class="hotels-section">
              <h3 class="stay-title">🏨 {{ t('map.stayAndBudget') }}</h3>
              <div v-for="(h, hi) in hotelList" :key="hi" class="hotel-card" :class="hotelFilterClass()">
                <div class="hotel-img-plc">
                  <svg viewBox="0 0 200 140"><rect width="200" height="140" rx="12" :fill="['#ede9fe','#fef3e8','#e8f4f8'][hi%3]"/><rect x="60" y="45" width="80" height="50" rx="6" fill="rgba(255,255,255,0.6)"/></svg>
                </div>
                <div class="hotel-info">
                  <div class="hotel-name">{{h.name}}</div>
                  <div class="hotel-meta">📍 {{h.district}} · ⭐{{h.rating}}</div>
                  <div class="hotel-desc" v-if="h.highlights">{{h.highlights}}</div>
                  <div class="hotel-price-row">
                    <span class="hotel-price">¥{{h.pricePerNight?.toLocaleString()}}</span><span class="hotel-unit">/{{ t('common.night') }}</span>
                    <span class="hotel-total">{{ t('map.totalNights', { n: tripDays }) }} ¥{{Number(h.pricePerNight||0)>0 ? (h.pricePerNight*tripDays).toLocaleString() : '--'}}</span>
                  </div>
                </div>
              </div>
              <div class="budget-card" v-if="costBreakdown">
                <h3>💰 {{ t('map.costEstimate') }}</h3>
                <div class="budget-rows">
                  <div v-for="(v,k) in costBreakdown" :key="k" class="budget-row" :class="{total:k==='total'}">
                    <span>{{ budgetLabel(k) }}</span><b>¥{{v.toLocaleString?.()||v}}</b>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>

    </div>

    <!-- 问AI栏：固定视口底部悬浮胶囊，抽屉展开时显示、收起态滑出（恢复"问AI/按住说话"可用） -->
    <transition name="ask-fade">
      <div class="ask-bar" v-if="phase==='completed' && planData && !showMapOverlay">
        <div class="ask-inner">
          <input v-model="adjustText" :placeholder="t('map.askAIHold')" @keyup.enter="submitAdjust" />
          <div class="ask-mic" :class="{ recording: voiceRecording }" :title="t('map.askAIHold')"
               @touchstart.prevent="voiceStart" @touchend.prevent="voiceStop" @touchcancel.prevent="voiceStop"
               @mousedown.prevent="voiceStart" @mouseup.prevent="voiceStop" @mouseleave.prevent="voiceStop">
            <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10a7 7 0 0 0 14 0"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
          </div>
          <div class="ask-fab" @click="submitAdjust" :title="t('map.askAIHold')">＋</div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.page { position:fixed; inset:0; background:#0f1923; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC',sans-serif; color:#333; }

/* 地图 */
.map-wrap { position:absolute; inset:0; z-index:1; }
.bmap { width:100%; height:100%; }
.fb { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; pointer-events:none; }
.fb-grad { position:absolute; inset:0; background:linear-gradient(160deg, #1a1a2e, #16213e, #0f3460, #1a1a2e); }
.fb-route { position:relative; z-index:1; display:flex; align-items:center; gap:20px; }
.fb-city { display:flex; flex-direction:column; align-items:center; gap:8px; }
.fb-dot { width:18px; height:18px; border-radius:50%; animation:pulse 2s ease-in-out infinite; }
.fb-dot.p { background:#8b5cf6; box-shadow:0 0 20px rgba(139,92,246,0.5); }
.fb-dot.o { background:#f59e0b; box-shadow:0 0 20px rgba(245,158,11,0.5); animation-delay:1s; }
.fb-city span { font-size:15px; font-weight:700; color:#fff; text-shadow:0 1px 4px rgba(0,0,0,0.5); }
.fb-line { width:100px; height:2px; background:linear-gradient(90deg,#8b5cf6,#f59e0b); border-radius:1px; overflow:hidden; }
.fb-li { width:100%; height:100%; background:repeating-linear-gradient(90deg,transparent,transparent 6px,rgba(255,255,255,0.4) 6px,rgba(255,255,255,0.4) 10px); animation:dash 0.6s linear infinite; }
@keyframes dash { to{background-position:16px 0} }
@keyframes pulse { 0%,100%{transform:scale(1);opacity:0.9} 50%{transform:scale(1.5);opacity:0.5} }

/* 顶部 */
.top { position:absolute; top:0; left:0; right:0; z-index:10; display:flex; align-items:center; padding:calc(env(safe-area-inset-top)+8px) 16px 8px; gap:12px; }
.top-btn { width:36px; height:36px; border-radius:50%; background:rgba(0,0,0,0.35); backdrop-filter:blur(10px); display:flex; align-items:center; justify-content:center; cursor:pointer; }
.top-btn:active { background:rgba(0,0,0,0.55); }
.top-title { flex:1; text-align:center; font-size:17px; font-weight:600; color:#fff; text-shadow:0 1px 4px rgba(0,0,0,0.4); }

/* 抽屉 */
.drawer { position:absolute; bottom:0; left:0; right:0; z-index:20; height:96%; background:rgba(245,245,247,0.97);backdrop-filter:blur(20px); border-radius:20px 20px 0 0; display:flex; flex-direction:column; box-shadow:0 -6px 30px rgba(0,0,0,0.12); will-change:transform; }
.handle { height:36px; min-height:36px; display:flex; align-items:center; justify-content:center; cursor:pointer; touch-action:none; }
.bar { width:36px; height:5px; background:#d1d5db; border-radius:3px; transition:transform .2s cubic-bezier(.34,1.56,.64,1), background .2s; }
.handle:active .bar { transform:scaleX(1.5); background:#a5b4fc; }
.body { flex:1; overflow-y:auto; -webkit-overflow-scrolling:touch; padding:0 16px 84px; }
/* 收起态（State A）：抽屉只露手柄+天数胶囊条，隐藏滚动内容与问AI栏 */
.drawer.collapsed .body { display:none; }

/* 地图覆盖层（State A）：容器穿透放行地图手势，仅胶囊/便签卡捕获点击 */
.map-overlay { position:absolute; inset:0; z-index:12; pointer-events:none; }
.ov-enter-active, .ov-leave-active { transition:opacity .25s ease; }
.ov-enter-from, .ov-leave-to { opacity:0; }

/* 左上筛选胶囊 */
.filter-chips { position:absolute; top:calc(env(safe-area-inset-top) + 66px); left:16px; display:flex; flex-direction:column; gap:9px; pointer-events:auto; }
.chip { display:flex; align-items:center; gap:7px; padding:8px 12px; border-radius:20px; background:rgba(255,255,255,0.92); backdrop-filter:blur(10px); -webkit-backdrop-filter:blur(10px); box-shadow:0 2px 10px rgba(0,0,0,0.12); border:1px solid rgba(255,255,255,0.6); cursor:pointer; transition:all .2s; }
.chip:active { transform:scale(.95); }
.chip.on { background:linear-gradient(135deg,#8b5cf6,#6366f1); border-color:transparent; box-shadow:0 4px 14px rgba(99,102,241,.35); }
.chip-label { font-size:13px; font-weight:600; color:#333; }
.chip.on .chip-label { color:#fff; }
.chip-hint { font-size:10.5px; color:#888; background:rgba(0,0,0,0.05); padding:1px 7px; border-radius:8px; }
.chip.on .chip-hint { color:#fff; background:rgba(255,255,255,0.25); }
.chip-arrow { transition:transform .2s; }
.chip.on .chip-arrow { transform:rotate(180deg); filter:invert(1); }

/* 底部选中景点便签卡 */
.spot-note-card { position:absolute; left:16px; right:16px; bottom:calc(13vh + 16px);pointer-events:auto; display:flex; gap:11px; padding:10px; border-radius:16px; background:rgba(255,255,255,0.95); backdrop-filter:blur(14px); -webkit-backdrop-filter:blur(14px); box-shadow:0 8px 26px rgba(0,0,0,0.16); border:1px solid rgba(255,255,255,0.6); cursor:pointer; }
.spot-note-card:active { transform:scale(.98); }
.snc-img { width:78px; height:64px; border-radius:10px; background-size:cover; background-position:center; flex-shrink:0; overflow:hidden; }
.snc-img.plc { display:flex; align-items:center; }
.snc-img.plc svg { width:100%; height:100%; }
.snc-info { flex:1; min-width:0; display:flex; flex-direction:column; justify-content:center; }
.snc-title { font-size:15px; font-weight:700; color:#1a1a2e; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.snc-meta { display:flex; gap:10px; font-size:11.5px; color:#888; margin-top:4px; }
.snc-cost { font-size:12px; color:#10b981; font-weight:600; margin-top:4px; }

/* 天数胶囊（收起条 + 展开行共用） */
.day-pills { display:flex; gap:8px; padding:0 0 12px; overflow-x:auto; scrollbar-width:none; }
.day-pills::-webkit-scrollbar { display:none; }
.day-pills.collapsed { padding:7px 16px 10px; background:#fff; border-top:1px solid #eef0f4; touch-action:none; }
.dpill { flex-shrink:0; padding:6px 15px; border-radius:14px; background:#fff; border:1px solid #eef0f4; color:#666; font-size:12.5px; font-weight:600; cursor:pointer; transition:all .2s; }
.dpill:active { transform:scale(.94); }
.dpill.on { background:#1a1a2e; color:#fff; border-color:#1a1a2e; }

/* 行程详情：所有天连续滚动阅读 —— 双 Tab + 天数胶囊一起吸顶，各天区块用分隔线区分 */
.sticky-head { position:sticky; top:0; z-index:6; background:#f5f5f7; margin:0 -16px; padding:0 16px; border-bottom:1px solid #ececf0; box-shadow:0 1px 6px rgba(0,0,0,0.03); }
.sticky-head .tab-row { margin-bottom:6px; }
.sticky-head .day-pills { margin:0; padding:0 0 10px; border:none; box-shadow:none; }
.day-section { scroll-margin-top:100px; margin-bottom:4px; }
.day-section + .day-section { border-top:1px dashed #e3e3e8; margin-top:6px; padding-top:16px; }
.day-content .spot-card:last-child { margin-bottom:2px; }

/* 携程时间线：左侧竖线 + 圆形节点，卡片在竖线右侧 */
.tl { position:relative; padding-left:26px; }
.tl::before { content:''; position:absolute; left:9px; top:6px; bottom:16px; width:2px; background:linear-gradient(180deg,#d8d8e2,#e3e3e8); border-radius:2px; }
.tl-item { position:relative; margin-bottom:12px; }
.tl-item:last-child { margin-bottom:2px; }
.tl-node { position:absolute; left:-25px; top:14px; width:12px; height:12px; border-radius:50%; background:#fff; border:3px solid #8b5cf6; box-shadow:0 1px 4px rgba(139,92,246,0.35); z-index:1; }
.tl-food { border-color:#f59e0b; box-shadow:0 1px 4px rgba(245,158,11,0.35); }
.tl .spot-card { margin-bottom:0; }
.tl .spot-card.active ~ .tl-node, .tl .spot-card.filter-hl ~ .tl-node { background:#8b5cf6; border-color:#8b5cf6; }
.tl .spot-card.filter-dim ~ .tl-node { opacity:.38; }
.tl-item:has(.meals-bar.filter-hl) .tl-node { background:#f59e0b; border-color:#f59e0b; }
.tl-item:has(.meals-bar.filter-dim) .tl-node { opacity:.38; }

/* 行程标题行（State B） */
.ih-title-row { display:flex; align-items:flex-start; gap:8px; padding:0 0 6px; }
.ih-title { flex:1; min-width:0; font-size:19px; font-weight:800; color:#1a1a2e; line-height:1.35; word-break:break-word; }
.ih-title-input { flex:1; min-width:0; border:1px solid rgba(139,92,246,0.4); outline:none; background:#fff; border-radius:8px; padding:6px 10px; font-size:16px; font-weight:700; color:#1a1a2e; box-sizing:border-box; }
.ih-title-input:focus { border-color:#8b5cf6; box-shadow:0 0 0 2px rgba(139,92,246,0.15); }
.ih-edit { flex-shrink:0; font-size:16px; cursor:pointer; padding-top:2px; transition:transform .2s; }
.ih-edit:hover { transform:scale(1.12); }
.ih-title-actions { flex-shrink:0; display:flex; gap:6px; padding-top:2px; }
.ih-btn { width:26px; height:26px; border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:13px; font-weight:700; cursor:pointer; user-select:none; -webkit-user-select:none; transition:transform .15s; }
.ih-btn:active { transform:scale(.9); }
.ih-btn.cancel { background:#f1f3f5; color:#888; }
.ih-btn.ok { background:linear-gradient(135deg,#8b5cf6,#6366f1); color:#fff; box-shadow:0 2px 8px rgba(139,92,246,0.3); }
.ih-sub-row { display:flex; align-items:flex-start; gap:10px; padding-bottom:12px; }
.ih-date { font-size:12px; color:#8b5cf6; font-weight:600; flex-shrink:0; padding-top:1px; }
.ih-summary { font-size:12px; color:#888; line-height:1.5; word-break:break-word; }

/* 天数标题 + 顺路优化 chip（State B）—— 携程式：Day 徽标 + 主题标题占一整行 */
.day-heading { display:flex; flex-direction:column; gap:8px; padding:2px 0 12px; }
.day-heading-title { display:flex; align-items:center; gap:10px; min-width:0; }
.day-badge { flex-shrink:0; padding:5px 12px; border-radius:14px; font-size:13px; font-weight:800; color:#fff; background:linear-gradient(135deg,#8b5cf6,#6366f1); box-shadow:0 2px 8px rgba(139,92,246,0.25); }
.day-title { font-size:15px; font-weight:700; color:#1a1a2e; min-width:0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.opt-chip { align-self:flex-start; padding:4px 10px; border-radius:12px; font-size:11px; font-weight:600; background:rgba(139,92,246,0.1); color:#7c3aed; }

/* 旅行灵感 */
.insp-overview { font-size:13px; color:#555; line-height:1.8; background:#fff; border-radius:14px; padding:14px; margin-bottom:12px; }
.tips-list { display:flex; flex-direction:column; gap:8px; margin-bottom:12px; }
.tip-item { font-size:13px; color:#666; line-height:1.7; background:#fff; border-radius:12px; padding:10px 12px; box-shadow:0 1px 4px rgba(0,0,0,0.04); }
.stay-title { margin:0 0 10px; font-size:15px; font-weight:700; color:#1a1a2e; }

/* 问AI栏（State B 常态，抽屉内钉底） */
/* 问AI栏：固定视口底部的悬浮胶囊，抽屉展开时显示、收起时滑出 */
.ask-bar { position:fixed; left:0; right:0; bottom:0; z-index:30; display:flex; justify-content:center; padding:8px 14px calc(env(safe-area-inset-bottom) + 10px); pointer-events:none; }
.ask-inner { pointer-events:auto; flex:1; max-width:320px; display:flex; align-items:center; gap:7px; background:rgba(255,255,255,0.95); -webkit-backdrop-filter:blur(14px); backdrop-filter:blur(14px); border-radius:24px; padding:6px 6px 6px 14px; box-shadow:0 8px 26px rgba(0,0,0,0.18); border:1px solid rgba(255,255,255,0.7); }
.ask-inner:focus-within { box-shadow:0 8px 26px rgba(0,0,0,0.22), 0 0 0 2px rgba(139,92,246,.25); }
.ask-bar input { flex:1; min-width:0; appearance:none; -webkit-appearance:none; border:none !important; outline:none; background:transparent !important; box-shadow:none; margin:0; padding:0; height:32px; font-size:13px; color:#333; box-sizing:border-box; }
.ask-bar input::placeholder { color:#a5a5aa; }
.ask-mic { width:34px; height:34px; border-radius:50%; flex-shrink:0; display:flex; align-items:center; justify-content:center; color:#8b5cf6; background:rgba(139,92,246,0.1); cursor:pointer; touch-action:none; user-select:none; -webkit-user-select:none; }
.ask-mic:active, .ask-mic.recording { background:linear-gradient(135deg,#8b5cf6,#6366f1); color:#fff; }
.ask-mic.recording { animation:voicePulse 1.5s ease-in-out infinite; }
.ask-fab { width:34px; height:34px; border-radius:50%; background:linear-gradient(135deg,#8b5cf6,#6366f1); color:#fff; font-size:19px; display:flex; align-items:center; justify-content:center; cursor:pointer; flex-shrink:0; transition:transform .2s; }
.ask-fab:active { transform:scale(.92); }
.ask-fade-enter-active, .ask-fade-leave-active { transition:opacity .3s ease, transform .3s ease; }
.ask-fade-enter-from, .ask-fade-leave-to { opacity:0; transform:translateY(120%); }
@keyframes voicePulse { 0%,100%{box-shadow:0 0 0 4px rgba(139,92,246,.15)} 50%{box-shadow:0 0 0 10px rgba(139,92,246,0)} }

/* 生成动画 — 携程同款 Shimmer 骨架屏 */
.gen { padding:0; }

/* 顶部状态条 */
.gen-status-bar { display:flex; align-items:center; justify-content:space-between; padding:14px 16px; background:#fff; border-radius:16px; margin-bottom:12px; }
.gsb-left { display:flex; align-items:center; gap:10px; }
.gsb-spinner { width:20px; height:20px; border:2.5px solid #e8e0f0; border-top-color:#8b5cf6; border-radius:50%; animation:spin .8s linear infinite; }
@keyframes spin { to{transform:rotate(360deg)} }
.gsb-text { font-size:14px; font-weight:500; color:#333; }
.gsb-pct { font-size:18px; font-weight:700; color:#8b5cf6; }

/* 骨架卡片 */
.skeleton-list { display:flex; flex-direction:column; gap:10px; }
.sk-card { background:#fff; border-radius:16px; padding:16px; animation:cardIn .4s ease-out both; }
@keyframes cardIn { from{opacity:0;transform:translateY(14px) scale(.97)} to{opacity:1;transform:translateY(0) scale(1)} }

/* Shimmer 动效 */
.sk-row { height:14px; border-radius:7px; background:linear-gradient(90deg, #f0f0f5 25%, #e8e8f0 50%, #f0f0f5 75%); background-size:200% 100%; animation:shimmer 1.8s ease-in-out infinite; }
@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }

.sk-title { height:18px; width:55%; margin-bottom:10px; }
.sk-tags { height:22px; width:35%; margin-bottom:12px; display:flex; gap:6px; background:none; animation:none; }
.sk-tags span { flex:1; height:100%; border-radius:12px; background:linear-gradient(90deg, #f0f0f5 25%, #e8e8f0 50%, #f0f0f5 75%); background-size:200% 100%; animation:shimmer 1.8s ease-in-out infinite; }
.sk-tags span:nth-child(2) { animation-delay:.2s; }

.sk-imgs { display:flex; gap:8px; margin-bottom:12px; }
.sk-img { flex:1; height:100px; border-radius:10px; background:linear-gradient(90deg, #f0f0f5 25%, #e8e8f0 50%, #f0f0f5 75%); background-size:200% 100%; animation:shimmer 1.8s ease-in-out infinite; }
.sk-img:nth-child(2) { animation-delay:.15s; }

.sk-text { margin-bottom:8px; }
.sk-text.short { width:75%; }
.sk-text.shorter { width:45%; }
.sk-meta { width:60%; height:12px; margin-top:10px; }

.stop-btn { display:block; margin:16px auto; padding:10px 40px; border:1px solid #e2e8f0; border-radius:22px; background:#fff; color:#94a3b8; font-size:13px; cursor:pointer; }

/* 完成 */
.done { padding:12px 0 12px; animation:fadeUp .45s ease-out both; }
@keyframes fadeUp { from{opacity:0;transform:translateY(14px)} to{opacity:1;transform:translateY(0)} }

/* 旧版 markdown 保存行程兜底 */
.md-title { font-size:15px; font-weight:700; color:#1a1a2e; margin-bottom:10px; }
.md-content { white-space:pre-wrap; word-break:break-word; line-height:1.8; font-size:13px; color:#444; background:#fff; border-radius:12px; padding:14px; }

/* 地图标记（自定义样式替代默认 label） */

/* 双Tab */
.tab-row { display:flex; gap:0; background:#fff; border-radius:20px; padding:4px; margin-bottom:12px; }
.tab { flex:1; text-align:center; padding:10px; border-radius:16px; font-size:14px; font-weight:500; color:#888; cursor:pointer; transition:all .25s cubic-bezier(.34,1.56,.64,1); }
.tab:active { transform:scale(.95); }
.tab.on { background:linear-gradient(135deg,#8b5cf6,#6366f1); color:#fff; font-weight:600; box-shadow:0 2px 8px rgba(139,92,246,0.3); }
/* 双模块严丝合缝：只保留外侧圆角，朝中间的那一侧直角，避免两个圆角对夹出缝隙 */
.tab:first-child.on { border-radius:16px 0 0 16px; }
.tab:last-child.on { border-radius:0 16px 16px 0; }

/* 景点卡片 */
.spot-card { background:#fff; border-radius:16px; margin-bottom:12px; overflow:hidden; box-shadow:0 1px 4px rgba(0,0,0,0.04); animation:cardIn .45s ease both; cursor:pointer; }
.spot-card.active { box-shadow:0 0 0 2px rgba(124,58,237,.55), 0 6px 16px rgba(124,58,237,.15); }
.spot-card.active .spot-title { color:#7c3aed; }
.spot-header { display:flex; align-items:center; gap:10px; padding:14px 14px 0; }
.spot-num { width:28px; height:28px; border-radius:50%; background:#8b5cf6; color:#fff; display:flex; align-items:center; justify-content:center; font-size:14px; font-weight:700; flex-shrink:0; }
.spot-title-row { flex:1; display:flex; align-items:center; gap:6px; flex-wrap:wrap; }
.spot-title { font-size:17px; font-weight:700; color:#1a1a2e; }
.spot-badges { display:flex; gap:4px; }
.hot-badge { padding:2px 8px; border-radius:8px; font-size:10px; background:#fee2e2; color:#dc2626; font-weight:600; }
.level-badge { padding:2px 8px; border-radius:8px; font-size:10px; background:#dbeafe; color:#2563eb; font-weight:600; }
.rank-badge { padding:2px 8px; border-radius:8px; font-size:10px; background:#fef3c7; color:#d97706; font-weight:600; }

.spot-hours { display:flex; align-items:center; gap:6px; padding:8px 14px 0; font-size:12px; color:#888; }
.hours-icon { font-size:14px; }

.spot-imgs { display:flex; gap:6px; padding:10px 14px 0; }
.spot-img { flex:1; min-width:0; height:110px; border-radius:10px; background-size:cover; background-position:center; box-shadow:inset 0 0 0 1px rgba(0,0,0,0.04); }
.spot-img-plc { overflow:hidden; }
.spot-img-plc svg { width:100%; height:100%; }

.spot-intro { padding:10px 14px 0; font-size:13px; color:#555; line-height:1.8; }
.spot-tip-inline { font-size:12px; color:#d97706; }

.spot-transport { display:flex; gap:14px; padding:10px 14px 0; font-size:12px; color:#888; }
.spot-cost { color:#10b981; font-weight:600; margin-left:auto; }

/* 美食 */
.meals-bar { padding:10px 14px; margin-bottom:12px; background:#fff; border-radius:12px; display:flex; align-items:center; gap:8px; flex-wrap:wrap; animation:cardIn .45s ease both; }
.meals-tag { font-size:14px; }
.meal { padding:4px 10px; background:rgba(245,158,11,0.1); color:#d97706; border-radius:10px; font-size:11px; }

/* 住宿 */
.hotels-section { padding-bottom:20px; }
.hotel-card { display:flex; gap:12px; background:#fff; border-radius:16px; padding:12px; margin-bottom:10px; animation:cardIn .45s ease both; }
.hotel-img-plc { width:100px; height:70px; border-radius:10px; overflow:hidden; flex-shrink:0; }
.hotel-img-plc svg { width:100%; height:100%; }
.hotel-info { flex:1; min-width:0; }
.hotel-name { font-size:15px; font-weight:600; color:#1a1a2e; }
.hotel-meta { font-size:12px; color:#888; margin:3px 0; }
.hotel-desc { font-size:12px; color:#666; margin-bottom:4px; }
.hotel-price-row { display:flex; align-items:baseline; gap:4px; }
.hotel-price { font-size:18px; font-weight:700; color:#e74c3c; }
.hotel-unit { font-size:11px; color:#999; }
.hotel-total { font-size:11px; color:#888; margin-left:8px; }

/* 预算 */
.budget-card { background:#fff; border-radius:16px; padding:16px; margin-top:8px; animation:cardIn .45s ease both; }
.budget-card h3 { margin:0 0 10px; font-size:15px; }
.budget-rows { display:flex; flex-direction:column; gap:6px; }
.budget-row { display:flex; justify-content:space-between; padding:10px 14px; background:#f8f9fa; border-radius:10px; font-size:13px; color:#555; }
.budget-row.total { background:linear-gradient(135deg,#8b5cf6,#6366f1); color:#fff; }
.budget-row.total span, .budget-row.total b { color:#fff; }
.budget-row b { font-size:14px; color:#1a1a2e; }

/* 悬浮保存按钮：地图上方（抽屉外），顶部栏下方右侧，紧凑醒目 */
.save-float { position:absolute; top:calc(env(safe-area-inset-top) + 56px); right:16px; z-index:15; display:flex; align-items:center; gap:5px; padding:6px 12px; border-radius:16px; background:linear-gradient(135deg,#10b981,#059669); color:#fff; font-size:12px; font-weight:600; line-height:1; box-shadow:0 3px 12px rgba(16,185,129,.4); cursor:pointer; animation:floatIn .4s ease; }
.save-float:active { transform:scale(.93); }
.save-float.saving { opacity:.75; pointer-events:none; }
.save-float-icon { font-size:13px; }
@keyframes floatIn { from{opacity:0;transform:translateY(-8px)} to{opacity:1;transform:translateY(0)} }

/* ===== 抽屉内容动画（克制的入场 + 天数切换过渡，去掉持续循环动效） ===== */
@keyframes cardIn { from{opacity:0;transform:translateY(10px)} to{opacity:1;transform:translateY(0)} }

/* 徽章轻弹出（一次性） */
.spot-badges span { animation:badgeIn .4s ease both; }
@keyframes badgeIn { from{opacity:0;transform:scale(.85)} to{opacity:1;transform:scale(1)} }

/* 美食标签轻弹出（一次性） */
.meal { animation:mealPop .35s ease both; }
@keyframes mealPop { from{opacity:0;transform:scale(.88) translateY(3px)} to{opacity:1;transform:scale(1) translateY(0)} }

/* 卡片悬停反馈（亮度/颜色，避免与入场 transform fill 冲突） */
.spot-card:hover .spot-title { color:#7c3aed; }
.spot-card:hover { box-shadow:0 5px 16px rgba(0,0,0,.08); }
.spot-img { transition:filter .3s ease; }
.spot-card:hover .spot-img { filter:brightness(1.06); }
.hotel-card:hover { box-shadow:0 6px 16px rgba(0,0,0,.08); }
.hotel-card { transition:box-shadow .25s ease; }

/* 天数切换过渡（淡入淡出 + 上下滑动） */
.day-switch-enter-active, .day-switch-leave-active { transition:opacity .28s ease, transform .28s ease; }
.day-switch-enter-from { opacity:0; transform:translateY(14px); }
.day-switch-leave-to { opacity:0; transform:translateY(-10px); }

/* 筛选：高亮命中类别 / 压暗未命中（行程卡 / 用餐 / 住宿 / 便签卡；地图钉标见全局样式） */
.spot-card.filter-dim, .hotel-card.filter-dim, .meals-bar.filter-dim, .spot-note-card.filter-dim { opacity:.38; filter:grayscale(.55); }
.spot-card.filter-hl { box-shadow:0 0 0 2px rgba(124,58,237,.55), 0 8px 20px rgba(124,58,237,.15); }
.spot-card.filter-hl .spot-title { color:#7c3aed; }
.hotel-card.filter-hl { box-shadow:0 0 0 2px rgba(124,58,237,.45); }
.hotel-card.filter-hl .hotel-name { color:#7c3aed; }
.meals-bar.filter-hl { background:rgba(139,92,246,.16); }
.spot-note-card.filter-hl { box-shadow:0 0 0 2px rgba(124,58,237,.5), 0 8px 26px rgba(124,58,237,.18); }
</style>

<!-- 地图标记样式：AMap 的 content 是 JS 动态插入地图容器，不在组件 scoped 作用域内，必须用全局样式 -->
<style>
.spot-marker { display:flex; flex-direction:column; align-items:center; gap:2px; cursor:pointer; transform-origin:bottom center; animation:markerPop .35s cubic-bezier(.34,1.56,.64,1) both; }
.spot-marker-name { padding:2px 7px; border-radius:7px; background:rgba(255,255,255,0.55); backdrop-filter:blur(6px); -webkit-backdrop-filter:blur(6px); color:#333; font-size:10px; font-weight:500; line-height:1.4; white-space:nowrap; text-align:center; box-shadow:0 1px 4px rgba(0,0,0,0.12); border:1px solid rgba(255,255,255,0.5); }
.spot-marker-pin { display:block; filter:drop-shadow(0 2px 4px rgba(0,0,0,0.22)); transition:transform .25s ease, filter .25s ease; }
.spot-marker.active .spot-marker-pin { transform:scale(1.3); filter:drop-shadow(0 0 8px rgba(124,58,237,.9)); }
.spot-marker.active .spot-marker-name { color:#7c3aed; font-weight:700; }
.city-marker { padding:5px 12px; border-radius:10px; background:linear-gradient(135deg,#8b5cf6,#6366f1); color:#fff; font-size:12px; font-weight:600; box-shadow:0 3px 10px rgba(99,102,241,0.35); white-space:nowrap; }
@keyframes markerPop { from{opacity:0;transform:scale(.6) translateY(6px)} to{opacity:1;transform:scale(1) translateY(0)} }
/* 筛选（地图钉标）：命中类别高亮描边、未命中压暗 */
.spot-marker.filter-dim { opacity:.35; }
.spot-marker.filter-hl .spot-marker-pin { filter:drop-shadow(0 0 7px rgba(124,58,237,.95)); }
.spot-marker.filter-hl .spot-marker-name { color:#7c3aed; font-weight:700; }
/* 位置为近似估算（地理编码全部 miss，散点兜底）：淡色虚线描边 + ≈ 角标，避免误认为精确定位 */
.spot-marker.approx .spot-marker-name { color:#9ca3af; border:1px dashed rgba(156,163,175,0.8); }
.spot-marker.approx .spot-marker-pin { opacity:.7; }
.spot-marker-approx { position:absolute; top:-4px; left:-4px; width:16px; height:16px; border-radius:8px; background:#f59e0b; color:#fff; font-size:11px; font-weight:700; line-height:16px; text-align:center; box-shadow:0 1px 3px rgba(0,0,0,0.25); z-index:2; }
</style>
