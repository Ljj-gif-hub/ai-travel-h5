<script setup>
/**
 * 订单中心 —— 统一淡紫色品牌 UI 规范
 * 分类 Tab：全部 / 机票 / 酒店 / 门票
 */
import { ref, onMounted, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showToast } from 'vant'
import { getToken } from '../utils/auth'
import { orderApi, paymentApi } from '../api'
import EmptyState from '../components/EmptyState.vue'

const router = useRouter()
const { t } = useI18n()

const goBack = () => { router.back() }

/* ==================== 分类 Tab ==================== */
const activeTab = ref('all')
const tabs = [
  { nameKey: 'all', key: 'all' },
  { nameKey: 'flight', key: 'flight' },
  { nameKey: 'hotel', key: 'hotel' },
  { nameKey: 'ticket', key: 'ticket' },
]

/* ==================== 列表数据 ==================== */
const orders = ref([])
const isLoading = ref(false)
const loadError = ref(false)

const loadOrders = async (type = '') => {
  isLoading.value = true
  loadError.value = false
  try {
    const response = await orderApi.getOrders(type)
    if (response.code === 0) {
      orders.value = response.data || []
    } else {
      orders.value = []
    }
  } catch (error) {
    console.log('获取订单列表失败:', error)
    orders.value = []
    if (error?.response?.status === 502) loadError.value = true
  } finally {
    isLoading.value = false
  }
}

const handleTabChange = (key) => {
  activeTab.value = key
  orders.value = []
  loadOrders(key === 'all' ? '' : key)
}

/* ==================== 操作 ==================== */
const handlePay = async (order) => {
  try {
    // 走支付对接层：先发起支付拿到渠道支付地址，再完成支付
    const res = await paymentApi.createPayment(order.id)
    if (res.code === 0) {
      // 模拟渠道：跳转 mock 支付地址即完成支付（真实渠道此处跳第三方收银台）
      const mock = await paymentApi.mockPay(res.data.orderNo)
      if (mock.code === 0) {
        showToast('模拟支付成功')
      } else {
        showToast(mock.message || '支付失败')
      }
    } else {
      showToast(res.message || '支付失败')
    }
    loadOrders(activeTab.value === 'all' ? '' : activeTab.value)
  } catch (error) { showToast('支付失败') }
}

const handleCancel = async (order) => {
  try {
    const response = await orderApi.cancelOrder(order.id)
    if (response.code === 0) {
      showToast('取消成功')
      loadOrders(activeTab.value === 'all' ? '' : activeTab.value)
    } else {
      showToast(response.message || '取消失败')
    }
  } catch (error) { showToast('取消失败') }
}

const getStatusText = (status) => {
  const map = { pending: t('orders.pending'), paid: t('orders.paid'), completed: t('orders.completed'), cancelled: t('orders.cancelled'), refunded: '已退款' }
  return map[status] || status
}

const getStatusColor = (status) => {
  const map = { pending: '#f59e0b', paid: '#22c55e', completed: '#6b7280', cancelled: '#9ca3af', refunded: '#6366f1' }
  return map[status] || '#6b7280'
}

/* ==================== 引导跳转 ==================== */
const handleGoExplore = () => {
  router.push('/') // 跳转首页
}

onMounted(() => {
  if (getToken()) loadOrders()
})

/* 【性能优化】离开时清理状态 */
onDeactivated(() => { isLoading.value = false; loadError.value = false })
</script>

<template>
  <div class="page-shell animate-fade-in">
    <!-- 顶部导航 -->
    <van-nav-bar :title="t('orders.title')" left-arrow safe-area-inset-top class="nav-bar" @click-left="goBack" />

    <!-- 分类 Tab -->
    <div class="filter-tabs">
      <div class="filter-slider" :style="{ left: `calc(${tabs.findIndex(t => t.key === activeTab) * 25}% + 4px)`, width: `calc(25% - 8px)` }" />
      <button
        v-for="tab in tabs" :key="tab.key"
        :class="['filter-tab', { active: activeTab === tab.key }]"
        @click="handleTabChange(tab.key)"
      >{{ t(`orders.${tab.nameKey}`) }}</button>
    </div>

    <div class="page-content">
      <!-- 分类 Tab 切换淡入淡出过渡 -->
      <transition name="tab-fade" mode="out-in">
        <div :key="activeTab">
          <!-- 骨架屏加载 -->
          <van-skeleton v-if="isLoading" title avatar row="3" />

          <!-- 错误兜底 -->
          <div v-else-if="loadError" class="error-state">
            <van-icon name="warn-o" size="48" color="#94A3B8" />
            <p class="error-text">加载失败，请稍后重试</p>
            <van-button round plain type="primary" size="small" class="retry-btn" @click="loadOrders(activeTab === 'all' ? '' : activeTab)">重新加载</van-button>
          </div>

          <!-- 空状态 -->
          <EmptyState
            v-else-if="orders.length === 0"
            icon="orders-o"
            title="暂无订单"
            desc="您还没有任何订单，快去看看吧"
            btn-text="去看看"
            btn-type="outline"
            @btn-click="handleGoExplore"
          />

          <!-- 订单列表 -->
          <div v-else class="orders-list">
            <div v-for="order in orders" :key="order.id" class="order-card">
              <div class="order-header">
                <span class="order-no">订单号：{{ order.orderNo }}</span>
                <span class="order-status" :style="{ color: getStatusColor(order.status) }">{{ getStatusText(order.status) }}</span>
              </div>

              <div class="order-body">
                <template v-if="order.type === 'flight'">
                  <div class="type-badge badge-flight">{{ t('orders.flight') }}</div>
                  <div class="flight-route">
                    <span class="route-city">{{ order.fromCity }}</span>
                    <van-icon name="arrow" size="20" color="#94A3B8" />
                    <span class="route-city">{{ order.toCity }}</span>
                  </div>
                  <div class="flight-detail">
                    <span>{{ order.flightNo }}</span>
                    <span>{{ order.date }}</span>
                  </div>
                </template>
                <template v-else-if="order.type === 'hotel'">
                  <div class="type-badge badge-hotel">{{ t('orders.hotel') }}</div>
                  <div class="info-name">{{ order.hotelName }}</div>
                  <div class="info-date">入住：{{ order.checkIn }} — 离店：{{ order.checkOut }}</div>
                </template>
                <template v-else-if="order.type === 'ticket'">
                  <div class="type-badge badge-ticket">{{ t('orders.ticket') }}</div>
                  <div class="info-name">{{ order.scenicName }}</div>
                  <div class="info-date">游玩日期：{{ order.date }}</div>
                </template>
              </div>

              <div class="order-footer">
                <div class="order-price">¥{{ order.price }}</div>
                <div class="order-actions">
                  <van-button v-if="order.status === 'pending'" size="small" class="pay-btn" @click="handlePay(order)">{{ t('orders.payNow') }}</van-button>
                  <van-button v-if="order.status === 'pending'" size="small" plain type="danger" class="cancel-btn" @click="handleCancel(order)">{{ t('orders.cancelOrder') }}</van-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<style scoped>
