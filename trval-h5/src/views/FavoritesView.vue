<script setup>
/**
 * 我的收藏 —— 统一淡紫色品牌 UI 规范
 * 分类 Tab：全部 / 景点 / 游记 / 攻略
 */
import { ref, onMounted, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showToast } from 'vant'
import { getToken } from '../utils/auth'
import { favoriteApi } from '../api'
import EmptyState from '../components/EmptyState.vue'

const router = useRouter()
const { t } = useI18n()

const goBack = () => { router.back() }

/* ==================== 分类 Tab ==================== */
const activeTab = ref('all')
const tabs = [
  { nameKey: 'all', key: 'all' },
  { nameKey: 'spot', key: 'spot' },
  { nameKey: 'note', key: 'note' },
  { nameKey: 'guide', key: 'guide' },
]

/* ==================== 列表数据 ==================== */
const favorites = ref([])
const isLoading = ref(false)
const loadError = ref(false)

// BUGID TAB-1 修复：请求序号计数器，快速切 Tab 时丢弃过期响应
let loadSeq = 0

const loadFavorites = async (type = '') => {
  const seq = ++loadSeq
  isLoading.value = true
  loadError.value = false
  try {
    const response = await favoriteApi.getFavorites(type)
    if (seq !== loadSeq) return // BUGID TAB-1：旧请求晚回，丢弃
    if (response.code === 0) {
      favorites.value = response.data || []
    } else {
      favorites.value = []
    }
  } catch (error) {
    if (seq !== loadSeq) return // BUGID TAB-1：旧请求晚回，丢弃
    console.log('获取收藏列表失败:', error)
    favorites.value = []
    if (error?.response?.status === 502) loadError.value = true
  } finally {
    if (seq === loadSeq) isLoading.value = false
  }
}

const handleTabChange = (key) => {
  activeTab.value = key
  favorites.value = []
  loadFavorites(key === 'all' ? '' : key)
}

const handleDelete = async (id) => {
  try {
    const response = await favoriteApi.deleteFavorite(id)
    if (response.code === 0) {
      showToast(t('wallet.unfavSuccess'))
      loadFavorites(activeTab.value === 'all' ? '' : activeTab.value)
    } else {
      showToast(response.message || t('wallet.unfavFail'))
    }
  } catch (error) { showToast(t('wallet.unfavFail')) }
}

const handleItemClick = (item) => {
  if (item.link) { router.push(item.link) }
}

const handleGoExplore = () => { router.push('/') }

/* ==================== 图片兜底 ==================== */
// BUGID FEAT-9 修复：移除第三方 AI 生图接口（trae-api-cn.mchost.guru），
// 直连外部接口会把标题拼进 prompt 外发（隐私泄露），且跨域/防盗链不稳定
const staticImageMap = ref({})

const loadStaticImageMap = async () => {
  try {
    const resp = await fetch('/city-images.json')
    if (resp.ok) Object.assign(staticImageMap.value, await resp.json())
  } catch {}
}

const getCoverUrl = (item) => {
  if (item.cover && !item.cover.includes('placeholder')) return item.cover
  const keyword = item.name || item.title || '旅行风景'
  if (staticImageMap.value[keyword]) return staticImageMap.value[keyword]
  // BUGID FEAT-9 修复：兜底改为项目本地占位图，不再外发生成请求
  return '/images/default-placeholder.png'
}

onMounted(() => {
  loadStaticImageMap()
  if (getToken()) loadFavorites()
})

/* 【性能优化】离开时清理状态 */
onDeactivated(() => { isLoading.value = false; loadError.value = false })
</script>

