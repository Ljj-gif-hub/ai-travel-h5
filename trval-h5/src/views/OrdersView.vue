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
import { orderApi, paymentApi, refundApi, invoiceApi } from '../api'
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
        showToast(t('payment.mockSuccess'))
      } else {
        showToast(mock.message || t('payment.payFail'))
      }
    } else {
      showToast(res.message || t('payment.payFail'))
    }
    loadOrders(activeTab.value === 'all' ? '' : activeTab.value)
  } catch (error) { showToast(t('payment.payFail')) }
}

const handleCancel = async (order) => {
  try {
    const response = await orderApi.cancelOrder(order.id)
    if (response.code === 0) {
      showToast(t('orders.cancelSuccess'))
      loadOrders(activeTab.value === 'all' ? '' : activeTab.value)
    } else {
      showToast(response.message || t('orders.cancelFail'))
    }
  } catch (error) { showToast(t('orders.cancelFail')) }
}

const getStatusText = (status) => {
  const map = { pending: t('orders.pending'), paid: t('orders.paid'), completed: t('orders.completed'), cancelled: t('orders.cancelled'), refunded: t('orders.refunded') }
  return map[status] || status
}

const getStatusColor = (status) => {
  const map = { pending: '#f59e0b', paid: '#22c55e', completed: '#6b7280', cancelled: '#9ca3af', refunded: '#6366f1' }
  return map[status] || '#6b7280'
}

/* ==================== 退款（新功能） ==================== */
const showRefundPopup = ref(false)
const refundForm = ref({ orderId: null, reason: '' })
const refundSubmitting = ref(false)

const openRefund = (order) => {
  refundForm.value = { orderId: order.id, reason: '' }
  showRefundPopup.value = true
}

const submitRefund = async () => {
  if (refundSubmitting.value) return
  refundSubmitting.value = true
  try {
    const res = await refundApi.requestRefund(refundForm.value.orderId, refundForm.value.reason.trim() || null)
    if (res.code === 0) {
      showRefundPopup.value = false
      showToast(t('orders.refundSuccess'))
    } else {
      showToast(res.message || t('orders.refundFail'))
    }
  } catch (error) { showToast(t('orders.refundFail')) }
  finally { refundSubmitting.value = false }
}

const showRefundsPopup = ref(false)
const refunds = ref([])
const loadingRefunds = ref(false)

const loadRefunds = async () => {
  loadingRefunds.value = true
  try {
    const res = await refundApi.getMyRefunds()
    if (res.code === 0) refunds.value = res.data || []
    else refunds.value = []
  } catch (error) { refunds.value = [] }
  finally { loadingRefunds.value = false }
}

const openRefunds = () => { showRefundsPopup.value = true; loadRefunds() }

const getRefundStatusText = (s) => ({ pending: t('orders.refundPending'), refunded: t('orders.refunded'), rejected: t('orders.refundRejected') }[s] || s)
const getRefundStatusColor = (s) => ({ pending: '#f59e0b', refunded: '#22c55e', rejected: '#ef4444' }[s] || '#6b7280')

/* ==================== 发票（新功能） ==================== */
const showInvoicePopup = ref(false)
const invoiceForm = ref({ orderId: null, title: '', taxNo: '', type: 'personal' })
const invoiceSubmitting = ref(false)

const openInvoice = (order) => {
  invoiceForm.value = { orderId: order.id, title: '', taxNo: '', type: 'personal' }
  showInvoicePopup.value = true
}

