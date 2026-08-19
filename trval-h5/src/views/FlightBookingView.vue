<script setup>
/**
 * 机票预订页 —— 机票预订对接（后端 /api/flight，mock/real 供应方）
 * 选择出发/到达城市 + 日期 → 搜索航班 → 选舱位 → 创建订单 → 跳订单中心支付
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showToast } from 'vant'
import { getToken } from '../utils/auth'
import { flightApi } from '../api'

const router = useRouter()
const { t } = useI18n()
const goBack = () => router.back()

const CITIES = ['北京', '上海', '广州', '深圳', '成都', '西安', '杭州', '重庆', '三亚', '南京', '东京', '巴黎', '曼谷', '新加坡']

/* ==================== 查询条件 ==================== */
const fromCity = ref('北京')
const toCity = ref('上海')
const passengers = ref(1)
const date = ref(todayStr(7))

function todayStr(daysAhead) {
  const d = new Date()
  d.setDate(d.getDate() + daysAhead)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const showDatePicker = ref(false)
const minDate = new Date()
const maxDate = new Date(Date.now() + 60 * 24 * 3600 * 1000)

/** van-date-picker 确认回调：把 selectedValues 数组转成 YYYY-MM-DD 字符串（date 保持字符串） */
const onDateConfirm = ({ selectedValues }) => {
  if (Array.isArray(selectedValues) && selectedValues.length === 3) {
    const [y, m, d] = selectedValues
    date.value = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
  }
  showDatePicker.value = false
  if (flights.value.length) search()
}

const swapCities = () => {
  const t = fromCity.value
  fromCity.value = toCity.value
  toCity.value = t
  if (flights.value.length) search()
}

/** 简单城市选择：点击循环切换（避免引入额外弹层） */
const pickCity = (current, list) => {
  const idx = list.indexOf(current)
  return list[(idx + 1) % list.length]
}

/* ==================== 航班列表 ==================== */
const flights = ref([])
const loading = ref(false)

const search = async () => {
  if (!fromCity.value.trim() || !toCity.value.trim()) { showToast(t('booking.selectCities')); return }
  if (fromCity.value.trim() === toCity.value.trim()) { showToast(t('booking.sameCity')); return }
  loading.value = true
  try {
    const res = await flightApi.searchFlights(fromCity.value, toCity.value, date.value)
    flights.value = (res && res.code === 0) ? (res.data || []) : []
    if (!flights.value.length) showToast(t('booking.noFlights'))
  } catch (e) {
    console.error('航班查询失败:', e)
    flights.value = []
    showToast(t('booking.searchFailRetry'))
  } finally {
    loading.value = false
  }
}

/* ==================== 下单 ==================== */
const showBook = ref(false)
// BUGID DBLCLICK-1 修复：提交锁，防止弱网双击重复创建订单
const submitting = ref(false)
const chosen = ref(null)

const openBook = (f) => {
  chosen.value = f
  showBook.value = true
}

const totalPrice = computed(() => (chosen.value ? chosen.value.price * passengers.value : 0))

const confirmBook = async () => {
  if (submitting.value) return // BUGID DBLCLICK-1：提交中防重入
  if (!getToken()) { showToast(t('common.notLoggedIn')); router.push('/login'); return }
  const f = chosen.value
  submitting.value = true
  try {
    const res = await flightApi.bookFlight({
      flightNo: f.flightNo,
      fromCity: f.fromCity,
      toCity: f.toCity,
      date: f.date, // 金额以服务端重新报价为准，客户端不传 price
      passengers: passengers.value,
      departureTime: `${f.date}T${f.departTime}:00`,
      arrivalTime: `${f.date}T${f.arrivalTime}:00`,
    })
    if (res && res.code === 0) {
      showToast(t('booking.orderSuccessPending'))
      showBook.value = false
      router.push('/orders')
    } else {
      showToast((res && res.message) || t('booking.orderFail'))
    }
  } catch (e) {
    showToast(t('booking.orderFailRetry'))
  } finally {
    submitting.value = false
  }
}

onMounted(search)
</script>

<template>
  <div class="flight-page">
    <van-nav-bar :title="t('booking.flightTitle')" left-arrow safe-area-inset-top class="nav-bar" @click-left="goBack" />

    <!-- 查询条件 -->
    <div class="search-panel">
      <div class="route-row">
        <div class="route-city" @click="fromCity = pickCity(fromCity, CITIES)">
          <span class="label">{{ t('booking.depart') }}</span>
          <span class="city-name">{{ fromCity }}</span>
        </div>
        <button class="swap-btn" @click="swapCities">
          <van-icon name="exchange" size="18" color="#9333ea" />
        </button>
        <div class="route-city" @click="toCity = pickCity(toCity, CITIES)">
          <span class="label">{{ t('booking.arrive') }}</span>
          <span class="city-name">{{ toCity }}</span>
        </div>
      </div>

      <div class="meta-row">
        <div class="meta-item" @click="showDatePicker = true">
          <van-icon name="calendar-o" color="#9333ea" />
          <span>{{ date }}</span>
        </div>
        <div class="meta-item">
          <van-icon name="friends-o" color="#9333ea" />
          <van-stepper v-model="passengers" min="1" max="9" button-size="22" />
        </div>
      </div>

      <van-button block type="primary" round class="search-btn" :loading="loading" @click="search">
        {{ loading ? t('booking.searching') : t('booking.searchFlights') }}
      </van-button>
    </div>

    <!-- 城市快速选择 -->
    <div class="city-chips">
      <button
        v-for="c in CITIES" :key="c"
        :class="['chip', { active: c === fromCity }]"
        @click="fromCity = c"
      >{{ c }}</button>
    </div>

    <!-- 航班列表 -->
    <div class="flight-list">
      <van-skeleton v-if="loading" title avatar row="3" />
      <div v-else-if="flights.length === 0" class="empty">{{ t('booking.noFlightsAvailable') }}</div>
      <div v-for="f in flights" :key="f.flightNo" class="flight-card" @click="openBook(f)">
        <div class="flight-main">
          <div class="time-pair">
            <div class="time">{{ f.departTime }}</div>
            <div class="city">{{ f.fromCity }}</div>
          </div>
          <div class="duration">
            <span class="airline">{{ f.airline }} {{ f.flightNo }}</span>
            <span class="dur">{{ Math.floor(f.durationMin / 60) }}h{{ f.durationMin % 60 }}m</span>
          </div>
          <div class="time-pair right">
            <div class="time">{{ f.arrivalTime }}</div>
            <div class="city">{{ f.toCity }}</div>
          </div>
        </div>
        <div class="flight-foot">
          <span class="cabin">{{ f.cabin }}</span>
          <span class="price">¥{{ f.price }}<span class="per">/{{ t('common.people') }}</span></span>
        </div>
      </div>
    </div>

    <!-- 日期选择（date 保持字符串，用 @confirm 手动转数组） -->
    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-date-picker
        :title="t('booking.selectDate')"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

    <!-- 确认下单 -->
    <van-popup v-model:show="showBook" position="bottom" round>
      <div class="book-panel">
        <div class="book-title">{{ t('booking.confirmFlight') }}</div>
        <div v-if="chosen" class="book-flight">
          <div class="bf-route">{{ chosen.airline }} {{ chosen.flightNo }} · {{ chosen.fromCity }} → {{ chosen.toCity }}</div>
          <div class="bf-time">{{ chosen.date }} {{ chosen.departTime }} - {{ chosen.arrivalTime }} · {{ chosen.cabin }}</div>
          <div class="bf-price">{{ t('booking.total') }} <span class="big">¥{{ totalPrice }}</span>（{{ passengers }} {{ t('common.people') }} × ¥{{ chosen.price }}）</div>
        </div>
        <div class="book-actions">
          <van-button round plain @click="showBook = false">{{ t('common.cancel') }}</van-button>
          <van-button round type="primary" :loading="submitting" :disabled="submitting" @click="confirmBook">{{ t('booking.goPay') }}</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.flight-page {
  width: 100%;
  min-height: 100vh;
  background: transparent;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

:deep(.nav-bar) {
  background: linear-gradient(135deg, rgba(219,234,254,0.9) 0%, rgba(240,249,255,0.9) 50%, rgba(243,232,255,0.9) 100%);
  backdrop-filter: blur(12px);
}

.search-panel {
  margin: 12px 16px;
  padding: 16px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.05);
}

.route-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.route-city {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.route-city .label { font-size: 11px; color: #9ca3af; }
.route-city .city-name { font-size: 20px; font-weight: 700; color: #1f2937; }

.swap-btn {
  width: 36px; height: 36px;
  border: none; background: #f3e8ff;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-top: 1px dashed #e5e7eb;
  border-bottom: 1px dashed #e5e7eb;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #374151;
}

.search-btn { margin-top: 14px; background: linear-gradient(135deg, #9333ea, #8b5cf6); border: none; }

.city-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 16px 8px;
}

.chip {
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #4b5563;
  font-size: 12px;
  padding: 5px 12px;
  border-radius: 20px;
}
.chip.active {
  background: #9333ea;
  color: #fff;
  border-color: #9333ea;
}

.flight-list { padding: 8px 16px 24px; }

.flight-card {
  background: #fff;
  border-radius: 14px;
  padding: 12px 14px;
  margin-bottom: 10px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.flight-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.time-pair { display: flex; flex-direction: column; }
.time-pair.right { text-align: right; }
.time { font-size: 20px; font-weight: 700; color: #111827; }
.city { font-size: 12px; color: #9ca3af; }

.duration {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.airline { font-size: 12px; color: #6b7280; }
.dur { font-size: 11px; color: #9ca3af; }

.flight-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f3f4f6;
}
.cabin { font-size: 12px; color: #9333ea; background: #f3e8ff; padding: 2px 8px; border-radius: 10px; }
.price { font-size: 18px; font-weight: 700; color: #ef4444; }
.per { font-size: 12px; color: #9ca3af; font-weight: 400; }

.empty { text-align: center; color: #9ca3af; padding: 40px 0; font-size: 14px; }

.book-panel { padding: 20px 16px calc(16px + env(safe-area-inset-bottom)); }
.book-title { font-size: 16px; font-weight: 700; margin-bottom: 12px; }
.book-flight {
  background: #f9fafb;
  border-radius: 12px;
  padding: 14px;
  margin-bottom: 16px;
}
.bf-route { font-size: 15px; font-weight: 600; color: #111827; margin-bottom: 6px; }
.bf-time { font-size: 13px; color: #6b7280; margin-bottom: 8px; }
.bf-price { font-size: 13px; color: #374151; }
.bf-price .big { font-size: 22px; font-weight: 700; color: #ef4444; }
.book-actions { display: flex; gap: 12px; }
.book-actions .van-button { flex: 1; }
</style>
