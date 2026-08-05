<script setup>
/**
 * 酒店预订页 —— 酒店预订对接（B1）
 * 选择城市 → 搜索酒店 → 选入住/晚数/间数 → 创建订单 → 跳订单中心支付
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showToast } from 'vant'
import { getToken } from '../utils/auth'
import { hotelApi } from '../api'

const router = useRouter()
const { t } = useI18n()
const goBack = () => router.back()

/* ==================== 城市选择 ==================== */
const cities = ['北京', '上海', '广州', '深圳', '成都', '西安', '杭州', '重庆', '三亚', '南京']
const currentCity = ref('北京')

/* ==================== 酒店列表 ==================== */
const hotels = ref([])
const loading = ref(false)

const loadHotels = async (city) => {
  loading.value = true
  try {
    const res = await hotelApi.searchHotels(city, { page: 0, size: 20 })
    if (res.code === 0) hotels.value = res.data?.list || []
    else hotels.value = []
  } catch (e) {
    console.log('获取酒店列表失败:', e)
    hotels.value = []
  } finally {
    loading.value = false
  }
}

const switchCity = (city) => {
  currentCity.value = city
  loadHotels(city)
}

/* ==================== 预订弹层 ==================== */
const showBook = ref(false)
const bookingHotel = ref(null)
const checkIn = ref(todayStr(1))
const nights = ref(1)
const rooms = ref(1)