const submitInvoice = async () => {
  if (invoiceSubmitting.value) return
  if (!invoiceForm.value.title.trim()) { showToast(t('orders.invoiceTitleRequired')); return }
  if (invoiceForm.value.type === 'company' && !invoiceForm.value.taxNo.trim()) { showToast(t('orders.invoiceTaxNoRequired')); return }
  invoiceSubmitting.value = true
  try {
    const res = await invoiceApi.issueInvoice(invoiceForm.value.orderId, {
      title: invoiceForm.value.title.trim(),
      taxNo: invoiceForm.value.taxNo.trim() || null,
      type: invoiceForm.value.type,
    })
    if (res.code === 0) {
      showInvoicePopup.value = false
      showToast(t('orders.invoiceSuccess'))
    } else {
      showToast(res.message || t('orders.invoiceFail'))
    }
  } catch (error) { showToast(t('orders.invoiceFail')) }
  finally { invoiceSubmitting.value = false }
}

const showInvoicesPopup = ref(false)
const invoices = ref([])
const loadingInvoices = ref(false)

const loadInvoices = async () => {
  loadingInvoices.value = true
  try {
    const res = await invoiceApi.getMyInvoices()
    if (res.code === 0) invoices.value = res.data || []
    else invoices.value = []
  } catch (error) { invoices.value = [] }
  finally { loadingInvoices.value = false }
}

const openInvoices = () => { showInvoicesPopup.value = true; loadInvoices() }

const getInvoiceTypeText = (type) => (type === 'company' ? t('orders.invoiceTypeCompany') : t('orders.invoiceTypePersonal'))
/** 后端 LocalDateTime → 'YYYY-MM-DD HH:mm' */
const formatDateTime = (s) => (s ? String(s).replace('T', ' ').slice(0, 16) : '')

/* ==================== 引导跳转 ==================== */
const handleGoExplore = () => {
  router.push('/') // 跳转首页
}

onMounted(() => {
  if (getToken()) loadOrders()
})

