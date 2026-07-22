<script setup>
/**
 * 消息 (Messages) Tab 页 — 消息分类 + AI对话历史 + 消息通知
 * 5-Tab 架构：独立页面，keep-alive 缓存，自包含完整 script/template/style
 */
import { ref, reactive, onActivated, onDeactivated, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { getToken } from '../utils/auth'
import { planApi } from '../api'
import EmptyState from '../components/EmptyState.vue'
import AIChatDialog from '../components/AIChatDialog.vue'
import { getAllSessions, deleteSession, switchToSession } from '../utils/chatSession'

defineOptions({ name: 'MessagesView' })

const router = useRouter()

/* ==================== 四分类消息入口 ==================== */
const categoryItems = reactive([
  { key: 'order',   label: '订单出行', icon: 'orders-o',  color: '#3B82F6', badge: 0 },
  { key: 'chat',    label: '互动消息', icon: 'chat-o',    color: '#F59E0B', badge: 2 },
  { key: 'notify',  label: '账户通知', icon: 'bell-o',    color: '#F97316', badge: 1 },
  { key: 'vip',     label: '会员服务', icon: 'gem-o',     color: '#EAB308', badge: 0 },
])

/* ==================== AI 对话列表 ==================== */
const conversations = ref([])
const isLoadingConvs = ref(false)

const loadConversations = () => {
  conversations.value = getAllSessions()
}

/* ==================== AI 对话弹窗 ==================== */
const showAIChat = ref(false)
const activeConv = ref(null)
const aiInitialMessages = ref([])

const openConversation = (conv) => {
  activeConv.value = conv
  const msgs = switchToSession(conv.id)
  aiInitialMessages.value = msgs || []
  showAIChat.value = true
}

const deleteConversation = async (id) => {
  try {
    await showConfirmDialog({ title: '删除对话', message: '确定要删除这条AI对话记录吗？' })
    deleteSession(id)
    loadConversations()
    showToast({ message: '已删除', position: 'middle' })
  } catch (e) { /* 取消 */ }
}

/* ==================== 消息通知列表 ==================== */
const notifications = ref([])

const loadNotifications = () => {
  // 模拟通知数据（后续接入真实API）
  notifications.value = [
    { id: 1, type: 'order', icon: 'orders-o', iconColor: '#3B82F6',
      title: '行程规划已完成', preview: '您的"北京5日游"行程已生成，点击查看详情',
      time: Date.now() - 1000 * 60 * 30, unread: true },
    { id: 2, type: 'system', icon: 'bell-o', iconColor: '#F97316',
      title: '系统通知', preview: '新版本已上线，新增AI智能对话功能',
      time: Date.now() - 1000 * 60 * 60 * 3, unread: true },
    { id: 3, type: 'coupon', icon: 'coupon-o', iconColor: '#F59E0B',
      title: '优惠券到账', preview: '恭喜您获得新人专享优惠券，限时领取',
      time: Date.now() - 1000 * 60 * 60 * 24, unread: false },
  ]
}

/* ==================== 格式化时间 ==================== */
const formatTime = (timeVal) => {
  if (!timeVal) return ''
  const date = new Date(timeVal)
  const now = new Date()
  const diffMs = now - date
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60))

  if (diffHours < 1) return '刚刚'
  if (diffHours < 24) return `${diffHours}小时前`
  if (diffHours < 48) return '昨天'
  if (diffHours < 168) return `${Math.floor(diffHours / 24)}天前`

  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}/${day}`
}

/* ==================== 对话预览文本提取 ==================== */
const getConvPreview = (conv) => {
  if (!conv.messages || conv.messages.length === 0) return '新对话'
  // 取最后一条AI消息的前50字作为预览
  const msgs = [...conv.messages].reverse()
  const lastAI = msgs.find(m => m.type === 'ai' && m.content)
  if (lastAI) return lastAI.content.slice(0, 50) + (lastAI.content.length > 50 ? '...' : '')
  const lastUser = msgs.find(m => m.type === 'user' && m.content)
  if (lastUser) return lastUser.content.slice(0, 50) + (lastUser.content.length > 50 ? '...' : '')
  return '新对话'
}

/* ==================== 事件处理 ==================== */
const handleCategoryClick = (cat) => {
  if (cat.key === 'order') {
    router.push('/orders')
  } else if (cat.key === 'chat') {
    // 互动消息 — 暂无独立页面
    showToast({ message: '互动消息功能开发中', position: 'middle' })
  } else if (cat.key === 'notify') {
    showToast({ message: '账户通知功能开发中', position: 'middle' })
  } else if (cat.key === 'vip') {
    showToast({ message: '会员服务功能开发中', position: 'middle' })
  }
}

const handleService = () => {
  showToast({ message: '客服功能开发中', position: 'middle' })
}

const handleSettings = () => {
  showToast({ message: '设置功能开发中', position: 'middle' })
}

const handleContactService = () => {
  showToast({ message: '客服功能开发中', position: 'middle' })
}

/* ==================== 关闭AI对话弹窗 ==================== */
const onAIChatClose = () => {
  showAIChat.value = false
  activeConv.value = null
  aiInitialMessages.value = []
  // 刷新对话列表（标题可能已更新）
  loadConversations()
}

/* ==================== 生命周期 ==================== */
onActivated(() => {
  loadConversations()
  loadNotifications()
})

onDeactivated(() => {
  showAIChat.value = false
  activeConv.value = null
})
</script>

<template>
  <div class="page-shell">

    <!-- 漂浮微光粒子 -->
    <div class="clouds-layer" aria-hidden="true">
      <span class="cloud-dot c1"></span><span class="cloud-dot c2"></span><span class="cloud-dot c3"></span>
    </div>

    <!-- ==================== 1. 顶部导航 ==================== -->
    <div class="msg-header entrance-item entrance-d1">
      <div class="header-top">
        <button class="header-btn" @click="handleService" aria-label="客服">
          <van-icon name="service-o" size="20" color="#64748B" />
        </button>
        <h1 class="header-title">消息</h1>
        <button class="header-btn" @click="handleSettings" aria-label="设置">
          <van-icon name="setting-o" size="20" color="#64748B" />
        </button>
      </div>

      <!-- ==================== 2. 四分类网格 ==================== -->
      <div class="category-grid card-macaron entrance-item entrance-d2">
        <div
          v-for="cat in categoryItems"
          :key="cat.key"
          class="cat-item"
          @click="handleCategoryClick(cat)"
        >
          <div class="cat-icon-wrap">
            <div class="cat-icon" :style="{ background: `${cat.color}14` }">
              <van-icon :name="cat.icon" :color="cat.color" size="22" />
            </div>
            <van-badge v-if="cat.badge > 0" :content="cat.badge" class="cat-badge" />
          </div>
          <span class="cat-label">{{ cat.label }}</span>
        </div>
      </div>
    </div>

    <!-- ==================== 3. AI 对话记录 ==================== -->
    <div class="content-area">
      <div class="section-title">
        <van-icon name="robot" size="18" color="#8B5CF6" />
        <span>AI 对话记录</span>
      </div>

      <!-- 加载骨架 -->
      <van-skeleton v-if="isLoadingConvs" title avatar row="3" />

      <!-- 空状态 -->
      <div v-else-if="conversations.length === 0" class="empty-conv-wrap">
        <EmptyState
          icon="chat-o"
          title="暂无AI对话"
          desc="前往首页使用AI规划行程，对话记录将自动保存在这里"
          btn-text="去AI规划"
          btn-type="gradient"
          @btn-click="() => router.push('/')"
        />
      </div>

      <!-- 对话列表 -->
      <div v-else class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-card"
        >
          <div class="conv-accent" />
          <div class="conv-info" @click="openConversation(conv)">
            <div class="conv-top-row">
              <span class="conv-title">{{ conv.title || '未命名对话' }}</span>
              <span class="conv-time">{{ formatTime(conv.updatedAt) }}</span>
            </div>
            <p class="conv-preview">{{ getConvPreview(conv) }}</p>
          </div>
          <div class="conv-actions">
            <van-icon
              name="arrow"
              size="16"
              color="#CBD5E1"
              class="conv-arrow"
              @click="openConversation(conv)"
            />
            <van-icon
              name="delete-o"
              size="18"
              color="#EF4444"
              class="conv-delete"
              @click.stop="deleteConversation(conv.id)"
            />
          </div>
        </div>
      </div>

      <!-- ==================== 4. 消息通知 ==================== -->
      <div class="section-title" style="margin-top: 24px;">
        <van-icon name="bell-o" size="18" color="#F97316" />
        <span>消息通知</span>
      </div>

      <!-- 空状态（带SVG邮箱插画） -->
      <div v-if="notifications.length === 0" class="empty-notif">
        <div class="mailbox-illustration">
          <svg viewBox="0 0 200 170" width="180" height="153" preserveAspectRatio="xMidYMid meet">
            <!-- 邮箱身体 -->
            <rect x="50" y="55" width="100" height="85" rx="10" fill="white" stroke="#E2E8F0" stroke-width="2" />
            <!-- 邮箱盖子 -->
            <path d="M50 65 L100 95 L150 65" fill="white" stroke="#E2E8F0" stroke-width="2" stroke-linecap="round" />
            <path d="M48 63 L100 93 L152 63" fill="none" stroke="#CBD5E1" stroke-width="1.5" stroke-linecap="round" />
            <!-- 邮箱红旗 -->
            <rect x="125" y="40" width="4" height="28" rx="2" fill="#EF4444" />
            <polygon points="129,40 155,47 129,54" fill="#EF4444" />
            <!-- 信封 -->
            <rect x="75" y="85" width="50" height="35" rx="4" fill="#F8FAFC" stroke="#E2E8F0" stroke-width="1.2" />
            <line x1="75" y1="85" x2="100" y2="102" stroke="#E2E8F0" stroke-width="1.2" />
            <line x1="125" y1="85" x2="100" y2="102" stroke="#E2E8F0" stroke-width="1.2" />
            <!-- 装饰：邮件飞出小信封 -->
            <rect x="155" y="22" width="18" height="12" rx="2" fill="#A78BFA" opacity="0.35" />
            <line x1="155" y1="22" x2="164" y2="28" stroke="white" stroke-width="0.8" opacity="0.5" />
            <line x1="173" y1="22" x2="164" y2="28" stroke="white" stroke-width="0.8" opacity="0.5" />
            <!-- 装饰小圆点 -->
            <circle cx="30" cy="100" r="2" fill="#A78BFA" opacity="0.2" />
            <circle cx="175" cy="80" r="2.5" fill="#6366F1" opacity="0.15" />
            <circle cx="175" cy="120" r="1.5" fill="#A78BFA" opacity="0.2" />
            <!-- 邮箱上的小笑脸 -->
            <circle cx="90" cy="140" r="5" fill="#F1F5F9" />
            <circle cx="89" cy="139" r="1" fill="#333" />
            <circle cx="92" cy="139" r="1" fill="#333" />
            <path d="M88 141 Q90 143 92 141" fill="none" stroke="#333" stroke-width="0.6" />
          </svg>
        </div>
        <p class="empty-notif-title">暂无更多消息</p>
        <p class="empty-notif-hint">您的订单通知、优惠活动、互动消息都会在这里显示</p>
        <button class="contact-btn" @click="handleContactService">
          <van-icon name="service-o" size="16" />
          <span>联系客服</span>
        </button>
      </div>

      <!-- 通知列表 -->
      <div v-else class="notif-list">
        <div
          v-for="item in notifications"
          :key="item.id"
          class="notif-item"
          :class="{ unread: item.unread }"
        >
          <div class="notif-icon-wrap">
            <div class="notif-icon" :style="{ background: `${item.iconColor}14` }">
              <van-icon :name="item.icon" :color="item.iconColor" size="20" />
            </div>
            <span v-if="item.unread" class="notif-dot" />
          </div>
          <div class="notif-body">
            <div class="notif-top-row">
              <span class="notif-title">{{ item.title }}</span>
              <span class="notif-time">{{ formatTime(item.time) }}</span>
            </div>
            <p class="notif-preview">{{ item.preview }}</p>
          </div>
        </div>
      </div>

      <div style="height: 4px;" />
    </div>

    <!-- ==================== 5. AI 对话弹窗 ==================== -->
    <AIChatDialog
      v-model:visible="showAIChat"
      :initial-messages="aiInitialMessages"
      @close="onAIChatClose"
    />
  </div>
</template>

<style scoped>
/* ==================== 页面外壳 ==================== */
.page-shell {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
  padding-bottom: calc(var(--tabbar-height, 56px) + var(--safe-area-bottom, 0px) + 8px);
  box-sizing: border-box;
  overflow-x: hidden;
}

/* ==================== 通用卡片 ==================== */
.card-macaron {
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 4px 18px rgba(0,0,0,0.04);
  border: 1px solid rgba(139,92,246,0.06);
}

/* ==================== 1. 顶部导航 ==================== */
.msg-header {
  position: relative;
  padding: calc(env(safe-area-inset-top, 0px) + 12px) 16px 0;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 4px 14px;
}

.header-title {
  font-size: 24px;
  font-weight: 700;
  color: #1E293B;
  margin: 0;
  letter-spacing: -0.3px;
}

.header-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: rgba(255,255,255,0.7);
  border-radius: 50%;
  cursor: pointer;
  transition: background 0.2s, transform 0.2s;
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}
.header-btn:active {
  background: rgba(139,92,246,0.08);
  transform: scale(0.92);
}

/* ==================== 2. 四分类网格 ==================== */
.category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  padding: 18px 6px;
  margin: 0 16px;
}

.cat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 10px 0;
  transition: transform 0.2s;
  position: relative;
}
.cat-item:active {
  transform: scale(0.94);
}

.cat-icon-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cat-icon {
  width: 50px;
  height: 50px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s;
}
.cat-item:hover .cat-icon {
  transform: scale(1.06);
}

.cat-badge {
  position: absolute;
  top: -2px;
  right: -6px;
}

.cat-label {
  font-size: 12px;
  color: #475569;
  font-weight: 500;
  text-align: center;
}

/* ==================== 内容区 ==================== */
.content-area {
  padding: 0 16px;
  max-width: 480px;
  margin: 0 auto;
}

/* ==================== 区块标题 ==================== */
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 16px;
  font-weight: 700;
  color: #1E293B;
  padding-top: 6px;
}

/* ==================== 3. AI 对话列表 ==================== */
.conv-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.conv-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 4px 18px rgba(0,0,0,0.04);
  border: 1px solid rgba(139,92,246,0.06);
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
}
.conv-card:active {
  transform: scale(0.98);
}

/* 左侧彩色强调条 */
.conv-accent {
  width: 4px;
  min-width: 4px;
  align-self: stretch;
  background: linear-gradient(180deg, #A78BFA, #8B5CF6, #6366F1);
  border-radius: 2px 0 0 2px;
}

.conv-info {
  flex: 1;
  min-width: 0;
  padding: 16px 0;
  cursor: pointer;
}

.conv-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  gap: 8px;
}

.conv-title {
  font-size: 15px;
  font-weight: 600;
  color: #1E293B;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.conv-time {
  font-size: 11px;
  color: #94A3B8;
  flex-shrink: 0;
  white-space: nowrap;
}

.conv-preview {
  font-size: 13px;
  color: #64748B;
  margin: 0;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px 12px 16px 0;
  flex-shrink: 0;
}

.conv-arrow {
  cursor: pointer;
  padding: 4px;
  border-radius: 50%;
  transition: background 0.2s;
}
.conv-arrow:active { background: rgba(139,92,246,0.06); }

.conv-delete {
  cursor: pointer;
  padding: 4px;
  border-radius: 50%;
  transition: background 0.2s, transform 0.2s;
}
.conv-delete:active {
  background: rgba(239,68,68,0.08);
  transform: scale(0.9);
}

/* ==================== 4. 消息通知 ==================== */

/* --- 空状态（邮箱插画） --- */
.empty-notif {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px 28px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 4px 18px rgba(0,0,0,0.04);
  border: 1px solid rgba(139,92,246,0.06);
  text-align: center;
  min-height: 340px;
}

.mailbox-illustration {
  margin-bottom: 20px;
  opacity: 0.85;
}

.empty-notif-title {
  font-size: 17px;
  font-weight: 600;
  color: #475569;
  margin: 0 0 8px;
}

.empty-notif-hint {
  font-size: 13px;
  color: #94A3B8;
  margin: 0 0 24px;
  line-height: 1.5;
  max-width: 260px;
}

.contact-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 11px 28px;
  border: 1.5px solid #C4B5FD;
  border-radius: 22px;
  background: #fff;
  color: #7C3AED;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.contact-btn:hover {
  background: #faf5ff;
  border-color: #A78BFA;
}
.contact-btn:active {
  transform: scale(0.95);
}

/* --- 通知列表 --- */
.notif-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 4px 18px rgba(0,0,0,0.04);
  border: 1px solid rgba(139,92,246,0.06);
  cursor: pointer;
  transition: transform 0.2s;
  position: relative;
}
.notif-item:active {
  transform: scale(0.98);
}

.notif-item.unread {
  border-left: 3px solid #8B5CF6;
}

.notif-icon-wrap {
  position: relative;
  flex-shrink: 0;
}

.notif-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.notif-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  background: #EF4444;
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 1px 3px rgba(239,68,68,0.3);
}

.notif-body {
  flex: 1;
  min-width: 0;
}

.notif-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  gap: 8px;
}

.notif-title {
  font-size: 15px;
  font-weight: 600;
  color: #1E293B;
}

.notif-time {
  font-size: 11px;
  color: #94A3B8;
  flex-shrink: 0;
  white-space: nowrap;
}

.notif-preview {
  font-size: 13px;
  color: #64748B;
  margin: 0;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ==================== VanBadge 定制 ==================== */
:deep(.van-badge) {
  font-size: 11px;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  padding: 0 5px;
}

/* ==================== VanSkeleton 覆盖 ==================== */
:deep(.van-skeleton) {
  padding: 16px;
}
:deep(.van-skeleton__title) {
  height: 20px;
}
:deep(.van-skeleton__avatar) {
  width: 46px;
  height: 46px;
  border-radius: 14px;
}

/* ==================== 空对话容器 ==================== */
.empty-conv-wrap {
  margin-bottom: 8px;
}

/*
 * ================================================================
 * 消息页专属动效
 * ================================================================
 */

/* 云端粒子 */
.clouds-layer { position: fixed; inset: 0; z-index: 0; pointer-events: none; overflow: hidden; }
.cloud-dot { position: absolute; border-radius: 50%; background: rgba(139,92,246,0.06); animation: cloudDriftSlow linear infinite; }
.c1 { width: 50px; height: 50px; top: 15%; left: 8%; animation-duration: 26s; }
.c2 { width: 35px; height: 35px; top: 40%; right: 12%; animation-duration: 32s; animation-delay: -8s; }
.c3 { width: 65px; height: 65px; top: 70%; left: 60%; animation-duration: 38s; animation-delay: -16s; }

/* 消息卡片hover右移+侧边光条 */
.msg-header { background-size: 200% 200%; }
/* msg-header不设animation避免覆盖entrance-item的entranceUp */
.cat-item, .conv-item, .notif-item {
  transition: transform 0.35s cubic-bezier(0.4,0,0.2,1), box-shadow 0.35s ease;
}
.cat-item:hover, .conv-item:hover, .notif-item:hover {
  transform: translateX(6px);
  box-shadow: -4px 0 14px rgba(139,92,246,0.10);
}
.cat-item:active, .conv-item:active { transform: scale(0.97); }

/* 未读红点呼吸 */
.cat-badge :deep(.van-badge) {
  animation: dotPulse 2s ease-in-out infinite;
}
</style>
