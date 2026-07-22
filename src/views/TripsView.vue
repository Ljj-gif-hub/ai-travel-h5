<script setup>
/**
 * 行程 Tab — 携程同款完整页面
 * 模块：线路规划 / 周边地图 / 城市攻略 / 行程列表 / 悬浮AI按钮
 */
import { ref, computed, onMounted, onActivated, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { getToken } from '../utils/auth'
import { planApi, noteApi } from '../api'
import { getHotDestinations, getCityAttractions } from '../api/destination'
import EmptyState from '../components/EmptyState.vue'
import AIChatDialog from '../components/AIChatDialog.vue'

defineOptions({ name: 'TripsView' })

const router = useRouter()

/* ==================== 状态 ==================== */
const trips = ref([])
const isLoading = ref(false)
const loadError = ref(false)
const showAIChat = ref(false)
const showMoreMenu = ref(false)

/* AI弹窗上下文 */
const aiContext = ref({ destination: '', budget: '', days: '' })

/* ==================== Tab / 行程列表 ==================== */
const tabs = [
  { key: 'all', title: '全部' },
  { key: 'upcoming', title: '待出行' },
  { key: 'doing', title: '进行中' },
  { key: 'done', title: '已完成' },
  { key: 'draft', title: '草稿' },
]
const activeTab = ref('all')

const inferStatus = (plan) => {
  if (!plan.planData?.dayPlans?.length) return 'draft'
  if (plan.travelDate) {
    const d = new Date(plan.travelDate); d.setHours(0, 0, 0, 0)
    const today = new Date(); today.setHours(0, 0, 0, 0)
    if (d < today) return 'done'
  }
  return 'upcoming'
}

const tripPlans = computed(() => trips.value.filter(t => (t.source || 'trip') === 'trip'))
const homePlans = computed(() => trips.value.filter(t => t.source === 'home'))

const hasTrips = computed(() => trips.value.length > 0)

/* ==================== 城市攻略数据 ==================== */
const cityGuides = ref([])
const guideCities = ['北京', '上海', '成都', '杭州']

const loadCityGuides = async () => {
  try {
    const results = await Promise.allSettled(
      guideCities.map(city => getCityAttractions(city))
    )
    cityGuides.value = results.map((r, i) => ({
      city: guideCities[i],
      attractions: r.status === 'fulfilled' && r.value?.code === 0 ? (r.value.data || []).slice(0, 4) : [],
    }))
  } catch (e) { /* 攻略降级 */ }
}

/* ==================== 热门目的地 ==================== */
const hotDestinations = ref([])
const loadHotDestinations = async () => {
  try {
    const res = await getHotDestinations()
    hotDestinations.value = (res.data || []).slice(0, 8)
  } catch (e) { /* 降级 */ }
}

/* ==================== 游记笔记 ==================== */
const travelNotes = ref([])
const loadTravelNotes = async () => {
  try {
    const res = await noteApi.getMyNotes()
    if (res.code === 0) travelNotes.value = (res.data || []).slice(0, 4)
  } catch (e) { /* 降级 */ }
}

/* ==================== 工具 ==================== */
const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  return `${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const getAttractions = (plan) => {
  if (!plan.planData?.dayPlans) return []
  const arr = []
  plan.planData.dayPlans.forEach(day => {
    day.timeSlots?.forEach(slot => { if (slot.attraction) arr.push(slot.attraction) })
  })
  return arr
}

/** 行程卡片标题 */
const cardTitle = (plan) => {
  const dest = plan.destination || '未知'
  const days = plan.days || 1
  return `${dest}${days}日游`
}

/** 行程路线摘要：Day1 景点A→景点B | Day2 景点C→景点D … */
const cardRoute = (plan) => {
  if (!plan.planData?.dayPlans) return ''
  const lines = plan.planData.dayPlans.slice(0, 3).map(day => {
    const spots = []
    day.timeSlots?.forEach(slot => { if (slot.attraction) spots.push(slot.attraction) })
    return spots.length > 0 ? `Day${day.day || '?'} ${spots.join(' → ')}` : ''
  }).filter(Boolean)
  const suffix = plan.planData.dayPlans.length > 3 ? ' …' : ''
  return lines.join(' | ') + suffix
}

/** 卡片日期信息：9月1日-9月6日·共6天·14个地点 */
const cardMeta = (plan) => {
  const days = plan.days || 0
  const locationCount = getAttractions(plan).length
  const parts = []
  // 日期范围
  if (plan.travelDate) {
    const start = new Date(plan.travelDate)
    const end = new Date(start)
    end.setDate(end.getDate() + days - 1)
    const fmt = d => `${d.getMonth() + 1}月${d.getDate()}日`
    parts.push(`${fmt(start)}-${fmt(end)}`)
  } else if (plan.createdAt) {
    const d = new Date(plan.createdAt)
    parts.push(`${d.getMonth() + 1}月${d.getDate()}日`)
  }
  if (days > 0) parts.push(`共${days}天`)
  if (locationCount > 0) parts.push(`${locationCount}个地点`)
  return parts.join('·')
}

const statusLabel = (s) => ({ upcoming: '待出行', doing: '进行中', done: '已完成', draft: '草稿' }[s] || s)
const statusColor = (s) => ({ upcoming: '#8B5CF6', doing: '#3B82F6', done: '#34D399', draft: '#F59E0B' }[s] || '#94A3B8')

/* ==================== 行程加载 ==================== */
const loadTrips = async () => {
  isLoading.value = true; loadError.value = false
  try {
    const res = await planApi.getSavedPlans()
    if (res.code === 0) {
      trips.value = (res.data || []).map(p => ({ ...p, _status: inferStatus(p) }))
    } else { trips.value = [] }
  } catch (e) {
    trips.value = []
    if (e?.response?.status === 502) loadError.value = true
  } finally { isLoading.value = false }
}

/* ==================== 操作 ==================== */
const viewTrip = (plan) => {
  try {
    if (!plan || !plan.destination) { showToast({ message: '行程数据异常', position: 'middle' }); return }
    router.push({ path: '/trip-map', query: { savedPlanId: plan.id } })
  } catch (e) { console.error('viewTrip 失败:', e) }
}

/* 规划来源筛选 */

const openAIChat = (ctx = {}) => {
  try {
    aiContext.value = ctx
    showAIChat.value = true
  } catch (e) { console.error('openAIChat 失败:', e) }
}

/** 跳转 AI 行程助手页面（携程同款） */
const goToAIPlanner = () => {
  try { router.push('/ai-planner') } catch (e) { console.error('goToAIPlanner 失败:', e) }
}

const aiOptimize = (plan) => {
  try {
    openAIChat({ destination: plan?.destination || '', budget: String(plan?.budget || ''), days: String(plan?.days || '') })
  } catch (e) { console.error('aiOptimize 失败:', e) }
}

const onPlanSaved = () => {
  try {
    showAIChat.value = false
    showToast({ message: '行程已保存', position: 'middle' })
    loadTrips()
  } catch (e) { console.error('onPlanSaved 失败:', e) }
}

const confirmDelete = async (plan) => {
  try {
    await showConfirmDialog({ title: '删除行程', message: `确定要删除「${plan?.destination || '未知'}」的行程吗？`, confirmButtonText: '删除', cancelButtonText: '取消' })
    if (!plan?.id) return
    const res = await planApi.deletePlan(plan.id)
    if (res.code === 0) {
      showToast({ message: '已删除', position: 'middle' })
      trips.value = trips.value.filter(t => t.id !== plan.id)
    } else { showToast(res.message || '删除失败') }
  } catch (e) { /* 取消或异常 */ }
}

const goHome = () => { try { router.push('/') } catch (e) {} }
const goMap = () => { try { showToast({ message: '地图功能开发中', position: 'middle' }) } catch (e) {} }
/*
 * 【修复】城市攻略/景点卡片点击 — 参数校验防止空值崩溃
 */
const goDestinationDetail = (city) => {
  try {
    if (!city) return
    router.push(`/destination-detail?city=${encodeURIComponent(city)}`)
  } catch (e) { console.error('goDestinationDetail 失败:', e) }
}
const goAttraction = (name) => {
  try {
    if (!name) return
    router.push(`/destination-detail?city=${encodeURIComponent(name)}`)
  } catch (e) { console.error('goAttraction 失败:', e) }
}
const goNotes = () => { try { router.push('/notes') } catch (e) {} }
const handleMoreAction = (action) => {
  try {
    showMoreMenu.value = false
    const actions = {
      import: '导入行程功能开发中',
      batchDelete: '批量删除功能开发中',
      export: '导出行程功能开发中',
      settings: '行程设置功能开发中',
    }
    showToast({ message: actions[action] || '功能开发中', position: 'middle' })
  } catch (e) { console.error('handleMoreAction 失败:', e) }
}

/* ==================== 生命周期 ==================== */
onMounted(() => {
  if (getToken()) { loadTrips(); loadCityGuides(); loadHotDestinations(); loadTravelNotes() }
})
onActivated(() => {
  if (getToken()) { loadTrips(); loadCityGuides() }
})
onDeactivated(() => { isLoading.value = false; loadError.value = false; showMoreMenu.value = false })
</script>

<template>
  <div class="trips-page">
    <!-- 漂浮粒子 -->
    <div class="clouds-layer" aria-hidden="true">
      <span class="cloud-dot c1"></span><span class="cloud-dot c2"></span><span class="cloud-dot c3"></span>
    </div>
    <!-- ======== 顶部导航 ======== -->
    <van-nav-bar safe-area-inset-top class="nav-bar">
      <template #title>
        <span class="nav-title">{{ hasTrips ? '我的全部行程' : '暂无行程' }}</span>
      </template>
      <template #right>
        <div class="nav-actions">
          <div class="nav-btn" @click="goToAIPlanner">
            <van-icon name="add" size="20" color="#7C3AED" />
          </div>
          <div class="nav-btn" @click="showMoreMenu = true">
            <van-icon name="ellipsis" size="20" color="#7C3AED" />
          </div>
        </div>
      </template>
    </van-nav-bar>

    <!-- ======== 更多菜单弹窗 ======== -->
    <van-popup v-model:show="showMoreMenu" position="top" :style="{ width: '160px', top: 'calc(env(safe-area-inset-top, 0px) + 48px)', right: '8px', borderRadius: '14px' }" overlay-class="no-overlay">
      <div class="more-menu">
        <div v-for="item in [
          { key: 'import', icon: 'down', label: '导入行程' },
          { key: 'batchDelete', icon: 'delete-o', label: '批量删除' },
          { key: 'export', icon: 'share-o', label: '导出行程' },
          { key: 'settings', icon: 'setting-o', label: '行程设置' },
        ]" :key="item.key" class="more-item" @click="handleMoreAction(item.key)">
          <van-icon :name="item.icon" size="16" color="#64748B" />
          <span>{{ item.label }}</span>
        </div>
      </div>
    </van-popup>

    <div class="trips-scroll">
      <div class="trips-inner">
        <!-- ======== 模块1：线路规划卡片（C位） ======== -->
        <div class="hero-plan-card entrance-item entrance-d1">
          <!-- 地图暗纹背景 -->
          <div class="hero-bg">
            <svg viewBox="0 0 360 180" preserveAspectRatio="none" class="hero-bg-svg">
              <path d="M0 80 Q60 50 120 75 Q180 40 240 65 Q300 35 360 55 L360 180 L0 180 Z" fill="rgba(139,92,246,0.04)" />
              <path d="M0 110 Q80 85 160 100 Q220 70 300 95 Q330 80 360 90 L360 180 L0 180 Z" fill="rgba(99,102,241,0.03)" />
              <circle cx="60" cy="70" r="4" fill="rgba(139,92,246,0.12)" />
              <circle cx="180" cy="55" r="3" fill="rgba(139,92,246,0.10)" />
              <circle cx="280" cy="50" r="5" fill="rgba(99,102,241,0.10)" />
              <line x1="60" y1="70" x2="180" y2="55" stroke="rgba(139,92,246,0.08)" stroke-width="1" stroke-dasharray="4,3" />
              <line x1="180" y1="55" x2="280" y2="50" stroke="rgba(99,102,241,0.08)" stroke-width="1" stroke-dasharray="4,3" />
            </svg>
            <!-- 浮动标签气泡 -->
            <span class="poi-bubble poi-1">🏔️ 景点</span>
            <span class="poi-bubble poi-2">🏨 酒店</span>
            <span class="poi-bubble poi-3">🚉 车站</span>
          </div>

          <div class="hero-header-row">
            <span class="hero-label">AI 线路规划</span>
            <span v-if="hasTrips" class="hero-link" @click="activeTab = 'all'">我的线路 &gt;</span>
          </div>
          <p class="hero-intro">智能推荐最佳旅行线路，一键生成专属行程</p>
          <button class="hero-start-btn btn-tap-scale" @click="goToAIPlanner">
            <van-icon name="compass-o" size="22" />
            <span>开始规划</span>
          </button>
          <p class="hero-sub-text">支持智能AI推荐线路</p>
        </div>

        <!-- ======== 模块2：周边游地图 ======== -->
        <div class="nearby-card card-macaron" @click="goMap">
          <div class="nearby-top">
            <div class="nearby-title-row">
              <van-icon name="location-o" size="20" color="#8B5CF6" />
              <span class="nearby-title">周边游地图</span>
            </div>
            <span class="nearby-link">探索周边出行灵感 &gt;</span>
          </div>
          <div class="nearby-map-preview">
            <svg viewBox="0 0 340 80" preserveAspectRatio="none" class="map-svg">
              <rect width="340" height="80" rx="10" fill="rgba(139,92,246,0.04)" />
              <circle cx="120" cy="35" r="6" fill="#8B5CF6" opacity="0.5" />
              <circle cx="120" cy="35" r="3" fill="#8B5CF6" />
              <circle cx="200" cy="50" r="4" fill="#6366F1" opacity="0.4" />
              <circle cx="80" cy="25" r="4" fill="#A78BFA" opacity="0.4" />
              <circle cx="250" cy="30" r="5" fill="#8B5CF6" opacity="0.35" />
              <line x1="80" y1="25" x2="200" y2="50" stroke="rgba(139,92,246,0.12)" stroke-width="1" />
              <line x1="120" y1="35" x2="250" y2="30" stroke="rgba(99,102,241,0.10)" stroke-width="1" />
            </svg>
          </div>
        </div>

        <!-- ======== 模块3：目的地攻略推荐 ======== -->
        <div v-for="guide in cityGuides" :key="guide.city" class="guide-section" v-show="guide.attractions.length > 0">
          <div class="sec-head">
            <span class="sec-title">{{ guide.city }}</span>
            <span class="sec-more" @click="goDestinationDetail(guide.city)">攻略 &gt;</span>
          </div>
          <div class="h-scroll">
            <div v-for="(attr, i) in guide.attractions" :key="i" class="guide-card" @click="goAttraction(attr.name)">
              <div class="guide-card-img-wrap">
                <div class="guide-card-img-placeholder">
                  <van-icon name="photo-o" size="24" color="rgba(139,92,246,0.3)" />
                </div>
                <span class="guide-card-tag">{{ attr.name?.length > 4 ? attr.name.slice(0, 4) + '..' : attr.name }}</span>
              </div>
              <div class="guide-card-body">
                <div class="guide-card-name">{{ attr.name }}</div>
                <div class="guide-card-meta" v-if="attr.rating">⭐ {{ attr.rating }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- ======== 模块4：达人笔记 ======== -->
        <div v-if="travelNotes.length > 0" class="guide-section">
          <div class="sec-head">
            <span class="sec-title">达人笔记</span>
            <span class="sec-more" @click="goNotes">更多 &gt;</span>
          </div>
          <div class="h-scroll">
            <div v-for="note in travelNotes" :key="note.id" class="note-mini-card" @click="router.push(`/notes?id=${note.id}`)">
              <div class="note-mini-cover">
                <van-icon name="file-text-o" size="28" color="rgba(139,92,246,0.3)" />
              </div>
              <div class="note-mini-body">
                <div class="note-mini-title">{{ note.title }}</div>
                <div class="note-mini-meta">
                  <span><van-icon name="good-job-o" size="11" /> {{ note.likes || 0 }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ======== 模块5：快捷模板 ======== -->
        <div class="templates-section" v-if="hotDestinations.length > 0">
          <div class="sec-head">
            <span class="sec-title">热门目的地</span>
          </div>
          <div class="quick-tags">
            <span v-for="dest in hotDestinations" :key="dest.name" class="quick-tag" @click="openAIChat({ destination: dest.name, budget: '', days: '' })">
              {{ dest.name }}
            </span>
          </div>
        </div>

        <!-- ======== 模块6：行程列表（有数据时展示） ======== -->
        <div v-if="hasTrips" class="trips-list-section">
          <div class="page-content">
            <!-- 骨架屏 -->
            <div v-if="isLoading" class="skeleton-list">
              <div v-for="i in 2" :key="i" class="trip-card-skeleton">
                <div class="sk-row sk-row-title" /><div class="sk-row sk-row-info" /><div class="sk-row sk-row-attract" />
              </div>
            </div>

            <!-- 错误 -->
            <div v-else-if="loadError" class="error-state">
              <van-icon name="warn-o" size="40" color="#94A3B8" /><p class="error-text">加载失败</p>
              <van-button round plain size="small" class="retry-btn" @click="loadTrips">重试</van-button>
            </div>

            <template v-else>
              <!-- ===== 行程规划 ===== -->
              <div class="section-block">
                <div class="section-head">
                  <span class="section-head-title">📋 行程规划</span>
                  <span class="section-head-count">{{ tripPlans.length }}条</span>
                </div>
                <div v-if="tripPlans.length === 0" class="empty-hint-row">暂无AI行程规划，<span class="link" @click="goToAIPlanner">去创建</span></div>
                <div v-for="trip in tripPlans" :key="trip.id" class="trip-card" @click="viewTrip(trip)">
                  <div class="trip-card-top">
                    <div class="trip-s-badge"><span class="trip-s-letter">S</span></div>
                    <span class="trip-card-label">我的线路</span>
                    <span class="trip-status-tag" :style="{ color: statusColor(trip._status), background: `${statusColor(trip._status)}15` }">{{ statusLabel(trip._status) }}</span>
                  </div>
                  <div class="trip-card-title">{{ cardTitle(trip) }}</div>
                  <div v-if="cardRoute(trip)" class="trip-card-route">{{ cardRoute(trip) }}</div>
                  <div class="trip-card-meta">{{ cardMeta(trip) }}</div>
                  <div class="trip-card-footer">
                    <span class="trip-detail-link">线路详情</span>
                  </div>
                </div>
              </div>

              <!-- ===== 首页规划 ===== -->
              <div class="section-block">
                <div class="section-head">
                  <span class="section-head-title">🏠 首页规划</span>
                  <span class="section-head-count">{{ homePlans.length }}条</span>
                </div>
                <div v-if="homePlans.length === 0" class="empty-hint-row">暂无首页快捷规划</div>
                <div v-for="trip in homePlans" :key="trip.id" class="trip-card" @click="viewTrip(trip)">
                  <div class="trip-card-top">
                    <div class="trip-s-badge trip-s-badge--home"><span class="trip-s-letter">S</span></div>
                    <span class="trip-card-label">我的线路</span>
                  </div>
                  <div class="trip-card-title">{{ cardTitle(trip) }}</div>
                  <div v-if="cardRoute(trip)" class="trip-card-route">{{ cardRoute(trip) }}</div>
                  <div class="trip-card-meta">{{ cardMeta(trip) }}</div>
                  <div class="trip-card-footer">
                    <span class="trip-detail-link">线路详情</span>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <div style="height: 80px;" />
      </div>
    </div>

    <!-- ======== 右下角悬浮AI按钮 ======== -->
    <button class="fab-ai-btn btn-tap-scale" @click="openAIChat({})">
      <svg viewBox="0 0 40 40" width="26" height="26">
        <circle cx="20" cy="16" r="10" fill="rgba(255,255,255,0.3)" />
        <circle cx="20" cy="16" r="6" fill="white" opacity="0.8" />
        <ellipse cx="20" cy="34" rx="12" ry="4" fill="rgba(255,255,255,0.2)" />
      </svg>
    </button>

    <!-- ======== AI规划弹窗 ======== -->
    <AIChatDialog v-model:visible="showAIChat" :context-query="aiContext" @plan-saved="onPlanSaved" />
  </div>
</template>

<style scoped>
/* ==================== 页面 ==================== */
.trips-page {
  width: 100%;
  /* 【修复】动态计算可视高度：视口 - 底部Tab(56px) - safe-area */
  min-height: calc(100vh - var(--tabbar-height, 56px) - var(--safe-area-bottom, 0px));
  height: calc(100vh - var(--tabbar-height, 56px) - var(--safe-area-bottom, 0px));
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
  position: relative;
  display: flex;
  flex-direction: column;
}

/* 【修复】滚动容器：flex:1撑满 + overflow-y + 底部安全padding */
.trips-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  padding-bottom: calc(80px + var(--safe-area-bottom, 0px));
}
.trips-inner { max-width: 480px; margin: 0 auto; padding: 0 14px; }

/* ==================== 导航栏 ==================== */
:deep(.nav-bar) { background: linear-gradient(135deg, rgba(233,213,255,0.9) 0%, rgba(240,249,255,0.9) 50%, rgba(253,244,255,0.9) 100%); backdrop-filter: blur(12px); }
:deep(.nav-bar .van-nav-bar__title) { color: #1E293B; font-weight: 600; }
.nav-title { font-size: 17px; }
.nav-actions { display: flex; gap: 4px; }
.nav-btn { width: 40px; height: 40px; min-width: 40px; min-height: 40px; border-radius: 50%; background: rgba(139,92,246,0.08); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all 0.2s; }
.nav-btn:active { background: rgba(139,92,246,0.18); transform: scale(0.9); }

/* 更多菜单 */
.more-menu { padding: 8px; }
.more-item { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-radius: 10px; cursor: pointer; font-size: 14px; color: #475569; transition: background 0.15s; }
.more-item:active { background: #faf5ff; }
:deep(.no-overlay) { background: transparent !important; }

/* ==================== 模块1：线路规划卡片 ==================== */
.hero-plan-card {
  position: relative; overflow: hidden;
  background: #fff; border-radius: 20px; padding: 24px 20px 20px; margin: 14px 0;
  box-shadow: 0 4px 18px rgba(0,0,0,0.04); border: 1px solid rgba(139,92,246,0.08);
  text-align: center;
}
.hero-bg { position: absolute; inset: 0; pointer-events: none; }
.hero-bg-svg { width: 100%; height: 100%; }
.poi-bubble {
  position: absolute; font-size: 11px; padding: 4px 10px; background: rgba(255,255,255,0.9);
  border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); color: #475569; font-weight: 500;
}
.poi-1 { top: 14px; left: 14%; }
.poi-2 { top: 30px; right: 18%; }
.poi-3 { bottom: 30px; left: 45%; }
.hero-header-row { display: flex; justify-content: space-between; align-items: center; position: relative; z-index: 2; margin-bottom: 8px; }
.hero-label { font-size: 17px; font-weight: 700; color: #1E293B; }
.hero-link { font-size: 13px; color: #8B5CF6; cursor: pointer; font-weight: 500; }
.hero-link:active { opacity: 0.6; }
.hero-intro { position: relative; z-index: 2; font-size: 13px; color: #94A3B8; margin: 0 0 20px; }
.hero-start-btn {
  position: relative; z-index: 2;
  display: inline-flex; align-items: center; gap: 8px;
  padding: 14px 40px; border: none; border-radius: 25px;
  background: linear-gradient(135deg, #A78BFA 0%, #8B5CF6 50%, #6366F1 100%);
  color: #fff; font-size: 16px; font-weight: 600; cursor: pointer;
  box-shadow: 0 8px 24px rgba(139,92,246,0.35); transition: all 0.25s;
}
.hero-start-btn:hover { box-shadow: 0 12px 32px rgba(139,92,246,0.45); transform: translateY(-2px); }
.hero-start-btn:active { transform: scale(0.96); }
.hero-sub-text { position: relative; z-index: 2; font-size: 11px; color: #94A3B8; margin: 10px 0 0; }

/* ==================== 模块2：周边游地图 ==================== */
.nearby-card { padding: 18px; margin-bottom: 14px; cursor: pointer; transition: transform 0.2s; }
.nearby-card:active { transform: scale(0.98); }
.nearby-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.nearby-title-row { display: flex; align-items: center; gap: 8px; }
.nearby-title { font-size: 16px; font-weight: 700; color: #1E293B; }
.nearby-link { font-size: 12px; color: #8B5CF6; font-weight: 500; }
.nearby-map-preview { border-radius: 14px; overflow: hidden; }
.map-svg { display: block; width: 100%; }

/* ==================== 通用区块头 ==================== */
.sec-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; padding: 0 2px; }
.sec-title { font-size: 16px; font-weight: 700; color: #1E293B; }
.sec-more { font-size: 13px; color: #8B5CF6; cursor: pointer; font-weight: 500; }
.sec-more:active { opacity: 0.6; }

/* ==================== 模块3：城市攻略 ==================== */
.guide-section { margin-bottom: 18px; }
.guide-card {
  flex-shrink: 0; width: 120px; background: #fff; border-radius: 14px;
  overflow: hidden; box-shadow: 0 3px 12px rgba(0,0,0,0.04); cursor: pointer;
  transition: transform 0.2s;
}
.guide-card:hover { transform: translateY(-3px); }
.guide-card-img-wrap { height: 70px; position: relative; }
.guide-card-img-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: rgba(139,92,246,0.04); }
.guide-card-tag { position: absolute; top: 6px; right: 6px; background: rgba(139,92,246,0.15); color: #7C3AED; font-size: 10px; padding: 2px 8px; border-radius: 8px; font-weight: 600; }
.guide-card-body { padding: 10px; }
.guide-card-name { font-size: 13px; font-weight: 600; color: #1E293B; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.guide-card-meta { font-size: 11px; color: #F59E0B; margin-top: 2px; }

/* ==================== 模块4：达人笔记 ==================== */
.note-mini-card {
  flex-shrink: 0; width: 145px; background: #fff; border-radius: 14px;
  box-shadow: 0 3px 12px rgba(0,0,0,0.04); cursor: pointer; transition: transform 0.2s;
  display: flex; flex-direction: column;
}
.note-mini-card:hover { transform: translateY(-3px); }
.note-mini-cover { height: 70px; display: flex; align-items: center; justify-content: center; background: rgba(139,92,246,0.04); border-radius: 14px 14px 0 0; }
.note-mini-body { padding: 10px 12px; }
.note-mini-title { font-size: 13px; font-weight: 600; color: #1E293B; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; line-height: 1.4; }
.note-mini-meta { font-size: 11px; color: #94A3B8; margin-top: 6px; display: flex; align-items: center; gap: 4px; }

/* ==================== 模块5：热门标签 ==================== */
.templates-section { margin-bottom: 18px; }
.quick-tags { display: flex; flex-wrap: wrap; gap: 10px; /* 【修复】flex-wrap确保窄屏自动换行 */ }
.quick-tag { padding: 9px 16px; background: #fff; border-radius: 20px; font-size: 13px; color: #7C3AED; cursor: pointer; border: 1px solid rgba(139,92,246,0.12); transition: all 0.2s; font-weight: 500; }
.quick-tag:active { background: #faf5ff; border-color: #C4B5FD; transform: scale(0.95); }

/* ==================== 模块6：行程列表 ==================== */
.trips-list-section { margin-top: 8px; }
.section-block { margin: 16px 0 8px; }
.section-head { display:flex; justify-content:space-between; align-items:center; padding:8px 0; }
.section-head-title { font-size:16px; font-weight:700; color:#1e293b; }
.section-head-count { font-size:12px; color:#94a3b8; }
.empty-hint-row { padding:16px; text-align:center; font-size:13px; color:#94a3b8; }
.empty-hint-row .link { color:#8B5CF6; cursor:pointer; }
:deep(.trip-tabs) { background: #fff; border-radius: 16px 16px 0 0; overflow: hidden; }
:deep(.trip-tabs .van-tab) { color: #64748B; font-size: 14px; }
:deep(.trip-tabs .van-tab--active) { color: #7C3AED; font-weight: 600; }
:deep(.trip-tabs .van-tabs__line) { background: #8B5CF6; height: 3px; border-radius: 2px; }

.page-content { padding: 0; }
.tab-pane { min-height: 200px; }
.tab-fade-enter-active, .tab-fade-leave-active { transition: opacity 0.22s ease; }
.tab-fade-enter-from, .tab-fade-leave-to { opacity: 0; }

.error-state { display: flex; flex-direction: column; align-items: center; padding: 60px 20px; text-align: center; }
.error-text { font-size: 15px; color: #94A3B8; margin: 12px 0 16px; }
.retry-btn { border-radius: 20px !important; color: #7C3AED !important; border-color: #C4B5FD !important; }
.empty-tab-state { padding: 20px 0; }

/* 骨架屏 */
.skeleton-list { padding: 12px 0; display: flex; flex-direction: column; gap: 12px; }
.trip-card-skeleton { background: #fff; border-radius: 18px; padding: 20px; box-shadow: 0 4px 18px rgba(0,0,0,0.04); }
.sk-row { height: 14px; border-radius: 4px; background: linear-gradient(90deg, #f0f0f0 25%, #f8f8f8 50%, #f0f0f0 75%); background-size: 200% 100%; animation: shimmer 1.5s ease-in-out infinite; margin-bottom: 12px; }
.sk-row:last-child { margin-bottom: 0; }
.sk-row-title { width: 55%; height: 18px; }
.sk-row-info { width: 75%; }
.sk-row-attract { width: 85%; height: 36px; }
@keyframes shimmer { 0% { background-position: -200% 0; } 100% { background-position: 200% 0; } }

/* ==================== 行程卡片（统一模板） ==================== */
.trips-list { display: flex; flex-direction: column; gap: 14px; padding: 12px 0; }
.trip-card {
  background: #fff;
  border-radius: 16px;
  padding: 18px 16px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  border: 1px solid rgba(139,92,246,0.06);
  transition: transform 0.2s;
  margin-bottom: 14px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.trip-card:active { transform: scale(0.985); }
/* 顶部：S图标 + 标签 + 状态 */
.trip-card-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.trip-s-badge {
  width: 26px; height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366F1, #4F46E5);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.trip-s-badge--home {
  background: linear-gradient(135deg, #F59E0B, #D97706);
}
.trip-s-letter {
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
}
.trip-card-label {
  font-size: 13px;
  color: #64748B;
  font-weight: 500;
  flex: 1;
}
.trip-status-tag {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 600;
  flex-shrink: 0;
}
/* 标题 */
.trip-card-title {
  font-size: 16px;
  font-weight: 700;
  color: #1E293B;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
/* 路线摘要 */
.trip-card-route {
  font-size: 12px;
  color: #64748B;
  line-height: 1.65;
  padding: 8px 12px;
  background: #f8f7ff;
  border-radius: 10px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
}
/* 日期元信息 */
.trip-card-meta {
  font-size: 12px;
  color: #94A3B8;
  line-height: 1.5;
}
/* 底部：线路详情 */
.trip-card-footer {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}
.trip-detail-link {
  font-size: 13px;
  color: #8B5CF6;
  font-weight: 500;
  cursor: pointer;
  padding: 4px 0;
}
.trip-detail-link:active { opacity: 0.6; }

/* ==================== 悬浮AI按钮 ==================== */
.fab-ai-btn {
  /* 【修复】根级fixed + 精确bottom计算：Tab栏高度(56px) + safe-area + 20px安全边距 */
  position: fixed;
  bottom: calc(var(--tabbar-height, 56px) + var(--safe-area-bottom, 0px) + 20px);
  right: 16px;
  /* 【修复】z-index: 9995 → 高于页面内容，低于van-popup(默认~3000)和系统toast */
  z-index: 9995;
  width: 56px; height: 56px; border-radius: 50%; border: none;
  background: linear-gradient(135deg, #A78BFA 0%, #8B5CF6 50%, #6366F1 100%);
  box-shadow: 0 8px 28px rgba(139,92,246,0.4);
  display: flex; align-items: center; justify-content: center; cursor: pointer;
  will-change: transform; transition: transform 0.2s, box-shadow 0.25s;
}
.fab-ai-btn:hover { box-shadow: 0 12px 36px rgba(139,92,246,0.55); transform: translateY(-3px); }
.fab-ai-btn:active { transform: scale(0.9); }

/*
 * ================================================================
 * 行程页专属动效
 * ================================================================
 */
.clouds-layer { position: fixed; inset: 0; z-index: 0; pointer-events: none; overflow: hidden; }
.cloud-dot { position: absolute; border-radius: 50%; background: rgba(139,92,246,0.05); animation: cloudDriftSlow linear infinite; }
.c1 { width: 40px; height: 40px; top: 20%; right: 10%; animation-duration: 30s; }
.c2 { width: 55px; height: 55px; top: 60%; left: 8%; animation-duration: 36s; animation-delay: -12s; }
.c3 { width: 35px; height: 35px; top: 85%; left: 65%; animation-duration: 26s; animation-delay: -6s; }
.hero-plan-card { background-size: 200% 200%; }
/* hero-plan-card不设animation避免覆盖entrance-item的entranceUp */
.guide-card { transition: transform 0.35s cubic-bezier(0.4,0,0.2,1), box-shadow 0.35s ease; }
.guide-card:hover { transform: translateY(-5px); box-shadow: 0 12px 28px rgba(139,92,246,0.10); }
.guide-card:hover :deep(.van-icon) { animation: iconSwing 0.8s ease-in-out; }
.trip-card { transition: transform 0.35s cubic-bezier(0.4,0,0.2,1), box-shadow 0.35s ease; }
.trip-card:hover { transform: translateY(-4px); box-shadow: 0 10px 24px rgba(139,92,246,0.08); }
.trip-card:active { transform: scale(0.98); }
.fab-ai-btn { animation: pulseGlow 2.5s ease-in-out infinite; }

/* 小屏适配 */
@media screen and (max-width: 360px) {
  .hero-plan-card { padding: 18px 14px; }
  .hero-start-btn { padding: 12px 32px; font-size: 15px; }
  .guide-card { width: 105px; }
}
</style>
