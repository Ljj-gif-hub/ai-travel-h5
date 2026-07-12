<template>
  <div class="planning-page">
    <!-- 顶部导航栏 -->
    <van-nav-bar
      class="page-navbar"
      title="AI 行程规划"
      left-arrow
      @click-left="goBack"
    />

    <!-- 行程概览 -->
    <div class="trip-overview">
      <div class="overview-card">
        <div class="overview-row">
          <div class="overview-item">
            <van-icon name="location-o" color="#667eea" size="18" />
            <div class="item-text">
              <span class="label">目的地</span>
              <span class="value">{{ destination }}</span>
            </div>
          </div>
          <div class="overview-divider"></div>
          <div class="overview-item">
            <van-icon name="calendar-o" color="#667eea" size="18" />
            <div class="item-text">
              <span class="label">天数</span>
              <span class="value">{{ days }} 天</span>
            </div>
          </div>
          <div class="overview-divider"></div>
          <div class="overview-item">
            <van-icon name="balance-o" color="#667eea" size="18" />
            <div class="item-text">
              <span class="label">预算</span>
              <span class="value">¥{{ budget }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 主要内容区 -->
    <div class="main-content">
      <!-- AI 行程规划卡片 -->
      <div class="section-card">
        <div class="section-header">
          <div class="header-left">
            <van-icon name="magic-o" color="#667eea" size="18" />
            <span class="section-title">AI 行程规划</span>
          </div>
          <van-tag v-if="!isStreaming" type="success" plain round size="small" class="status-badge">已完成</van-tag>
          <van-tag v-else type="primary" plain round size="small" class="status-badge">生成中...</van-tag>
        </div>
        <div 
          ref="planContainerRef"
          class="content-area markdown-body" 
          @scroll="handleScroll"
        >
          <!-- 骨架屏 -->
          <div v-if="loading && isStreaming" class="skeleton-wrapper">
            <div v-for="i in 3" :key="i" class="skeleton-block" :style="{ width: `${60 + Math.random() * 30}%`, height: '20px' }"></div>
          </div>
          
          <!-- 生成的内容 -->
          <div v-else-if="planContent" v-html="formattedContent" class="markdown-content"></div>
          
          <!-- 空状态 -->
          <div v-else class="empty-state">
            <van-icon name="info-o" size="24" color="#ccc" />
            <span>暂无行程规划内容</span>
          </div>
        </div>
      </div>

      <!-- 详情卡片网格 -->
      <div class="detail-grid">
        <!-- 预算明细卡片 -->
        <div class="section-card">
          <div class="section-header">
            <div class="header-left">
              <van-icon name="balance-list-o" color="#52c41a" size="18" />
              <span class="section-title">预算明细</span>
            </div>
          </div>
          <div class="budget-detail-list">
            <div class="budget-item">
              <span class="budget-label">交通费用</span>
              <span class="budget-amount">¥{{ Math.round(budget * 0.3) }}</span>
            </div>
            <div class="budget-divider"></div>
            <div class="budget-item">
              <span class="budget-label">住宿费用</span>
              <span class="budget-amount">¥{{ Math.round(budget * 0.4) }}</span>
            </div>
            <div class="budget-divider"></div>
            <div class="budget-item">
              <span class="budget-label">餐饮费用</span>
              <span class="budget-amount">¥{{ Math.round(budget * 0.2) }}</span>
            </div>
            <div class="budget-divider"></div>
            <div class="budget-item">
              <span class="budget-label">其他杂费</span>
              <span class="budget-amount">¥{{ Math.round(budget * 0.1) }}</span>
            </div>
            <div class="budget-divider"></div>
            <div class="budget-total">
              <span>总计</span>
              <span class="total-amount">¥{{ budget }}</span>
            </div>
          </div>
        </div>

        <!-- 温馨提示卡片 -->
        <div class="section-card">
          <div class="section-header">
            <div class="header-left">
              <van-icon name="lightbulb-o" color="#faad14" size="18" />
              <span class="section-title">温馨提示</span>
            </div>
          </div>
          <div class="tips-list">
            <div v-for="(tip, index) in tipsList" :key="index" class="tip-item">
              <van-icon name="check-circle" color="#52c41a" size="16" />
              <span class="tip-text">{{ tip }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ✅ 底部操作栏：完美融入页面流 (替换了原来的 fixed 定位) -->
      <div class="action-bar-integrated">
        <van-button
          class="action-btn-secondary"
          type="default"
          round
          block
          @click="copyPlan"
        >
          <van-icon name="documents-o" size="16" />
          复制行程
        </van-button>
        <van-button
          class="action-btn-primary"
          type="primary"
          round
          block
          @click="goToChat"
        >
          <van-icon name="service-o" size="16" />
          咨询AI助手
        </van-button>
      </div>

    </div>

    <!-- 底部安全区（适配全面屏） -->
    <div class="bottom-safe-area"></div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { 
  NavBar, Button, Icon, Tag, showToast, showLoadingToast, showSuccessToast, closeToast 
} from 'vant'

const router = useRouter()
const route = useRoute()

// --- 基础数据（从路由参数获取）---
const destination = ref(route.query.destination || '北京')
const budget = ref(Number(route.query.budget) || 5000)
const days = ref(Number(route.query.days) || 2)

// --- 状态管理 ---
const planContent = ref('')
const loading = ref(false)
const isStreaming = ref(false)
const planContainerRef = ref(null)
const isScrolledToBottom = ref(true)

// --- Mock 数据 ---
const tipsList = ref([
  '随身携带身份证件，以备不时之需',
  '提前预订热门景点门票，避免排队',
  '关注当地天气预报，准备合适衣物',
  '准备常用药品，如感冒药、肠胃药',
  '保管好个人财物，注意安全'
])

// --- 核心方法 ---
// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (planContainerRef.value && isScrolledToBottom.value) {
    planContainerRef.value.scrollTop = planContainerRef.value.scrollHeight
  }
}

