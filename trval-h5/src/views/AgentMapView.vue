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

defineOptions({ name: 'AgentMapView' })
const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const destCity = ref(route.query.destination || '')
const originCity = ref(route.query.origin || t('map.departure'))
const tripDays = ref(Number(route.query.days) || 3)
const tripPeople = ref(Number(route.query.people) || 2)
const phase = ref('generating')
const activeTab = ref('plan') // plan | hotel
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
// 抽屉拉到最底部（收起）时隐藏调整胶囊
const showAdjustBar = computed(() => phase.value === 'completed' && !!planData.value && drawerPct.value > 40)

// ====== 简略预览：抽屉收起时展示行程/住宿/天数摘要（携程同款） ======
const PREVIEW_MAX_PCT = 36 // 抽屉位置低于此值（接近收起）时显示简略预览
const showCollapsedPreview = computed(() =>
  phase.value === 'completed' && !!planData.value && drawerPct.value <= PREVIEW_MAX_PCT
)
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
const hotelPreview = computed(() => {
  if (!hotelList.value.length) return t('map.noHotels')
  const first = hotelList.value[0]
  const extra = hotelList.value.length > 1 ? t('map.hotelMore', { n: hotelList.value.length }) : ''
  const price = first.pricePerNight ? t('map.hotelPriceFrom', { price: first.pricePerNight }) : ''
  return `${first.name}${extra}${price}`
})
function expandFromPreview() { snapTo(MID) }

// ====== 可拖拽抽屉（仅手柄区域可拖拽，内容区自由滚动） ======
const MIN = 26, MID = 55, MAX = 92
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
  const deltaZoom = (deltaPct / (MAX_PCT - MIN_PCT)) * ZOOM_RANGE
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
let markerInstances = [] // 已添加的地图标记实例（重新生成时清除）
const markerByName = {}  // 景点名 → AMap.Marker 实例（扇形展开用）
const markerPrimary = new Set() // 同地点合并后的代表景点名；declutter 只按代表名排布，避免把已合并的同一图标重新堆开
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
  // 高德地理编码兜底
  try {
    const r = await fetch(`/api/map/suggestion?keyword=${encodeURIComponent(c)}`).then(r => r.json())
    if (r.code === 0 && r.data?.[0]) return { lat: r.data[0].latitude, lng: r.data[0].longitude }
  } catch {}
  // 最终兜底：中国大陆中心
  return { lat: 35.86, lng: 104.19 }
}

let mapLoaded = false

async function initMap() {
  if (window.AMap) { mapLoaded = true; await initAmapMap(); return }
  await new Promise(r => {
    const s = document.createElement('script'); s.src = '/api/map/script'
    s.onload = () => { let n = 0; const c = setInterval(() => { if (window.AMap) { clearInterval(c); r() } else if (n++ > 20) { clearInterval(c); r() } }, 200) }
    s.onerror = () => r(); document.head.appendChild(s)
  })
  if (window.AMap) { mapLoaded = true; await initAmapMap() }
}

