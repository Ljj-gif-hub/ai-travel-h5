<template>
  <div class="detail-page">
    <div class="page-container">
      <van-nav-bar
        :title="city"
        :left-text="t('common.back')"
        left-arrow
        safe-area-inset-top
        class="nav-bar"
        @click-left="goBack"
      />
      <div class="page-content">
        <div class="city-card">
          <div class="city-img-wrap">
            <img :src="getSafeImageUrl(cityImage)" :alt="city" class="city-img" loading="lazy" @error="handleImageError('city')" />
            <div class="city-img-mask"></div>
            <div class="city-info-overlay">
              <h1 class="city-name">{{ sanitizeText(city) }}</h1>
              <span class="city-province" v-if="cityInfo.province">{{ sanitizeText(cityInfo.province) }}</span>
            </div>
          </div>
          <div class="city-desc" v-if="cityInfo.description">{{ sanitizeText(cityInfo.description) }}</div>
        </div>

        <!-- 目的地天气（新功能） -->
        <div class="weather-section">
          <div class="section-header">
            <van-icon name="cloud-o" size="18" color="#9333ea" />
            <span class="section-title">{{ t('destination.weather') }}</span>
          </div>
          <div v-if="weatherLoading" class="weather-loading"><van-loading size="20" color="#9333ea" /><span>{{ t('common.loading') }}</span></div>
          <div v-else-if="weatherFailed" class="weather-failed"><van-icon name="warning-o" size="16" color="#f59e0b" /><span>{{ t('destination.weatherUnavailable') }}</span></div>
          <template v-else-if="weather">
            <div class="weather-now">
              <div class="weather-now-left">
                <div class="weather-temp">{{ weather.temperature }}<span class="weather-unit">°</span></div>
                <div class="weather-cond">{{ weather.weather }}</div>
              </div>
              <div class="weather-now-right">
                <div class="weather-detail" v-if="weather.humidity">{{ t('destination.weatherHumidity') }} {{ weather.humidity }}%</div>
                <div class="weather-detail" v-if="weather.windDirection">{{ t('destination.weatherWind', { dir: weather.windDirection, power: weather.windPower || '-' }) }}</div>
                <div class="weather-detail" v-if="weather.reportTime">{{ t('destination.weatherUpdated', { time: weather.reportTime }) }}</div>
              </div>
            </div>
            <div v-if="weatherForecast.length" class="weather-forecast">
              <div class="weather-forecast-title">{{ t('destination.weatherForecast') }}</div>
              <div class="weather-forecast-row">
                <div v-for="(day, i) in weatherForecast" :key="i" class="weather-day">
                  <div class="weather-day-date">{{ fmtWeatherDate(day.date) }}</div>
                  <div class="weather-day-cond">{{ day.dayWeather }}</div>
                  <div class="weather-day-temp">{{ day.dayTemp }}°<span v-if="day.nightTemp"> / {{ day.nightTemp }}°</span></div>
                </div>
              </div>
            </div>
          </template>
        </div>

        <div class="map-section">
          <div class="section-header">
            <van-icon name="location-o" size="18" color="#9333ea" />
            <span class="section-title">{{ t('destination.cityMap') }}</span>
          </div>
          <div id="map-container" class="map-container"></div>
          <div v-if="mapError" class="map-fallback">
            <van-icon name="warning-o" size="20" color="#f59e0b" />
            <span>{{ t('destination.mapError') }}</span>
          </div>
        </div>

        <div class="attractions-section">
          <div class="section-header">
            <div class="section-icon-wrapper">
              <van-icon name="guide-o" size="18" color="#fff" />
            </div>
            <span class="section-title">{{ t('destination.hotAttractions') }}</span>
            <span class="section-count" v-if="attractions.length">{{ attractions.length }}{{ t('destination.countSuffix') }}</span>
          </div>

          <div v-if="isLoadingAttractions" class="loading-state">
            <van-loading size="28px" color="#9333ea">{{ t('common.loading') }}</van-loading>
          </div>

          <div v-else-if="attractions.length === 0" class="empty-state">
            <van-icon name="bookmark-o" size="40" color="#d0d0d0" />
            <p class="empty-title">{{ t('destination.noAttractions') }}</p>
          </div>

          <div v-else class="attractions-list">
            <div
              v-for="(attr, index) in attractions"
              :key="index"
              class="attraction-card"
              :class="{ expanded: isExpanded(index) }"
              @click="toggleExpand(index)"
            >
              <div class="attraction-header">
                <div class="attraction-index">{{ index + 1 }}</div>
                <div class="attraction-main">
                  <div class="attraction-name">{{ sanitizeText(attr.name) }}</div>
                  <div class="attraction-address" v-if="attr.address">
                    <van-icon name="location-o" size="12" color="#9ca3af" />
                    <span>{{ sanitizeText(attr.address) }}</span>
                  </div>
                  <div class="attraction-rating" v-if="attr.rating">
                    <van-rate
                      :model-value="Number(attr.rating) || 0"
                      readonly
                      size="12"
                      color="#fbbf24"
                      void-color="#e5e7eb"
                    />
                    <span class="rating-num">{{ Number(attr.rating).toFixed(1) }}</span>
                  </div>
                </div>
                <van-icon
                  :name="isExpanded(index) ? 'arrow-up' : 'arrow-down'"
                  size="14"
                  color="#a0aec0"
                  class="expand-icon"
                />
              </div>
              <div v-if="isExpanded(index) && attr.description" class="attraction-detail">
                {{ sanitizeText(attr.description) }}
              </div>
              <div v-else-if="attr.description" class="attraction-detail collapsed">
                {{ sanitizeText(attr.description) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NavBar, Rate, Loading, Icon } from 'vant'
import { getHotDestinations, getCityAttractions } from '../api/destination'
import { weatherApi } from '../api'
import { sanitizeHtml, getProxyImageUrl } from '../utils/security'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const { t } = useI18n()
const router = useRouter()

const staticImageMap = ref({})

const loadStaticImageMap = async () => {
  try {
    const resp = await fetch('/city-images.json')
    if (resp.ok) Object.assign(staticImageMap.value, await resp.json())
  } catch {}
}

/*
 * 【修复】无city参数时自动跳转热门目的地列表，避免空白页
 * 根因：直接访问 /destination-detail 无 query 时 city 为空，页面完全空白
 */
const city = ref(route.query.city || '')
// 行程页景点卡传入的景点推荐图（覆盖城市封面，优先级最高）；无则用城市图
const heroImg = route.query.img || ''
const cityInfo = ref({})
const cityImageOverride = ref(heroImg) // 后端素材库/热门目的地接口返回的封面图（优先级最高）
const attractions = ref([])
const isLoadingAttractions = ref(false)
const mapError = ref(false)
const expandedIds = ref([])
let mapInstance = null

// BUGID L-DEST-1 修复：无城市名时跳转目的地列表。
// 注意：`<script setup>` 顶层不能用 `return`（编译器报错），
// 故用 hasCity 标志短路后续 onMounted 副作用（见 onMounted 开头判空返回）
const hasCity = !!(city.value && city.value.trim())
if (!hasCity) {
  router.replace('/destinations')
}

// BUGID FEAT-7 修复：封面图改为 computed——staticImageMap 异步加载完成会自动重新求值，
// 不再同步求值拿到空 map 后直连外部 AI 生图接口；无图时回退本地占位图
const cityImage = computed(() => {
  if (cityImageOverride.value) return cityImageOverride.value
  if (staticImageMap.value[city.value]) return staticImageMap.value[city.value]
  // 兼容带"市"后缀名（行程页逆地理返回 三亚市），本地图库键是短名（三亚）
  const short = city.value.replace(/市$/, '')
  if (short !== city.value && staticImageMap.value[short]) return staticImageMap.value[short]
  return '/images/default-placeholder.png'
})

const goBack = () => {
  try {
    if (window.history.length <= 1) { router.push('/') }
    else { router.back() }
  } catch (e) { router.push('/') }
}

const isExpanded = (index) => expandedIds.value.includes(index)

const toggleExpand = (index) => {
  const i = expandedIds.value.indexOf(index)
  if (i >= 0) {
    expandedIds.value.splice(i, 1)
  } else {
    expandedIds.value.push(index)
  }
}

const sanitizeText = (text) => {
  return sanitizeHtml(text || '')
}

const getSafeImageUrl = (url) => {
  return getProxyImageUrl(url)
}

const handleImageError = (type) => {
  // BUGID FEAT-7 修复：cityImage 已为 computed，改由覆盖源兜底占位图
  if (type === 'city') {
    cityImageOverride.value = '/images/default-placeholder.png'
  }
}

const loadCityInfo = async () => {
  try {
    const res = await getHotDestinations()
    const found = (res.data || []).find(d => d.name === city.value || d.name === city.value.replace(/市$/, ''))
    if (found) {
      cityInfo.value = found
      // 已传入景点推荐图(img参数)时优先用它，不被城市封面覆盖
      if (found.imageUrl && !heroImg) {
        cityImageOverride.value = found.imageUrl
      }
    }
  } catch (e) {
    console.error('获取城市信息失败:', e)
  }
}

const loadAttractions = async () => {
  isLoadingAttractions.value = true
  try {
    const res = await getCityAttractions(city.value)
    attractions.value = res.data || []
  } catch (e) {
    console.error('获取景点列表失败:', e)
    attractions.value = []
  } finally {
    isLoadingAttractions.value = false
  }
}

let mapProvider = null // 'amap' | 'baidu' | 'leaflet'

/** 从后端获取当前地图提供商 */
const fetchMapProvider = async () => {
  try {
    const resp = await fetch('/api/map/config')
    const json = await resp.json()
    if (json.code === 0 && json.data) return json.data.provider || 'amap'
  } catch (e) { /* ignore */ }
  return 'amap'
}

/** 加载地图 SDK（优先高德，其次百度，兜底 Leaflet） */
const loadMapSDK = async (provider) => {
  // 尝试高德
  if (provider === 'amap') {
    const ok = await loadAmap()
    if (ok) { mapProvider = 'amap'; return true }
  }
  // 尝试百度
  if (provider === 'baidu' || provider === 'amap') {
    const ok = await loadBaidu()
    if (ok) { mapProvider = 'baidu'; return true }
  }
  // 兜底 Leaflet
  const ok = await loadLeafletSDK()
  if (ok) { mapProvider = 'leaflet'; return true }
  return false
}

const pendingIntervals = []
onUnmounted(() => {
  pendingIntervals.forEach(clearInterval); pendingIntervals.length = 0
  // 销毁地图实例释放内存（AMap/BMapGL 用 destroy，Leaflet 用 remove），
  // 否则每次进入本页泄漏几十 MB，累积导致标签页 out of memory
  try {
    if (mapInstance && typeof mapInstance.destroy === 'function') mapInstance.destroy()
    else if (mapInstance && typeof mapInstance.remove === 'function') mapInstance.remove()
  } catch {}
  mapInstance = null
})

const loadAmap = () => new Promise(resolve => {
  if (window.AMap) { resolve(true); return }
  const script = document.createElement('script')
  script.src = '/api/map/script'
  const timeout = setTimeout(() => resolve(false), 5000)
  script.onload = () => {
    clearTimeout(timeout)
    let retries = 0
    const check = setInterval(() => {
      if (window.AMap) { clearInterval(check); resolve(true) }
      else if (retries++ > 30) { clearInterval(check); resolve(false) }
    }, 150)
    pendingIntervals.push(check)
  }
  script.onerror = () => { clearTimeout(timeout); resolve(false) }
  document.body.appendChild(script)
})

const loadBaidu = () => new Promise(resolve => {
  if (window.BMapGL) { resolve(true); return }
  // 通过后端代理 /api/map/script 加载百度地图 SDK（服务端带 AK，不暴露到前端）
  const script = document.createElement('script')
  script.src = '/api/map/script'
  const timeout = setTimeout(() => resolve(false), 5000)
  script.onload = () => {
    clearTimeout(timeout)
    let retries = 0
    const check = setInterval(() => {
      if (window.BMapGL) { clearInterval(check); resolve(true) }
      else if (retries++ > 50) { clearInterval(check); resolve(false) }
    }, 100)
    pendingIntervals.push(check)
  }
  script.onerror = () => { clearTimeout(timeout); resolve(false) }
  document.body.appendChild(script)
})

const loadLeafletSDK = () => new Promise(resolve => {
  if (window.L) { resolve(true); return }
  const css = document.createElement('link')
  css.rel = 'stylesheet'; css.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'
  document.head.appendChild(css)
  const script = document.createElement('script')
  script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
  script.onload = () => resolve(true)
  script.onerror = () => resolve(false)
  document.body.appendChild(script)
})

/** 城市中心坐标 */
const getCenter = () => {
  if (cityInfo.value.lat && cityInfo.value.lng) {
    return { lat: Number(cityInfo.value.lat), lng: Number(cityInfo.value.lng) }
  }
  if (attractions.value.length && attractions.value[0].lat && attractions.value[0].lng) {
    return { lat: Number(attractions.value[0].lat), lng: Number(attractions.value[0].lng) }
  }
  return { lat: 39.915, lng: 116.404 }
}

const initMap = async () => {
  try {
    const provider = await fetchMapProvider()
    const loaded = await loadMapSDK(provider)
    if (!loaded) { mapError.value = true; return }

    const el = document.getElementById('map-container')
    if (!el) return

    const center = getCenter()

    if (mapProvider === 'amap') {
      mapInstance = new window.AMap.Map('map-container', {
        center: [center.lng, center.lat],
        zoom: 12,
        viewMode: '2D',
        resizeEnable: true,
      })
      attractions.value.forEach(attr => {
        if (!attr.lat || !attr.lng) return
        const marker = new window.AMap.Marker({
          position: [Number(attr.lng), Number(attr.lat)],
          title: sanitizeText(attr.name),
        })
        mapInstance.add(marker)
        const label = new window.AMap.Text({
          text: sanitizeText(attr.name),
          position: [Number(attr.lng), Number(attr.lat)],
          offset: [0, -24],
          style: {
            border: 'none', background: 'rgba(139,92,246,0.9)', color: '#fff',
            padding: '2px 6px', borderRadius: '4px', fontSize: '11px',
          },
        })
        mapInstance.add(label)
      })
    } else if (mapProvider === 'baidu') {
      mapInstance = new window.BMapGL.Map('map-container')
      mapInstance.centerAndZoom(new window.BMapGL.Point(center.lng, center.lat), 12)
      mapInstance.enableScrollWheelZoom(true)
      attractions.value.forEach(attr => {
        if (!attr.lat || !attr.lng) return
        const point = new window.BMapGL.Point(Number(attr.lng), Number(attr.lat))
        const marker = new window.BMapGL.Marker(point)
        mapInstance.addOverlay(marker)
        const label = new window.BMapGL.Label(sanitizeText(attr.name), {
          offset: new window.BMapGL.Size(-20, -28),
        })
        label.setStyle({
          border: 'none', background: 'rgba(139,92,246,0.9)', color: '#fff',
          padding: '2px 6px', borderRadius: '4px', fontSize: '11px',
        })
        marker.setLabel(label)
      })
    } else if (mapProvider === 'leaflet' && window.L) {
      const L = window.L
      mapInstance = L.map('map-container', {
        center: [center.lat, center.lng], zoom: 12,
        zoomControl: true, attributionControl: false,
      })
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(mapInstance)
      attractions.value.forEach(attr => {
        if (!attr.lat || !attr.lng) return
        const m = L.circleMarker([Number(attr.lat), Number(attr.lng)], {
          radius: 6, fillColor: '#8B5CF6', color: '#fff', weight: 2, fillOpacity: 0.9,
        }).addTo(mapInstance)
        m.bindTooltip(sanitizeText(attr.name), { permanent: true, direction: 'top', className: 'leaflet-label' })
      })
    }
  } catch (e) {
    console.error('地图初始化失败:', e)
    mapError.value = true
  }
}

onMounted(async () => {
  // BUGID L-DEST-1 修复：无 city 参数时直接返回，不再执行加载/地图/天气等副作用
  if (!hasCity) return
  await loadStaticImageMap()
  await Promise.all([loadCityInfo(), loadAttractions()])
  await initMap()
  loadWeather()
})

/* ==================== 目的地天气（新功能） ==================== */
const weather = ref(null)
const weatherLoading = ref(false)
const weatherFailed = ref(false)

const loadWeather = async () => {
  if (!city.value) return
  weatherLoading.value = true
  weatherFailed.value = false
  try {
    const res = await weatherApi.getWeather(city.value)
    // 空成功（城市名无效时后端返回 {forecast:[]}）也按失败降级，避免天气块空白
    if (res.code === 0 && res.data?.temperature) weather.value = res.data
    else { weather.value = null; weatherFailed.value = true }
  } catch (e) {
    // 高德 Key 未配置/服务失败时后端返回 502，此处静默降级展示提示
    weather.value = null
    weatherFailed.value = true
  } finally {
    weatherLoading.value = false
  }
}

/** 未来 2-3 天预报（跳过首条「今天」，当前天气已单独展示） */
const weatherForecast = computed(() => (weather.value?.forecast || []).slice(1, 4))

const fmtWeatherDate = (s) => {
  const p = String(s || '').split('-')
  return p.length >= 3 ? `${Number(p[1])}/${Number(p[2])}` : s
}
</script>

<style scoped>
.detail-page {
  width: 100%;
  min-height: 100vh;
  background: transparent;
  display: flex;
  flex-direction: column;
  padding-bottom: calc(var(--tabbar-height) + 20px + var(--safe-area-bottom));
  box-sizing: border-box;
}

.page-container {
  width: 100%;
  max-width: 480px;
  margin: 0 auto;
  box-sizing: border-box;
}

:deep(.nav-bar) {
  background: linear-gradient(135deg, rgba(233,213,255,0.9) 0%, rgba(240,249,255,0.9) 50%, rgba(253,244,255,0.9) 100%);
  backdrop-filter: blur(12px);
}
:deep(.nav-bar .van-nav-bar__title) {
  color: #1f2937;
  font-weight: 600;
}
:deep(.nav-bar .van-nav-bar__left) {
  color: #4b5563;
}

.page-content {
  flex: 1;
  padding: 16px;
}

.city-card {
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(16px) saturate(160%);
  -webkit-backdrop-filter: blur(16px) saturate(160%);
  border: 1px solid rgba(255,255,255,0.5);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.04);
  margin-bottom: 16px;
}

