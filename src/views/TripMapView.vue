<template>
  <!--
    TripMapView.vue — 携程同款地图底+可拖拽抽屉行程页
    层级结构：
      z1: 全屏地图容器（底层永久固定，不随抽屉滑动消失）
      z2: 地图半透明黑色弱化蒙版（透明度联动抽屉高度）
      z3: 可拖拽抽屉弹窗容器（核心交互层，三段式吸附）
      z4: 页面顶部状态栏（返回/分享按钮）
  -->
  <div class="trip-map-page">
    <!-- ==================== z1: 全屏百度地图容器 ==================== -->
    <div class="map-container" ref="mapContainerRef">
      <div id="trip-bmap-container" class="bmap-inner"></div>
    </div>

    <!-- ==================== z2: 地图半透明遮罩层（透明度联动抽屉） ==================== -->
    <div
      class="map-overlay"
      :style="{ opacity: overlayOpacity }"
    ></div>

    <!-- ==================== z4: 顶部状态栏 ==================== -->
    <div class="top-bar">
      <div class="top-btn back-btn" @click="goBack">
        <van-icon name="arrow-left" size="22" color="#fff" />
      </div>
      <div class="top-title">{{ store.state.params.destination || '行程规划' }}</div>
      <div class="top-btn share-btn" @click="handleShare">
        <van-icon name="share-o" size="20" color="#fff" />
      </div>
    </div>

    <!-- ==================== z3: 可拖拽抽屉组件 ==================== -->
    <DragSheet
      v-model="store.state.drawerState"
      :minHeight="20"
      :midHeight="65"
      :maxHeight="95"
      :overlayOpacity="0.55"
      @state-change="onDrawerStateChange"
    >
      <!-- 状态A: AI行程生成中 -->
      <TripGeneratingState
        v-if="store.state.phase === 'generating'"
        :progress="store.state.progress"
        :currentStep="store.state.currentStep"
        :stepList="store.state.stepList"
        @stop="handleStopGeneration"
      />

      <!-- 状态B: 行程生成完成 -->
      <TripCompletedState
        v-else-if="store.state.phase === 'completed'"
        :planData="store.state.planData"
        :costBreakdown="store.state.costBreakdown"
        :hotelList="store.state.hotelList"
        :drawerState="store.state.drawerState"
        @share="handleShare"
        @like="handleLike"
        @dislike="handleDislike"
        @save="handleSavePlan"
      />

      <!-- 底部固定：语音输入栏 -->
      <VoiceInput
        :disabled="store.state.phase === 'completed' ? false : true"
        @result="onVoiceResult"
      />
    </DragSheet>
  </div>
</template>

<script setup>
/**
 * TripMapView — 地图底+可拖拽抽屉行程主页面
 *
 * 核心功能：
 * 1. 全屏百度地图作为永久背景（实例不销毁）
 * 2. 三段式拖拽抽屉（收起20%/半展65%/全屏95%）
 * 3. 双状态兼容：生成中 / 生成完成
 * 4. 地图标记渲染（地标+地铁站）
 * 5. SSE 流式进度订阅
 */
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'

// 导入自定义组件
import DragSheet from '../components/DragSheet.vue'
import TripGeneratingState from '../components/TripGeneratingState.vue'
import TripCompletedState from '../components/TripCompletedState.vue'
import VoiceInput from '../components/VoiceInput.vue'

// 导入共享状态和 API
import { useTripStore } from '../stores/trip.js'
import { tripNewApi } from '../api/tripNew.js'

const router = useRouter()
const route = useRoute()
const store = useTripStore()

/* ==================== 地图管理 ==================== */
const mapContainerRef = ref(null)

// 地图单例：页面切换不销毁地图实例，避免重复加载卡顿
let mapInstance = null
let mapMarkers = []      // 当前地图上的标记集合
let mapProvider = null   // 'baidu' | 'leaflet' — 当前使用的地图引擎
let leafletLoaded = false

/**
 * 加载百度地图 SDK（后端代理）
 * 后端 AK 未配置时返回 window._baiduMapUnavailable=true 标记脚本
 */
