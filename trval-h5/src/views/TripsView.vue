<script setup>
import { ref, computed, watch, onMounted, onActivated, onDeactivated, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showToast, showConfirmDialog } from 'vant'
import { getToken } from '../utils/auth'
import { planApi, templateApi } from '../api'
import { getHotDestinations, getSurroundTour } from '../api/destination'
import EmptyState from '../components/EmptyState.vue'
import AIChatDialog from '../components/AIChatDialog.vue'
import LazyImage from '../components/LazyImage.vue'

defineOptions({ name: 'TripsView' })

const router = useRouter()
const { t } = useI18n()

const trips = ref([])
const isLoading = ref(false)
const loadError = ref(false)
const showAIChat = ref(false)
const showMoreMenu = ref(false)
const aiContext = ref({ destination: '', budget: '', days: '' })

/* 携程模式：后端 API → 静态 JSON → 渐变色占位 */
const cityImageMap = ref({})

const loadCityImageMap = async () => {
  try {
    const resp = await fetch('/city-images.json')
    if (resp.ok) Object.assign(cityImageMap.value, await resp.json())
  } catch {}
  // 合并景点图映射：推荐卡片引用的景点名（故宫/外滩等）能命中本地图，避免全部落到后端占位图
  try {
    const resp = await fetch('/attraction-images.json')
    if (resp.ok) {
      const attrMap = await resp.json()
      for (const [k, v] of Object.entries(attrMap)) {
        if (v && !cityImageMap.value[k]) cityImageMap.value[k] = v
      }
    }
  } catch {}
}

/** 主渠道：静态 JSON（本地图库，同源必达）；兜底：后端 API */
const getCityImage = (name) => {
  return cityImageMap.value[name] || `/api/city/image?name=${encodeURIComponent(name)}`
}

/** 景点卡片取图：后端 POI 实景图 -> 本地静态图库 -> 城市图（guide.city 是已知城市，必能出图） */
const getAttractionImage = (city, attr) => {
  if (attr.imageUrl) return attr.imageUrl
  const local = cityImageMap.value[attr.name]
  if (local) return local
  return getCityImage(city)
}

/** 景点图加载失败兜底：先换城市图（只换一次），再失败才隐藏让位占位图标 */
const onGuideImgError = (e, city) => {
  const img = e.target
  if (!img) return
  if (img.dataset.fb) { img.style.display = 'none'; return }
  img.dataset.fb = '1'
  img.src = getCityImage(city)
}

const carouselImages = ref([])
const currentCarouselIndex = ref(0)
let carouselTimer = null

const staticImageMap = ref({})

const loadCarouselImages = async () => {
  try {
    // 加载静态图片映射
    const imgResp = await fetch('/city-images.json')
    if (imgResp.ok) Object.assign(staticImageMap.value, await imgResp.json())
  } catch {}

  try {
    const resp = await fetch('/api/map/scenic-photos')
    const json = await resp.json()
    if (json.code === 0 && json.data?.length > 0) carouselImages.value = json.data
    if (carouselImages.value.length === 0) {
      carouselImages.value = [
        staticImageMap.value['桂林'] || '/images/landmarks/1a57149358c0.jpg',
        staticImageMap.value['张家界'] || '/images/landmarks/1260698db1f0.jpg',
        staticImageMap.value['成都'] || '/images/landmarks/14bf5c897776.jpg',
        staticImageMap.value['黄山'] || '/images/landmarks/1986d7d41d8a.jpg',
      ]
    }
    startCarousel()
  } catch (e) {}
}

const startCarousel = () => {
  if (carouselTimer) clearInterval(carouselTimer)
  carouselTimer = setInterval(() => {
    currentCarouselIndex.value = (currentCarouselIndex.value + 1) % carouselImages.value.length
  }, 4000)
}
const stopCarousel = () => { if (carouselTimer) { clearInterval(carouselTimer); carouselTimer = null } }

const showNearbyMap = ref(false) // 周边游全屏弹层
const nearbyMapInstance = ref(null)
const nearbyMapProvider = ref(null)
const userLocation = ref(null)
const locatingUser = ref(false)
const locationError = ref('')

/* ==================== 周边游（从出发城市可到达城市 + 热门路线） ==================== */
const surroundData = ref(null)        // /api/map/surround-tour 返回
const activeTourTab = ref('surround') // 'surround' | 'routes'
const activeDeparture = ref('')       // 起点，默认取 defaultDeparture
const tourLoading = ref(false)
const showCitySheet = ref(false)
const selectedCity = ref(null)
const showDeparturePicker = ref(false)
const transitMode = ref('rail')       // 铁路游 / 自驾游（筛选（视觉态））
const durationFilter = ref(3)         // 时长筛选（小时，0=全天）
const durationOptions = [
  { h: 1, label: '1小时' }, { h: 3, label: '3小时' }, { h: 6, label: '6小时' }, { h: 0, label: '全天' },
]

const locateUser = () => new Promise((resolve) => {
  locatingUser.value = true; locationError.value = ''
  if (!navigator.geolocation) { locationError.value = t('trips.geolocationUnsupported'); locatingUser.value = false; resolve(false); return }
  navigator.geolocation.getCurrentPosition(
    (pos) => { userLocation.value = { lat: pos.coords.latitude, lng: pos.coords.longitude }; locatingUser.value = false; resolve(true) },
    (err) => { locationError.value = err.code === 1 ? t('trips.locationPermissionDenied') : t('trips.locationFailed'); locatingUser.value = false; userLocation.value = { lat: 39.915, lng: 116.404 }; resolve(false) },
    { timeout: 10000, enableHighAccuracy: true, maximumAge: 60000 }
  )
})

/** 加载周边游数据（一次缓存，切换出发城市不再请求） */
const loadSurroundTour = async () => {
  if (surroundData.value) return
  tourLoading.value = true
  try {
    const res = await getSurroundTour()
    if (res.code === 0 && res.data) {
      surroundData.value = res.data
      activeDeparture.value = res.data.defaultDeparture || res.data.departureCities?.[0]?.name || ''
    }
  } catch (e) { showToast(t('trips.tourLoadFailed')) }
  finally { tourLoading.value = false }
}

/** 当前出发城市可到达的城市全集 */
const surroundCities = computed(() => surroundData.value?.surround?.[activeDeparture.value] || [])
/** 按时长筛选后的城市（供地图钉 / 底部计数 / 城市列表） */
const filteredCities = computed(() => {
  const dur = durationFilter.value
  if (!dur) return surroundCities.value
  const maxMin = dur * 60
  return surroundCities.value.filter(c => c.transitMin && c.transitMin <= maxMin)
})
/** 出发城市坐标点 */
const departurePoint = computed(() =>
  surroundData.value?.departureCities?.find(d => d.name === activeDeparture.value) || null)

/** 钉上短时长文案：'高铁14分钟 起' -> '14分钟' */
const transitShort = (c) => (c?.transitLabel || '').replace(/高铁|动车| 起|起/g, '')

/** 城市卡片轮播图：城市封面 + 热门景点图 */
const resolveCityImages = (c) => {
  if (!c) return []
  const arr = [getCityImage(c.name)]
  ;(c.hotSpots || []).forEach(n => { const u = getCityImage(n); if (u) arr.push(u) })
  return arr
}

/** 路线卡片轮播封面 */
const routeCoverImages = (route) => (route?.covers || []).map(n => getCityImage(n)).filter(Boolean)

const openCitySheet = (city) => { selectedCity.value = city; showCitySheet.value = true }
const closeCitySheet = () => { showCitySheet.value = false }
const viewAllDestinations = () => { if (filteredCities.value.length) openCitySheet(filteredCities.value[0]) }
const selectDeparture = (name) => { if (!name) return; activeDeparture.value = name; showDeparturePicker.value = false; refreshCityPins() }
const setTransitMode = (m) => { transitMode.value = m }
/** 时长筛选切换，完成后重绘钉 */
const setDurationFilter = (h) => { durationFilter.value = h; refreshCityPins() }
/** 点击遍历时长档位（1/3/6/全天 循环） */
const cycleDuration = () => {
  const cur = durationFilter.value
  const idx = durationOptions.findIndex(o => o.h === cur)
  const next = durationOptions[(idx + 1) % durationOptions.length]
  setDurationFilter(next.h)
}
/** 图片加载失败兜底：隐藏让位占位图标 */
const onCoverErr = (e) => { const img = e.target; if (img) img.style.display = 'none' }
/** 点击城市卡 → 进入城市详情页 */
const goCityDetail = (c) => { if (!c?.name) return; showCitySheet.value = false; goDestinationDetail(c.name) }

const loadAmapForNearby = () => new Promise((resolve) => {
  if (window.AMap) { resolve(true); return }
  const script = document.createElement('script')
  script.src = '/api/map/script'
  const timeout = setTimeout(() => resolve(false), 8000)
  script.onload = () => { clearTimeout(timeout); let r = 0; const c = setInterval(() => { if (window.AMap) { clearInterval(c); resolve(true) } else if (r++ > 40) { clearInterval(c); resolve(false) } }, 150) }
  script.onerror = () => { clearTimeout(timeout); resolve(false) }
  document.head.appendChild(script)
})

const openNearbyMap = async () => {
  showNearbyMap.value = true
  activeTourTab.value = 'surround' // 【tour-immersive】确保地图容器可见，AMap 才能命中尺寸
  await nextTick()
  await loadSurroundTour()
  await nextTick()
  await initSurroundMap()
  // 弹层滑入动画约 0.3s，动画结束容器尺寸才稳定；补一次 resize+重定位，避免底部抽屉变矮后地图渲染不全
  setTimeout(() => {
    try {
      if (!nearbyMapInstance.value) return
      if (nearbyMapProvider.value === 'amap' && typeof nearbyMapInstance.value.resize === 'function') nearbyMapInstance.value.resize()
      else if (nearbyMapProvider.value === 'leaflet') nearbyMapInstance.value.invalidateSize()
      if (nearbyMapProvider.value === 'amap') fitAmapView(); else fitLeafletView()
    } catch (e) {}
  }, 350)
}
const closeNearbyMap = () => {
  showNearbyMap.value = false; showCitySheet.value = false; showDeparturePicker.value = false
  // MAPLEAK-2 修复：Leaflet 实例只有 remove() 无 destroy()，双判断销毁防泄漏
  if (nearbyMapInstance.value) {
    try { if (typeof nearbyMapInstance.value.destroy === 'function') nearbyMapInstance.value.destroy() } catch (e) {}
    try { if (typeof nearbyMapInstance.value.remove === 'function') nearbyMapInstance.value.remove() } catch (e) {}
    nearbyMapInstance.value = null
  }
}
const goMap = () => { openNearbyMap() }