.city-img-wrap {
  position: relative;
  width: 100%;
  height: 180px;
  overflow: hidden;
}

.city-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.city-img-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0) 45%, rgba(0, 0, 0, 0.6) 100%);
}

.city-info-overlay {
  position: absolute;
  left: 18px;
  bottom: 14px;
  display: flex;
  flex-direction: column;
}

.city-name {
  font-size: 26px;
  font-weight: 700;
  color: #fff;
  margin: 0;
  text-shadow: 0 2px 6px rgba(0, 0, 0, 0.4);
  letter-spacing: -0.5px;
}

.city-province {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
  margin-top: 2px;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
}

.city-desc {
  padding: 14px 18px 18px;
  font-size: 14px;
  color: #4b5563;
  line-height: 1.6;
}

.map-section {
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(14px) saturate(160%);
  -webkit-backdrop-filter: blur(14px) saturate(160%);
  border: 1px solid rgba(255,255,255,0.5);
  border-radius: 20px;
  padding: 16px;
  box-shadow: 0 3px 14px rgba(0, 0, 0, 0.03);
  margin-bottom: 16px;
}

/* ==================== 目的地天气（新功能） ==================== */
.weather-section {
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(14px) saturate(160%);
  -webkit-backdrop-filter: blur(14px) saturate(160%);
  border: 1px solid rgba(255,255,255,0.5);
  border-radius: 20px;
  padding: 16px;
  box-shadow: 0 3px 14px rgba(0, 0, 0, 0.03);
  margin-bottom: 16px;
}
.weather-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 0;
  font-size: 13px;
  color: #9ca3af;
}
.weather-failed {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 20px 0;
  font-size: 13px;
  color: #92400e;
  background: #fef3c7;
  border-radius: 12px;
}
.weather-now {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 4px 10px;
}
.weather-temp {
  font-size: 34px;
  font-weight: 700;
  color: #1f2937;
  line-height: 1;
}
.weather-unit { font-size: 18px; font-weight: 500; color: #6b7280; }
.weather-cond { font-size: 14px; color: #4b5563; margin-top: 4px; }
.weather-now-right { display: flex; flex-direction: column; align-items: flex-end; gap: 3px; }
.weather-detail { font-size: 11px; color: #9ca3af; }
.weather-forecast { border-top: 1px dashed #e5e7eb; padding-top: 10px; }
.weather-forecast-title { font-size: 12px; font-weight: 600; color: #6b7280; margin-bottom: 8px; }
.weather-forecast-row { display: flex; gap: 8px; }
.weather-day {
  flex: 1;
  background: rgba(147, 51, 234, 0.05);
  border-radius: 12px;
  padding: 10px 6px;
  text-align: center;
}
.weather-day-date { font-size: 12px; font-weight: 600; color: #374151; }
.weather-day-cond { font-size: 12px; color: #6b7280; margin: 4px 0 2px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.weather-day-temp { font-size: 11px; color: #9ca3af; }

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.section-icon-wrapper {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #9333ea 0%, #8b5cf6 100%);
  border-radius: 8px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  flex: 1;
}

.section-count {
  font-size: 12px;
  color: #9ca3af;
}

.map-container {
  width: 100%;
  height: 200px;
  border-radius: 14px;
  overflow: hidden;
  background: #f3f4f6;
}

.map-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 200px;
  border-radius: 14px;
  background: #fef3c7;
  color: #92400e;
  font-size: 13px;
}

.attractions-section {
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(14px) saturate(160%);
  -webkit-backdrop-filter: blur(14px) saturate(160%);
  border: 1px solid rgba(255,255,255,0.5);
  border-radius: 20px;
  padding: 16px;
  box-shadow: 0 3px 14px rgba(0, 0, 0, 0.03);
}

.loading-state {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  text-align: center;
}

.empty-title {
  font-size: 14px;
  color: #999;
  margin: 12px 0 0;
}

.attractions-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.attraction-card {
  background: #f9fafb;
  border-radius: 14px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1.5px solid transparent;
}

.attraction-card:hover {
  background: #f5f3ff;
  border-color: rgba(147, 51, 234, 0.12);
}

.attraction-card.expanded {
  background: #f5f3ff;
  border-color: rgba(147, 51, 234, 0.2);
}

.attraction-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.attraction-index {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #9333ea 0%, #8b5cf6 100%);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  border-radius: 8px;
}

.attraction-main {
  flex: 1;
  min-width: 0;
}

.attraction-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.attraction-address {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.attraction-rating {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rating-num {
  font-size: 12px;
  color: #f59e0b;
  font-weight: 600;
}

.expand-icon {
  flex-shrink: 0;
  margin-top: 4px;
}

.attraction-detail {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e5e7eb;
}

.attraction-detail.collapsed {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 6px;
  padding-top: 0;
  border-top: none;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Leaflet 标记标签样式 */
:deep(.leaflet-label) {
  background: rgba(139, 92, 246, 0.9) !important;
  border: none !important;
  border-radius: 4px !important;
  padding: 2px 6px !important;
  font-size: 11px !important;
  color: #fff !important;
  font-weight: 500 !important;
  box-shadow: 0 1px 4px rgba(0,0,0,0.15) !important;
  white-space: nowrap !important;
}
</style>