const loadBaiduMapSDK = () => {
  return new Promise((resolve) => {
    if (window.BMapGL) { resolve('baidu'); return }
    if (window._baiduMapLoading) {
      const check = setInterval(() => {
        if (window.BMapGL) { clearInterval(check); resolve('baidu'); return }
        if (window._baiduMapUnavailable) { clearInterval(check); resolve('unavailable'); return }
      }, 200)
      return
    }
    window._baiduMapLoading = true
    const script = document.createElement('script')
    script.src = '/api/map/script'
    // 设置超时：3秒后如果还没加载成功，直接降级
    const timeout = setTimeout(() => {
      window._baiduMapLoading = false
      window._baiduMapUnavailable = true
      resolve('unavailable')
    }, 3000)
    script.onload = () => {
      clearTimeout(timeout)
      let retries = 0
      const check = setInterval(() => {
        if (window.BMapGL) { clearInterval(check); window._baiduMapLoading = false; resolve('baidu'); return }
        if (window._baiduMapUnavailable || retries++ > 20) {
          clearInterval(check); window._baiduMapLoading = false; resolve('unavailable')
        }
      }, 200)
    }
    script.onerror = () => {
      clearTimeout(timeout)
      window._baiduMapLoading = false
      window._baiduMapUnavailable = true
      resolve('unavailable')  // 加载失败 → 直接降级，不报错
    }
    document.head.appendChild(script)
  })
}

/**
 * 加载 Leaflet 免费地图（百度不可用时的降级方案）
 * 使用 unpkg CDN，无需 API Key
 */
const loadLeaflet = () => {
  return new Promise((resolve) => {
    if (leafletLoaded && window.L) { resolve(window.L); return }
    if (window.L) { leafletLoaded = true; resolve(window.L); return }
    const script = document.createElement('script')
    script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
    script.onload = () => { leafletLoaded = true; resolve(window.L) }
    script.onerror = () => resolve(null)
    document.head.appendChild(script)
  })
}

/**
 * 初始化地图 — 优先百度地图，不可用时降级 Leaflet/OpenStreetMap
 */
const initMap = async (centerCity) => {
  try {
    const provider = await loadBaiduMapSDK()

    if (provider === 'baidu') {
      mapProvider = 'baidu'
      await initBaiduMap(centerCity)
    } else {
      // 降级到 Leaflet 免费地图
      console.log('[地图] 百度地图不可用，降级使用 Leaflet + OpenStreetMap')
      mapProvider = 'leaflet'
      const L = await loadLeaflet()
      if (!L) { showToast('地图加载失败，请检查网络'); return }
      await initLeafletMap(centerCity, L)
    }
  } catch (e) {
    console.error('[地图] 初始化失败:', e)
    // 最终兜底：尝试 Leaflet
    if (mapProvider !== 'leaflet') {
      mapProvider = 'leaflet'
      const L = await loadLeaflet()
      if (L) await initLeafletMap(centerCity, L)
      else showToast('地图加载失败，请检查网络')
    }
  }
}

/**
 * 百度地图初始化
 */
const initBaiduMap = (centerCity) => {
  if (mapInstance) {
    const container = document.getElementById('trip-bmap-container')
    if (container && mapInstance.getContainer() !== container) {
      container.appendChild(mapInstance.getContainer())
    }
    const center = getCityCenter(centerCity)
    mapInstance.centerAndZoom(new window.BMapGL.Point(center.lng, center.lat), 13)
    return
  }

  const container = document.getElementById('trip-bmap-container')
  if (!container) { console.warn('地图容器未就绪'); return }

  mapInstance = new window.BMapGL.Map('trip-bmap-container', { enableMapClick: false })
  const center = getCityCenter(centerCity)
  mapInstance.centerAndZoom(new window.BMapGL.Point(center.lng, center.lat), 13)
  mapInstance.enableScrollWheelZoom(true)
  mapInstance.enableContinuousZoom(true)
  mapInstance.enableInertialDragging(true)
  mapInstance.addControl(new window.BMapGL.NavigationControl3D())
  addCityLabelBaidu(centerCity, center)
  console.log('[地图] 百度地图 GL 初始化完成:', centerCity)
}

/**
 * Leaflet 地图初始化（免费 OpenStreetMap 瓦片）
 */
