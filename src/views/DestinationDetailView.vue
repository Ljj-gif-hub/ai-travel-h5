<template>
  <div class="detail-page">
    <div class="page-container">
      <van-nav-bar
        :title="city"
        left-text="返回"
        left-arrow
        safe-area-inset-top
        class="nav-bar"
        @click-left="goBack"
      />
      <div class="page-content">
        <div class="city-card">
          <div class="city-img-wrap">
            <img :src="getSafeImageUrl(cityImage)" :alt="city" class="city-img" @error="handleImageError('city')" />
            <div class="city-img-mask"></div>
            <div class="city-info-overlay">
              <h1 class="city-name">{{ sanitizeText(city) }}</h1>
              <span class="city-province" v-if="cityInfo.province">{{ sanitizeText(cityInfo.province) }}</span>
            </div>
          </div>
          <div class="city-desc" v-if="cityInfo.description">{{ sanitizeText(cityInfo.description) }}</div>
        </div>

        <div class="map-section">
          <div class="section-header">
            <van-icon name="location-o" size="18" color="#9333ea" />
            <span class="section-title">城市地图</span>
          </div>
          <div id="map-container" class="map-container"></div>
          <div v-if="mapError" class="map-fallback">
            <van-icon name="warning-o" size="20" color="#f59e0b" />
            <span>地图加载失败，请检查网络</span>
          </div>
        </div>

        <div class="attractions-section">
          <div class="section-header">
            <div class="section-icon-wrapper">
              <van-icon name="guide-o" size="18" color="#fff" />
            </div>
            <span class="section-title">热门景点</span>
            <span class="section-count" v-if="attractions.length">{{ attractions.length }}个</span>
          </div>

          <div v-if="isLoadingAttractions" class="loading-state">
            <van-loading size="28px" color="#9333ea">加载中...</van-loading>
          </div>

          <div v-else-if="attractions.length === 0" class="empty-state">
            <van-icon name="bookmark-o" size="40" color="#d0d0d0" />
            <p class="empty-title">暂无景点数据</p>
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
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NavBar, Rate, Loading, Icon } from 'vant'
import { getHotDestinations, getCityAttractions } from '../api/destination'
import { sanitizeHtml, getProxyImageUrl } from '../utils/security'

const route = useRoute()
const router = useRouter()

const IMAGE_API = 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image'

const getImageUrl = (keyword) => {
  const encodedKeyword = encodeURIComponent(`${keyword} 风景 旅游摄影`)
  return `${IMAGE_API}?prompt=${encodedKeyword}&image_size=landscape_4_3`
}

/*
 * 【修复】无city参数时自动跳转热门目的地列表，避免空白页
 * 根因：直接访问 /destination-detail 无 query 时 city 为空，页面完全空白
 */
const city = ref(route.query.city || '')
const cityInfo = ref({})
const cityImage = ref('')
const attractions = ref([])
const isLoadingAttractions = ref(false)
const mapError = ref(false)
const expandedIds = ref([])
let mapInstance = null

// 【修复】参数校验：无城市名时自动跳转目的地列表
if (!city.value || !city.value.trim()) {
  router.replace('/destinations')
}

const goBack = () => {
  try {
    if (window.history.length <= 1) { router.push('/') }
    else { router.back() }
  } catch (e) { router.push('/') }
}

// 初始化城市图片（需在city有值后才设置）
cityImage.value = city.value ? getImageUrl(city.value) : ''

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
  if (type === 'city') {
    cityImage.value = '/images/default-placeholder.png'
  }
}

const loadCityInfo = async () => {
  try {
    const res = await getHotDestinations()
    const found = (res.data || []).find(d => d.name === city.value)
    if (found) {
      cityInfo.value = found
      if (found.imageUrl) {
        cityImage.value = found.imageUrl
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

const loadBaiduMapScript = () => {
  return new Promise((resolve, reject) => {
    if (window.BMapGL) {
      resolve(window.BMapGL)
      return
    }
    if (window._baiduMapLoading) {
      const wait = setInterval(() => {
        if (window.BMapGL) {
          clearInterval(wait)
          resolve(window.BMapGL)
        }
      }, 100)
      return
    }
    window._baiduMapLoading = true
    window._baiduMapInit = () => {
      window._baiduMapLoading = false
      resolve(window.BMapGL)
    }
    const script = document.createElement('script')
    script.src = '/api/map/script'
    script.onerror = () => {
      window._baiduMapLoading = false
      reject(new Error('百度地图脚本加载失败'))
    }
    document.body.appendChild(script)
  })
}

const initMap = async () => {
  try {
    const BMapGL = await loadBaiduMapScript()
    const el = document.getElementById('map-container')
    if (!el) return

    mapInstance = new BMapGL.Map('map-container')

    let center
    if (cityInfo.value.lat && cityInfo.value.lng) {
      center = new BMapGL.Point(Number(cityInfo.value.lng), Number(cityInfo.value.lat))
    } else if (attractions.value.length && attractions.value[0].lat && attractions.value[0].lng) {
      center = new BMapGL.Point(Number(attractions.value[0].lng), Number(attractions.value[0].lat))
    } else {
      center = new BMapGL.Point(116.404, 39.915)
    }
    mapInstance.centerAndZoom(center, 12)
    mapInstance.enableScrollWheelZoom(true)

    attractions.value.forEach(attr => {
      if (attr.lat && attr.lng) {
        const point = new BMapGL.Point(Number(attr.lng), Number(attr.lat))
        const marker = new BMapGL.Marker(point)
        mapInstance.addOverlay(marker)
        const label = new BMapGL.Label(sanitizeText(attr.name), {
          offset: new BMapGL.Size(-20, -28),
        })
        label.setStyle({
          border: 'none',
          background: 'rgba(139, 92, 246, 0.9)',
          color: '#fff',
          padding: '2px 6px',
          borderRadius: '4px',
          fontSize: '11px',
        })
        marker.setLabel(label)
      }
    })
  } catch (e) {
    console.error('地图初始化失败:', e)
    mapError.value = true
  }
}

onMounted(async () => {
  await Promise.all([loadCityInfo(), loadAttractions()])
  await initMap()
})
</script>

<style scoped>
.detail-page {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(180deg, #faf5ff 0%, #f1f5f9 100%);
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
  background: #ffffff;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
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
  background: #ffffff;
  border-radius: 20px;
  padding: 16px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.05);
  margin-bottom: 16px;
}

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
  background: #ffffff;
  border-radius: 20px;
  padding: 16px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.05);
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
</style>