/** 城市钉（AMap 自定义 content） */
const addAmapCityMarkers = (map) => {
  const markers = []
  filteredCities.value.forEach(c => {
    if (!c.lat || !c.lng) return
    const el = document.createElement('div')
    el.className = 'city-pin'
    el.innerHTML = `<span class="city-pin-city">${c.name}</span><span class="city-pin-time">${transitShort(c)}</span>`
    const mk = new window.AMap.Marker({ position: [Number(c.lng), Number(c.lat)], content: el, offset: new window.AMap.Pixel(-30, -18), zIndex: 30 })
    mk.on('click', () => openCitySheet(c))
    map.add(mk)
    markers.push(mk)
  })
  return markers
}

/** 城市钉（Leaflet 兜底） */
const addLeafletCityMarkers = (map) => {
  const markers = []
  filteredCities.value.forEach(c => {
    if (!c.lat || !c.lng) return
    const mk = window.L.circleMarker([Number(c.lat), Number(c.lng)], { radius: 8, fillColor: '#8B5CF6', color: '#fff', weight: 2, fillOpacity: 0.95 })
      .addTo(map).bindTooltip(`${c.name} · ${transitShort(c)}`, { permanent: true, direction: 'top', className: 'tour-leaflet-label' })
    mk.on('click', () => openCitySheet(c))
    markers.push(mk)
  })
  return markers
}

let departureMarker = null
let cityMarkers = []

const clearCityMarkers = () => {
  if (nearbyMapProvider.value === 'amap' && nearbyMapInstance.value && window.AMap) {
    cityMarkers.forEach(m => { try { m.setMap(null) } catch (e) {} })
    if (departureMarker) { try { departureMarker.setMap(null) } catch (e) {}; departureMarker = null }
  } else if (nearbyMapProvider.value === 'leaflet' && nearbyMapInstance.value && window.L) {
    cityMarkers.forEach(m => { try { nearbyMapInstance.value.removeLayer(m) } catch (e) {} })
    if (departureMarker) { try { nearbyMapInstance.value.removeLayer(departureMarker) } catch (e) {}; departureMarker = null }
  }
  cityMarkers = []
}