const initLeafletMap = (centerCity, L) => {
  // 如果已有 Leaflet 实例，直接移动中心
  if (mapInstance && mapProvider === 'leaflet') {
    const center = getCityCenter(centerCity)
    mapInstance.setView([center.lat, center.lng], 13)
    return
  }

  // 销毁旧地图实例
  if (mapInstance) {
    try { mapInstance.remove() } catch (e) {}
    mapInstance = null
  }

  const container = document.getElementById('trip-bmap-container')
  if (!container) { console.warn('地图容器未就绪'); return }

  mapInstance = L.map('trip-bmap-container', {
    center: [getCityCenter(centerCity).lat, getCityCenter(centerCity).lng],
    zoom: 13,
    zoomControl: true,
    attributionControl: false, // 隐藏 Leaflet 水印
  })

  // OpenStreetMap 瓦片（免费，无需 API Key）
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
  }).addTo(mapInstance)

  // 城市名称标签
  addCityLabelLeaflet(centerCity, L)
  console.log('[地图] Leaflet + OSM 初始化完成:', centerCity)
}

/**
 * 城市中心坐标映射（常见旅游城市）
 */
const getCityCenter = (city) => {
  const map = {
    '北京': { lat: 39.915, lng: 116.404 },
    '上海': { lat: 31.230, lng: 121.474 },
    '广州': { lat: 23.129, lng: 113.264 },
    '深圳': { lat: 22.543, lng: 114.058 },
    '杭州': { lat: 30.274, lng: 120.155 },
    '成都': { lat: 30.573, lng: 104.067 },
    '重庆': { lat: 29.563, lng: 106.552 },
    '西安': { lat: 34.342, lng: 108.940 },
    '南京': { lat: 32.060, lng: 118.797 },
    '武汉': { lat: 30.593, lng: 114.305 },
    '厦门': { lat: 24.480, lng: 118.089 },
    '三亚': { lat: 18.253, lng: 109.512 },
    '巴黎': { lat: 48.857, lng: 2.352 },
    '东京': { lat: 35.676, lng: 139.650 },
    '伦敦': { lat: 51.507, lng: -0.128 },
    '纽约': { lat: 40.713, lng: -74.006 },
  }
  const clean = city?.replace('市', '') || '北京'
  return map[clean] || { lat: 39.915, lng: 116.404 }
}

/**
 * 百度地图城市标签
 */
const addCityLabelBaidu = (city, center) => {
  if (!window.BMapGL || !mapInstance) return
  const label = new window.BMapGL.Label(city || '目的地', {
    position: new window.BMapGL.Point(center.lng, center.lat),
    offset: new window.BMapGL.Size(-40, -60),
  })
  label.setStyle({
    fontSize: '20px', fontWeight: '700', color: '#fff',
    backgroundColor: 'rgba(0,0,0,0.5)', border: 'none',
    borderRadius: '12px', padding: '8px 20px', letterSpacing: '2px',
  })
  mapInstance.addOverlay(label)
}

/**
 * Leaflet 地图城市标签
 */
const addCityLabelLeaflet = (city, L) => {
  if (!mapInstance) return
  const center = getCityCenter(city)
  const icon = L.divIcon({
    className: 'city-label-marker',
    html: `<div style="font-size:18px;font-weight:700;color:#fff;background:rgba(0,0,0,0.5);border-radius:12px;padding:6px 18px;white-space:nowrap;letter-spacing:2px;text-shadow:0 1px 3px rgba(0,0,0,0.3)">${city || '目的地'}</div>`,
    iconSize: [120, 36],
    iconAnchor: [60, 50],
  })
  L.marker([center.lat, center.lng], { icon, interactive: false }).addTo(mapInstance)
}

/**
 * 加载并渲染地图标记（地标 + 地铁站）
 */