function todayStr(daysAhead) {
  const d = new Date()
  d.setDate(d.getDate() + daysAhead)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const checkOut = computed(() => {
  const d = new Date(checkIn.value + 'T00:00:00')
  d.setDate(d.getDate() + nights.value)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
})

const totalPrice = computed(() => {
  const h = bookingHotel.value
  if (!h) return 0
  return Math.round((Number(h.pricePerNight) || 0) * nights.value * rooms.value)
})

const openBook = (hotel) => {
  bookingHotel.value = hotel
  checkIn.value = todayStr(1)
  nights.value = 1
  rooms.value = 1
  showBook.value = true
}

const confirmBook = async () => {
  if (!getToken()) { showToast(t('common.notLoggedIn')); router.push('/login'); return }
  try {
    const res = await hotelApi.bookHotel({
      hotelId: bookingHotel.value.id,
      checkIn: checkIn.value,
      checkOut: checkOut.value,
      rooms: rooms.value,
    })
    if (res.code === 0) {
      showToast(t('booking.successPending'))
      showBook.value = false
      router.push('/orders')
    } else {
      showToast(res.message || t('booking.fail'))
    }
  } catch (e) {
    showToast(t('booking.failRetry'))
  }
}

onMounted(() => { loadHotels(currentCity.value) })
</script>

<template>
  <div class="booking-page">
    <van-nav-bar :title="t('booking.title')" left-arrow safe-area-inset-top class="nav-bar" @click-left="goBack" />

    <!-- 城市切换 -->
    <div class="city-chips">
      <button
        v-for="c in cities" :key="c"
        :class="['chip', { active: c === currentCity }]"
        @click="switchCity(c)"
      >{{ c }}</button>
    </div>

    <!-- 酒店列表 -->
    <div class="hotel-list">
      <van-skeleton v-if="loading" title avatar row="3" />
      <div v-else-if="hotels.length === 0" class="empty">{{ t('booking.noHotels') }}</div>
      <div v-for="h in hotels" :key="h.id" class="hotel-card" @click="openBook(h)">
        <img :src="h.imageUrl" class="hotel-img" alt="" loading="lazy" />
        <div class="hotel-info">
          <div class="hotel-name">{{ h.name }}</div>
          <div class="hotel-addr">{{ h.address }}</div>
          <div class="hotel-meta">
            <span class="hotel-price">¥{{ h.pricePerNight }}/{{ t('common.night') }}</span>
            <span class="hotel-rating">⭐ {{ h.rating }}</span>
          </div>
        </div>
        <van-button size="small" type="primary" round class="book-btn">{{ t('booking.book') }}</van-button>
      </div>
    </div>

    <!-- 预订弹层 -->
    <van-popup v-model:show="showBook" position="bottom" round :style="{ padding: '20px 16px calc(20px + env(safe-area-inset-bottom))' }">
      <template v-if="bookingHotel">
        <div class="pop-title">{{ bookingHotel.name }}</div>

        <div class="form-row">
          <label>{{ t('booking.checkInDate') }}</label>
          <input v-model="checkIn" type="date" class="date-input" :min="todayStr(0)" />
        </div>
        <div class="form-row">
          <label>{{ t('booking.nights') }}</label>
          <van-stepper v-model="nights" min="1" max="30" />
        </div>
        <div class="form-row">
          <label>{{ t('booking.rooms') }}</label>
          <van-stepper v-model="rooms" min="1" max="9" />
        </div>
        <div class="form-row">
          <span class="form-note">{{ t('booking.checkOutAt') }} {{ checkOut }}</span>
          <span class="pop-price">{{ t('booking.total') }} ¥{{ totalPrice }}</span>
        </div>

        <van-button block type="primary" class="confirm-btn" @click="confirmBook">{{ t('booking.confirmBook') }}</van-button>
      </template>
    </van-popup>
  </div>
</template>

<style scoped>
.booking-page {
  width: 100%;
  min-height: 100vh;
  background: transparent;
  box-sizing: border-box;
  padding-bottom: calc(62px + var(--safe-area-bottom) + 16px);
  overflow-x: hidden;
}
:deep(.nav-bar) {
  background: linear-gradient(135deg, rgba(233,213,255,0.9) 0%, rgba(240,249,255,0.9) 50%, rgba(253,244,255,0.9) 100%);
  backdrop-filter: blur(12px);
}
:deep(.nav-bar .van-nav-bar__title) { color: #1E293B; font-weight: 600; }

/* 城市切换 */
.city-chips { display: flex; flex-wrap: wrap; gap: 8px; padding: 4px 16px 12px; }
.chip {
  padding: 7px 14px; border-radius: 16px; border: 1px solid rgba(139,92,246,0.2);
  background: rgba(255,255,255,0.6); color: #64748B; font-size: 13px; cursor: pointer;
  transition: all 0.2s;
}
.chip.active { background: linear-gradient(135deg, #8B5CF6, #6366F1); color: #fff; border-color: transparent; box-shadow: 0 4px 12px rgba(139,92,246,0.25); }

/* 酒店列表 */
.hotel-list { padding: 0 16px; display: flex; flex-direction: column; gap: 12px; }
.hotel-card {
  display: flex; align-items: center; gap: 12px; background: rgba(255,255,255,0.85);
  border-radius: 16px; padding: 12px; box-shadow: 0 4px 18px rgba(0,0,0,0.05);
  border: 1px solid rgba(139,92,246,0.06); cursor: pointer;
}
.hotel-card:active { transform: scale(0.98); }
.hotel-img { width: 88px; height: 88px; border-radius: 12px; object-fit: cover; background: #f0f0f0; flex-shrink: 0; }
.hotel-info { flex: 1; min-width: 0; }
.hotel-name { font-size: 15px; font-weight: 600; color: #1E293B; margin-bottom: 4px; }
.hotel-addr { font-size: 11px; color: #94A3B8; margin-bottom: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.hotel-meta { display: flex; align-items: center; gap: 10px; }
.hotel-price { font-size: 15px; font-weight: 700; color: #7C3AED; }
.hotel-rating { font-size: 12px; color: #F59E0B; }
.book-btn { flex-shrink: 0; background: linear-gradient(135deg, #8B5CF6, #6366F1) !important; border: none !important; }
.empty { text-align: center; color: #94A3B8; padding: 60px 0; font-size: 14px; }

/* 预订弹层 */
.pop-title { font-size: 16px; font-weight: 700; color: #1E293B; margin-bottom: 16px; }
.form-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid #F1F5F9; font-size: 14px; color: #334155; }
.form-note { color: #94A3B8; font-size: 13px; }
.pop-price { font-size: 17px; font-weight: 700; color: #7C3AED; }
.date-input { border: 1px solid #E2E8F0; border-radius: 8px; padding: 6px 10px; font-size: 14px; color: #334155; background: #fff; }
.confirm-btn { margin-top: 20px; border-radius: 22px !important; background: linear-gradient(135deg, #8B5CF6, #6366F1) !important; border: none !important; font-weight: 600; }
</style>