/** 重绘出发城市 + 城市钉（出发城市/时长筛选切换后调用） */
const refreshCityPins = async () => {
  if (!nearbyMapInstance.value) return
  clearCityMarkers()
  const dept = departurePoint.value
  if (nearbyMapProvider.value === 'amap') {
    if (dept) departureMarker = new window.AMap.Marker({ position: [dept.lng, dept.lat], icon: new window.AMap.Icon({ size: new window.AMap.Size(26, 26), image: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="26" height="26"><circle cx="13" cy="13" r="10" fill="#6366F1" stroke="#fff" stroke-width="3"/><circle cx="13" cy="13" r="3.5" fill="#fff"/></svg>'), imageSize: new window.AMap.Size(26, 26) }), title: activeDeparture.value, zIndex: 100 }).addTo(nearbyMapInstance.value)
    cityMarkers = addAmapCityMarkers(nearbyMapInstance.value)
    fitAmapView()
  } else {
    if (dept) departureMarker = window.L.circleMarker([dept.lat, dept.lng], { radius: 10, fillColor: '#6366F1', color: '#fff', weight: 3, fillOpacity: 1 }).addTo(nearbyMapInstance.value).bindTooltip(activeDeparture.value, { permanent: true, direction: 'right' })
    cityMarkers = addLeafletCityMarkers(nearbyMapInstance.value)
    fitLeafletView()
  }
}

const fitAmapView = () => {
  try {
    // 不用 setFitView（会把横跨半个中国的全部城市都框进来→全国视野）；
    // 固定以出发城市为中心、zoom 6 的区域视野，才符合"周边游"截图（出发城市居中 + 附近城市环绕）
    const dept = departurePoint.value
    if (dept) nearbyMapInstance.value.setZoomAndCenter(6, [dept.lng, dept.lat])
  } catch (e) {}
}
const fitLeafletView = () => {
  try {
    const dept = departurePoint.value
    if (dept) nearbyMapInstance.value.setView([dept.lat, dept.lng], 6)
  } catch (e) {}
}

/** 初始化周边游地图（AMap 或 Leaflet 兜底），中心默认出发城市 */
const initSurroundMap = async () => {
  const container = document.getElementById('tour-map-container'); if (!container) return
  const dept = departurePoint.value || { lat: 39.904, lng: 116.407 }
  const loaded = await loadAmapForNearby()
  if (loaded && window.AMap) {
    nearbyMapProvider.value = 'amap'
    const map = new window.AMap.Map('tour-map-container', { center: [dept.lng, dept.lat], zoom: 6, viewMode: '2D', resizeEnable: true })
    nearbyMapInstance.value = map
    refreshCityPins()
  } else {
    nearbyMapProvider.value = 'leaflet'
    const L = window.L; if (!L) { const css = document.createElement('link'); css.rel = 'stylesheet'; css.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'; document.head.appendChild(css); const s = document.createElement('script'); s.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'; await new Promise(r => { s.onload = r; s.onerror = r; document.head.appendChild(s) }) }
    const map = L.map('tour-map-container', { center: [dept.lat, dept.lng], zoom: 6, zoomControl: true, attributionControl: false })
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map)
    nearbyMapInstance.value = map
    refreshCityPins()
  }
}

/** 重新居中到出发城市 */
const resetSurroundView = () => {
  if (!nearbyMapInstance.value) return
  if (nearbyMapProvider.value === 'amap') fitAmapView()
  else if (nearbyMapProvider.value === 'leaflet') fitLeafletView()
}

// 【tour-immersive】切回「周边游」Tab 时地图容器由 display:none 变可见，需让地图实例重新计算尺寸
watch(activeTourTab, (v) => {
  if (v !== 'surround') return
  nextTick(() => {
    const inst = nearbyMapInstance.value; if (!inst) return
    if (nearbyMapProvider.value === 'amap') inst.resize()
    else setTimeout(() => inst.invalidateSize && inst.invalidateSize(), 150)
  })
})

/* 周边游空态入口卡片改版后不再需要迷你地图（附近景点半径POI功能已移除） */

const tabs = [
  { key: 'all', title: '全部' }, { key: 'upcoming', title: '待出行' }, { key: 'doing', title: '进行中' }, { key: 'done', title: '已完成' }, { key: 'draft', title: '草稿' },
]
const activeTab = ref('all')

const inferStatus = (plan) => {
  if (!plan.planData?.dayPlans?.length) return 'draft'
  if (plan.travelDate) { const d = new Date(plan.travelDate); d.setHours(0,0,0,0); const today = new Date(); today.setHours(0,0,0,0); if (d < today) return 'done' }
  return 'upcoming'
}

const tripPlans = computed(() => trips.value.filter(t => (t.source || 'trip') !== 'home'))
const homePlans = computed(() => trips.value.filter(t => t.source === 'home'))
const hasTrips = computed(() => trips.value.length > 0)

const cityGuides = ref([])

const loadCityGuides = async () => {
  if (!userLocation.value) await locateUser()
  if (!userLocation.value) { cityGuides.value = []; return }
  let currentCity = ''
  try { const resp = await fetch(`/api/city/location?lat=${userLocation.value.lat}&lng=${userLocation.value.lng}`); const json = await resp.json(); if (json.code === 0 && json.data) currentCity = json.data.city || json.data.province || '' } catch (e) {}
  let cities = []
  if (currentCity) {
    cities = [currentCity]
    if (hotDestinations.value.length === 0) await loadHotDestinations()
    cities.push(...hotDestinations.value.filter(d => d.name !== currentCity).sort(() => Math.random() - 0.5).slice(0, 3).map(d => d.name))
  } else {
    if (hotDestinations.value.length === 0) await loadHotDestinations()
    cities = [...hotDestinations.value].sort(() => Math.random() - 0.5).slice(0, 4).map(d => d.name)
  }
  try {
    const results = await Promise.allSettled(cities.map(c => import('../api/destination').then(m => m.getCityAttractions(c))))
    cityGuides.value = results.map((r, i) => ({ city: cities[i], label: cities[i] === currentCity ? t('trips.currentCityLabel') : t('trips.hotRecommendLabel'), attractions: (r.status === 'fulfilled' && r.value?.code === 0) ? (r.value.data || []).slice(0, 4).map(a => ({ name: a.name, address: a.address || '', rating: a.rating || null, imageUrl: a.imageUrl || '' })) : [] })).filter(g => g.attractions.length > 0)
  } catch (e) { cityGuides.value = [] }
}

const hotDestinations = ref([])
const loadHotDestinations = async () => { try { const res = await getHotDestinations(); hotDestinations.value = (res.data || []).slice(0, 8) } catch (e) {} }

const formatTime = (timeStr) => { if (!timeStr) return ''; const d = new Date(timeStr); return `${String(d.getMonth()+1).padStart(2,'0')}/${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}` }
const getAttractions = (plan) => { if (!plan.planData?.dayPlans) return []; const arr = []; plan.planData.dayPlans.forEach(day => { day.timeSlots?.forEach(slot => { if (slot.attraction) arr.push(slot.attraction) }) }); return arr }
const cardTitle = (plan) => { const dest = plan.destination || t('trips.unknown'); const days = plan.days || 1; return t('trips.dayTrip', { dest, days }) }
const cardRoute = (plan) => { if (!plan.planData?.dayPlans) return ''; const lines = plan.planData.dayPlans.slice(0, 3).map(day => { const spots = []; day.timeSlots?.forEach(slot => { if (slot.attraction) spots.push(slot.attraction) }); return spots.length > 0 ? `Day${day.day||'?'} ${spots.join(' → ')}` : '' }).filter(Boolean); return lines.join(' | ') + (plan.planData.dayPlans.length > 3 ? ' …' : '') }
const cardMeta = (plan) => { const days = plan.days || 0; const locationCount = getAttractions(plan).length; const parts = []; if (plan.travelDate) { const start = new Date(plan.travelDate); const end = new Date(start); end.setDate(end.getDate()+days-1); const fmt = d => t('trips.dateFormat', { month: d.getMonth()+1, day: d.getDate() }); parts.push(`${fmt(start)}-${fmt(end)}`) } else if (plan.createdAt) { parts.push(t('trips.dateFormat', { month: new Date(plan.createdAt).getMonth()+1, day: new Date(plan.createdAt).getDate() })) }; if (days>0) parts.push(t('trips.totalDays', { days })); if (locationCount>0) parts.push(t('trips.spotCount', { count: locationCount })); return parts.join('·') }
const statusLabel = (s) => ({ upcoming: t('trips.statusUpcoming'), doing: t('trips.statusDoing'), done: t('trips.statusDone'), draft: t('trips.statusDraft') }[s] || s)
const statusColor = (s) => ({ upcoming:'#8B5CF6', doing:'#3B82F6', done:'#34D399', draft:'#F59E0B' }[s] || 'var(--text-hint)')

const loadTrips = async () => { isLoading.value = true; loadError.value = false; try { const res = await planApi.getSavedPlans(); if (res.code === 0) trips.value = (res.data || []).map(p => ({ ...p, _status: inferStatus(p) })); else trips.value = [] } catch (e) { trips.value = []; if (e?.response?.status === 502) loadError.value = true } finally { isLoading.value = false } }
const viewTrip = (plan) => { if (!plan?.destination) { showToast(t('trips.planDataError')); return }; router.push({ path: '/agent-map', query: { savedPlanId: plan.id } }) }
const openAIChat = (ctx = {}) => { aiContext.value = ctx; showAIChat.value = true }
const goToAgentPlanner = () => { router.push('/agent-planner') }
const goMyRoutes = () => { router.push('/my-routes') }
const onPlanSaved = () => { showAIChat.value = false; showToast(t('trips.planSaved')); router.push('/my-routes') }
const confirmDelete = async (plan) => { try { await showConfirmDialog({ title: t('trips.deleteTrip'), message: t('trips.confirmDeleteMsg', { name: plan?.destination || t('trips.unknown') }) }); if (!plan?.id) return; const res = await planApi.deletePlan(plan.id); if (res.code===0) { showToast(t('trips.deleted')); trips.value = trips.value.filter(t => t.id !== plan.id) } } catch (e) {} }
/** 城市名规范化：去掉后缀"市"，与热门目的地/本地图库短名一致（三亚市→三亚），保证天气/图片/景点都能命中 */
const toShortCity = (name) => (name || '').replace(/市$/, '')

/** 进 destination-detail：city 必传（天气/景点/地图都按城市），img 可选（景点推荐图覆盖城市封面），name 可选（标题显示景点名而非城市名） */
const goDestinationDetail = (city, img = '', name = '') => {
  if (!city) return
  const params = [`city=${encodeURIComponent(toShortCity(city))}`]
  if (img) params.push(`img=${encodeURIComponent(img)}`)
  if (name) params.push(`name=${encodeURIComponent(name)}`)
  router.push(`/destination-detail?${params.join('&')}`)
}

/** 景点推荐卡：复用卡片当前展示的景点图（POI实景→图库→城市兜底）进详情，标题显示景点名 */
const goGuideAttraction = (attr, city) => {
  if (!attr?.name || !city) return
  goDestinationDetail(city, getAttractionImage(city, attr), attr.name)
}
const handleMoreAction = (action) => { showMoreMenu.value = false; showToast(t('trips.featureWip')) }

/* ==================== 行程模板（新功能） ==================== */
const templates = ref([])
const templatesLoading = ref(false)
const templatesError = ref(false)
const instantiatingId = ref(null)

const loadTemplates = async () => {
  templatesLoading.value = true
  templatesError.value = false
  try {
    const res = await templateApi.getMarket({ page: 0, size: 3 })
    if (res.code === 0) templates.value = res.data?.list || []
    else templates.value = []
  } catch (e) { templates.value = []; templatesError.value = true }
  finally { templatesLoading.value = false }
}

const templateTags = (tmpl) => (tmpl.tags || '').split(',').map(s => s.trim()).filter(Boolean).slice(0, 3)

const templateMeta = (tmpl) => {
  const parts = []
  if (tmpl.days) parts.push(t('trips.totalDays', { days: tmpl.days }))
  if (tmpl.budget) parts.push(t('trips.templateBudget', { budget: tmpl.budget }))
  if (tmpl.people) parts.push(t('trips.templatePeople', { people: tmpl.people }))
  return parts.join(' · ')
}

const useTemplate = async (tmpl) => {
  if (instantiatingId.value) return
  if (!getToken()) { showToast(t('common.notLoggedIn')); return }
  try {
    await showConfirmDialog({
      title: t('trips.templateConfirmTitle'),
      message: t('trips.templateConfirmMsg', { name: tmpl.name }),
      confirmButtonText: t('trips.useTemplate'),
      cancelButtonText: t('common.cancel'),
    })
  } catch (e) { return } // 取消
  instantiatingId.value = tmpl.id
  try {
    const res = await templateApi.instantiate(tmpl.id)
    if (res.code === 0 && res.data?.planId) {
      showToast(t('trips.templateUsed'))
      // 与本地行程一致：跳转行程地图页查看新行程
      router.push({ path: '/agent-map', query: { savedPlanId: res.data.planId } })
    } else {
      showToast(res.message || t('trips.templateUseFailed'))
    }
  } catch (e) { showToast(t('trips.templateUseFailed')) }
  finally { instantiatingId.value = null }
}

// L-TRIPS-1 修复：onMounted 只做首次加载；onActivated 只做 keep-alive 恢复（首次激活紧随 onMounted，用 dataLoaded 守卫避免双发 loadTrips 等请求）
let dataLoaded = false

onMounted(() => {
  loadCityImageMap(); loadTemplates()
  if (getToken()) {
    loadCityGuides(); loadHotDestinations(); loadCarouselImages()
    dataLoaded = true
  }
})

onActivated(() => {
  loadCityImageMap(); loadTemplates()
  if (!getToken()) return
  if (!dataLoaded) {
    // 首次未加载（含游客先开页→别处登录→返回）→ 补全首次加载，保证轮播图可用
    loadCityGuides(); loadHotDestinations(); loadCarouselImages()
    dataLoaded = true
  } else if (carouselImages.value.length === 0) {
    // 数据已加载但轮播图缺失（首屏请求失败）→ 补加载，避免 startCarousel 对空数组取模
    loadCarouselImages()
  } else {
    startCarousel()
  }
})

onDeactivated(() => {
  isLoading.value = false; loadError.value = false; showMoreMenu.value = false; stopCarousel()
  dataLoaded = false // L-TRIPS-1 修复：离开后复位，返回时重新加载
})

onUnmounted(() => {
  stopCarousel()
  // MAPLEAK-2 修复：Leaflet 实例无 destroy()，双判断销毁防泄漏
  if (nearbyMapInstance.value) {
    try { if (typeof nearbyMapInstance.value.destroy === 'function') nearbyMapInstance.value.destroy() } catch (e) {}
    try { if (typeof nearbyMapInstance.value.remove === 'function') nearbyMapInstance.value.remove() } catch (e) {}
    nearbyMapInstance.value = null
  }
})
</script>

<template>
  <div class="trips-page">
    <!-- 漂浮粒子 — 已禁用 -->
    <van-nav-bar safe-area-inset-top class="nav-bar">
      <template #title><span class="nav-title">{{ t('trips.navTitle') }}</span></template>
      <template #right>
        <div class="nav-actions">
          <div class="nav-btn" @click="goToAgentPlanner"><van-icon name="add" size="20" color="#7C3AED" /></div>
          <div class="nav-btn" @click="showMoreMenu = true"><van-icon name="ellipsis" size="20" color="#7C3AED" /></div>
        </div>
      </template>
    </van-nav-bar>
    <van-popup v-model:show="showMoreMenu" position="top" :style="{ width:'160px', top:'calc(env(safe-area-inset-top,0px)+48px)', right:'8px', borderRadius:'14px' }" overlay-class="no-overlay">
      <div class="more-menu">
        <div v-for="item in [{key:'import',icon:'down',label:t('trips.importTrips')},{key:'batchDelete',icon:'delete-o',label:t('trips.batchDelete')},{key:'export',icon:'share-o',label:t('trips.exportTrips')},{key:'settings',icon:'setting-o',label:t('trips.tripSettings')}]" :key="item.key" class="more-item" @click="handleMoreAction(item.key)"><van-icon :name="item.icon" size="16" color="var(--text-secondary)" /><span>{{ item.label }}</span></div>
      </div>
    </van-popup>

    <div class="trips-scroll">
      <div class="trips-inner">
        <div class="plan-sec-head">
          <span class="plan-sec-title">{{ t('trips.routePlanning') }}</span>
          <span class="plan-sec-link" @click="goMyRoutes">{{ t('trips.myRoutes') }} <van-icon name="arrow" size="12" /></span>
        </div>

        <div class="hero-plan-card entrance-item entrance-d1" @click="goToAgentPlanner">
          <div v-if="carouselImages.length === 0" class="hero-placeholder" />
          <div v-for="(img, idx) in carouselImages" :key="idx" class="hero-carousel-img" :class="{ active: idx === currentCarouselIndex }" :style="{ backgroundImage: `url(${img})` }" />
          <div class="hero-mask" />
          <span class="hero-tag">{{ t('trips.heroBadge') }}</span>
          <span class="hero-top-link" @click.stop="goMyRoutes">{{ t('trips.myRoutes') }} <van-icon name="arrow" size="12" /></span>
          <button class="hero-glass-btn" @click.stop="goToAgentPlanner">
            <span class="hero-btn-title">🤖 {{ t('agent.aiAgentPlanning') }}</span>
            <van-icon name="arrow" size="14" color="#fff" />
          </button>
        </div>

        <div class="nearby-card card-macaron" @click="goMap">
          <div v-if="carouselImages.length" class="nearby-card-bg">
            <div v-for="(img, idx) in carouselImages.slice(0, 4)" :key="idx" class="nearby-card-img" :class="{ active: (currentCarouselIndex % 4) === idx }" :style="{ backgroundImage: `url(${img})` }" />
          </div>
          <div class="nearby-card-mask" />
          <div class="nearby-card-body">
            <div class="nearby-card-tag"><van-icon name="location-o" size="16" color="#fff" />{{ t('trips.tourSurround') }}</div>
            <div class="nearby-card-desc">{{ t('trips.tourSurroundDesc') }}</div>
            <span class="nearby-card-go">{{ t('trips.exploreNearby') }}</span>
          </div>
        </div>

        <van-popup v-model:show="showNearbyMap" position="bottom" class="tour-fullscreen" @closed="closeNearbyMap">
          <div class="tour-page">
            <!-- 地图铺满整个弹层（周边游 Tab 显示，v-show 保持容器在 DOM 以复用 AMap 实例） -->
            <div v-show="activeTourTab === 'surround'" class="tour-map-wrap">
              <div v-if="tourLoading" class="tour-map-loading"><van-loading size="24" color="#8B5CF6" /><span>{{ t('trips.loadingMap') }}</span></div>
              <div id="tour-map-container" class="tour-map-box"></div>
            </div>

            <!-- 顶部浮动导航（不占文档流，悬浮于地图上） -->
            <div class="tour-float-nav">
              <van-icon name="arrow-left" size="18" @click="closeNearbyMap" />
              <span class="tour-float-title">{{ activeTourTab === 'routes' ? t('trips.popularRoutes') : t('trips.tourSurround') }}</span>
              <van-icon name="cross" size="18" color="var(--text-secondary)" @click="closeNearbyMap" />
            </div>

            <!-- 顶部浮动：分段（周边游/热门路线）+ 筛选（铁路游/出发地/时长） -->
            <div class="tour-pill-card">
              <div class="tour-seg">
                <span class="tour-seg-item" :class="{ active: activeTourTab === 'surround' }" @click="activeTourTab = 'surround'">{{ t('trips.tourSurround') }}</span>
                <span class="tour-seg-item" :class="{ active: activeTourTab === 'routes' }" @click="activeTourTab = 'routes'">{{ t('trips.popularRoutes') }}</span>
              </div>
              <div v-if="activeTourTab === 'surround'" class="tour-filter-row">
                <span class="filter-chip" @click="setTransitMode(transitMode === 'rail' ? 'driving' : 'rail')">
                  <van-icon name="success" v-if="transitMode === 'rail'" size="12" color="#8B5CF6" />{{ t('trips.filterRail') }}<van-icon name="arrow-down" size="10" />
                </span>
                <span class="filter-chip" @click="showDeparturePicker = true">{{ activeDeparture }}<van-icon name="arrow-down" size="10" /></span>
                <span class="filter-chip" @click="cycleDuration">
                  {{ durationFilter ? t('trips.hours', { n: durationFilter }) : t('trips.allDay') }}<van-icon name="arrow-down" size="10" />
                </span>
              </div>
            </div>

            <!-- 周边游 Tab：右侧浮动控制 + 底部查看目的地 -->
            <template v-if="activeTourTab === 'surround'">
              <div class="tour-map-controls">
                <button class="tour-ctrl-btn" @click="resetSurroundView" :title="t('trips.locToDep')"><van-icon name="aim" size="18" color="#8B5CF6" /></button>
                <button class="tour-ctrl-btn" @click="closeNearbyMap"><van-icon name="cross" size="18" color="var(--text-secondary)" /></button>
              </div>
              <div class="tour-float-bottom">
                <div class="tour-bottom-bar" @click="viewAllDestinations">
                  <span>{{ t('trips.viewDestinations', { n: filteredCities.length }) }}</span>
                  <van-icon name="arrow-up" size="14" color="#fff" />
                </div>
              </div>
            </template>

            <!-- 热门路线 Tab：浮层列表（铺满弹层，留出顶部浮动控件空间） -->
            <div v-else class="tour-routes-list">
              <div v-if="tourLoading" class="tour-map-loading"><van-loading size="24" color="#8B5CF6" /><span>{{ t('trips.loadingMap') }}</span></div>
              <template v-else>
                <div v-for="route in surroundData?.routes || []" :key="route.id" class="route-card">
                  <van-swipe class="route-cover" :autoplay="0" :show-indicators="routeCoverImages(route).length > 1" indicator-color="#fff">
                    <van-swipe-item v-for="(img, i) in routeCoverImages(route)" :key="i">
                      <img :src="img" class="route-cover-img" loading="lazy" @error="onCoverErr" />
                    </van-swipe-item>
                    <van-swipe-item v-if="!routeCoverImages(route).length"><div class="route-cover-fallback"><van-icon name="photo-o" size="26" color="rgba(255,255,255,0.85)" /></div></van-swipe-item>
                  </van-swipe>
                  <div class="route-body">
                    <div class="route-name">{{ route.name }}</div>
                    <div class="route-meta">{{ route.km }} · {{ t('trips.totalDays', { days: route.days }) }} · {{ t('trips.routeCities', { n: route.cityCount }) }}</div>
                    <div class="route-tags"><span v-for="c in route.cities" :key="c" class="route-tag">{{ c }}</span></div>
                    <div class="route-tagline" v-if="route.tagline">{{ route.tagline }}</div>
                  </div>
                </div>
                <div v-if="!surroundData?.routes?.length" class="tour-empty"><van-icon name="location-o" size="30" color="#CBD5E1" /><span>{{ t('trips.emptyTour') }}</span></div>
              </template>
            </div>
          </div>
        </van-popup>

        <!-- 城市底部弹层（出发地 → 可到达城市列表） -->
        <van-popup v-model:show="showCitySheet" position="bottom" :style="{ height:'72%' }" round closeable close-icon="cross" @closed="closeCitySheet">
          <div class="city-sheet">
            <div class="city-sheet-head">
              <span class="city-sheet-title">{{ t('trips.citySheetTitle', { dep: activeDeparture }) }}</span>
              <span class="city-sheet-count">{{ t('trips.viewDestinations', { n: filteredCities.length }) }}</span>
            </div>
            <div class="city-sheet-list">
              <div v-for="c in filteredCities" :key="c.name" class="city-card" @click="goCityDetail(c)">
                <van-swipe class="city-card-swipe" :autoplay="0" :show-indicators="resolveCityImages(c).length > 1" indicator-color="#fff">
                  <van-swipe-item v-for="(img, i) in resolveCityImages(c)" :key="i">
                    <img :src="img" class="city-card-img" loading="lazy" @error="onCoverErr" />
                  </van-swipe-item>
                  <van-swipe-item v-if="!resolveCityImages(c).length"><div class="city-card-img-fallback"><van-icon name="photo-o" size="38" color="#fff" /></div></van-swipe-item>
                </van-swipe>
                <div class="city-card-shade" />
                <div class="city-card-body">
                  <div class="city-card-top">
                    <span class="city-card-name">{{ c.name }}</span>
                    <span class="heat-badge"><van-icon name="fire-o" size="12" color="#FF5A5F" />{{ t('trips.heat', { n: c.heat }) }}</span>
                  </div>
                  <div class="city-card-tags"><span v-for="tag in c.tags" :key="tag" class="city-card-tag">{{ tag }}</span></div>
                  <div class="city-card-meta">
                    <span class="transit-tag">{{ c.transitLabel }}</span>
                    <span class="price-tag">{{ c.price }}</span>
                  </div>
                  <div class="city-card-desc" v-if="c.description">{{ t('trips.hotSpot') }} · {{ c.description }}</div>
                </div>
              </div>
            </div>
          </div>
        </van-popup>

        <!-- 出发城市选择弹层 -->
        <van-popup v-model:show="showDeparturePicker" position="bottom" round :style="{ height:'auto' }" closeable close-icon="cross">
          <div class="depart-picker">
            <div class="depart-picker-title">{{ t('trips.departCity') }}</div>
            <div v-for="d in surroundData?.departureCities || []" :key="d.name" class="depart-picker-item" :class="{ active: d.name === activeDeparture }" @click="selectDeparture(d.name)">
              <span>{{ d.name }}</span><van-icon name="success" v-if="d.name === activeDeparture" color="#8B5CF6" />
            </div>
          </div>
        </van-popup>

        <div v-for="guide in cityGuides" :key="guide.city" class="guide-section" v-show="guide.attractions.length > 0">
          <div class="sec-head"><span class="sec-title">{{ guide.city }}</span><span class="sec-guide-label" v-if="guide.label">{{ guide.label }}</span><span class="sec-more" @click="goDestinationDetail(guide.city)">{{ t('trips.guideLink') }}</span></div>
          <div class="h-scroll">
            <div v-for="(attr, i) in guide.attractions" :key="i" class="guide-card" @click="goGuideAttraction(attr, guide.city)">
              <div class="guide-card-img-wrap">
                <img :src="getAttractionImage(guide.city, attr)" class="guide-card-img" loading="lazy" @error="e => onGuideImgError(e, guide.city)" />
                <div class="guide-card-img-placeholder"><van-icon name="photo-o" size="24" color="rgba(139,92,246,0.3)" /></div>
                <span class="guide-card-tag">{{ guide.label }}</span>
              </div>
              <div class="guide-card-body">
                <div class="guide-card-name">{{ attr.name }}</div>
                <div class="guide-card-meta" v-if="attr.rating"><span class="meta-star">⭐ {{ Number(attr.rating).toFixed(1) }}</span><span class="meta-go">{{ t('trips.routeDetail') }}</span></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 行程模板市场（新功能） -->
        <div class="templates-section" v-if="templates.length > 0 || (!hasTrips && hotDestinations.length > 0)">
          <div class="sec-head"><span class="sec-title">{{ t('trips.tripTemplates') }}</span><span class="sec-guide-label">{{ t('trips.templateSlogan') }}</span></div>
          <div v-if="templatesLoading" class="template-skeleton">
            <div v-for="i in 2" :key="i" class="template-card-skeleton"><div class="sk-thumb" /><div class="sk-lines"><div class="sk-line" /><div class="sk-line sk-line--short" /></div></div>
          </div>
          <div v-else-if="templates.length > 0" class="template-list">
            <div v-for="tmpl in templates" :key="tmpl.id" class="template-card">
              <div class="template-cover">
                <LazyImage v-if="tmpl.coverImage" :src="tmpl.coverImage" :alt="tmpl.name" class="template-cover-img" />
                <div v-else class="template-cover-fallback"><van-icon name="photo-o" size="22" color="rgba(139,92,246,0.4)" /></div>
              </div>
              <div class="template-info">
                <div class="template-name">{{ tmpl.name }}</div>
                <div class="template-dest"><van-icon name="location-o" size="11" /> {{ tmpl.destination }}</div>
                <div class="template-meta">{{ templateMeta(tmpl) }}</div>
                <div class="template-tags" v-if="templateTags(tmpl).length">
                  <span v-for="tag in templateTags(tmpl)" :key="tag" class="template-tag">#{{ tag }}</span>
                </div>
                <div class="template-bottom">
                  <span class="template-downloads">{{ t('trips.templateDownloads', { n: tmpl.downloads || 0 }) }}</span>
                  <button class="template-use-btn btn-tap-scale" :disabled="instantiatingId === tmpl.id" @click="useTemplate(tmpl)">
                    <van-loading v-if="instantiatingId === tmpl.id" size="12" color="#fff" />
                    <span v-else>{{ t('trips.useTemplate') }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div v-else-if="templatesError" class="template-fail"><span>{{ t('trips.templateLoadFailed') }}</span><span class="link" @click="loadTemplates">{{ t('common.tryAgain') }}</span></div>

          <template v-if="!hasTrips && hotDestinations.length > 0">
            <div class="sec-head hot-head"><span class="sec-title">{{ t('trips.hotDestinations') }}</span></div>
            <div class="quick-tags"><span v-for="dest in hotDestinations" :key="dest.name" class="quick-tag" @click="openAIChat({ destination: dest.name, budget: '', days: '' })">{{ dest.name }}</span></div>
          </template>
        </div>

        <div style="height:80px" />
      </div>
    </div>

    <Transition name="fab-pop">
      <button v-if="!showAIChat" class="fab-ai-btn btn-tap-scale" @click="openAIChat({})">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#7C3AED" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" fill="#7C3AED" fill-opacity="0.15"/>
        <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
        <circle cx="18" cy="5" r="1.5" fill="#A78BFA" stroke="none"/>
        <circle cx="6" cy="19" r="1.5" fill="#A78BFA" stroke="none"/>
      </svg>
    </button>
    </Transition>
    <AIChatDialog v-model:visible="showAIChat" :context-query="aiContext" @plan-saved="onPlanSaved" />
  </div>
</template>

<style scoped>
.trips-page { width:100%; min-height:100vh; background:transparent; position:relative; display:flex; flex-direction:column; padding-bottom:calc(10px + 48px + 12px + var(--safe-area-bottom, 0px)); }
.trips-scroll { flex:1; overflow-y:auto; overflow-x:hidden; -webkit-overflow-scrolling:touch; }
.trips-inner { max-width:480px; margin:0 auto; padding:0 20px; }
:deep(.nav-bar) { background:linear-gradient(160deg, rgba(255,255,255,0.55) 0%, rgba(255,255,255,0.2) 40%, rgba(255,255,255,0.55) 100%),rgba(255,255,255,0.6) !important; backdrop-filter:blur(22px) saturate(180%); -webkit-backdrop-filter:blur(22px) saturate(180%); border-bottom:0.5px solid rgba(255,255,255,0.55) !important; box-shadow:inset 0 1px 0 rgba(255,255,255,0.65) !important; }
:deep(.nav-bar .van-nav-bar__title) { color:var(--text-primary); font-weight:600; }
.nav-title { font-size:17px; }
.nav-actions { display:flex; gap:4px; }
.nav-btn { width:40px; height:40px; min-width:40px; min-height:40px; border-radius:50%; background:rgba(139,92,246,0.08); display:flex; align-items:center; justify-content:center; cursor:pointer; transition:all 0.2s; }
.nav-btn:active { background:rgba(139,92,246,0.18); transform:scale(0.9); }
.more-menu { padding:8px; }
.more-item { display:flex; align-items:center; gap:10px; padding:12px 14px; border-radius:10px; cursor:pointer; font-size:14px; color:#475569; transition:background 0.15s; }
.more-item:active { background:#faf5ff; }
:deep(.no-overlay) { background:transparent !important; }

.guide-zone { position:relative; padding-left:28px; }
.guide-line { position:absolute; left:7px; top:0; bottom:0; width:2px; z-index:0; background:linear-gradient(180deg, rgba(139,92,246,0.5) 0%, rgba(139,92,246,0.3) 25%, rgba(99,102,241,0.25) 50%, rgba(139,92,246,0.2) 75%, rgba(139,92,246,0.08) 100%); border-radius:1px; box-shadow:0 0 6px rgba(139,92,246,0.15); }
.guide-line::after { content:''; position:absolute; left:4px; top:0; bottom:0; width:1px; z-index:-1; background:linear-gradient(180deg, rgba(139,92,246,0.25) 0%, rgba(99,102,241,0.15) 30%, transparent 70%); border-radius:1px; }
.guide-line::before { content:''; position:absolute; top:-4px; left:-5px; width:12px; height:12px; border-radius:50%; background:#8B5CF6; box-shadow:0 0 10px rgba(139,92,246,0.5), 0 0 20px rgba(139,92,246,0.2); animation:nodePulse 2.5s ease-in-out infinite; }
@keyframes nodePulse { 0%,100% { box-shadow:0 0 8px rgba(139,92,246,0.4), 0 0 16px rgba(139,92,246,0.15); } 50% { box-shadow:0 0 14px rgba(139,92,246,0.65), 0 0 28px rgba(139,92,246,0.3); } }

.plan-sec-head { display:flex; align-items:center; justify-content:space-between; padding:0 2px 6px; margin-top:14px; }
.plan-sec-title { font-size:16px; font-weight:700; color:var(--text-primary); }
.plan-sec-link { display:flex; align-items:center; gap:3px; font-size:13px; color:var(--text-secondary); font-weight:500; cursor:pointer; padding:4px 10px; border-radius:10px; background:rgba(139,92,246,0.06); transition:background 0.15s; }
.plan-sec-link:active { background:rgba(139,92,246,0.14); }

.hero-plan-card { position:relative; z-index:1; overflow:hidden; height:220px; margin:14px 0; cursor:pointer; border-radius:20px; background:#1e1b2e; box-shadow:0 4px 20px rgba(0,0,0,0.1); transition:transform 0.25s, box-shadow 0.25s; }
.hero-plan-card:active { transform:scale(0.985); box-shadow:0 2px 10px rgba(0,0,0,0.15); }
.hero-placeholder { position:absolute; inset:0; z-index:0; background:linear-gradient(135deg, #667eea 0%, #764ba2 50%, #5b2d8e 100%); }
.hero-carousel-img { position:absolute; inset:0; z-index:0; background-size:cover; background-position:center; opacity:0; transition:opacity 1.2s ease-in-out; }
.hero-carousel-img.active { opacity:1; }
.hero-mask { position:absolute; inset:0; z-index:1; background:linear-gradient(180deg, rgba(0,0,0,0.20) 0%, rgba(0,0,0,0.03) 45%, rgba(0,0,0,0.50) 90%); }
.hero-tag { position:absolute; top:16px; left:18px; z-index:3; font-size:12px; color:rgba(255,255,255,0.85); padding:4px 10px; border-radius:10px; background:rgba(255,255,255,0.12); backdrop-filter:blur(6px); -webkit-backdrop-filter:blur(6px); font-weight:500; letter-spacing:0.5px; }
.hero-top-link { position:absolute; top:16px; right:18px; z-index:3; font-size:12px; color:#fff; font-weight:500; cursor:pointer; display:flex; align-items:center; gap:3px; padding:4px 10px; border-radius:10px; background:rgba(0,0,0,0.2); backdrop-filter:blur(6px); -webkit-backdrop-filter:blur(6px); text-shadow:0 1px 3px rgba(0,0,0,0.3); }
.hero-top-link:active { background:rgba(0,0,0,0.35); }
.hero-glass-btn { position:absolute; bottom:20px; left:50%; transform:translateX(-50%); z-index:3; display:flex; flex-direction:row; align-items:center; gap:7px; padding:11px 26px; border:none; border-radius:26px; background:linear-gradient(135deg,#8B5CF6,#6366F1); color:#fff; cursor:pointer; white-space:nowrap; font-size:14px; font-weight:600; box-shadow:0 8px 22px rgba(99,102,241,0.40); transition:all 0.3s; }
.hero-glass-btn:hover { box-shadow:0 10px 28px rgba(99,102,241,0.5); }
.hero-glass-btn:active { transform:translateX(-50%) scale(0.96); }
.hero-btn-title { display:flex; align-items:center; gap:5px; font-size:14px; font-weight:600; }

.nearby-card { position:relative; z-index:1; overflow:hidden; height:200px; margin-bottom:14px; cursor:pointer; border-radius:20px; background:#e8e4f0; box-shadow:0 4px 18px rgba(0,0,0,0.06); transition:transform 0.25s, box-shadow 0.25s; }
.nearby-card:active { transform:scale(0.985); box-shadow:0 2px 8px rgba(0,0,0,0.08); }
.nearby-card-bg { position:absolute; inset:0; }
.nearby-card-img { position:absolute; inset:0; background-size:cover; background-position:center; opacity:0; transition:opacity 1.2s ease-in-out; }
.nearby-card-img.active { opacity:1; }
.nearby-card-mask { position:absolute; inset:0; background:linear-gradient(180deg, rgba(0,0,0,0.18) 0%, rgba(0,0,0,0.05) 45%, rgba(0,0,0,0.5) 90%); }
.nearby-card-body { position:relative; z-index:2; height:100%; display:flex; flex-direction:column; justify-content:flex-end; padding:16px; }
.nearby-card-tag { display:flex; align-items:center; gap:6px; font-size:18px; font-weight:700; color:#fff; text-shadow:0 1px 4px rgba(0,0,0,0.4); }
.nearby-card-desc { font-size:12px; color:rgba(255,255,255,0.88); margin-top:4px; }
.nearby-card-go { align-self:flex-start; margin-top:10px; font-size:12px; font-weight:600; color:#fff; padding:7px 16px; background:rgba(139,92,246,0.85); backdrop-filter:blur(8px); -webkit-backdrop-filter:blur(8px); border-radius:16px; box-shadow:0 2px 8px rgba(0,0,0,0.15); cursor:pointer; }
.nearby-mini-map-box { position:absolute; inset:0; z-index:1; }
.nearby-mini-placeholder { position:absolute; inset:0; z-index:2; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:8px; font-size:13px; color:#8B5CF6; background:linear-gradient(135deg, rgba(245,243,250,0.95), rgba(237,233,240,0.95)); }
.nearby-overlay { position:absolute; inset:0; z-index:2; pointer-events:none; display:flex; flex-direction:column; justify-content:space-between; padding:14px 18px; }
.nearby-overlay::before { content:''; position:absolute; top:0; left:0; right:0; height:60px; background:linear-gradient(180deg, rgba(0,0,0,0.35) 0%, rgba(0,0,0,0.08) 70%, transparent 100%); border-radius:20px 20px 0 0; }
.nearby-overlay::after { content:''; position:absolute; bottom:0; left:0; right:0; height:40px; background:linear-gradient(0deg, rgba(0,0,0,0.2) 0%, transparent 100%); border-radius:0 0 20px 20px; }
.nearby-top { position:relative; z-index:1; display:flex; justify-content:space-between; align-items:center; }
.nearby-title-row { display:flex; align-items:center; gap:8px; }
.nearby-title { font-size:17px; font-weight:700; color:#fff; text-shadow:0 1px 4px rgba(0,0,0,0.4); }
.nearby-link { font-size:12px; color:#fff; font-weight:600; padding:6px 14px; background:rgba(139,92,246,0.75); backdrop-filter:blur(8px); -webkit-backdrop-filter:blur(8px); border-radius:16px; text-shadow:0 1px 2px rgba(0,0,0,0.2); box-shadow:0 2px 8px rgba(0,0,0,0.15); pointer-events:auto; cursor:pointer; transition:transform 0.15s; }
.nearby-link:active { transform:scale(0.93); background:rgba(139,92,246,0.9); }

.nearby-full-page { display:flex; flex-direction:column; height:100%; width:100%; background:#f5f3fa; }
.nearby-topbar { flex-shrink:0; display:flex; align-items:center; gap:12px; padding:14px 16px; padding-top:calc(14px + env(safe-area-inset-top, 0px)); background:#fff; border-bottom:1px solid #f0edf5; }
.nearby-topbar-title { font-size:17px; font-weight:700; color:var(--text-primary); flex:1; }
.nearby-loc-text { font-size:11px; color:#8B5CF6; font-weight:500; }
.nearby-map-wrap { flex:1; position:relative; min-height:0; }
.nearby-locating { position:absolute; inset:0; z-index:10; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:12px; background:rgba(255,255,255,0.9); font-size:14px; color:var(--text-secondary); }
.nearby-map-box { width:100%; height:100%; }
.nearby-map-controls { position:absolute; right:12px; top:12px; z-index:20; display:flex; flex-direction:column; gap:8px; }
.nearby-ctrl-btn { width:38px; height:38px; border-radius:50%; border:none; background:#fff; box-shadow:0 2px 10px rgba(0,0,0,0.12); display:flex; align-items:center; justify-content:center; cursor:pointer; }
.nearby-ctrl-btn:active { transform:scale(0.92); }
.nearby-bottom-panel { flex-shrink:0; max-height:40%; background:#fff; border-radius:20px 20px 0 0; box-shadow:0 -4px 20px rgba(0,0,0,0.06); display:flex; flex-direction:column; overflow:hidden; }
.nearby-radius-bar { display:flex; align-items:center; gap:12px; padding:14px 16px; border-bottom:1px solid #f0edf5; }
.nearby-radius-label { font-size:13px; color:var(--text-secondary); font-weight:500; }
.nearby-radius-options { display:flex; gap:8px; }
.nearby-radius-btn { padding:6px 14px; border-radius:16px; border:1px solid #E2E8F0; background:#fff; font-size:12px; color:var(--text-secondary); cursor:pointer; font-weight:500; }
.nearby-radius-btn.active { background:#8B5CF6; color:#fff; border-color:#8B5CF6; }
.nearby-loading-row { display:flex; align-items:center; justify-content:center; gap:8px; padding:20px; font-size:13px; color:var(--text-hint); }
.nearby-attraction-list { flex:1; overflow:hidden; display:flex; flex-direction:column; }
.nearby-list-head { display:flex; align-items:center; justify-content:space-between; padding:12px 16px 8px; }
.nearby-list-head span:first-child { font-size:15px; font-weight:700; color:var(--text-primary); }
.nearby-count { font-size:12px; color:#8B5CF6; font-weight:500; }
.nearby-empty { display:flex; flex-direction:column; align-items:center; gap:8px; padding:30px; font-size:13px; color:var(--text-hint); }
.nearby-scroll-list { flex:1; overflow-y:auto; padding:0 16px 16px; -webkit-overflow-scrolling:touch; }
.nearby-attr-card { display:flex; align-items:center; gap:12px; padding:14px; margin-bottom:8px; background:#faf8ff; border-radius:14px; cursor:pointer; transition:background 0.15s; }
.nearby-attr-card:active { background:#f0edfa; }
.nearby-attr-index { width:26px; height:26px; border-radius:50%; background:linear-gradient(135deg, #8B5CF6, #6366F1); color:#fff; font-size:12px; font-weight:700; display:flex; align-items:center; justify-content:center; flex-shrink:0; }
.nearby-attr-info { flex:1; min-width:0; }
.nearby-attr-name { font-size:14px; font-weight:600; color:var(--text-primary); margin-bottom:2px; }
.nearby-attr-addr { font-size:11px; color:var(--text-hint); display:flex; align-items:center; gap:3px; margin-bottom:2px; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; }
.nearby-attr-rating { font-size:11px; color:#F59E0B; }
:deep(.nearby-leaflet-label) { background:#8B5CF6 !important; border:none !important; border-radius:4px !important; padding:1px 6px !important; font-size:10px !important; color:#fff !important; font-weight:500 !important; box-shadow:0 1px 3px rgba(0,0,0,0.15) !important; }

.sec-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; padding:0 2px; }
.sec-title { font-size:16px; font-weight:700; color:var(--text-primary); }
.sec-more { font-size:13px; color:#8B5CF6; cursor:pointer; font-weight:500; }
.sec-more:active { opacity:0.6; }
.sec-guide-label { font-size:10px; color:#8B5CF6; background:rgba(139,92,246,0.08); padding:2px 8px; border-radius:8px; font-weight:500; margin-left:auto; margin-right:8px; }

.guide-section { margin-bottom:18px; }
.guide-card { flex-shrink:0; width:236px; background:linear-gradient(160deg, rgba(255,255,255,0.7) 0%, rgba(255,255,255,0.14) 40%, rgba(255,255,255,0.32) 100%),rgba(255,255,255,0.6); backdrop-filter:blur(12px) saturate(150%); -webkit-backdrop-filter:blur(12px) saturate(150%); border-radius:16px; overflow:hidden; box-shadow:inset 0 1px 0 rgba(255,255,255,0.6),0 4px 16px rgba(0,0,0,0.05); border:1px solid rgba(255,255,255,0.65); cursor:pointer; transition:transform 0.2s; }
.guide-card:hover { transform:translateY(-5px); }
.guide-card-img-wrap { height:190px; position:relative; overflow:hidden; }
.guide-card-img { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; z-index:1; }
.guide-card-img-placeholder { position:absolute; inset:0; z-index:0; display:flex; align-items:center; justify-content:center; background:rgba(139,92,246,0.04); }
.guide-card-tag { position:absolute; top:12px; left:12px; z-index:2; font-size:11px; font-weight:600; color:#fff; padding:4px 10px; border-radius:10px; background:rgba(0,0,0,0.35); backdrop-filter:blur(8px); -webkit-backdrop-filter:blur(8px); letter-spacing:0.5px; }
.guide-card-body { padding:12px 13px 14px; }
.guide-card-name { font-size:15px; font-weight:700; color:var(--text-primary); line-height:1.4; display:-webkit-box; -webkit-line-clamp:2; line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }
.guide-card-meta { font-size:12px; margin-top:8px; display:flex; align-items:center; justify-content:space-between; }
.meta-star { color:#F59E0B; font-weight:600; }
.meta-go { color:#8B5CF6; font-weight:500; font-size:12px; }

.templates-section { margin-bottom:18px; }

/* ==================== 行程模板卡片（新功能） ==================== */
.template-list { display:flex; flex-direction:column; gap:10px; }
.template-card {
  display:flex; gap:12px; padding:12px;
  background:linear-gradient(160deg, rgba(255,255,255,0.65) 0%, rgba(255,255,255,0.12) 40%, rgba(255,255,255,0.3) 100%),rgba(255,255,255,0.55);
  backdrop-filter:blur(12px) saturate(150%); -webkit-backdrop-filter:blur(12px) saturate(150%);
  border-radius:16px; border:1px solid rgba(255,255,255,0.6);
  box-shadow:inset 0 1px 0 rgba(255,255,255,0.6), 0 2px 10px rgba(0,0,0,0.03);
  transition:transform 0.2s;
}
.template-card:active { transform:scale(0.985); }
.template-cover { width:84px; height:84px; border-radius:12px; overflow:hidden; flex-shrink:0; position:relative; background:rgba(139,92,246,0.06); }
.template-cover-img { width:100%; height:100%; object-fit:cover; display:block; }
.template-cover-fallback { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; }
.template-info { flex:1; min-width:0; display:flex; flex-direction:column; }
.template-name { font-size:15px; font-weight:700; color:var(--text-primary); overflow:hidden; white-space:nowrap; text-overflow:ellipsis; }
.template-dest { font-size:12px; color:#8B5CF6; display:flex; align-items:center; gap:2px; margin-top:2px; }
.template-meta { font-size:11px; color:var(--text-hint); margin-top:3px; }
.template-tags { display:flex; flex-wrap:wrap; gap:6px; margin-top:4px; }
.template-tag { font-size:10px; color:#64748b; background:rgba(139,92,246,0.07); padding:1px 8px; border-radius:8px; }
.template-bottom { display:flex; align-items:center; justify-content:space-between; margin-top:auto; padding-top:6px; }
.template-downloads { font-size:11px; color:#94a3b8; }
.template-use-btn {
  display:flex; align-items:center; justify-content:center; gap:4px;
  min-width:92px; padding:6px 12px; border:none; border-radius:16px;
  background:linear-gradient(135deg, #8B5CF6, #6366F1); color:#fff;
  font-size:12px; font-weight:600; cursor:pointer;
  box-shadow:0 4px 12px rgba(139,92,246,0.25); transition:all 0.2s;
}
.template-use-btn:active { transform:scale(0.95); }
.template-use-btn:disabled { opacity:0.7; }
.template-fail { display:flex; align-items:center; gap:8px; padding:14px 4px; font-size:13px; color:#94a3b8; }
.template-fail .link { color:#8B5CF6; cursor:pointer; font-size:12px; }
.template-skeleton { display:flex; flex-direction:column; gap:10px; }
.template-card-skeleton { display:flex; gap:12px; padding:12px; background:rgba(255,255,255,0.5); border-radius:16px; animation:shimmer 1.8s ease-in-out infinite; }
.sk-thumb { width:84px; height:84px; border-radius:12px; background:#eee; flex-shrink:0; }
.sk-lines { flex:1; display:flex; flex-direction:column; gap:10px; justify-content:center; }
.sk-line { height:14px; border-radius:4px; background:#eee; width:80%; }
.sk-line--short { width:50%; }
.hot-head { margin-top:16px; }

.quick-tags { display:flex; flex-wrap:wrap; gap:10px; }
.quick-tag { padding:9px 16px; background:linear-gradient(160deg, rgba(255,255,255,0.6) 0%, rgba(255,255,255,0.12) 40%, rgba(255,255,255,0.3) 100%),rgba(255,255,255,0.55); backdrop-filter:blur(10px) saturate(150%); -webkit-backdrop-filter:blur(10px) saturate(150%); border-radius:20px; font-size:13px; color:#7C3AED; cursor:pointer; border:1px solid rgba(255,255,255,0.55); box-shadow:inset 0 1px 0 rgba(255,255,255,0.55); transition:all 0.2s; font-weight:500; }
.quick-tag:active { background:#faf5ff; border-color:#C4B5FD; transform:scale(0.95); }

.trips-list-section { margin-top:8px; }
.section-block { margin:16px 0 8px; position:relative; z-index:1; }
.section-head { display:flex; justify-content:space-between; align-items:center; padding:0 0 8px 0; }
.section-head-title { font-size:16px; font-weight:700; color:#1e293b; }
.section-head-count { font-size:12px; color:#94a3b8; }
.empty-hint-row { padding:16px; text-align:center; font-size:13px; color:#94a3b8; }
.empty-hint-row .link { color:#8B5CF6; cursor:pointer; }
.page-content { padding:0; }

.error-state { display:flex; flex-direction:column; align-items:center; padding:60px 20px; text-align:center; }
.error-text { font-size:15px; color:var(--text-hint); margin:12px 0 16px; }
.retry-btn { border-radius:20px !important; color:#7C3AED !important; border-color:#C4B5FD !important; }
.skeleton-list { padding:12px 0; display:flex; flex-direction:column; gap:12px; }
.trip-card-skeleton { background:#fff; border-radius:18px; padding:20px; box-shadow:0 4px 18px rgba(0,0,0,0.04); transform:translateZ(0); }
.sk-row { height:14px; border-radius:4px; background:#f0f0f0; margin-bottom:12px; }
.sk-row:last-child { margin-bottom:0; }
.sk-row-title { width:55%; height:18px; }
.sk-row-info { width:75%; }
.sk-row-attract { width:85%; height:36px; }
@keyframes shimmer { 0% { opacity:0.4; } 50% { opacity:0.8; } 100% { opacity:0.4; } }
.trip-card-skeleton { animation: shimmer 1.8s ease-in-out infinite; }

.trip-card { position:relative; z-index:1; background:linear-gradient(160deg, rgba(255,255,255,0.65) 0%, rgba(255,255,255,0.12) 40%, rgba(255,255,255,0.3) 100%),rgba(255,255,255,0.55); backdrop-filter:blur(14px) saturate(160%); -webkit-backdrop-filter:blur(14px) saturate(160%); border-radius:16px; padding:18px 16px; box-shadow:inset 0 1px 0 rgba(255,255,255,0.6),0 2px 10px rgba(0,0,0,0.03); border:1px solid rgba(255,255,255,0.65); transition:transform 0.2s; margin-bottom:14px; cursor:pointer; display:flex; flex-direction:column; gap:10px; }
.trip-card:active { transform:scale(0.985); }
.trip-card-top { display:flex; align-items:center; gap:8px; }
.trip-s-badge { width:26px; height:26px; border-radius:50%; background:linear-gradient(135deg, #6366F1, #4F46E5); display:flex; align-items:center; justify-content:center; flex-shrink:0; }
.trip-s-badge--home { background:linear-gradient(135deg, #F59E0B, #D97706); }
.trip-s-letter { color:#fff; font-size:13px; font-weight:700; line-height:1; }
.trip-card-label { font-size:13px; color:var(--text-secondary); font-weight:500; flex:1; }
.trip-status-tag { font-size:11px; padding:2px 10px; border-radius:10px; font-weight:600; flex-shrink:0; }
.trip-card-title { font-size:16px; font-weight:700; color:var(--text-primary); line-height:1.5; display:-webkit-box; -webkit-line-clamp:2; line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }
.trip-card-route { font-size:12px; color:var(--text-secondary); line-height:1.65; padding:8px 12px; background:#f8f7ff; border-radius:10px; overflow:hidden; display:-webkit-box; -webkit-line-clamp:3; line-clamp:3; -webkit-box-orient:vertical; }
.trip-card-meta { font-size:12px; color:var(--text-hint); line-height:1.5; }
.trip-card-footer { display:flex; justify-content:center; padding-top:4px; }
.trip-detail-link { font-size:13px; color:#8B5CF6; font-weight:500; cursor:pointer; padding:4px 0; }
.trip-detail-link:active { opacity:0.6; }

.fab-ai-btn { position:fixed; bottom:calc(10px + 48px + 16px + var(--safe-area-bottom, 0px)); right:16px; z-index:9995; width:48px; height:48px; border-radius:50%; border:1px solid rgba(255,255,255,0.55); background:rgba(255,255,255,0.6); backdrop-filter:blur(18px) saturate(180%); -webkit-backdrop-filter:blur(18px) saturate(180%); box-shadow:0 0 24px rgba(139,92,246,0.3),0 0 48px rgba(139,92,246,0.12); display:flex; align-items:center; justify-content:center; cursor:pointer; will-change:transform; transition:transform 0.2s, box-shadow 0.25s; }
.fab-ai-btn:hover { box-shadow:0 0 32px rgba(139,92,246,0.4),0 0 56px rgba(139,92,246,0.18); transform:translateY(-3px); }
.fab-ai-btn:active { transform:scale(0.9); }

.clouds-layer { position:fixed; inset:0; z-index:0; pointer-events:none; overflow:hidden; }
.cloud-dot { position:absolute; border-radius:50%; background:rgba(139,92,246,0.05); animation:cloudDriftSlow linear infinite; }
.c1 { width:40px; height:40px; top:20%; right:10%; animation-duration:30s; }
.c2 { width:55px; height:55px; top:60%; left:8%; animation-duration:36s; animation-delay:-12s; }
.c3 { width:35px; height:35px; top:85%; left:65%; animation-duration:26s; animation-delay:-6s; }
@keyframes cloudDriftSlow { 0% { transform:translateY(0) translateX(0); opacity:0.5; } 50% { transform:translateY(-10px) translateX(8px); opacity:1; } 100% { transform:translateY(0) translateX(0); opacity:0.5; } }

.hero-plan-card { transition:transform 0.35s cubic-bezier(0.4,0,0.2,1), box-shadow 0.35s ease; }
.guide-card { transition:transform 0.35s cubic-bezier(0.4,0,0.2,1), box-shadow 0.35s ease; }
.guide-card:hover { transform:translateY(-5px); box-shadow:0 12px 28px rgba(139,92,246,0.10); }
.trip-card { transition:transform 0.35s cubic-bezier(0.4,0,0.2,1), box-shadow 0.35s ease; }
.trip-card:hover { transform:translateY(-4px); box-shadow:0 10px 24px rgba(139,92,246,0.08); }
.fab-ai-btn { animation:pulseGlow 2.5s ease-in-out infinite; }

/* FAB 弹出/隐藏动画 */
.fab-pop-enter-active { transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1); }
.fab-pop-leave-active { transition: all 0.25s cubic-bezier(0.4, 0, 1, 1); }
.fab-pop-enter-from { opacity: 0; transform: scale(0.3) translateY(20px); }
.fab-pop-leave-to   { opacity: 0; transform: scale(0.5) translateY(30px); }
@keyframes pulseGlow { 0%,100% { box-shadow:0 8px 28px rgba(139,92,246,0.4); } 50% { box-shadow:0 12px 36px rgba(139,92,246,0.6); } }

/* ==================== 周边游（tour，沉浸式地图） ==================== */
/* 底部抽屉：不是撑满整屏，留出顶部间隙；dvh 让顶栏不跑到地址栏后面 */
.tour-fullscreen { width:100%; height:84vh; height:84dvh; max-height:84dvh; border-radius:20px 20px 0 0; overflow:hidden; box-shadow:0 -8px 30px rgba(0,0,0,0.14); }
.tour-page { position:relative; width:100%; height:100%; overflow:hidden; background:#e6e3ef; }

/* 地图铺满弹层（周边游 Tab） */
.tour-map-wrap { position:absolute; inset:0; z-index:0; }
.tour-map-loading { position:absolute; inset:0; z-index:10; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:12px; background:var(--bg-card-solid,#fff); font-size:13px; color:var(--text-secondary); }
.tour-map-box { width:100%; height:100%; }

/* 顶部浮动导航（悬浮于地图，不占文档流、不遮挡地图） */
.tour-float-nav { position:absolute; top:12px; left:10px; right:10px; z-index:20; display:flex; align-items:center; gap:8px; padding:9px 12px; border-radius:16px; background:rgba(255,255,255,0.82); backdrop-filter:blur(18px) saturate(170%); -webkit-backdrop-filter:blur(18px) saturate(170%); box-shadow:0 4px 16px rgba(0,0,0,0.12); border:1px solid rgba(255,255,255,0.6); }
.tour-float-title { flex:1; text-align:center; font-size:16px; font-weight:700; color:var(--text-primary); }
.tour-float-nav .van-icon { color:var(--text-primary); }

/* 顶部浮动 分段（周边游/热门路线）+ 筛选（铁路游/出发地/时长） */
.tour-pill-card { position:absolute; top:62px; left:10px; right:10px; z-index:19; padding:8px; border-radius:18px; background:rgba(255,255,255,0.82); backdrop-filter:blur(18px) saturate(170%); -webkit-backdrop-filter:blur(18px) saturate(170%); box-shadow:0 4px 16px rgba(0,0,0,0.12); border:1px solid rgba(255,255,255,0.6); }
.tour-seg { display:flex; gap:6px; padding:3px; border-radius:14px; background:rgba(139,92,246,0.08); }
.tour-seg-item { flex:1; text-align:center; padding:7px 0; border-radius:11px; font-size:14px; font-weight:600; color:var(--text-secondary); cursor:pointer; transition:all 0.2s; }
.tour-seg-item.active { color:#fff; background:linear-gradient(135deg, #8B5CF6, #6366F1); box-shadow:0 3px 10px rgba(139,92,246,0.25); }
.tour-filter-row { display:flex; gap:8px; padding:8px 2px 0; overflow-x:auto; -webkit-overflow-scrolling:touch; }
.filter-chip { display:flex; align-items:center; gap:4px; padding:6px 12px; border-radius:13px; font-size:12px; font-weight:500; color:#475569; background:#fff; border:1px solid #ECE9F5; white-space:nowrap; cursor:pointer; flex-shrink:0; }
.filter-chip:active { background:#ECE9F5; }

/* 右侧浮动控制按钮 */
.tour-map-controls { position:absolute; right:12px; top:150px; z-index:20; display:flex; flex-direction:column; gap:8px; }
.tour-ctrl-btn { width:40px; height:40px; border-radius:50%; border:none; background:#fff; box-shadow:0 2px 10px rgba(0,0,0,0.12); display:flex; align-items:center; justify-content:center; cursor:pointer; }
.tour-ctrl-btn:active { transform:scale(0.92); }

/* 底部浮动「查看 N 个目的地」 */
.tour-float-bottom { position:absolute; left:0; right:0; bottom:calc(16px + env(safe-area-inset-bottom, 0px)); z-index:20; display:flex; justify-content:center; padding:0 24px; }
.tour-bottom-bar { display:flex; align-items:center; justify-content:center; gap:6px; padding:13px 26px; border-radius:24px; background:linear-gradient(135deg, #8B5CF6, #6366F1); color:#fff; font-size:14px; font-weight:700; cursor:pointer; box-shadow:0 6px 18px rgba(139,92,246,0.35); }
.tour-bottom-bar:active { transform:scale(0.98); }

/* 热门路线 */
.tour-routes-list { position:absolute; inset:0; z-index:5; overflow-y:auto; background:var(--bg-page,#f5f3fa); padding:150px 16px 24px; -webkit-overflow-scrolling:touch; }
.route-card { border-radius:16px; overflow:hidden; margin-bottom:14px; background:linear-gradient(160deg, rgba(255,255,255,0.7) 0%, rgba(255,255,255,0.14) 40%, rgba(255,255,255,0.3) 100%),rgba(255,255,255,0.6); border:1px solid rgba(255,255,255,0.65); box-shadow:inset 0 1px 0 rgba(255,255,255,0.65), 0 2px 12px rgba(0,0,0,0.04); }
.route-cover { position:relative; height:120px; }
.route-cover-img { width:100%; height:120px; object-fit:cover; display:block; }
.route-cover-fallback { height:120px; display:flex; align-items:center; justify-content:center; background:linear-gradient(135deg, #8B5CF6, #6366F1); }
.route-body { padding:12px 14px; }
.route-name { font-size:15px; font-weight:700; color:var(--text-primary); }
.route-meta { font-size:12px; color:#8B5CF6; margin-top:4px; font-weight:500; }
.route-tags { display:flex; flex-wrap:wrap; gap:6px; margin-top:8px; }
.route-tag { font-size:11px; color:var(--text-secondary); background:rgba(139,92,246,0.08); padding:2px 9px; border-radius:8px; }
.route-tagline { font-size:12px; color:var(--text-secondary); margin-top:8px; line-height:1.5; }
.tour-empty { display:flex; flex-direction:column; align-items:center; gap:10px; padding:50px 20px; font-size:13px; color:var(--text-hint); }

/* 城市底部弹层 */
.city-sheet { height:100%; display:flex; flex-direction:column; }
.city-sheet-head { flex-shrink:0; display:flex; align-items:center; justify-content:space-between; padding:14px 44px 14px 18px; }
.city-sheet-title { font-size:16px; font-weight:700; color:var(--text-primary); }
.city-sheet-count { font-size:12px; color:#8B5CF6; font-weight:500; }
.city-sheet-list { flex:1; overflow-y:auto; padding:0 16px 24px; -webkit-overflow-scrolling:touch; }
.city-card { position:relative; border-radius:16px; overflow:hidden; margin-bottom:14px; }
.city-card-swipe { position:relative; height:150px; }
.city-card-img { width:100%; height:150px; object-fit:cover; display:block; }
.city-card-img-fallback { width:100%; height:150px; display:flex; align-items:center; justify-content:center; background:linear-gradient(135deg, #8B5CF6, #6366F1); }
.city-card-shade { position:absolute; inset:0; background:linear-gradient(180deg, rgba(0,0,0,0.10) 0%, rgba(0,0,0,0.02) 45%, rgba(0,0,0,0.55) 100%); pointer-events:none; }
.city-card-body { position:absolute; left:0; right:0; bottom:0; padding:26px 14px 12px; z-index:2; }
.city-card-top { display:flex; align-items:center; justify-content:space-between; }
.city-card-name { font-size:18px; font-weight:700; color:#fff; text-shadow:0 1px 4px rgba(0,0,0,0.4); }
.heat-badge { display:flex; align-items:center; gap:3px; font-size:12px; font-weight:700; color:#FF5A5F; background:rgba(255,255,255,0.92); padding:3px 9px; border-radius:12px; }
.city-card-tags { display:flex; gap:6px; margin-top:6px; }
.city-card-tag { font-size:10px; color:#fff; background:rgba(255,255,255,0.2); padding:2px 8px; border-radius:8px; backdrop-filter:blur(4px); -webkit-backdrop-filter:blur(4px); }
.city-card-meta { display:flex; align-items:center; gap:8px; margin-top:8px; }
.transit-tag { font-size:12px; font-weight:700; color:#fff; background:linear-gradient(135deg, #8B5CF6, #6366F1); padding:3px 10px; border-radius:12px; }
.price-tag { font-size:12px; font-weight:700; color:#fff; background:rgba(255,255,255,0.22); padding:3px 10px; border-radius:12px; }
.city-card-desc { font-size:12px; color:rgba(255,255,255,0.92); margin-top:8px; line-height:1.5; }

/* 出发城市选择 */
.depart-picker { padding:48px 16px 20px; }
.depart-picker-title { font-size:16px; font-weight:700; color:var(--text-primary); text-align:center; margin-bottom:8px; }
.depart-picker-item { display:flex; align-items:center; justify-content:space-between; padding:14px 12px; border-radius:12px; font-size:15px; color:var(--text-primary); cursor:pointer; }
.depart-picker-item:active { background:#faf5ff; }
.depart-picker-item.active { color:#8B5CF6; font-weight:700; }

/* 周边游深色模式 */
html[data-theme='dark'] .tour-page { background:var(--bg-page); }
html[data-theme='dark'] .tour-topbar,
html[data-theme='dark'] .tour-tabs,
html[data-theme='dark'] .tour-float-nav,
html[data-theme='dark'] .tour-pill-card { background:rgba(30,33,47,0.82); border-color:rgba(255,255,255,0.10); box-shadow:0 4px 16px rgba(0,0,0,0.35); }
html[data-theme='dark'] .tour-float-nav .van-icon { color:#E2E8F0; }
html[data-theme='dark'] .tour-float-title { color:var(--text-primary); }
html[data-theme='dark'] .tour-seg { background:rgba(139,92,246,0.16); }
html[data-theme='dark'] .tour-seg-item { color:var(--text-secondary); }
html[data-theme='dark'] .filter-chip { color:var(--text-secondary); background:var(--bg-card); border-color:var(--glass-border); }
html[data-theme='dark'] .tour-map-loading,
html[data-theme='dark'] .tour-ctrl-btn { background:var(--bg-card-solid); }
html[data-theme='dark'] .tour-routes-list { background:var(--bg-page); }
html[data-theme='dark'] .route-card { background:var(--bg-card-solid); border-color:var(--glass-border); }
html[data-theme='dark'] .route-tag { color:var(--text-secondary); }

@media screen and (max-width:360px) {
  .hero-glass-btn { padding:9px 18px; bottom:16px; }
  .guide-card { width:210px; }
  .guide-card-img-wrap { height:168px; }
}
</style>

<!-- 全局：横向滚动渐隐 -->
<style>
.h-scroll {
  display: flex; gap: 10px; overflow-x: auto; padding-bottom: 4px;
  -webkit-overflow-scrolling: touch; scrollbar-width: none;
  -webkit-mask-image: linear-gradient(90deg, #000 0%, #000 85%, transparent 100%);
  mask-image: linear-gradient(90deg, #000 0%, #000 85%, transparent 100%);
}
.h-scroll::-webkit-scrollbar { display: none; }

/* ==================== 深色模式（B4） ==================== */
html[data-theme='dark'] .nearby-card { background: var(--bg-card-solid); }
html[data-theme='dark'] .nearby-mini-placeholder { background: linear-gradient(135deg, var(--bg-glass-strong), var(--bg-glass)); }
html[data-theme='dark'] .nearby-full-page { background: var(--bg-page); }
html[data-theme='dark'] .nearby-topbar,
html[data-theme='dark'] .nearby-bottom-panel { background: var(--bg-card-solid); border-color: var(--glass-border); }
html[data-theme='dark'] .nearby-locating { background: var(--bg-card-solid); }
html[data-theme='dark'] .nearby-ctrl-btn { background: var(--bg-card-solid); }
html[data-theme='dark'] .more-item { color: var(--text-secondary); }
html[data-theme='dark'] .more-item:active { background: var(--bg-card); }

/* ==================== 周边游地图钉（注入 AMap/Leaflet DOM，须为全局样式） ==================== */
.city-pin { display:flex; flex-direction:column; align-items:center; gap:1px; padding:5px 9px; border-radius:16px; background:#fff; color:var(--text-primary); box-shadow:0 2px 10px rgba(0,0,0,0.18); border:1px solid #ECE9F5; font-size:11px; font-weight:600; line-height:1.1; white-space:nowrap; cursor:pointer; }
.city-pin-city { color:var(--text-primary); }
.city-pin-time { color:#8B5CF6; font-weight:700; }
.tour-leaflet-label { background:#8B5CF6 !important; border:none !important; border-radius:6px !important; padding:2px 8px !important; font-size:10px !important; color:#fff !important; font-weight:600 !important; box-shadow:0 1px 4px rgba(0,0,0,0.2) !important; }
html[data-theme='dark'] .city-pin { background:var(--bg-card-solid); border-color:var(--glass-border); }
</style>
