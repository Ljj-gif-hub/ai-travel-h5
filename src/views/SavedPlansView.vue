<script setup>
/**
 * 我的规划 —— 无分类 Tab，与其他三页统一淡紫色品牌 UI
 * 修复：删除多余白色空白块；空状态使用 EmptyState 组件统一布局
 * 按钮使用渐变紫色（规划专属）
 */
import { ref, onMounted, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { getToken } from '../utils/auth'
import { planApi } from '../api'
import EmptyState from '../components/EmptyState.vue'

const router = useRouter()

/* ==================== 列表数据 ==================== */
const plans = ref([])
const isLoading = ref(false)
const loadError = ref(false)

/* ==================== 导航 ==================== */
const goBack = () => {
  if (window.history.length <= 1) { router.push('/') } else { router.back() }
}

const goHome = () => { router.push('/') }

/* ==================== 工具函数 ==================== */
const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
}

const getAttractions = (plan) => {
  if (!plan.planData || !plan.planData.dayPlans) return []
  const attractions = []
  plan.planData.dayPlans.forEach(day => {
    if (day.timeSlots) {
      day.timeSlots.forEach(slot => {
        if (slot.attraction) attractions.push(slot.attraction)
      })
    }
  })
  return attractions
}

/* ==================== 数据加载 ==================== */
const fetchPlans = async () => {
  isLoading.value = true
  loadError.value = false
  try {
    const response = await planApi.getSavedPlans()
    if (response.code === 0) {
      plans.value = response.data || []
    } else {
      showToast(response.message || '加载失败')
    }
  } catch (error) {
    console.error('获取规划列表失败:', error)
    plans.value = []
    if (error?.response?.status === 502) loadError.value = true
    else showToast('加载失败')
  } finally {
    isLoading.value = false
  }
}

/* ==================== 操作 ==================== */
const viewPlan = (plan) => {
  router.push({
    path: '/planning',
    query: { destination: plan.destination, budget: plan.budget, days: plan.days, people: plan.people, savedPlanId: plan.id },
  })
}

const confirmDelete = async (plan) => {
  try {
    await showConfirmDialog({
      title: '删除规划',
      message: `确定要删除「${plan.destination}」的规划吗？此操作不可恢复。`,
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    const response = await planApi.deletePlan(plan.id)
    if (response.code === 0) {
      showToast('已删除')
      plans.value = plans.value.filter(p => p.id !== plan.id)
    } else {
      showToast(response.message || '删除失败')
    }
  } catch (e) { /* 用户取消 */ }
}

onMounted(() => {
  if (getToken()) fetchPlans()
})

/* 【性能优化】离开时清理状态 */
onDeactivated(() => { isLoading.value = false; loadError.value = false })
</script>

<template>
  <div class="page-shell animate-fade-in">
    <!-- 顶部导航 -->
    <van-nav-bar title="我的规划" left-arrow safe-area-inset-top class="nav-bar" @click-left="goBack" />

    <div class="page-content">
      <!-- 骨架屏加载 -->
      <van-skeleton v-if="isLoading" title avatar row="3" />

      <!-- 错误兜底 -->
      <div v-else-if="loadError" class="error-state">
        <van-icon name="warn-o" size="48" color="#94A3B8" />
        <p class="error-text">加载失败，请稍后重试</p>
        <van-button round plain type="primary" size="small" class="retry-btn" @click="fetchPlans">重新加载</van-button>
      </div>

      <!-- 空状态 -->
      <EmptyState
        v-else-if="plans.length === 0"
        icon="todo-list-o"
        title="暂无保存的规划"
        desc="生成行程后点击保存按钮即可在这里查看"
        btn-text="去生成规划"
        btn-type="gradient"
        @btn-click="goHome"
      />

      <!-- 规划列表 -->
      <div v-else class="plans-list">
        <div v-for="plan in plans" :key="plan.id" class="plan-card" @click="viewPlan(plan)">
          <div class="plan-card-top">
            <div class="plan-destination">
              <van-icon name="location-o" color="#8B5CF6" size="16" />
              <span class="destination-name">{{ plan.destination }}</span>
            </div>
            <van-tag type="primary" plain round size="small">{{ plan.days }}天</van-tag>
          </div>

          <div class="plan-card-body">
            <div class="plan-info-row">
              <div class="plan-info-item">
                <van-icon name="balance-o" size="14" color="#8B5CF6" />
                <span>¥{{ plan.budget }}</span>
              </div>
              <div class="plan-info-item">
                <van-icon name="friends-o" size="14" color="#8B5CF6" />
                <span>{{ plan.people || 2 }}人</span>
              </div>
              <div class="plan-info-item">
                <van-icon name="clock-o" size="14" color="#94A3B8" />
                <span>{{ formatTime(plan.createdAt) }}</span>
              </div>
            </div>

            <div v-if="getAttractions(plan).length" class="plan-attractions">
              <van-icon name="bookmark-o" size="13" color="#F59E0B" />
              <span class="attractions-text">{{ getAttractions(plan).slice(0, 3).join('、') }}{{ getAttractions(plan).length > 3 ? '...' : '' }}</span>
            </div>
          </div>

          <div class="plan-card-actions">
            <van-button size="small" plain round class="action-btn" @click.stop="viewPlan(plan)">
              <van-icon name="eye-o" size="12" /> 查看
            </van-button>
            <van-button size="small" plain round class="action-btn delete-btn" @click.stop="confirmDelete(plan)">
              <van-icon name="delete-o" size="12" /> 删除
            </van-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ==================== 页面外壳 ==================== */
.page-shell {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
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

/* ==================== 规划列表 ==================== */
.plans-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.plan-card {
  background: #fff;
  border-radius: 18px;
  padding: 20px;
  box-shadow: 0 4px 18px rgba(0,0,0,0.04);
  border: 1px solid rgba(139,92,246,0.06);
  transition: transform 0.2s;
  cursor: pointer;
}
.plan-card:active {
  transform: scale(0.98);
}

.plan-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.plan-destination {
  display: flex;
  align-items: center;
  gap: 6px;
}
.destination-name {
  font-size: 17px;
  font-weight: 700;
  color: #1E293B;
}

.plan-card-body {
  margin-bottom: 14px;
}
.plan-info-row {
  display: flex;
  gap: 16px;
  margin-bottom: 10px;
}
.plan-info-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #64748B;
}

.plan-attractions {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 14px;
  background: #f8f7ff;
  border-radius: 12px;
}
.attractions-text {
  font-size: 12px;
  color: #94A3B8;
  line-height: 1.5;
  flex: 1;
}

.plan-card-actions {
  display: flex;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px solid #F1F5F9;
}
.action-btn {
  color: #7C3AED !important;
  border: 1.5px solid #C4B5FD !important;
  background: #fff !important;
  border-radius: 16px !important;
  font-size: 12px;
  height: 28px;
}
.delete-btn {
  color: #EF4444 !important;
  border-color: #FECACA !important;
}
</style>