/* ==================== 页面外壳 ==================== */
.page-shell {
  width: 100%;
  min-height: 100vh;
  background: transparent;
  padding-bottom: calc(62px + var(--safe-area-bottom) + 16px);
  box-sizing: border-box;
  overflow-x: hidden;
}

/* ==================== 导航栏 ==================== */
:deep(.nav-bar) {
  background: linear-gradient(135deg, rgba(233,213,255,0.9) 0%, rgba(240,249,255,0.9) 50%, rgba(253,244,255,0.9) 100%);
  backdrop-filter: blur(12px);
}
:deep(.nav-bar .van-nav-bar__title) {
  color: #1E293B;
  font-weight: 600;
  font-size: 17px;
}

/* 分类 Tab — 滑动指示器 */
.filter-tabs { display: flex; margin: 0 12px 12px; padding: 4px; position: relative; background: rgba(255,255,255,0.5); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); border-radius: 14px; border: 1px solid rgba(255,255,255,0.5); }
.filter-slider { position: absolute; top: 4px; height: calc(100% - 8px); background: #fff; border-radius: 11px; z-index: 0; box-shadow: 0 1px 4px rgba(0,0,0,0.06); transition: left 0.35s, width 0.35s; }
.filter-tab { flex: 1; padding: 9px 0; border: none; border-radius: 11px; font-size: 13px; font-weight: 500; cursor: pointer; background: transparent; color: #94A3B8; position: relative; z-index: 1; transition: color 0.3s; }
.filter-tab.active { color: #7C3AED; font-weight: 600; }
.filter-tab:active { transform: scale(0.96); }

/* ==================== 内容区 ==================== */
.page-content {
  padding: 16px;
  box-sizing: border-box;
}

/* ==================== 错误状态 ==================== */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 20px;
  text-align: center;
}
.error-text {
  font-size: 15px;
  color: #94A3B8;
  margin: 16px 0 20px;
}
.retry-btn {
  border-radius: 20px !important;
  color: #7C3AED !important;
  border-color: #C4B5FD !important;
}

/* ==================== 订单列表 ==================== */
.orders-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.order-card {
  background: #fff;
  border-radius: 18px;
  padding: 20px;
  box-shadow: 0 4px 18px rgba(0,0,0,0.04);
  border: 1px solid rgba(139,92,246,0.06);
  transition: transform 0.2s;
}
.order-card:active {
  transform: scale(0.98);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid #F1F5F9;
}
.order-no {
  font-size: 13px;
  color: #94A3B8;
}
.order-status {
  font-size: 14px;
  font-weight: 600;
}

/* ==================== 订单内容 ==================== */
.order-body {
  margin-bottom: 16px;
}

/* 类型标签 */
.type-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 10px;
  margin-bottom: 10px;
}
.badge-flight {
  background: rgba(59,130,246,0.1);
  color: #3B82F6;
}
.badge-hotel {
  background: rgba(245,158,11,0.1);
  color: #F59E0B;
}
.badge-ticket {
  background: rgba(52,211,153,0.1);
  color: #34D399;
}

/* 机票 */
.flight-route {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.route-city {
  font-size: 18px;
  font-weight: 700;
  color: #1E293B;
}
.flight-detail {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #94A3B8;
}

/* 酒店 / 门票 */
.info-name {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
  margin-bottom: 4px;
}
.info-date {
  font-size: 13px;
  color: #94A3B8;
}

/* ==================== 订单底部 ==================== */
.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 14px;
  border-top: 1px solid #F1F5F9;
}
.order-price {
  font-size: 18px;
  font-weight: 700;
  color: #7C3AED;
}
.order-actions {
  display: flex;
  gap: 8px;
}
.pay-btn {
  border-radius: 16px !important;
  background: linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%) !important;
  border: none !important;
  color: white !important;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(139,92,246,0.25);
}
.cancel-btn {
  border-radius: 16px !important;
  color: #EF4444 !important;
  border: 1.5px solid #FECACA !important;
  background: #fff !important;
}
</style>