const loadMapMarkers = async (city) => {
  if (!mapInstance || !city) return

  // 清除旧标记
  clearMapMarkers()

  try {
    // 获取地标数据
    const landmarksResult = await tripNewApi.getMapMarkers(city)
    if (landmarksResult.code === 0 && landmarksResult.data) {
      const landmarks = landmarksResult.data
      landmarks.forEach((item) => {
        addMarker(item.latitude, item.longitude, item.name, item.type)
      })
    }

    // 获取地铁站数据
    const metroResult = await tripNewApi.getMetroStations(city)
    if (metroResult.code === 0 && metroResult.data) {
      const stations = metroResult.data
      stations.forEach((item) => {
        addMarker(item.latitude, item.longitude, item.name, 'metro')
      })
    }

    // 存储标记数据供后续使用
    store.state.mapMarkers = [
      ...(landmarksResult?.data || []),
      ...(metroResult?.data || []),
    ]

    console.log(`[地图] 已加载 ${mapMarkers.length} 个地图标记`)
  } catch (e) {
    console.error('[地图] 加载标记失败:', e)
  }
}

/**
 * 在地图上添加标记点（兼容百度地图和 Leaflet）
 */
const addMarker = (lat, lng, name, type = 'attraction') => {
  if (!mapInstance) return

  const colors = { attraction: '#FF6B6B', metro: '#4ECDC4', landmark: '#FFD93D', hotel: '#8B5CF6' }
  const color = colors[type] || '#8B5CF6'
  const size = type === 'metro' ? 6 : 10

  if (mapProvider === 'baidu') {
    addMarkerBaidu(lat, lng, name, color, size)
  } else if (mapProvider === 'leaflet') {
    const L = window.L
    if (!L) return
    addMarkerLeaflet(lat, lng, name, color, size, L)
  }
}

/** 百度地图标记 */
const addMarkerBaidu = (lat, lng, name, color, size) => {
  if (!window.BMapGL) return
  const point = new window.BMapGL.Point(lng, lat)
  const canvas = document.createElement('canvas')
  canvas.width = size * 2 + 4; canvas.height = size * 2 + 4
  const ctx = canvas.getContext('2d')
  ctx.beginPath(); ctx.arc(size + 2, size + 2, size, 0, 2 * Math.PI)
  ctx.fillStyle = color; ctx.fill()
  ctx.strokeStyle = '#fff'; ctx.lineWidth = 2; ctx.stroke()
  const marker = new window.BMapGL.Marker(point, { icon: canvas })
  mapInstance.addOverlay(marker)
  // 名称标签
  const label = new window.BMapGL.Label(name, {
    position: point, offset: new window.BMapGL.Size(-name.length * 5, 16),
  })
  label.setStyle({ fontSize: '11px', color: '#333', backgroundColor: 'rgba(255,255,255,0.9)', border: 'none', borderRadius: '4px', padding: '2px 6px' })
  mapInstance.addOverlay(label)
  mapMarkers.push({ marker, label, provider: 'baidu' })
}

/** Leaflet 地图标记 */
const addMarkerLeaflet = (lat, lng, name, color, size, L) => {
  const circleMarker = L.circleMarker([lat, lng], {
    radius: size, fillColor: color, color: '#fff', weight: 2,
    fillOpacity: 0.9,
  }).addTo(mapInstance)
  circleMarker.bindTooltip(name, {
    permanent: true, direction: 'top',
    className: 'leaflet-marker-label',
    offset: [0, -size - 4],
  })
  mapMarkers.push({ marker: circleMarker, provider: 'leaflet' })
}

/**
 * 清除所有地图标记
 */
const clearMapMarkers = () => {
  if (!mapInstance) return
  mapMarkers.forEach(({ marker, label, provider }) => {
    if (provider === 'baidu' || label) {
      try { mapInstance.removeOverlay(marker) } catch (e) {}
      if (label) try { mapInstance.removeOverlay(label) } catch (e) {}
    } else if (provider === 'leaflet') {
      try { mapInstance.removeLayer(marker) } catch (e) {}
    }
  })
  mapMarkers = []
}

/* ==================== 蒙版透明度：联动抽屉高度 ==================== */
const overlayOpacity = computed(() => {
  const state = store.state.drawerState
  const stateMap = { min: 0.15, mid: 0.35, max: 0.55 }
  return stateMap[state] || 0.35
})

/* ==================== 行程生成逻辑 ==================== */
let abortSSE = null

/**
 * 启动 AI 行程生成流程
 * 1. 初始化地图（后台加载，不阻塞）
 * 2. POST /trip/generate 获取 taskId
 * 3. GET /trip/progress/{taskId} SSE 订阅进度
 */
