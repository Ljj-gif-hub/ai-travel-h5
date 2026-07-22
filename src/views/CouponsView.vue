<script setup>
/**
 * 优惠券 —— 统一淡紫色品牌 UI 规范
 * 分类 Tab：未使用 / 已使用 / 已过期
 * 修复：补充缺失空状态图标 + 引导按钮
 */
import { ref, onMounted, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getToken } from '../utils/auth'
import { couponApi } from '../api'
import EmptyState from '../components/EmptyState.vue'

const router = useRouter()

const goBack = () => { router.back() }

/* ==================== 分类 Tab ==================== */
const activeTab = ref('unused')
const tabs = [
  { name: '未使用', key: 'unused' },
  { name: '已使用', key: 'used' },
  { name: '已过期', key: 'expired' },
]

/* ==================== 列表数据 ==================== */
const coupons = ref([])
const isLoading = ref(false)
const loadError = ref(false)

const loadCoupons = async (status) => {
  isLoading.value = true
  loadError.value = false
  try {
    const response = await couponApi.getCoupons(status)
    if (response.code === 0) {
      coupons.value = response.data || []
    } else {
      coupons.value = []
    }
  } catch (error) {
    console.log('获取优惠券列表失败:', error)
    coupons.value = []
    if (error?.response?.status === 502) loadError.value = true
  } finally {
    isLoading.value = false
  }
}

const handleTabChange = (key) => {
  activeTab.value = key
  coupons.value = []
  loadCoupons(key)
}

const handleUse = async (coupon) => {
  try {
    const response = await couponApi.useCoupon(coupon.id, null)
    if (response.code === 0) {
      showToast('使用成功')
      loadCoupons(activeTab.value)
    } else {
      showToast(response.message || '使用失败')
    }
  } catch (error) { showToast('使用失败') }
}

const handleGoExplore = () => { router.push('/') }

onMounted(() => {
  if (getToken()) loadCoupons('unused')
})

/* 【性能优化】离开时清理状态 */
onDeactivated(() => { isLoading.value = false; loadError.value = false })
</script>

<template>
  <div class="page-shell">
    <!-- 顶部导航 -->
    <van-nav-bar title="优惠券" left-text="返回" left-arrow safe-area-inset-top class="nav-bar" @click-left="goBack" />

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
            <van-button round plain class="retry-btn" size="small" @click="loadCoupons(activeTab)">重新加载</van-button>
          </div>

          <!-- 空状态 -->
          <EmptyState
            v-else-if="coupons.length === 0"
            icon="coupon-o"
            title="暂无优惠券"
            desc="参与活动即可领取专属优惠券"
            btn-text="去看看"
            btn-type="outline"
            @btn-click="handleGoExplore"
          />

          <!-- 优惠券列表 -->
          <div v-else class="coupons-list">
            <div
              v-for="coupon in coupons"
              :key="coupon.id"
              class="coupon-card"
              :class="{ 'is-inactive': activeTab !== 'unused' }"
            >
              <div class="coupon-left">
                <div class="coupon-value">
                  <span class="value-symbol">¥</span>
                  <span class="value-num">{{ coupon.value }}</span>
                </div>
                <div v-if="coupon.minAmount" class="coupon-condition">满{{ coupon.minAmount }}可用</div>
              </div>
              <div class="coupon-right">
                <div class="coupon-title">{{ coupon.title }}</div>
                <div class="coupon-category">{{ coupon.category || '通用' }}</div>
                <div class="coupon-date">有效期至 {{ coupon.validUntil }}</div>
              </div>
              <div v-if="activeTab === 'unused'" class="coupon-action">
                <van-button class="use-btn" size="small" @click="handleUse(coupon)">立即使用</van-button>
              </div>
              <div v-else class="coupon-status">
                <van-icon :name="activeTab === 'used' ? 'checked' : 'clock-o'" size="20" :color="activeTab === 'used' ? '#22c55e' : '#F59E0B'" />
              </div>
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

/* ==================== 优惠券列表 ==================== */
.coupons-list { display: flex; flex-direction: column; gap: 16px; }

.coupon-card {
  display: flex;
  background: #fff;
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 4px 18px rgba(0,0,0,0.04);
  border: 1px solid rgba(139,92,246,0.06);
  transition: transform 0.2s;
}
.coupon-card:active { transform: scale(0.98); }
.coupon-card.is-inactive { opacity: 0.5; filter: grayscale(30%); }

.coupon-left {
  width: 115px;
  min-width: 115px;
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
  position: relative;
}

/* 锯齿效果 - 使用页面顶部渐变颜色作为背景色 */
.coupon-left::after {
  content: '';
  position: absolute;
  right: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 16px;
  height: 16px;
  background: #faf5ff;
  border-radius: 50%;
}

.coupon-value { display: flex; align-items: baseline; }
.value-symbol { font-size: 18px; color: #fff; font-weight: 600; }
.value-num { font-size: 38px; color: #fff; font-weight: 700; line-height: 1; }
.coupon-condition { font-size: 12px; color: rgba(255,255,255,0.8); margin-top: 4px; }

.coupon-right { flex: 1; padding: 16px; display: flex; flex-direction: column; justify-content: center; }
.coupon-title { font-size: 15px; font-weight: 600; color: #1E293B; margin-bottom: 4px; }
.coupon-category { font-size: 12px; color: #7C3AED; margin-bottom: 4px; }
.coupon-date { font-size: 12px; color: #94A3B8; }

.coupon-action { padding: 0 16px; display: flex; align-items: center; }
.use-btn {
  border-radius: 16px !important;
  background: linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 600;
}

.coupon-status { padding: 0 20px; display: flex; align-items: center; }
</style>