<template>
  <div class="page-shell">
    <!-- 顶部导航 -->
    <van-nav-bar :title="t('wallet.favTitle')" :left-text="t('common.back')" left-arrow safe-area-inset-top class="nav-bar" @click-left="goBack" />

    <!-- 分类 Tab -->
    <div class="filter-tabs">
      <div class="filter-slider" :style="{ left: `calc(${tabs.findIndex(t => t.key === activeTab) * 25}% + 4px)`, width: `calc(25% - 8px)` }" />
      <button
        v-for="tab in tabs" :key="tab.key"
        :class="['filter-tab', { active: activeTab === tab.key }]"
        @click="handleTabChange(tab.key)"
      >{{ t('wallet.' + tab.nameKey) }}</button>
    </div>

    <div class="page-content">
      <transition name="tab-fade" mode="out-in">
        <div :key="activeTab">
          <!-- 骨架屏加载 -->
          <van-skeleton v-if="isLoading" title avatar row="3" />

          <!-- 错误兜底 -->
          <div v-else-if="loadError" class="error-state">
            <van-icon name="warn-o" size="48" color="#94A3B8" />
            <p class="error-text">{{ t('common.requestFailed') }}</p>
            <van-button round plain class="retry-btn" size="small" @click="loadFavorites(activeTab === 'all' ? '' : activeTab)">{{ t('common.retry') }}</van-button>
          </div>

          <!-- 空状态 -->
          <EmptyState
            v-else-if="favorites.length === 0"
            icon="star-o"
            :title="t('wallet.noFavs')"
            :desc="t('wallet.noFavsDesc')"
            :btn-text="t('wallet.goExplore')"
            btn-type="outline"
            @btn-click="handleGoExplore"
          />

          <!-- 收藏列表 -->
          <div v-else class="favorites-list">
            <div v-for="item in favorites" :key="item.id" class="favorite-item" @click="handleItemClick(item)">
              <van-image width="100px" height="80px" :src="getCoverUrl(item)" fit="cover" class="item-cover" radius="12px">
                <template #error><div class="cover-fallback"><van-icon name="photo-o" size="28" color="#94A3B8" /></div></template>
              </van-image>
              <div class="item-info">
                <div class="item-name">{{ item.name || item.title }}</div>
                <div v-if="item.desc" class="item-desc">{{ item.desc }}</div>
                <div class="item-meta">
                  <span v-if="item.rating" class="meta-tag rating-tag"><van-icon name="star" size="10" color="#F59E0B" /> {{ item.rating }}</span>
                  <span v-if="item.author" class="meta-tag">{{ item.author }}</span>
                  <span v-if="item.likes" class="meta-tag"><van-icon name="good-job-o" size="10" color="#94A3B8" /> {{ item.likes }}</span>
                </div>
              </div>
              <van-icon name="delete-o" size="20" color="#94A3B8" class="delete-icon" @click.stop="handleDelete(item.id)" />
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<style scoped>
/* ==================== 统一页面外壳 ==================== */
.page-shell {
  width: 100%;
  min-height: 100vh;
  background: transparent;
  padding-bottom: calc(62px + var(--safe-area-bottom) + 16px);
  box-sizing: border-box;
  overflow-x: hidden;
}

:deep(.nav-bar) {
  background: linear-gradient(135deg, rgba(233,213,255,0.9) 0%, rgba(240,249,255,0.9) 50%, rgba(253,244,255,0.9) 100%);
  backdrop-filter: blur(12px);
}
:deep(.nav-bar .van-nav-bar__title) { color: #1E293B; font-weight: 600; font-size: 17px; }

/* ==================== 统一 Tab 栏 ==================== */
/* 分类 Tab — 滑动指示器 */
.filter-tabs {
  display: flex;
  margin: 0 12px 12px;
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(12px) saturate(150%);
  -webkit-backdrop-filter: blur(12px) saturate(150%);
  border-radius: 14px;
  padding: 4px;
  position: relative;
  border: 1px solid rgba(255,255,255,0.5);
}
.filter-slider {
  position: absolute; top: 4px; height: calc(100% - 8px);
  background: #fff;
  border-radius: 11px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  transition: left 0.35s cubic-bezier(0.4, 0, 0.2, 1), width 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 0;
}
.filter-tab {
  flex: 1; padding: 9px 0; border: none; border-radius: 11px;
  font-size: 13px; font-weight: 500; cursor: pointer;
  background: transparent; color: #94A3B8;
  position: relative; z-index: 1;
  transition: color 0.3s ease;
}
.filter-tab.active { color: #7C3AED; font-weight: 600; }
.filter-tab:active { transform: scale(0.96); }

.page-content { padding: 16px; box-sizing: border-box; }

/* ==================== 错误状态 ==================== */
.error-state { display: flex; flex-direction: column; align-items: center; padding: 80px 20px; text-align: center; }
.error-text { font-size: 15px; color: #94A3B8; margin: 16px 0 20px; }
.retry-btn {
  color: #7C3AED !important;
  border: 1.5px solid #C4B5FD !important;
  background: #fff !important;
  border-radius: 16px !important;
}

/* ==================== 收藏列表 ==================== */
.favorites-list { display: flex; flex-direction: column; gap: 12px; }

.favorite-item {
  display: flex;
  gap: 12px;
  background: #fff;
  border-radius: 16px;
  padding: 14px;
  box-shadow: 0 4px 18px rgba(0,0,0,0.04);
  border: 1px solid rgba(139,92,246,0.06);
  transition: transform 0.2s;
}
.favorite-item:active { transform: scale(0.98); }

.item-cover { flex-shrink: 0; border-radius: 12px; overflow: hidden; }
.cover-fallback {
  width: 100px;
  height: 80px;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}

.item-info { flex: 1; display: flex; flex-direction: column; justify-content: space-between; overflow: hidden; }
.item-name { font-size: 15px; font-weight: 600; color: #1E293B; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-desc { font-size: 12px; color: #94A3B8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 2px; }
.item-meta { display: flex; gap: 8px; margin-top: 4px; }
.meta-tag { font-size: 11px; color: #64748B; display: flex; align-items: center; gap: 2px; }
.rating-tag { color: #F59E0B; }

.delete-icon { flex-shrink: 0; align-self: center; cursor: pointer; padding: 4px; }
.delete-icon:active { opacity: 0.5; }
</style>
