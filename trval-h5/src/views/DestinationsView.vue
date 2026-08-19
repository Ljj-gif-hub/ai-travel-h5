<template>
  <div class="destinations-page">
    <div class="page-container">
      <van-nav-bar
        :title="t('destination.hot')"
        :left-text="t('common.back')"
        left-arrow
        safe-area-inset-top
        class="nav-bar"
        @click-left="goBack"
      />
      <div class="page-content">
        <div class="page-title-area">
          <div class="page-title-icon">
            <van-icon name="fire-o" size="20" color="#fff" />
          </div>
          <div class="page-title-text">
            <h2 class="page-title">{{ isPersonalized ? t('destination.recommendedForYou') : t('destination.hot') }}</h2>
            <p class="page-subtitle">{{ isPersonalized ? t('destination.recommendSubtitle') : t('destination.hotSubtitle') }}</p>
          </div>
        </div>

        <div v-if="isLoading" class="loading-state">
          <van-loading size="32px" color="#9333ea">{{ t('common.loading') }}</van-loading>
        </div>

        <div v-else-if="destinations.length === 0" class="empty-state">
          <van-icon name="location-o" size="48" color="#d0d0d0" />
          <p class="empty-title">{{ t('destination.empty') }}</p>
        </div>

        <div v-else class="dest-grid">
          <div
            v-for="(dest, index) in destinations"
            :key="index"
            class="dest-card"
            @click="goToDetail(dest)"
          >
            <div class="dest-img-wrap">
              <img :src="dest.image" :alt="dest.name" class="dest-img" loading="lazy" decoding="async" @error="onImageError" />
              <div class="dest-img-mask"></div>
              <div class="dest-heat" v-if="dest.heat">
                <van-icon name="fire-o" size="12" color="#fff" />
                <span>{{ dest.heat }}</span>
              </div>
              <div class="dest-name-overlay">
                <span class="dest-name">{{ dest.name }}</span>
                <span class="dest-province" v-if="dest.province">{{ dest.province }}</span>
              </div>
            </div>
            <div class="dest-desc">{{ dest.description || t('destination.descFallback') }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Loading, Icon, showToast } from 'vant'
import { getHotDestinations } from '../api/destination'
import { getToken } from '../utils/auth'
import { recommendApi } from '../api/index'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const { t } = useI18n()

// BUGID FEAT-9 修复：移除第三方 AI 生图接口（trae-api-cn.mchost.guru），
// 直连外部接口会把目的地名拼进 prompt 外发（隐私泄露），且跨域/防盗链不稳定
const staticImageMap = ref({})

const loadStaticImageMap = async () => {
  try {
    const resp = await fetch('/city-images.json')
    if (resp.ok) Object.assign(staticImageMap.value, await resp.json())
  } catch {}
}

/** 主渠道：后端 API → 静态 JSON 兜底 → 本地占位图 */
const getImageUrl = (keyword) => {
  if (staticImageMap.value[keyword]) return staticImageMap.value[keyword]
  // BUGID FEAT-9 修复：兜底改为项目本地占位图，不再外发生成请求
  return '/images/default-placeholder.png'
}

// BUGID FEAT-9 修复：图片加载失败时切换本地占位图，避免整卡空白
const onImageError = (e) => {
  const img = e.target
  img.onerror = null // 防止占位图也失败时无限重试
  img.src = '/images/default-placeholder.png'
}

const destinations = ref([])
const isLoading = ref(false)
const isPersonalized = ref(false)

const goBack = () => {
  try {
    if (window.history.length <= 1) { router.push('/') }
    else { router.back() }
  } catch (e) { router.push('/') }
}

/*
 * 【修复】goToDetail 增加参数校验和异常捕获
 * 根因：dest为null/undefined时 dest.name 抛出 TypeError，点击卡片无响应
 */
const goToDetail = (dest) => {
  try {
    if (!dest || !dest.name) { showToast(t('destination.infoError')); return }
    router.push({ path: '/destination-detail', query: { city: dest.name } })
  } catch (e) { console.error('goToDetail 失败:', e); showToast(t('destination.jumpFailed')) }
}

const loadDestinations = async () => {
  isLoading.value = true
  try {
    // 已登录：优先个性化推荐（根据收藏/游记/行程画像），失败则回退热门
    if (getToken()) {
      try {
        const rec = await recommendApi.getRecommendDestinations(10)
        const list = (rec && rec.data) || []
        if (list.length > 0) {
          isPersonalized.value = true
          destinations.value = list.map(item => ({
            name: item.name,
            image: item.cover || getImageUrl(item.name),
            description: item.reason || t('destination.recommendDescFallback'),
          }))
          return
        }
      } catch (e) {
        console.warn('个性化推荐加载失败，回退热门目的地:', e)
      }
    }
    const res = await getHotDestinations()
    const list = res.data || []
    isPersonalized.value = false
    destinations.value = list.map(item => ({
      ...item,
      image: item.imageUrl || getImageUrl(item.name),
    }))
  } catch (e) {
    console.error('获取热门目的地失败:', e)
    showToast(t('common.requestFailed'))
    destinations.value = []
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  loadStaticImageMap()
  loadDestinations()
})
</script>

<style scoped>
.destinations-page {
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

.page-title-area {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding: 0 4px;
}

.page-title-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #9333ea 0%, #8b5cf6 100%);
  border-radius: 12px;
  box-shadow: 0 6px 16px rgba(147, 51, 234, 0.3);
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
  letter-spacing: -0.3px;
}

.page-subtitle {
  font-size: 12px;
  color: #9ca3af;
  margin: 2px 0 0;
}

.loading-state {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 80px 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 20px;
  text-align: center;
}

.empty-title {
  font-size: 15px;
  color: #999;
  margin: 16px 0 0;
}

.dest-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.dest-card {
  background: #ffffff;
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.dest-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 24px rgba(147, 51, 234, 0.12);
}

.dest-card:active {
  transform: translateY(-1px) scale(0.99);
}

.dest-img-wrap {
  position: relative;
  width: 100%;
  height: 120px;
  overflow: hidden;
}

.dest-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.dest-img-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0) 40%, rgba(0, 0, 0, 0.55) 100%);
}

.dest-heat {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 3px;
  background: linear-gradient(135deg, #f97316 0%, #ef4444 100%);
  color: #fff;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 20px;
  font-weight: 500;
}

.dest-name-overlay {
  position: absolute;
  left: 10px;
  bottom: 8px;
  display: flex;
  flex-direction: column;
}

.dest-name {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
}

.dest-province {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.4);
}

.dest-desc {
  padding: 10px 12px 12px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
