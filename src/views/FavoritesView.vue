<script setup>
/**
 * 我的收藏 —— 统一淡紫色品牌 UI 规范
 * 分类 Tab：全部 / 景点 / 游记 / 攻略
 */
import { ref, onMounted, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getToken } from '../utils/auth'
import { favoriteApi } from '../api'
import EmptyState from '../components/EmptyState.vue'

const router = useRouter()

const goBack = () => { router.back() }

/* ==================== 分类 Tab ==================== */
const activeTab = ref('all')
const tabs = [
  { name: '全部', key: 'all' },
  { name: '景点', key: 'spot' },
  { name: '游记', key: 'note' },
  { name: '攻略', key: 'guide' },
]

/* ==================== 列表数据 ==================== */
const favorites = ref([])
const isLoading = ref(false)
const loadError = ref(false)

const loadFavorites = async (type = '') => {
  isLoading.value = true
  loadError.value = false
  try {
    const response = await favoriteApi.getFavorites(type)
    if (response.code === 0) {
      favorites.value = response.data || []
    } else {
      favorites.value = []
    }
  } catch (error) {
    console.log('获取收藏列表失败:', error)
    favorites.value = []
    if (error?.response?.status === 502) loadError.value = true
  } finally {
    isLoading.value = false
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
      showToast('取消收藏成功')
      loadFavorites(activeTab.value === 'all' ? '' : activeTab.value)
    } else {
      showToast(response.message || '取消收藏失败')
    }
  } catch (error) { showToast('取消收藏失败') }
}

const handleItemClick = (item) => {
  if (item.link) { router.push(item.link) }
}

const handleGoExplore = () => { router.push('/') }

/* ==================== 图片兜底 ==================== */
const IMAGE_API = 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image'
const getCoverUrl = (item) => {
  if (item.cover && !item.cover.includes('placeholder')) return item.cover
  const keyword = item.name || item.title || '旅行风景'
  return `${IMAGE_API}?prompt=${encodeURIComponent(keyword + ' 旅游 高清')}&image_size=landscape_4_3`
}

onMounted(() => {
  if (getToken()) loadFavorites()
})

/* 【性能优化】离开时清理状态 */
onDeactivated(() => { isLoading.value = false; loadError.value = false })
</script>

<template>
  <div class="page-shell">
    <!-- 顶部导航 -->
    <van-nav-bar title="我的收藏" left-text="返回" left-arrow safe-area-inset-top class="nav-bar" @click-left="goBack" />

    <!-- 分类 Tab -->
    <van-tabs v-model:active="activeTab" @change="handleTabChange" class="page-tabs" :duration="0.25" swipeable>
      <van-tab v-for="tab in tabs" :key="tab.key" :title="tab.name" />
    </van-tabs>

    <div class="page-content">
      <transition name="tab-fade" mode="out-in">
        <div :key="activeTab">
          <!-- 骨架屏加载 -->
          <van-skeleton v-if="isLoading" title avatar row="3" />

          <!-- 错误兜底 -->
          <div v-else-if="loadError" class="error-state">
            <van-icon name="warn-o" size="48" color="#94A3B8" />
            <p class="error-text">加载失败，请稍后重试</p>
            <van-button round plain class="retry-btn" size="small" @click="loadFavorites(activeTab === 'all' ? '' : activeTab)">重新加载</van-button>
          </div>

          <!-- 空状态 -->
          <EmptyState
            v-else-if="favorites.length === 0"
            icon="star-o"
            title="暂无收藏"
            desc="收藏喜欢的景点和攻略，方便随时查看"
            btn-text="去看看"
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
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
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
:deep(.page-tabs) { background: #fff; }
:deep(.page-tabs .van-tabs__nav) { padding: 0 8px; }
:deep(.page-tabs .van-tab) { font-size: 14px; color: #64748B; }
:deep(.page-tabs .van-tab--active) { color: #7C3AED; font-weight: 600; }
:deep(.page-tabs .van-tabs__line) { background: #8B5CF6; height: 3px; border-radius: 2px; }

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
