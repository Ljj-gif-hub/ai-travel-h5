<script setup>
/**
 * AgentMapView.vue — Agent 行程规划地图页 v5
 * 可拖拽抽屉 + 百度地图 + 景点图片 + 携程同款动画
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { agentPlanStream } from '../api/agent'
import { sceneApi } from '../api/index.js'

defineOptions({ name: 'AgentMapView' })
const router = useRouter()
const route = useRoute()

const destCity = ref(route.query.destination || '')
const originCity = ref(route.query.origin || '出发地')
const tripDays = ref(Number(route.query.days) || 3)
const tripPeople = ref(Number(route.query.people) || 2)
const phase = ref('generating')
const activeTab = ref('plan') // plan | hotel
const activeDay = ref(0) // 当前查看的天索引
const agentProgress = ref(0)
const agentStep = ref('正在连接 AI Agent…')
const planData = ref(null)
const costBreakdown = ref(null)
const hotelList = ref([])
const attractionImages = ref({})
const budgetLabels = { transport:'交通', accommodation:'住宿', food:'餐饮', tickets:'门票', shopping:'购物', total:'总计' }

let streamAbort = null
const stepList = ref([
  { name: '分析目的地特色', status: 'wait' },
  { name: '智能规划每日行程', status: 'wait' },
  { name: '核算预算与路线', status: 'wait' },
  { name: '优化调整方案', status: 'wait' },
  { name: '生成完整行程', status: 'wait' },
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

// ====== 可拖拽抽屉（仅手柄区域可拖拽，内容区自由滚动） ======
const MIN = 12, MID = 55, MAX = 85
const drawerPct = ref(MID)
const isDragging = ref(false)
let handleTouchId = null, hStartY = 0, hStartPct = 0, hDragOn = false

function snapTo(target) {
  drawerPct.value = Math.max(MIN, Math.min(MAX, target))
}

function onHandleTouchStart(e) {
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
  }
  hDragOn = false
}

// ====== 地图 ======
let mapInstance = null
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
  // 城市标签
  const marker = new window.AMap.Marker({
    position: [center.lng, center.lat],
    label: { content: destCity.value, direction: 'top', offset: [0, -20] },
    zIndex: 100,
  })
  mapInstance.add(marker)
}

function addMarkers(markers) {
  if (!mapInstance || !window.AMap) return
  markers.forEach(m => {
    const mk = new window.AMap.Marker({
      position: [m.lng, m.lat],
      label: { content: m.name, direction: 'right', offset: [10, 0] },
    })
    mapInstance.add(mk)
  })
}

async function loadImages(dayPlans) {
  const names = new Set()
  for (const dp of dayPlans) for (const s of (dp.timeSlots||[])) { if (s.attraction) names.add(s.attraction) }
  for (const name of names) {
    try { const r = await sceneApi.getSceneImage(name); if (r.code===0&&r.data?.imgUrl) attractionImages.value[name] = r.data.imgUrl } catch {}
  }
}

// ====== SSE 流式生成 ======
async function startGeneration() {
  const steps = stepList.value
  steps.forEach(s => s.status = 'wait')
  agentLogs.value = []
  agentProgress.value = 0
  agentStep.value = '正在连接 AI Agent…'

  streamAbort = agentPlanStream({
    destination: destCity.value, origin: originCity.value, days: tripDays.value,
    budget: Number(route.query.budget) || 5000, people: tripPeople.value,
    companion: route.query.companion || '',
    styles: route.query.styles ? route.query.styles.split(',') : [],
    hotel_level: route.query.hotel_level || '舒适型', pace: route.query.pace || '适中',
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
            activity: s.activity || '', duration: s.duration || '', cost: `${s.cost || 0}元`,
            transport: s.transport || '', tips: s.tips || '', hours: s.hours || '',
          })), meals: dp.meals || [],
        })), tips: d.tips || [],
      }
      costBreakdown.value = d.budget_detail || null
      hotelList.value = (d.hotels || []).map(h => ({
        name: h.name, district: h.district, pricePerNight: h.price_per_night, rating: h.rating, highlights: h.highlights,
      }))
      const center = await getCenter(destCity.value)
      const markers = []; (d.day_plans || []).forEach(dp => { (dp.time_slots || []).forEach(s => { if (s.attraction) markers.push({ name: s.attraction, lat: s.lat || center.lat + (Math.random() - .5) * .03, lng: s.lng || center.lng + (Math.random() - .5) * .03 }) }) })
      addMarkers(markers); loadImages(d.day_plans || [])
      phase.value = 'completed'; snapTo(MAX)
    },
    onError(msg) { showToast(msg || '规划失败'); router.replace('/agent-planner') },
  })
}

onMounted(() => { if(!destCity.value){router.replace('/agent-planner');return}; initMap(); startGeneration() })
onBeforeUnmount(() => { if (streamAbort) streamAbort() })
function goBack() { router.replace('/agent-planner') }
function handleStop() { if (streamAbort) streamAbort(); router.replace('/agent-planner') }
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
      <span class="top-title">{{ destCity }} · {{ tripDays }}天</span>
    </div>

    <!-- 可拖拽抽屉 -->
    <div class="drawer"
      :style="{transform:`translateY(${100-drawerPct}%)`,transition:isDragging?'none':'transform 0.35s cubic-bezier(0.4,0,0.2,1)'}">
      <div class="handle" @click="snapTo(drawerPct>50?MIN:MAX)"
        @touchstart.passive="onHandleTouchStart" @touchmove="onHandleTouchMove"
        @touchend="onHandleTouchEnd" @touchcancel="onHandleTouchEnd">
        <div class="bar"></div>
      </div>
      <div class="body">

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

          <button class="stop-btn" @click="handleStop">停止生成</button>
        </div>

        <!-- 完成 -->
        <div v-else-if="phase==='completed'&&planData" class="done">
          <!-- 双 Tab -->
          <div class="tab-row">
            <div class="tab" :class="{on:activeTab==='plan'}" @click="activeTab='plan'">📅 行程</div>
            <div class="tab" :class="{on:activeTab==='hotel'}" @click="activeTab='hotel'">🏨 住宿</div>
          </div>

          <!-- 行程 Tab -->
          <template v-if="activeTab==='plan'">
            <div class="day-tabs">
              <div v-for="(dp, idx) in planData.dayPlans" :key="dp.day"
                   class="day-tab" :class="{on:activeDay===idx}" @click="activeDay=idx">
                <span class="dt-num">Day{{dp.day}}</span>
                <span class="dt-title">{{dp.dayTitle?.replace('第'+dp.day+'天：','').replace('第'+dp.day+'天:','')}}</span>
              </div>
            </div>

            <template v-if="planData.dayPlans[activeDay]">
              <div v-for="(slot, si) in planData.dayPlans[activeDay].timeSlots" :key="si" class="spot-card">
                <div class="spot-header">
                  <span class="spot-num">{{si+1}}</span>
                  <div class="spot-title-row">
                    <span class="spot-title">{{slot.attraction}}</span>
                    <span v-if="si<=1" class="spot-badges">
                      <span class="hot-badge" v-if="si===0">🔥 热度10</span>
                      <span class="level-badge" v-if="si===0">5A</span>
                      <span class="rank-badge">🏆 必去榜单</span>
                    </span>
                  </div>
                </div>
                <div class="spot-hours" v-if="slot.hours||slot.tips">
                  <span class="hours-icon">🕐</span>
                  <span>{{slot.hours||'详情见注意事项'}}</span>
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
                <span v-for="m in planData.dayPlans[activeDay].meals" :key="m" class="meal">{{m}}</span>
              </div>
            </template>
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
                    <span class="hotel-price">¥{{h.pricePerNight?.toLocaleString()}}</span><span class="hotel-unit">/晚</span>
                    <span class="hotel-total">共{{tripDays}}晚 ¥{{(h.pricePerNight*tripDays)?.toLocaleString()}}</span>
                  </div>
                </div>
              </div>
              <div class="budget-card" v-if="costBreakdown">
                <h3>💰 费用预估</h3>
                <div class="budget-rows">
                  <div v-for="(v,k) in costBreakdown" :key="k" class="budget-row" :class="{total:k==='total'}">
                    <span>{{budgetLabels[k]||k}}</span><b>¥{{v.toLocaleString?.()||v}}</b>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- 底部栏 -->
          <div class="bottom-bar">
            <div class="ai-input" @click="showToast('功能开发中')"><span>💬 调整行程，如「放慢节奏」...</span></div>
            <div class="hotel-bar" @click="activeTab='hotel'"><span>🏨 住宿</span></div>
          </div>
        </div>
      </div>
    </div>
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
.bar { width:36px; height:5px; background:#d1d5db; border-radius:3px; }
.body { flex:1; overflow-y:auto; -webkit-overflow-scrolling:touch; padding:0 16px 120px; }

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
@keyframes cardIn { from{opacity:0;transform:translateY(12px)} to{opacity:1;transform:translateY(0)} }

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
.done { padding:12px 0 160px; }

/* 双Tab */
.tab-row { display:flex; gap:0; background:#fff; border-radius:20px; padding:4px; margin-bottom:12px; }
.tab { flex:1; text-align:center; padding:10px; border-radius:16px; font-size:14px; font-weight:500; color:#888; cursor:pointer; transition:all .25s; }
.tab.on { background:linear-gradient(135deg,#8b5cf6,#6366f1); color:#fff; font-weight:600; box-shadow:0 2px 8px rgba(139,92,246,0.3); }

/* 天数标签栏 */
.day-tabs { display:flex; gap:8px; padding:0 0 12px; overflow-x:auto; scrollbar-width:none; }
.day-tabs::-webkit-scrollbar { display:none; }
.day-tab { flex-shrink:0; padding:8px 14px; border-radius:14px; background:#fff; cursor:pointer; text-align:center; min-width:72px; transition:all .25s; }
.day-tab.on { background:#1a1a2e; }
.day-tab.on .dt-num, .day-tab.on .dt-title { color:#fff; }
.dt-num { display:block; font-size:12px; font-weight:600; color:#8b5cf6; }
.dt-title { display:block; font-size:10px; color:#888; margin-top:2px; white-space:nowrap; }

/* 景点卡片 */
.spot-card { background:#fff; border-radius:16px; margin-bottom:12px; overflow:hidden; box-shadow:0 1px 4px rgba(0,0,0,0.04); }
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
.meals-bar { padding:10px 14px; margin-bottom:12px; background:#fff; border-radius:12px; display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.meals-tag { font-size:14px; }
.meal { padding:4px 10px; background:rgba(245,158,11,0.1); color:#d97706; border-radius:10px; font-size:11px; }

/* 住宿 */
.hotels-section { padding-bottom:20px; }
.hotel-card { display:flex; gap:12px; background:#fff; border-radius:16px; padding:12px; margin-bottom:10px; }
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
.budget-card { background:#fff; border-radius:16px; padding:16px; margin-top:8px; }
.budget-card h3 { margin:0 0 10px; font-size:15px; }
.budget-rows { display:flex; flex-direction:column; gap:6px; }
.budget-row { display:flex; justify-content:space-between; padding:10px 14px; background:#f8f9fa; border-radius:10px; font-size:13px; color:#555; }
.budget-row.total { background:linear-gradient(135deg,#8b5cf6,#6366f1); color:#fff; }
.budget-row.total span, .budget-row.total b { color:#fff; }
.budget-row b { font-size:14px; color:#1a1a2e; }

/* 底部栏 */
.bottom-bar { position:fixed; bottom:0; left:0; right:0; z-index:30; display:flex; gap:8px; padding:10px 16px calc(env(safe-area-inset-bottom)+10px); background:rgba(255,255,255,0.9); backdrop-filter:blur(20px); border-top:1px solid rgba(0,0,0,0.05); }
.ai-input { flex:1; padding:12px 16px; background:#f5f5f7; border-radius:22px; font-size:13px; color:#aaa; cursor:pointer; }
.hotel-bar { padding:12px 18px; background:#8b5cf6; border-radius:22px; color:#fff; font-size:13px; font-weight:500; cursor:pointer; white-space:nowrap; }
</style>