const startGeneration = async () => {
  store.resetState()
  store.state.phase = 'generating'
  store.state.drawerState = 'mid'

  const params = {
    destination: route.query.destination || '北京',
    days: parseInt(route.query.days) || 3,
    people: parseInt(route.query.people) || 2,
    budget: parseInt(route.query.budget) || 5000,
    origin: route.query.origin || '',
    // 用户偏好（来自 AITripPlanner）
    companion: route.query.companion || '',
    styles: route.query.styles || '',
    hotel: route.query.hotel || '',
    pace: route.query.pace || '',
    schedule: route.query.schedule || '',
  }
  store.state.params = params

  // 初始化地图（与生成并行，不阻塞）
  initMap(params.destination)
  loadMapMarkers(params.destination)

  // 构建初始 planData 骨架
  store.state.planData = {
    destination: params.destination,
    days: params.days,
    people: params.people,
    totalBudget: params.budget,
    dayPlans: [],
    overview: '',
  }
  store.state.costBreakdown = null
  store.state.hotelList = []

  // 单端点 SSE 调用：POST → SSE流 → 实时进度 + 分模块内容
  abortSSE = tripNewApi.generateAndStream(params, {
    onProgress: (data) => {
      store.state.progress = data.progress || 0
      store.state.currentStep = data.stepName || data.summary || ''
      if (data.allStepList) {
        store.state.stepList = data.allStepList.map(s => ({
          name: s.name, progress: s.progress, status: s.status === 'doing' ? 'active' : s.status,
        }))
      }
      if (data.taskId && !store.state.taskId) store.state.taskId = data.taskId
      // 捕获预览数据
      if (data.previewData) {
        if (data.previewData.spots) store.state._spotsPreview = data.previewData.spots
        if (data.previewData.hotels || data.previewData.count) store.state._hotelsPreview = data.previewData
        if (data.previewData.tips) store.state._tipsPreview = data.previewData.tips
      }
      // 进度达100% → 立即切换完成态，后续内容流式填充
      if (data.progress >= 100 || data.finish) {
        store.state.phase = 'completed'
        store.state.drawerState = 'mid'
      }
      console.log(`[进度] ${data.progress}% - ${data.stepName || data.summary}`)
    },
    onComplete: (data) => {
      store.state.progress = 100
      store.state.phase = 'completed'
      store.state.drawerState = 'mid'
      // 保留流式积累的 dayPlans/tips，仅补全缺失字段
      if (!store.state.planData) {
        store.state.planData = { destination: params.destination, days: params.days, people: params.people, dayPlans: [], overview: '' }
      }
      store.state.planData.destination = data.destination || store.state.planData.destination || params.destination
      store.state.planData.days = data.days || store.state.planData.days || params.days
      store.state.planData.people = store.state.planData.people || params.people
      store.state.planData.totalBudget = store.state.planData.totalBudget || params.budget
      store.state.planData.overview = store.state.planData.overview || ('AI 智能生成' + (data.destination || params.destination) + (data.days || params.days) + '天深度行程')
      // 仅在 costBreakdown 为空时设默认值
      if (!store.state.costBreakdown) {
        const b = params.budget
        store.state.costBreakdown = { hotelCost: Math.round(b*0.35), ticketCost: Math.round(b*0.25), foodCost: Math.round(b*0.25), transportCost: Math.round(b*0.15), totalCost: b }
      }
      showSuccessToast('行程已生成')
    },
    onError: (msg) => {
      console.error('[生成] 失败:', msg)
      showToast(msg || '生成失败，请重试')
      store.state.phase = 'completed' // 退出生成中状态
      store.state.currentStep = '生成失败'
    },
    onStop: () => {
      showToast('已停止')
      store.resetState()
    },

    // ===== 实时内容流式渲染 =====
    onTextUpdate: (data) => {
      if (!store.state.planData) {
        store.state.planData = { destination: params.destination, days: params.days, dayPlans: [], overview: '', content: '' }
      }
      if (!store.state.planData.content) store.state.planData.content = ''
      store.state.planData.content += data.text || ''
    },

    onDayUpdate: (data) => {
      if (!store.state.planData) {
        store.state.planData = { destination: params.destination, days: params.days, dayPlans: [], overview: '' }
      }
      // 避免重复添加同一天
      const existing = store.state.planData.dayPlans.find(d => d.day === data.day)
      if (!existing) {
        store.state.planData.dayPlans.push({
          day: data.day,
          dayTitle: data.dayTitle || ('第' + data.day + '天'),
          timeSlots: data.timeSlots || [],
        })
        // 按天排序
        store.state.planData.dayPlans.sort((a, b) => a.day - b.day)
      }
      console.log('[行程] Day' + data.day + '/' + data.totalDays + ':', data.dayTitle)
    },

    onTransportUpdate: (data) => {
      if (data.transport && store.state.planData) {
        store.state.planData.transport = data.transport
        console.log('[交通]', data.transport.departType, '→', data.transport.returnType)
      }
    },

    onHotelUpdate: (data) => {
      if (data.hotel) {
        store.state.hotelList.push(data.hotel)
        console.log('[酒店]', data.hotel.name, '¥' + data.hotel.pricePerNight + '/晚')
      }
    },

    onTipsUpdate: (data) => {
      if (data.tips && store.state.planData) {
        store.state.planData.tips = data.tips
        console.log('[贴士]', data.tips.length + '条')
      }
    },

    onCostUpdate: (data) => {
      store.state.costBreakdown = {
        hotelCost: data.hotelCost || 0,
        ticketCost: data.ticketCost || 0,
        foodCost: data.foodCost || 0,
        transportCost: data.transportCost || 0,
        totalCost: data.totalCost || params.budget,
      }
      console.log('[费用] 总计¥' + (data.totalCost || params.budget))
    },
  })
}