/* 【性能优化】离开时清理状态 */
onDeactivated(() => {
  isLoading.value = false
  loadError.value = false
  showRefundPopup.value = false
  showRefundsPopup.value = false
  showInvoicePopup.value = false
  showInvoicesPopup.value = false
})
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

    <!-- 售后入口：我的退款 / 我的发票 -->
    <div class="after-sales-row">
      <span class="after-sales-link" @click="openRefunds"><van-icon name="gold-coin-o" size="13" /> {{ t('orders.myRefunds') }}</span>
      <span class="after-sales-link" @click="openInvoices"><van-icon name="description" size="13" /> {{ t('orders.myInvoices') }}</span>
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
            <p class="error-text">{{ t('common.requestFailed') }}</p>
            <van-button round plain type="primary" size="small" class="retry-btn" @click="loadOrders(activeTab === 'all' ? '' : activeTab)">{{ t('common.retry') }}</van-button>
          </div>

          <!-- 空状态 -->
          <EmptyState
            v-else-if="orders.length === 0"
            icon="orders-o"
            :title="t('orders.noOrders')"
            :desc="t('orders.noOrdersDesc')"
            :btn-text="t('orders.goExplore')"
            btn-type="outline"
            @btn-click="handleGoExplore"
          />

          <!-- 订单列表 -->
          <div v-else class="orders-list">
            <div v-for="order in orders" :key="order.id" class="order-card">
              <div class="order-header">
                <span class="order-no">{{ t('orders.orderNo') }}：{{ order.orderNo }}</span>
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
                  <div class="info-date">{{ t('orders.checkIn') }}：{{ order.checkIn }} — {{ t('orders.checkOut') }}：{{ order.checkOut }}</div>
                </template>
                <template v-else-if="order.type === 'ticket'">
                  <div class="type-badge badge-ticket">{{ t('orders.ticket') }}</div>
                  <div class="info-name">{{ order.scenicName }}</div>
                  <div class="info-date">{{ t('orders.playDate') }}：{{ order.date }}</div>
                </template>
              </div>

              <div class="order-footer">
                <div class="order-price">¥{{ order.price }}</div>
                <div class="order-actions">
                  <van-button v-if="order.status === 'pending'" size="small" class="pay-btn" @click="handlePay(order)">{{ t('orders.payNow') }}</van-button>
                  <van-button v-if="order.status === 'pending'" size="small" plain type="danger" class="cancel-btn" @click="handleCancel(order)">{{ t('orders.cancelOrder') }}</van-button>
                  <van-button v-if="order.status === 'paid' || order.status === 'completed'" size="small" plain class="invoice-btn" @click="openInvoice(order)">{{ t('orders.invoice') }}</van-button>
                  <van-button v-if="order.status === 'paid' || order.status === 'completed'" size="small" plain class="refund-btn" @click="openRefund(order)">{{ t('orders.applyRefund') }}</van-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </div>

    <!-- ==================== 申请退款弹窗 ==================== -->
    <van-popup v-model:show="showRefundPopup" position="bottom" round safe-area-inset-bottom>
      <div class="aftersale-pop">
        <div class="pop-header"><span class="pop-title">{{ t('orders.applyRefund') }}</span><van-icon name="cross" size="18" color="#94a3b8" @click="showRefundPopup = false" /></div>
        <div class="pop-body">
          <van-field
            v-model="refundForm.reason"
            type="textarea"
            rows="3"
            maxlength="200"
            :label="t('orders.refundReason')"
            :placeholder="t('orders.refundReasonPlaceholder')"
          />
          <van-button block round class="pop-submit" :loading="refundSubmitting" @click="submitRefund">
            {{ refundSubmitting ? t('orders.refundSubmitting') : t('orders.refundSubmit') }}
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- ==================== 我的退款列表 ==================== -->
    <van-popup v-model:show="showRefundsPopup" position="bottom" :style="{ height: '70%' }" round safe-area-inset-bottom>
      <div class="aftersale-list-pop">
        <div class="pop-header"><span class="pop-title">{{ t('orders.myRefunds') }}</span><van-icon name="cross" size="18" color="#94a3b8" @click="showRefundsPopup = false" /></div>
        <div class="pop-list-body">
          <EmptyState
            v-if="!loadingRefunds && refunds.length === 0"
            icon="gold-coin-o"
            :title="t('orders.noRefunds')"
          />
          <div v-else class="aftersale-list">
            <div v-for="r in refunds" :key="r.id" class="aftersale-card">
              <div class="aftersale-card-top">
                <span class="aftersale-amount">¥{{ r.amount }}</span>
                <span class="aftersale-status" :style="{ color: getRefundStatusColor(r.status) }">{{ getRefundStatusText(r.status) }}</span>
              </div>
              <div class="aftersale-meta">{{ t('orders.orderNo') }}：{{ r.orderId }}</div>
              <div v-if="r.reason" class="aftersale-reason">{{ t('orders.refundReason') }}：{{ r.reason }}</div>
              <div class="aftersale-time">{{ t('orders.refundTime') }}：{{ formatDateTime(r.createdAt) }}</div>
            </div>
          </div>
        </div>
      </div>
    </van-popup>

    <!-- ==================== 开发票弹窗 ==================== -->
    <van-popup v-model:show="showInvoicePopup" position="bottom" round safe-area-inset-bottom>
      <div class="aftersale-pop">
        <div class="pop-header"><span class="pop-title">{{ t('orders.invoice') }}</span><van-icon name="cross" size="18" color="#94a3b8" @click="showInvoicePopup = false" /></div>
        <div class="pop-body">
          <van-radio-group v-model="invoiceForm.type" direction="horizontal" class="invoice-type-row">
            <van-radio name="personal">{{ t('orders.invoiceTypePersonal') }}</van-radio>
            <van-radio name="company">{{ t('orders.invoiceTypeCompany') }}</van-radio>
          </van-radio-group>
          <van-field v-model="invoiceForm.title" :label="t('orders.invoiceTitle')" :placeholder="t('orders.invoiceTitlePlaceholder')" maxlength="200" />
          <van-field v-model="invoiceForm.taxNo" :label="t('orders.invoiceTaxNo')" :placeholder="t('orders.invoiceTaxNoPlaceholder')" maxlength="50" />
          <van-button block round class="pop-submit" :loading="invoiceSubmitting" @click="submitInvoice">
            {{ invoiceSubmitting ? t('orders.invoiceSubmitting') : t('orders.invoiceSubmit') }}
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- ==================== 我的发票列表 ==================== -->
    <van-popup v-model:show="showInvoicesPopup" position="bottom" :style="{ height: '70%' }" round safe-area-inset-bottom>
      <div class="aftersale-list-pop">
        <div class="pop-header"><span class="pop-title">{{ t('orders.myInvoices') }}</span><van-icon name="cross" size="18" color="#94a3b8" @click="showInvoicesPopup = false" /></div>
        <div class="pop-list-body">
          <EmptyState
            v-if="!loadingInvoices && invoices.length === 0"
            icon="description"
            :title="t('orders.noInvoices')"
          />
          <div v-else class="aftersale-list">
            <div v-for="inv in invoices" :key="inv.id" class="aftersale-card">
              <div class="aftersale-card-top">
                <span class="aftersale-amount">¥{{ inv.amount }}</span>
                <span class="invoice-type-tag">{{ getInvoiceTypeText(inv.type) }}</span>
              </div>
              <div class="aftersale-meta">{{ t('orders.invoiceNo') }}：{{ inv.invoiceNo }}</div>
              <div class="aftersale-meta">{{ t('orders.invoiceTitle') }}：{{ inv.title }}<span v-if="inv.taxNo">（{{ inv.taxNo }}）</span></div>
              <div class="aftersale-time">{{ t('orders.invoiceTime') }}：{{ formatDateTime(inv.createdAt) }}</div>
            </div>
          </div>
        </div>
      </div>
    </van-popup>
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
.invoice-btn {
  border-radius: 16px !important;
  color: #3B82F6 !important;
  border: 1.5px solid #BFDBFE !important;
  background: #fff !important;
}
.refund-btn {
  border-radius: 16px !important;
  color: #F59E0B !important;
  border: 1.5px solid #FDE68A !important;
  background: #fff !important;
}