async function initAmapMap() {
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

/** 地理编码：把景点名解析为真实坐标（带 city 参数提高准确度；兜底用城市中心附近小偏移） */
async function geocodeName(name) {
  try {
    // 传 city 让高德限定在城市内搜索，避免"西湖"这类歧义词搜到别处
    const r = await fetch(`/api/map/suggestion?keyword=${encodeURIComponent(name)}&city=${encodeURIComponent(destCity.value)}`).then(r => r.json())
    if (r.code === 0 && r.data && r.data[0] && r.data[0].lat != null && r.data[0].lng != null) {
      return { lat: r.data[0].lat, lng: r.data[0].lng }
    }
  } catch {}
  const center = await getCenter(destCity.value)
  return { lat: center.lat + (Math.random() - .5) * .02, lng: center.lng + (Math.random() - .5) * .02 }
}

/** 同地点合并阈值（米）：坐标距离小于该值的推荐视为同一个地点，合并为一个定位图标。
 *  例：LLM 常把"武侯祠（含锦里古街）"和"锦里古街"同时推荐出来，两点紧邻，地图上应只显示一个 pin。 */
const SAME_PLACE_M = 150
/** 按坐标相近程度把景点分组成"同一地点"，每组保持行程出现顺序（names[0] 为最早出现者，即标签最上一行） */
function groupBySamePlace(markers) {
  const groups = []
  for (const m of markers) {
    let host = null
    for (const g of groups) {
      const dy = (g.lat - m.lat) * 111000
      const dx = (g.lng - m.lng) * 111000 * Math.cos(g.lat * Math.PI / 180)
      if (Math.sqrt(dx * dx + dy * dy) < SAME_PLACE_M) { host = g; break }
    }
    if (host) host.names.push(m.name)
    else groups.push({ lat: m.lat, lng: m.lng, names: [m.name] })
  }
  return groups
}
/** 转义 HTML，防止景点名里的特殊字符破坏标签 DOM */
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]))
}