/**
 * 停止生成
 */
const handleStopGeneration = async () => {
  if (store.state.taskId) {
    await tripNewApi.stopTrip(store.state.taskId)
  }
  if (abortSSE) {
    abortSSE()
    abortSSE = null
  }
  store.resetState()
  showToast('已停止生成')
}

/**
 * 语音识别结果回调
 */
const onVoiceResult = (text) => {
  if (!text) return
  // 如果有已完成的行程，发送到 AI 对话
  console.log('[语音] 识别结果:', text)
  showToast('语音识别: ' + text)
}

/* ==================== 操作按钮逻辑 ==================== */

const handleShare = () => {
  if (!store.state.planData) {
    showToast('暂无行程可分享')
    return
  }
  // 复制行程摘要到剪贴板
  const dest = store.state.params.destination
  const days = store.state.params.days
  const cost = store.state.costBreakdown?.totalCost || '--'
  const text = `【${dest}${days}天旅行规划】预估总价 ¥${cost}\n来自 AI 智能旅游助手`
  navigator.clipboard?.writeText(text).then(() => {
    showToast('行程摘要已复制，去分享给好友吧！')
  }).catch(() => showToast('分享失败'))
}

const handleLike = () => {
  showSuccessToast('感谢你的喜欢！')
}

const handleDislike = () => {
  showToast('感谢反馈，我们会继续优化')
}

const handleSavePlan = async () => {
  if (!store.state.planData) {
    showToast('暂无行程可保存')
    return
  }
  try {
    // 复用已有 planApi 保存逻辑
    const { planApi } = await import('../api/index.js')
    const result = await planApi.savePlan({
      destination: store.state.params.destination,
      days: store.state.params.days,
      budget: store.state.params.budget,
      people: store.state.params.people,
      planData: store.state.planData,
      source: 'trip',
    })
    if (result.code === 0) {
      showSuccessToast('行程已保存')
    } else {
      showToast(result.message || '保存失败')
    }
  } catch (e) {
    console.error('保存失败:', e)
    showToast('保存失败，请重试')
  }
}

/**
 * 抽屉状态变化回调 — 联动地图蒙版
 */
const onDrawerStateChange = (newState) => {
  console.log('[抽屉] 状态切换:', newState)
}

/**
 * 加载已保存的行程规划（不触发AI生成）
 */