// 监听滚动状态
const handleScroll = (e) => {
  const { scrollTop, scrollHeight, clientHeight } = e.target
  isScrolledToBottom.value = scrollHeight - scrollTop - clientHeight <= 5
}

// 监听内容变化，自动滚动
watch(planContent, () => {
  if (isStreaming.value) {
    scrollToBottom()
  }
})

// Markdown 格式化
const formatMarkdown = (text) => {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/^\d+\.\s(.+)$/gm, '<li>$1</li>')
    .replace(/\n\n/g, '')
    .replace(/\n/g, '')
    .replace(/^(?!<h|<li|<p|<br)(.+)$/gm, '$1')
}

const formattedContent = computed(() => formatMarkdown(planContent.value))

// 获取AI行程规划
const fetchTravelPlan = async () => {
  loading.value = true
  isStreaming.value = true
  planContent.value = ''

  showLoadingToast({
    message: 'AI 正在为你规划行程...',
    forbidClick: true,
    loadingType: 'spinner',
  })

  try {
    // 模拟流式输出（实际项目替换为真实API调用）
    await new Promise(resolve => setTimeout(resolve, 1200))
    planContent.value = `### Day 1: 抵达与初探
**上午**
- 抵达${destination.value}，办理酒店入住（推荐住在市中心，交通便利）
- 简单休整后，前往城市中心广场，感受当地氛围

**下午**
- 参观${destination.value}标志性历史建筑，了解城市文化底蕴
- 逛一逛周边老街，品尝地道街头小吃

**晚上**
- 前往当地知名夜市，体验烟火气
- 早点休息，为次日行程储备精力

### Day 2: 深度体验
**上午**
- 前往${destination.value}必打卡的自然/人文景区，拍照留念
- 乘坐景区观光车，节省体力同时欣赏全景

**下午**
- 体验当地特色民俗活动（如手工制作、非遗表演等）
- 选购特色伴手礼，准备返程

**晚上**
- 享用告别晚餐，尝试当地招牌菜
- 整理行李，准备次日返程`
    
    isStreaming.value = false
    showSuccessToast('行程规划已完成')
  } catch (error) {
    console.error('AI请求失败:', error)
    showToast('AI规划失败，请稍后重试')
    isStreaming.value = false
  } finally {
    loading.value = false
    closeToast()
  }
}

// 复制行程
const copyPlan = async () => {
  try {
    await navigator.clipboard.writeText(planContent.value)
    showToast({
      message: '已复制到剪贴板',
      position: 'top',
      style: { 
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', 
        color: '#ffffff', 
        fontSize: '14px', 
        borderRadius: '10px', 
        padding: '12px 20px', 
      },
    })
  } catch (err) {
    showToast('复制失败，请手动复制')
  }
}

// 跳转AI聊天页
const goToChat = () => {
  router.push({
    path: '/chat',
    query: { 
      destination: destination.value, 
      budget: budget.value, 
      days: days.value 
    }
  })
}

// 返回上一页
const goBack = () => {
  if (window.history.length <= 1) {
    router.push('/')
  } else {
    router.back()
  }
}

