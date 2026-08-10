<script setup>
/**
 * TripCalendarView — 行程日历视图（B3）
 * 以「天为单位的纵向日程卡」展示行程（移动端短途游，不做月历网格）：
 * 顶部日期条（Day N · 日期 · 星期），下方按天展示 上午/下午/晚上 时段、餐饮、酒店。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showToast } from 'vant'
import { useTripStore } from '../stores/trip'
import { planApi } from '../api'
import EmptyState from '../components/EmptyState.vue'

const router = useRouter()
const route = useRoute()
const store = useTripStore()
const { t } = useI18n()

const plan = ref(null)
const loading = ref(true)

const goBack = () => router.back()

/* 日期计算：优先用行程的 travelDate，否则以今天为第 1 天顺延 */
function computeDate(offset, base) {
  const d = base ? new Date(base) : new Date()
  d.setDate(d.getDate() + offset)
  const weekdays = [t('calendar.week0'), t('calendar.week1'), t('calendar.week2'), t('calendar.week3'), t('calendar.week4'), t('calendar.week5'), t('calendar.week6')]
  return { dateStr: t('calendar.dateStr', { month: d.getMonth() + 1, day: d.getDate() }), weekday: t('calendar.weekday', { day: weekdays[d.getDay()] }) }
}

const days = computed(() => {
  if (!plan.value) return []
  const dayPlans = plan.value.dayPlans || []
  let base = null
  try {
    if (plan.value.travelDate) base = new Date(plan.value.travelDate)
  } catch (e) { base = null }
  return dayPlans.map((dp, i) => {
    const date = computeDate(i, base)
    return { ...dp, index: i, dateStr: date.dateStr, weekday: date.weekday }
  })
})

const totalCost = computed(() => {
  const bd = plan.value?.budgetDetail
  return bd?.total || bd?.accommodation ? (bd.total || 0) : null
})

const stripTags = (name) => (name || '').replace(/【[^】]*】/g, '').trim()

const goMap = () => {
  // 带 destination/savedPlanId 跳地图，避免 AgentMapView 因无参数被弹回 /trips
  const dest = plan.value?.destination || ''
  const id = route.query.savedPlanId || store.state.savedPlanId || ''
  router.push({ path: '/agent-map', query: { destination: dest, savedPlanId: id } })
}