const loadSavedPlan = async (planId) => {
  try {
    const { planApi } = await import('../api/index.js')
    const result = await planApi.getPlanById(planId)
    if (result.code === 0 && result.data) {
      const data = result.data
      store.state.params = {
        destination: data.destination || '北京',
        days: data.days || 3,
        people: data.people || 2,
        budget: data.budget || 5000,
      }
      store.state.planData = data.planData || data.planJson
      if (typeof store.state.planData === 'string') {
        try { store.state.planData = JSON.parse(store.state.planData) } catch (e) {}
      }
      // 如果是 Markdown 文本格式
      if (data.planJson && typeof data.planJson === 'string' && data.planJson.startsWith('##')) {
        store.state.planData = { destination: data.destination, days: data.days, content: data.planJson }
      }
      store.state.phase = 'completed'
      store.state.drawerState = 'mid'
      showSuccessToast('已加载保存的行程')
      initMap(data.destination || '北京')
      loadMapMarkers(data.destination || '北京')
    } else {
      showToast('加载失败，将重新生成')
      startGeneration()
    }
  } catch (e) {
    console.error('加载保存规划失败:', e)
    startGeneration()
  }
}

const goBack = () => {
  // 清理 SSE 连接（abortSSE内部会调用stopTrip）
  if (abortSSE) {
    abortSSE()
    abortSSE = null
  }
  // 地图实例不销毁，保留在内存中加速下次访问
  router.back()
}

const onUnmountedCleanup = () => {
  if (abortSSE) {
    abortSSE()
    abortSSE = null
  }
}

/* ==================== 生命周期 ==================== */
onMounted(async () => {
  const savedPlanId = route.query.savedPlanId
  if (savedPlanId) {
    // 加载已保存的规划，不重新生成
    await loadSavedPlan(savedPlanId)
  } else {
    startGeneration()
  }
})

onUnmounted(() => {
  if (abortSSE) {
    abortSSE()
    abortSSE = null
  }
  store.resetState()
})
</script>

<style scoped>
/* ==================== 页面容器 ==================== */
.trip-map-page {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  /* 使用 100dvh 适配移动端动态视口 */
  height: 100dvh;
  overflow: hidden;
  background: #000;
}

/* ==================== z1: 全屏地图容器 ==================== */
.map-container {
  position: absolute;
  inset: 0;
  z-index: 10;
  width: 100%;
  height: 100%;
}

.bmap-inner {
  width: 100%;
  height: 100%;
}

/* 百度地图内部元素样式覆盖 */
.bmap-inner :deep(.BMap_mask) {
  background: transparent !important;
}

/* ==================== z2: 地图半透明遮罩层 ==================== */
.map-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  background: rgba(0, 0, 0, 0.7);
  pointer-events: none; /* 不阻挡地图交互 */
  transition: opacity 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: opacity;
}

/* ==================== z4: 顶部状态栏 ==================== */
.top-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  padding-top: calc(12px + env(safe-area-inset-top, 0px));
  background: linear-gradient(180deg, rgba(0,0,0,0.4) 0%, rgba(0,0,0,0.1) 80%, transparent 100%);
}

.top-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.2s ease, background 0.2s ease;
}

.top-btn:active {
  transform: scale(0.92);
  background: rgba(0, 0, 0, 0.5);
}

.top-title {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  text-shadow: 0 1px 4px rgba(0,0,0,0.5);
  letter-spacing: 0.5px;
}

/* Leaflet 地图标记标签样式 */
:deep(.leaflet-marker-label) {
  background: rgba(255,255,255,0.92) !important;
  border: none !important;
  border-radius: 4px !important;
  padding: 1px 6px !important;
  font-size: 11px !important;
  color: #333 !important;
  font-weight: 500 !important;
  box-shadow: 0 1px 4px rgba(0,0,0,0.12) !important;
  white-space: nowrap !important;
}

/* Leaflet 弹出层圆角 */
:deep(.leaflet-tooltip) {
  border-radius: 4px !important;
}

/* 修正 Leaflet 容器内 z-index */
:deep(.leaflet-pane) { z-index: 1 !important; }
:deep(.leaflet-control) { z-index: 2 !important; }

/* Leaflet 城市名称标签 */
:deep(.city-label-marker) {
  background: transparent !important;
  border: none !important;
}
</style>