onMounted(() => {
  fetchTravelPlan()
})
</script>

<style scoped>
.planning-page {
  min-height: 100vh;
  background: #f5f6fa;
  /* 移除 padding-bottom，因为按钮已融入内容流 */
}

/* 导航栏 */
.page-navbar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.page-navbar :deep(.van-nav-bar__title) {
  color: #fff;
  font-weight: 500;
  font-size: 16px;
}
.page-navbar :deep(.van-nav-bar__left) {
  color: #fff;
}
.page-navbar :deep(.van-nav-bar__arrow) {
  color: #fff;
}

/* 行程概览 */
.trip-overview {
  padding: 16px 16px 0;
}
.overview-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}
.overview-row {
  display: flex;
  align-items: center;
  justify-content: space-around;
}
.overview-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: center;
}
.item-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.label {
  font-size: 11px;
  color: #999;
  margin-bottom: 2px;
}
.value {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}
.overview-divider {
  width: 1px;
  height: 34px;
  background: #eee;
}

/* 主要内容区 */
.main-content {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 通用卡片样式 */
.section-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

/* 区块头部 */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

/* 状态徽章 */
.status-badge {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 20px;
}

/* 骨架屏 */
.skeleton-wrapper {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.skeleton-block {
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 8px;
}
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* 内容区 */
.content-area {
  position: relative;
  max-height: 400px;
  overflow-y: auto;
  padding-right: 4px;
  scroll-behavior: smooth;
  font-size: 14px;
  line-height: 1.8;
  color: #444;
}
.content-area::-webkit-scrollbar {
  width: 4px;
}
.content-area::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 4px;
}

/* Markdown 渲染样式 */
.markdown-body :deep(h1) {
  font-size: 18px;
  color: #333;
  margin: 16px 0 8px;
  padding-bottom: 6px;
  border-bottom: 2px solid #667eea;
}
.markdown-body :deep(h2) {
  font-size: 16px;
  color: #333;
  margin: 14px 0 6px;
}
.markdown-body :deep(h3) {
  font-size: 15px;
  color: #555;
  margin: 12px 0 4px;
}
.markdown-body :deep(strong) {
  color: #222;
  font-weight: 700;
}
.markdown-body :deep(li) {
  margin: 4px 0;
  padding-left: 8px;
  list-style: none;
  position: relative;
}
.markdown-body :deep(li)::before {
  content: '•';
  color: #667eea;
  font-weight: bold;
  position: absolute;
  left: -4px;
}
.markdown-body :deep(p) {
  margin: 8px 0;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 0;
  color: #aaa;
  font-size: 13px;
}

/* 详情卡片网格布局 */
.detail-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}
@media (min-width: 768px) {
  .detail-grid {
    grid-template-columns: 1fr 1fr;
  }
}

/* 预算明细样式 */
.budget-detail-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.budget-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #666;
}
.budget-label {
  color: #999;
}
.budget-amount {
  font-weight: 600;
  color: #333;
}
.budget-divider {
  height: 1px;
  background: #f0f0f0;
  margin: 8px 0;
}
.budget-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: bold;
  color: #333;
  padding-top: 4px;
}
.total-amount {
  color: #ff4d4f;
}

/* 温馨提示样式 */
.tips-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tip-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #666;
  padding: 4px 0;
}
.tip-text {
  line-height: 1.5;
}

/* ========================================== */
/* ✅ 全新样式：融入页面的底部操作栏 */
/* ========================================== */
.action-bar-integrated {
  display: grid;
  /* 关键：左右两个按钮平均分配宽度 */
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  
  /* 视觉：看起来像一个独立的卡片模块 */
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  margin-top: 16px; /* 与上方卡片保持间距 */
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  box-sizing: border-box;
}

/* 按钮基础样式 */
.action-btn-secondary,
.action-btn-primary {
  height: 44px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s ease;
}

/* 次要按钮（复制） */
.action-btn-secondary {
  border: 1px solid #667eea;
  color: #667eea;
  background: #fff;
}
.action-btn-secondary:active {
  background-color: #f0f2ff;
  transform: scale(0.98);
}

/* 主要按钮（咨询） */
.action-btn-primary {
  border: none;
  color: #fff;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}
.action-btn-primary:active {
  opacity: 0.85;
  transform: scale(0.98);
}

/* 底部安全区占位 */
.bottom-safe-area {
  height: calc(20px + env(safe-area-inset-bottom));
}
</style>