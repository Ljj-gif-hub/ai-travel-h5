<script setup>
/**
 * 我的线路 —— 用户生成的行程规划列表页（携程风格截图2）
 * 顶部分类 Tab（全部/待出行/已出行）+ 线路卡片（封面/标题/meta/时间）+ 删除 + 底部「新建线路」FAB
 * 从 /trips（行程 Tab）的「我的线路」入口进入；行程数据全部移到本页，不再在 /trips 展示
 */
import { ref, computed, onMounted, onActivated, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showToast, showConfirmDialog } from 'vant'
import { getToken } from '../utils/auth'
import { planApi } from '../api'
import EmptyState from '../components/EmptyState.vue'

defineOptions({ name: 'MyRoutesView' })

const router = useRouter()
const { t } = useI18n()

const plans = ref([])
const isLoading = ref(false)
const loadError = ref(false)
const activeTab = ref('all')

/* ---- 图片映射：城市封面 + 景点图，供线路卡封面兜底（与 /trips 共用逻辑） ---- */
const cityImageMap = ref({})
const loadCityImageMap = async () => {
  try {
    const resp = await fetch('/city-images.json')
    if (resp.ok) Object.assign(cityImageMap.value, await resp.json())
  } catch {}
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

const toShortCity = (name) => (name || '').replace(/市$/, '')

/* ---- 工具函数 ---- */
const getAttractions = (plan) => {
  if (!plan.planData?.dayPlans) return []
  const arr = []
  plan.planData.dayPlans.forEach(day => {
    ;(day.timeSlots || []).forEach(slot => { if (slot.attraction) arr.push(slot.attraction) })
  })
  return arr
}

/** 状态推导：无内容=草稿；有出发日且早于今天=已出行；否则=待出行（后端未返回 travelDate 时默认待出行） */
const planStatus = (plan) => {
  if (!plan.planData?.dayPlans?.length) return 'draft'
  if (plan.travelDate) {
    const d = new Date(plan.travelDate); d.setHours(0, 0, 0, 0)
    const today = new Date(); today.setHours(0, 0, 0, 0)
    if (d < today) return 'done'
  }
  return 'upcoming'
}

const tabs = computed(() => [
  { key: 'all', title: t('trips.routesAll') },
  { key: 'upcoming', title: t('trips.statusUpcoming') },
  { key: 'done', title: t('trips.statusDone') },
])

const filteredPlans = computed(() => {
  if (activeTab.value === 'all') return plans.value
  if (activeTab.value === 'upcoming') return plans.value.filter(p => ['upcoming', 'draft'].includes(planStatus(p)))
  return plans.value.filter(p => planStatus(p) === 'done')
})

/** 封面：城市图 → 景点图 → 渐变占位 */
const planCover = (plan) => {
  const dest = toShortCity(plan.destination)
  if (dest && cityImageMap.value[dest]) return cityImageMap.value[dest]
  for (const a of getAttractions(plan)) if (cityImageMap.value[a]) return cityImageMap.value[a]
  return ''
}

const planTitle = (plan) => plan.planData?.title || plan.title || t('trips.dayTrip', { dest: plan.destination || t('trips.unknown'), days: plan.days || 1 })

const formatDate = (str) => {
  if (!str) return ''
  const d = new Date(str)
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

const planMeta = (plan) => {
  const parts = []
  const days = plan.days || 0
  const spots = getAttractions(plan).length
  if (days) parts.push(t('trips.totalDays', { days }))
  if (spots) parts.push(t('trips.spotCount', { count: spots }))
  return parts.join(' · ')
}

const planDate = (plan) => t('trips.autoSaved', { date: formatDate(plan.createdAt) })

/* ---- 数据加载 ---- */
const loadPlans = async () => {
  isLoading.value = true
  loadError.value = false
  try {
    const res = await planApi.getSavedPlans()
    if (res.code === 0) plans.value = (res.data || []).map(p => ({ ...p, _status: planStatus(p) }))
    else plans.value = []
  } catch (e) {
    plans.value = []
    if (e?.response?.status === 502) loadError.value = true
    else showToast(t('trips.loadFailed'))
  } finally { isLoading.value = false }
}

/* ---- 导航 / 操作 ---- */
const goBack = () => { if (window.history.length <= 1) router.push('/trips'); else router.back() }
const viewTrip = (plan) => { if (!plan?.id) return; router.push({ path: '/agent-map', query: { savedPlanId: plan.id } }) }
const createRoute = () => { router.push('/agent-planner') }
const onRightIcon = () => { showToast(t('trips.featureWip')) }

const confirmDelete = async (plan) => {
  try {
    await showConfirmDialog({
      title: t('trips.deleteTrip'),
      message: t('trips.confirmDeleteMsg', { name: plan?.destination || t('trips.unknown') }),
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel'),
    })
    if (!plan?.id) return
    const res = await planApi.deletePlan(plan.id)
    if (res.code === 0) {
      showToast(t('trips.deleted'))
      plans.value = plans.value.filter(p => p.id !== plan.id)
    } else {
      showToast(res.message || t('trips.deleteFailed'))
    }
  } catch (e) { /* 用户取消 */ }
}

/* ---- 生命周期 ---- */
let dataLoaded = false
onMounted(() => {
  loadCityImageMap()
  if (getToken()) { loadPlans(); dataLoaded = true }
})
onActivated(() => {
  loadCityImageMap()
  if (!getToken()) return
  if (!dataLoaded) { loadPlans(); dataLoaded = true }
  else loadPlans() // 从行程生成/编辑返回时刷新，保证最新
})
onDeactivated(() => { isLoading.value = false; loadError.value = false; dataLoaded = false })
</script>

<template>
  <div class="my-routes-page">
    <van-nav-bar :title="t('trips.myRoutes')" left-arrow safe-area-inset-top class="nav-bar" @click-left="goBack">
      <template #right>
        <div class="nav-right" @click="onRightIcon"><van-icon name="location-o" size="20" color="var(--text-primary)" /></div>
      </template>
    </van-nav-bar>

    <!-- 分类 Tab -->
    <div class="tabs">
      <span v-for="tab in tabs" :key="tab.key" class="tab" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">{{ tab.title }}</span>
    </div>

    <!-- 列表 -->
    <div class="list-wrap">
      <template v-if="isLoading">
        <div v-for="i in 3" :key="i" class="route-skeleton" />
      </template>

      <div v-else-if="loadError" class="error-state">
        <van-icon name="warn-o" size="44" color="var(--text-hint)" />
        <p class="error-text">{{ t('trips.loadFailedRetry') }}</p>
        <van-button round plain size="small" class="retry-btn" @click="loadPlans">{{ t('common.tryAgain') }}</van-button>
      </div>

      <EmptyState
        v-else-if="filteredPlans.length === 0"
        icon="todo-list-o"
        :title="t('trips.noSavedPlans')"
        :desc="t('trips.noSavedPlansHint')"
        :btn-text="t('trips.goCreatePlan')"
        btn-type="gradient"
        @btn-click="createRoute"
      />

      <template v-else>
        <div v-for="plan in filteredPlans" :key="plan.id" class="route-card" @click="viewTrip(plan)">
          <div class="route-cover">
            <img v-if="planCover(plan)" :src="planCover(plan)" class="route-cover-img" loading="lazy" @error="e => (e.target.style.display = 'none')" />
            <div v-else class="route-cover-fb"><van-icon name="photo-o" size="24" color="rgba(255,255,255,0.85)" /></div>
          </div>
          <div class="route-info">
            <div class="route-title">{{ planTitle(plan) }}</div>
            <div v-if="planMeta(plan)" class="route-meta">{{ planMeta(plan) }}</div>
            <div class="route-date">{{ planDate(plan) }}</div>
          </div>
          <div class="route-more" @click.stop="confirmDelete(plan)"><van-icon name="ellipsis" size="22" color="var(--text-hint)" /></div>
        </div>
      </template>
    </div>

    <Transition name="fab-pop">
      <button class="fab-create btn-tap-scale" @click="createRoute">
        <van-icon name="plus" size="16" color="#fff" />
        <span>{{ t('trips.createRoute') }}</span>
      </button>
    </Transition>
  </div>
</template>

<style scoped>
.my-routes-page {
  width: 100%; min-height: 100vh; background: transparent;
  padding-bottom: calc(110px + var(--safe-area-bottom, 0px));
  box-sizing: border-box;
}
:deep(.nav-bar) {
  background: linear-gradient(160deg, rgba(255,255,255,0.55) 0%, rgba(255,255,255,0.2) 40%, rgba(255,255,255,0.55) 100%), rgba(255,255,255,0.6) !important;
  backdrop-filter: blur(22px) saturate(180%); -webkit-backdrop-filter: blur(22px) saturate(180%);
  border-bottom: 0.5px solid rgba(255,255,255,0.55) !important; box-shadow: inset 0 1px 0 rgba(255,255,255,0.65) !important;
}
:deep(.nav-bar .van-nav-bar__title) { color: var(--text-primary); font-weight: 600; }
.nav-right { width: 34px; height: 34px; display: flex; align-items: center; justify-content: center; }

.tabs { display: flex; gap: 26px; padding: 6px 20px 12px; }
.tab { font-size: 15px; color: var(--text-secondary); font-weight: 500; position: relative; padding-bottom: 6px; cursor: pointer; transition: color 0.2s; }
.tab.active { color: #7C3AED; font-weight: 700; }
.tab.active::after { content: ''; position: absolute; left: 0; right: 0; bottom: 0; height: 3px; border-radius: 2px; background: linear-gradient(90deg, #8B5CF6, #6366F1); }

.list-wrap { padding: 4px 16px 16px; }
.route-card {
  display: flex; gap: 12px; align-items: center; padding: 12px; margin-bottom: 12px; cursor: pointer;
  background: linear-gradient(160deg, rgba(255,255,255,0.65) 0%, rgba(255,255,255,0.12) 40%, rgba(255,255,255,0.3) 100%), rgba(255,255,255,0.55);
  backdrop-filter: blur(14px) saturate(160%); -webkit-backdrop-filter: blur(14px) saturate(160%);
  border: 1px solid rgba(255,255,255,0.6); border-radius: 16px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.6), 0 2px 10px rgba(0,0,0,0.03);
  transition: transform 0.2s;
}
.route-card:active { transform: scale(0.985); }
.route-cover { width: 120px; height: 88px; border-radius: 12px; overflow: hidden; flex-shrink: 0; position: relative; background: #e9e6f5; }
.route-cover-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.route-cover-fb { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #8B5CF6, #6366F1); }
.route-info { flex: 1; min-width: 0; }
.route-title { font-size: 15px; font-weight: 700; color: var(--text-primary); line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.route-meta { font-size: 12px; color: var(--text-hint); margin-top: 4px; }
.route-date { font-size: 12px; color: #94a3b8; margin-top: 4px; }
.route-more { flex-shrink: 0; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: rgba(148,163,184,0.08); }
.route-more:active { background: rgba(148,163,184,0.16); }

.route-skeleton { display: flex; gap: 12px; padding: 12px; margin-bottom: 12px; border-radius: 16px; background: rgba(255,255,255,0.5); animation: shimmer 1.8s ease-in-out infinite; }
.route-skeleton::before { content: ''; width: 120px; height: 88px; border-radius: 12px; background: #ecebf3; flex-shrink: 0; }
.route-skeleton::after { content: ''; flex: 1; height: 60px; background: linear-gradient(90deg, #f0eff6 25%, #f7f6fb 50%, #f0eff6 75%); border-radius: 8px; }
@keyframes shimmer { 0% { opacity: 0.4; } 50% { opacity: 0.8; } 100% { opacity: 0.4; } }

.error-state { display: flex; flex-direction: column; align-items: center; padding: 60px 20px; text-align: center; }
.error-text { font-size: 14px; color: var(--text-hint); margin: 12px 0 16px; }
.retry-btn { border-radius: 20px !important; color: #7C3AED !important; border-color: #C4B5FD !important; }

.fab-create {
  position: fixed; bottom: calc(24px + var(--safe-area-bottom, 0px)); left: 50%; transform: translateX(-50%);
  z-index: 9995; display: flex; align-items: center; gap: 6px; padding: 12px 28px;
  border: none; border-radius: 24px; background: linear-gradient(135deg, #8B5CF6, #6366F1);
  color: #fff; font-size: 15px; font-weight: 700; box-shadow: 0 8px 24px rgba(139,92,246,0.4); cursor: pointer;
}
.fab-create:active { transform: translateX(-50%) scale(0.95); }
.fab-pop-enter-active { transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); }
.fab-pop-leave-active { transition: all 0.2s cubic-bezier(0.4, 0, 1, 1); }
.fab-pop-enter-from { opacity: 0; transform: translateX(-50%) scale(0.4); }
.fab-pop-leave-to { opacity: 0; transform: translateX(-50%) scale(0.6); }
</style>