onMounted(async () => {
  try {
    if (store.state.planData) {
      plan.value = store.state.planData
    } else {
      const id = route.query.savedPlanId || store.state.savedPlanId
      if (id) {
        const res = await planApi.getPlanById(id)
        if (res.code === 0 && res.data) {
          plan.value = res.data.planData || res.data.planJson
        }
      }
    }
    if (typeof plan.value === 'string') {
      try { plan.value = JSON.parse(plan.value) } catch (e) { plan.value = null }
    }
  } catch (e) {
    console.error('加载行程失败:', e)
    plan.value = null
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="calendar-page">
    <van-nav-bar :title="t('calendar.title')" left-arrow safe-area-inset-top class="nav-bar" @click-left="goBack" />

    <div v-if="loading" class="center"><van-loading color="#8B5CF6" size="28" /></div>

    <div v-else-if="!plan || !plan.dayPlans || plan.dayPlans.length === 0" class="center">
      <EmptyState icon="calendar-o" :title="t('calendar.noPlan')" :desc="t('calendar.noPlanHint')" />
    </div>

    <div v-else class="content">
      <!-- 概览卡 -->
      <div class="overview-card">
        <div class="ov-title">{{ plan.destination }} · {{ plan.days }} {{ t('common.days') }}</div>
        <div class="ov-meta">
          <span v-if="plan.people">{{ plan.people }} {{ t('common.people') }}</span>
          <span v-if="totalCost != null">{{ t('calendar.budget') }} ¥{{ totalCost }}</span>
        </div>
        <p v-if="plan.overview" class="ov-desc">{{ plan.overview }}</p>
        <van-button size="mini" round plain type="primary" class="map-btn" @click="goMap">{{ t('calendar.viewMap') }}</van-button>
      </div>

      <!-- 日期条 -->
      <div class="date-strip">
        <div v-for="(d, i) in days" :key="i" class="date-chip" :class="{ active: i === 0 }">
          <div class="chip-day">Day {{ i + 1 }}</div>
          <div class="chip-date">{{ d.dateStr }}</div>
          <div class="chip-week">{{ d.weekday }}</div>
        </div>
      </div>

      <!-- 按天日程卡 -->
      <div v-for="(d, i) in days" :key="i" class="day-card">
        <div class="day-head">
          <span class="day-badge">Day {{ d.index + 1 }}</span>
          <span class="day-date">{{ d.dateStr }} {{ d.weekday }}</span>
        </div>
        <div v-if="d.day_title" class="day-title">{{ d.day_title }}</div>

        <div v-for="(slot, si) in (d.timeSlots || [])" :key="si" class="slot-row">
          <div class="slot-label" :class="`t-${slot.time_of_day || 'am'}`">{{ slot.time_of_day || t('calendar.morning') }}</div>
          <div class="slot-body">
            <div class="slot-name">{{ stripTags(slot.attraction) }}</div>
            <div class="slot-meta">
              <span v-if="slot.time">{{ slot.time }}</span>
              <span v-if="slot.duration">{{ slot.duration }}</span>
              <span v-if="slot.cost">¥{{ slot.cost }}</span>
              <span v-if="slot.transport && slot.transport !== '步行'">{{ slot.transport }}</span>
            </div>
          </div>
        </div>

        <div v-if="d.meals && d.meals.length" class="meals-block">
          <div class="meals-title">🍽 {{ t('calendar.meals') }}</div>
          <div v-for="(m, mi) in d.meals" :key="mi" class="meal">{{ m }}</div>
        </div>
      </div>

      <!-- 酒店 -->
      <div v-if="plan.hotels && plan.hotels.length" class="hotel-card">
        <div class="hotel-title">🏨 {{ t('calendar.hotels') }}</div>
        <div v-for="(h, hi) in plan.hotels" :key="hi" class="hotel-item">
          <span class="hotel-name">{{ h.name }}</span>
          <span class="hotel-price">¥{{ h.price_per_night || h.total_price || '' }}/{{ t('common.night') }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.calendar-page {
  width: 100%;
  min-height: 100vh;
  background: transparent;
  box-sizing: border-box;
  padding-bottom: calc(60px + var(--safe-area-bottom));
  overflow-x: hidden;
}
.nav-bar { background: rgba(255,255,255,0.6); backdrop-filter: blur(16px); }
.center { display: flex; justify-content: center; padding: 80px 0; }

.content { max-width: 480px; margin: 0 auto; padding: 8px 16px; }

.overview-card {
  background: linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%);
  border-radius: 18px; padding: 18px 18px; color: #fff; margin-bottom: 14px;
  box-shadow: 0 8px 24px rgba(139,92,246,0.28);
}
.ov-title { font-size: 21px; font-weight: 700; margin-bottom: 4px; }
.ov-meta { display: flex; gap: 14px; font-size: 13px; opacity: 0.9; margin-bottom: 8px; }
.ov-desc { font-size: 13px; line-height: 1.6; opacity: 0.92; margin-bottom: 10px; }
.map-btn { background: rgba(255,255,255,0.16) !important; border: 1px solid rgba(255,255,255,0.4) !important; color: #fff !important; }

.date-strip { display: flex; gap: 8px; overflow-x: auto; margin-bottom: 14px; padding-bottom: 4px; }
.date-chip {
  flex-shrink: 0; background: rgba(255,255,255,0.85); border-radius: 12px; padding: 8px 14px;
  text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}
.date-chip.active { background: linear-gradient(135deg, #8B5CF6, #6366F1); color: #fff; }
.chip-day { font-size: 13px; font-weight: 700; }
.chip-date { font-size: 12px; opacity: 0.85; }
.chip-week { font-size: 11px; opacity: 0.7; }

.day-card {
  background: rgba(255,255,255,0.88); border-radius: 16px; padding: 16px;
  margin-bottom: 12px; box-shadow: 0 4px 16px rgba(0,0,0,0.05);
  border: 1px solid rgba(139,92,246,0.08);
}
.day-head { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.day-badge { font-size: 12px; font-weight: 700; color: #fff; background: linear-gradient(135deg, #8B5CF6, #6366F1); padding: 3px 10px; border-radius: 10px; }
.day-date { font-size: 13px; color: #64748B; }
.day-title { font-size: 15px; font-weight: 600; color: #1E293B; margin-bottom: 10px; }

.slot-row { display: flex; gap: 10px; padding: 8px 0; border-bottom: 1px dashed #F1F5F9; }
.slot-row:last-child { border-bottom: none; }
.slot-label { font-size: 11px; font-weight: 600; width: 42px; flex-shrink: 0; padding-top: 2px; }
.t-上午 { color: #F59E0B; }
.t-下午 { color: #6366F1; }
.t-晚上 { color: #8B5CF6; }
.slot-name { font-size: 14px; font-weight: 600; color: #1E293B; margin-bottom: 3px; }
.slot-meta { display: flex; flex-wrap: wrap; gap: 8px; font-size: 11px; color: #94A3B8; }

.meals-block { margin-top: 10px; }
.meals-title { font-size: 13px; font-weight: 600; color: #64748B; margin-bottom: 4px; }
.meal { font-size: 12px; color: #64748B; line-height: 1.7; }

.hotel-card { background: rgba(255,255,255,0.88); border-radius: 16px; padding: 16px; box-shadow: 0 4px 16px rgba(0,0,0,0.05); }
.hotel-title { font-size: 14px; font-weight: 700; color: #1E293B; margin-bottom: 8px; }
.hotel-item { display: flex; justify-content: space-between; padding: 6px 0; font-size: 13px; color: #334155; }
.hotel-price { color: #7C3AED; font-weight: 600; }
</style>