async function addMarkers(names) {
  if (!mapInstance || !window.AMap) return
  // 并行地理编码真实坐标，避免全部随机堆在市中心
  const markers = await Promise.all(names.map(async (name) => {
    const c = await geocodeName(name)
    return { name, lat: c.lat, lng: c.lng }
  }))
  // 同一地点去重：相邻的推荐合并为一个定位图标，避免地图上出现重复定位针
  const groups = groupBySamePlace(markers)
  groups.forEach(group => {
    // 标准定位针图标（teardrop pin）+ 干净文字气泡
    const el = document.createElement('div')
    el.className = 'spot-marker'
    el.innerHTML =
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
  // 优先用预拉取的本地静态图（同源、必然能加载）。
  // 不再调 /scene/image：线上百度 AK 为空，恒返回不可达的 picsum 占位图 → 卡片背景空白。
  if (attractionImageMap === null) {
    try { const r = await fetch('/attraction-images.json').then(res => res.json()); attractionImageMap = r || {} } catch { attractionImageMap = {} }
  }
  const names = new Set()
  for (const dp of dayPlans) for (const s of (dp.timeSlots||[])) { if (s.attraction) names.add(s.attraction) }
  for (const name of names) {
    const local = matchLocalImage(name, attractionImageMap)
    if (local) attractionImages.value[name] = local
    // 未命中静态图 → 不设图，卡片显示占位 SVG
  }
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
  if (!text) { showToast(t('map.enterAdjust')); return }
  adjustText.value = ''
  phase.value = 'generating'
  startGeneration(text)
}

async function startGeneration(adjustment = '') {
  // 重新生成前清空旧标记与旧方案
  if (mapInstance && window.AMap) {
    markerInstances.forEach(mk => { try { mapInstance.remove(mk) } catch {} })
  }
  markerInstances = []
  markerPositions.value = {}
  markerEls.value = {}
  markerPrimary.clear()
  activeSpot.value = ''
  planData.value = null
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
onBeforeUnmount(() => { if (streamAbort) streamAbort(); cancelMapZoom(); if (declutterTimer) clearTimeout(declutterTimer) })
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


    <!-- 顶部 -->
    <div class="top">
      <div class="top-btn" @click="goBack">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#fff" stroke-width="2.2"><polyline points="15 18 9 12 15 6"/></svg>
      </div>
      <span class="top-title">{{ destCity }} · {{ tripDays }}{{ t('common.days') }}</span>
    </div>

    <!-- 悬浮保存：抽屉外，地图上方，生成完成后显示 -->
    <div class="save-float" v-if="phase==='completed'&&planData" :class="{ saving: isSaving }" @click="savePlan">
      <span class="save-float-icon">💾</span><span>{{ isSaving ? t('map.saving') : t('map.saveTrip') }}</span>
    </div>

    <!-- 可拖拽抽屉 -->
    <div class="drawer"
      :style="{transform:`translateY(${100-drawerPct}%)`,transition:isDragging?'none':'transform 0.5s cubic-bezier(0.34,1.56,0.64,1)'}">
      <div class="handle" @click="snapTo(drawerPct>50?MIN:MAX)"
        @touchstart.passive="onHandleTouchStart" @touchmove="onHandleTouchMove"
        @touchend="onHandleTouchEnd" @touchcancel="onHandleTouchEnd">
        <div class="bar"></div>
      </div>
      <div class="body">

        <!-- 简略预览：抽屉拉到底部（收起）时显示行程/住宿/天数摘要，点击或上滑展开（携程同款） -->
        <transition name="cp">
          <div class="collapsed-preview" v-if="showCollapsedPreview" @click="expandFromPreview"
            @touchstart.passive="onHandleTouchStart" @touchmove="onHandleTouchMove"
            @touchend="onHandleTouchEnd" @touchcancel="onHandleTouchEnd">
            <div class="cp-row">
              <span class="cp-ico">🗺️</span>
              <span class="cp-txt">{{ itinPreview }}</span>
              <span class="cp-arrow">{{ t('map.swipeUp') }}</span>
            </div>
            <div class="cp-row">
              <span class="cp-ico">🏨</span>
              <span class="cp-txt">{{ hotelPreview }}</span>
            </div>
            <div class="cp-days">
              <span v-for="dp in planData.dayPlans" :key="dp.day" class="cp-day">D{{ dp.day }}</span>
              <span class="cp-days-hint">{{ t('map.totalDays', { days: planData.days || tripDays }) }}</span>
            </div>
          </div>
        </transition>

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
          <!-- 双 Tab -->
          <div class="tab-row">
            <div class="tab" :class="{on:activeTab==='plan'}" @click="activeTab='plan'">📅 {{ t('map.itineraryTab') }}</div>
            <div class="tab" :class="{on:activeTab==='hotel'}" @click="activeTab='hotel'">🏨 {{ t('map.hotelTab') }}</div>
          </div>

          <!-- 行程 Tab -->
          <template v-if="activeTab==='plan'">
            <div class="day-tabs">
              <div v-for="(dp, idx) in planData.dayPlans" :key="dp.day"
                   class="day-tab" :class="{on:activeDay===idx}" @click="activeDay=idx"
                   :style="{ animationDelay: (idx * 0.06) + 's' }">
                <span class="dt-num">Day{{dp.day}}</span>
                <span class="dt-title">{{dp.dayTitle?.replace('第'+dp.day+'天：','').replace('第'+dp.day+'天:','')}}</span>
              </div>
            </div>

            <transition name="day-switch" mode="out-in">
              <div v-if="planData.dayPlans[activeDay]" :key="activeDay" class="day-content">
              <div v-for="(slot, si) in planData.dayPlans[activeDay].timeSlots" :key="si" class="spot-card"
                   :class="{ active: activeSpot === slot.attraction }"
                   @click="goToSpot(slot.attraction)"
                   :style="{ animationDelay: (si * 0.06) + 's' }">
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
                  <div class="spot-img" v-if="attractionImages[slot.attraction]"
                       :style="{backgroundImage:'url('+attractionImages[slot.attraction]+')'}"></div>
                  <div class="spot-img spot-img-plc" v-else>
                    <svg viewBox="0 0 400 240"><rect width="400" height="240" fill="#e8f4f8"/><circle cx="200" cy="100" r="50" fill="rgba(255,255,255,0.5)"/></svg>
                  </div>
                  <div class="spot-img spot-img-plc">
                    <svg viewBox="0 0 400 240"><rect width="400" height="240" fill="#fef3e8"/><circle cx="200" cy="100" r="50" fill="rgba(255,255,255,0.5)"/></svg>
                  </div>
                </div>
                <div class="spot-intro">{{slot.activity}}</div>
                <div class="spot-intro spot-tip-inline" v-if="slot.tips" v-text="slot.tips"></div>
                <div class="spot-transport">
                  <span v-if="slot.transport">🚗 {{slot.transport}}</span>
                  <span>⏱ {{slot.duration}}</span>
                  <span class="spot-cost">💰 {{slot.cost}}</span>
                </div>
              </div>
                <div class="meals-bar" v-if="planData.dayPlans[activeDay].meals?.length">
                  <span class="meals-tag">🍽</span>
                  <span v-for="(m, mi) in planData.dayPlans[activeDay].meals" :key="m" class="meal" :style="{ animationDelay: (mi * 0.06) + 's' }">{{m}}</span>
                </div>
              </div>
            </transition>
          </template>

          <!-- 住宿 Tab -->
          <template v-if="activeTab==='hotel'">
            <div class="hotels-section">
              <div v-for="(h, hi) in hotelList" :key="hi" class="hotel-card">
                <div class="hotel-img-plc">
                  <svg viewBox="0 0 200 140"><rect width="200" height="140" rx="12" :fill="['#ede9fe','#fef3e8','#e8f4f8'][hi%3]"/><rect x="60" y="45" width="80" height="50" rx="6" fill="rgba(255,255,255,0.6)"/></svg>
                </div>
                <div class="hotel-info">
                  <div class="hotel-name">{{h.name}}</div>
                  <div class="hotel-meta">📍 {{h.district}} · ⭐{{h.rating}}</div>
                  <div class="hotel-desc" v-if="h.highlights">{{h.highlights}}</div>
                  <div class="hotel-price-row">
                    <span class="hotel-price">¥{{h.pricePerNight?.toLocaleString()}}</span><span class="hotel-unit">/{{ t('common.night') }}</span>
                    <span class="hotel-total">{{ t('map.totalNights', { n: tripDays }) }} ¥{{(h.pricePerNight*tripDays)?.toLocaleString()}}</span>
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

    <!-- 调整行程：固定在视口底部，不随抽屉/页面滑动；抽屉拉到底部时滑出隐藏 -->
    <transition name="adjust-fade">
      <div class="bottom-bar" v-if="showAdjustBar">
        <div class="adjust-bar">
          <input v-model="adjustText" :placeholder="t('map.adjustPlaceholder')" @keyup.enter="submitAdjust" />
          <div class="adjust-btn" @click="submitAdjust">{{ t('map.adjust') }}</div>
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
.drawer { position:absolute; bottom:0; left:0; right:0; z-index:20; height:88%; background:rgba(245,245,247,0.97); backdrop-filter:blur(20px); border-radius:20px 20px 0 0; display:flex; flex-direction:column; box-shadow:0 -6px 30px rgba(0,0,0,0.12); will-change:transform; }
.handle { height:36px; min-height:36px; display:flex; align-items:center; justify-content:center; cursor:pointer; touch-action:none; }
.bar { width:36px; height:5px; background:#d1d5db; border-radius:3px; transition:transform .2s cubic-bezier(.34,1.56,.64,1), background .2s; }
.handle:active .bar { transform:scaleX(1.5); background:#a5b4fc; }
.body { flex:1; overflow-y:auto; -webkit-overflow-scrolling:touch; padding:0 16px 120px; }

/* 简略预览：抽屉收起时的行程/住宿/天数摘要（携程同款） */
.collapsed-preview { padding:9px 0 10px; cursor:pointer; touch-action:none; }
.cp-row { display:flex; align-items:center; gap:7px; font-size:12.5px; color:#333; line-height:1.45; }
.cp-row + .cp-row { margin-top:5px; }
.cp-ico { font-size:14px; }
.cp-txt { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.cp-arrow { flex-shrink:0; color:#8b5cf6; font-size:11px; font-weight:600; }
.cp-days { display:flex; align-items:center; gap:6px; margin-top:8px; flex-wrap:wrap; }
.cp-day { padding:2px 9px; border-radius:8px; background:linear-gradient(135deg,#8b5cf6,#6366f1); color:#fff; font-size:10.5px; font-weight:600; }
.cp-days-hint { margin-left:auto; font-size:11px; color:#999; }

/* 摘要卡出现/消失动画：淡入 + 从下方上滑 */
.cp-enter-active { transition: opacity .3s ease, transform .3s ease; }
.cp-leave-active { transition: opacity .2s ease, transform .2s ease; }
.cp-enter-from { opacity: 0; transform: translateY(18px); }
.cp-leave-to { opacity: 0; transform: translateY(18px); }

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
.done { padding:12px 0 160px; animation:fadeUp .45s ease-out both; }
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

/* 天数标签栏 */
.day-tabs { display:flex; gap:8px; padding:0 0 12px; overflow-x:auto; scrollbar-width:none; }
.day-tabs::-webkit-scrollbar { display:none; }
.day-tab { flex-shrink:0; padding:8px 14px; border-radius:14px; background:#fff; cursor:pointer; text-align:center; min-width:72px; transition:all .25s; animation:cardIn .4s ease both; }
.day-tab:active { transform:scale(.94); }
.day-tab.on { background:#1a1a2e; }
.day-tab.on .dt-num, .day-tab.on .dt-title { color:#fff; }
.dt-num { display:block; font-size:12px; font-weight:600; color:#8b5cf6; }
.dt-title { display:block; font-size:10px; color:#888; margin-top:2px; white-space:nowrap; }

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
.spot-img { flex:1; height:130px; border-radius:10px; background-size:cover; background-position:center; }
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

/* 底部：独立悬浮的调整胶囊（窄、高、离底悬浮） */
.bottom-bar { position:fixed; bottom:0; left:0; right:0; z-index:30; display:flex; justify-content:center; padding:8px 14px calc(env(safe-area-inset-bottom) + 26px); background:transparent; pointer-events:none; }
.adjust-bar { pointer-events:auto; flex:1; max-width:280px; display:flex; align-items:center; gap:6px; background:rgba(255,255,255,0.9); backdrop-filter:blur(14px); -webkit-backdrop-filter:blur(14px); border-radius:26px; padding:7px 6px 7px 16px; box-sizing:border-box; box-shadow:0 8px 26px rgba(0,0,0,0.16); border:1px solid rgba(255,255,255,0.6); overflow:hidden; transition:box-shadow .2s; }
.adjust-bar:focus-within { box-shadow:0 8px 26px rgba(0,0,0,0.2), 0 0 0 2px rgba(139,92,246,.25); }
.adjust-bar input { flex:1; min-width:0; appearance:none; -webkit-appearance:none; border:none !important; outline:none; background:transparent !important; background-color:transparent !important; box-shadow:none; margin:0; padding:0; height:30px; line-height:30px; font-size:13px; color:#333; box-sizing:border-box; }
.adjust-bar input::placeholder { color:#a5a5aa; }
.adjust-btn { height:32px; padding:0 14px; border-radius:17px; background:linear-gradient(135deg,#8b5cf6,#6366f1); color:#fff; font-size:12px; font-weight:600; cursor:pointer; flex-shrink:0; display:flex; align-items:center; justify-content:center; transition:transform .2s, opacity .2s; }
.adjust-btn:active { transform:scale(.94); opacity:.85; }

/* 胶囊滑出/滑入动画（抽屉拉到底部时隐藏） */
.adjust-fade-enter-active, .adjust-fade-leave-active { transition:opacity .3s ease, transform .3s ease; }
.adjust-fade-enter-from, .adjust-fade-leave-to { opacity:0; transform:translateY(100%); }
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

/* 选中日签轻放大（一次性） */
.day-tab.on { animation:tabGrow .35s ease; }
@keyframes tabGrow { from{transform:scale(.94)} to{transform:scale(1)} }

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
</style>