/* ==================== 售后入口 / 弹窗（新功能） ==================== */
.after-sales-row {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  padding: 0 20px 2px;
}
.after-sales-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #7C3AED;
  cursor: pointer;
  padding: 2px 0;
}
.after-sales-link:active { opacity: 0.6; }

.aftersale-pop {
  background: #fff;
  border-radius: 20px 20px 0 0;
  padding-bottom: calc(16px + env(safe-area-inset-bottom, 0px));
}
.pop-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
  border-bottom: 1px solid #F1F5F9;
}
.pop-title { font-size: 17px; font-weight: 700; color: #1e293b; }
.pop-body { padding: 16px 20px 8px; }
.pop-submit {
  margin-top: 16px;
  background: linear-gradient(135deg, #8B5CF6, #6366F1) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(139, 92, 246, 0.25);
}
.invoice-type-row { display: flex; gap: 24px; margin-bottom: 8px; padding: 0 4px; }

.aftersale-list-pop {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f8f7fc;
  border-radius: 20px 20px 0 0;
}
.pop-list-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  -webkit-overflow-scrolling: touch;
}
.aftersale-list { display: flex; flex-direction: column; gap: 10px; }
.aftersale-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}
.aftersale-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.aftersale-amount { font-size: 16px; font-weight: 700; color: #7C3AED; }
.aftersale-status { font-size: 13px; font-weight: 600; }
.invoice-type-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(59, 130, 246, 0.1);
  color: #3B82F6;
}
.aftersale-meta { font-size: 12px; color: #64748b; margin-bottom: 2px; }
.aftersale-reason { font-size: 12px; color: #64748b; margin-bottom: 2px; }
.aftersale-time { font-size: 11px; color: #94a3b8; margin-top: 4px; }
</style>
