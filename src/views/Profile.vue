<script setup>
import { ref, onMounted, reactive, onActivated, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog, showLoadingToast, closeToast } from 'vant'

/*
 * 【Bug修复】显式声明组件名，供 keep-alive 的 include 白名单匹配
 * 缺失会导致 Tab 切换时组件无法命中缓存，每次销毁重建 → 空白
 */
defineOptions({ name: 'ProfileView' })
import { getToken, removeToken, getCurrentUsername } from '../utils/auth'
import { sanitizeHtml } from '../utils/security'
import { userApi, favoriteApi, couponApi, orderApi, noteApi } from '../api'
import {
  getCurrentUser,
  setCurrentUser,
  getMyData,
  setMyData,
  clearSession as clearAccountSession,
} from '../utils/userAccountStorage'

const router = useRouter()

/* ==================== 返回 ==================== */
const goBack = () => { if (window.history.length <= 1) router.push('/'); else router.back() }

/* ==================== 用户信息 ==================== */
const userInfo = reactive({
  avatar: '', nickname: '', username: '', level: '',
  points: 0, following: 0, followers: 0, travelNotes: 0, bio: '',
})

const travelStats = reactive({ citiesVisited: 0, totalDays: 0, totalSpent: 0, totalPhotos: 0 })

const isLoggedIn = ref(!!getToken())
const showEditPopup = ref(false)
const showInvitePopup = ref(false)
const editForm = ref({ nickname: '', bio: '' })

/* ==================== 服务列表 ==================== */
const serviceList = ref([
  { name: '我的行程规划', icon: 'bookmark-o', desc: '查看和编辑旅行规划', badge: 0, path: '/trips', color: '#8B5CF6' },
  { name: '我的订单', icon: 'orders-o', desc: '机票、酒店、门票订单', badge: 0, path: '/orders', color: '#6366F1' },
  { name: '我的收藏', icon: 'star-o', desc: '收藏的景点和攻略', badge: 0, path: '/favorites', color: '#F59E0B' },
  { name: '优惠券', icon: 'coupon-o', desc: '可用优惠券和折扣', badge: 0, path: '/coupons', color: '#34D399' },
  { name: 'AI对话记录', icon: 'chat-o', desc: 'AI旅行对话历史', badge: 0, path: '/messages', color: '#3B82F6' },
])

/* ==================== 快捷操作 ==================== */
const quickActions = [
  { name: '写游记', icon: 'edit', color: '#8B5CF6', path: '/write-note' },
  { name: '发动态', icon: 'photograph', color: '#FB7185', path: '/post' },
  { name: '邀请好友', icon: 'friends-o', color: '#34D399', action: 'invite' },
  { name: '意见反馈', icon: 'smile-comment-o', color: '#F59E0B', path: '/feedback' },
]

/* ==================== 工具 ==================== */
const getBadgeContent = (num) => (num > 9 ? '9+' : String(num))
const formatNumber = (num) => {
  if (num === undefined || num === null) return '0'
  if (num >= 10000) return (num / 10000).toFixed(1) + 'w'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k'
  return String(num)
}
const formatMoney = (num) => (num || 0).toLocaleString('zh-CN')

/* ==================== 事件处理 ==================== */
/*
 * 【修复】所有点击处理增加 try-catch，防止单点崩溃导致页面无响应
 */
const handleAvatarClick = () => {
  try { if (!isLoggedIn.value) { router.push('/login'); return }; router.push('/edit-profile') }
  catch (e) { console.error('handleAvatarClick 失败:', e) }
}
const handleLevelClick = () => {
  try { if (!isLoggedIn.value) { router.push('/login'); return }; showToast('会员升级功能开发中') }
  catch (e) { console.error('handleLevelClick 失败:', e) }
}
const handleMetaClick = (type) => {
  try {
    if (!isLoggedIn.value) { router.push('/login'); return }
    const routes = { following: '/following', followers: '/followers', notes: '/notes' }
    if (routes[type]) router.push(routes[type])
  } catch (e) { console.error('handleMetaClick 失败:', e) }
}
const handleEditClick = () => {
  try {
    if (!isLoggedIn.value) { router.push('/login'); return }
    editForm.value = { nickname: userInfo.nickname, bio: userInfo.bio || '' }; showEditPopup.value = true
  } catch (e) { console.error('handleEditClick 失败:', e) }
}
const saveProfile = async () => {
  if (!editForm.value.nickname.trim()) { showToast('昵称不能为空'); return }
  const toast = showLoadingToast({ message: '保存中...', duration: 0, forbidClick: true })
  try {
    const response = await userApi.updateProfile({ nickname: editForm.value.nickname, bio: editForm.value.bio })
    if (response.code === 0) {
      const data = response.data; userInfo.nickname = data.nickname; userInfo.bio = data.bio || ''
      // 【多账号隔离】写入当前账号独立存储
      setMyData('userInfo', { ...userInfo })
      localStorage.setItem('userInfo', JSON.stringify(userInfo)); showEditPopup.value = false; closeToast(); showToast('修改成功')
    } else { closeToast(); showToast(response.message || '保存失败') }
  } catch (error) {
    closeToast(); userInfo.nickname = editForm.value.nickname; userInfo.bio = editForm.value.bio
    setMyData('userInfo', { ...userInfo })
    localStorage.setItem('userInfo', JSON.stringify(userInfo)); showEditPopup.value = false; showToast('已保存到本地')
  }
}

const handleWriteNote = () => { if (!isLoggedIn.value) { router.push('/login'); return }; router.push('/write-note') }
const handleInvite = () => { if (!isLoggedIn.value) { router.push('/login'); return }; showInvitePopup.value = true }
const copyInviteLink = async () => {
  const link = window.location.origin + '/register?invite=' + (userInfo.username || 'traveler')
  try { await navigator.clipboard.writeText(link); showToast('邀请链接已复制') } catch { showToast('复制失败') }
}
const shareInvite = () => { showToast('请复制链接后分享给好友') }
const handleQuickAction = (item) => {
  if (!isLoggedIn.value) { router.push('/login'); return }
  if (item.action === 'invite') handleInvite(); else if (item.path) router.push(item.path)
}
const handleServiceClick = (item) => {
  if (!isLoggedIn.value) { router.push('/login'); return }
  if (item.path) router.push(item.path)
}

/* ==================== 退出登录 ==================== */
const handleLogout = async () => {
  try {
    await showConfirmDialog({ title: '确认退出', message: '确定要退出登录吗？' })
    showLoadingToast({ message: '退出中...', duration: 0, forbidClick: true, loadingType: 'spinner' })
    try { await userApi.logout() } catch (error) { /* 后端失败继续清除 */ }
    finally {
      removeToken()
      // 【多账号隔离】退出仅清空会话缓存，保留账号持久化数据
      clearAccountSession()
      isLoggedIn.value = false; resetUserInfo(); closeToast()
      showToast({ message: '已退出', position: 'middle' })
      setTimeout(() => router.push('/login'), 800)
    }
  } catch (e) { /* 取消 */ }
}

const resetUserInfo = () => {
  userInfo.avatar = ''; userInfo.nickname = '旅行者'; userInfo.username = ''; userInfo.level = '普通会员'
  userInfo.points = 0; userInfo.following = 0; userInfo.followers = 0; userInfo.travelNotes = 0; userInfo.bio = ''
  travelStats.citiesVisited = 0; travelStats.totalDays = 0; travelStats.totalSpent = 0; travelStats.totalPhotos = 0
  serviceList.value.forEach(item => item.badge = 0)
}

/* ==================== 数据加载 ==================== */
const loadProfile = async () => {
  try {
    const response = await userApi.getProfile()
    if (response.code === 0) {
      const data = response.data
      userInfo.avatar = data.avatar || userInfo.avatar
      userInfo.nickname = data.nickname || '旅行者'; userInfo.username = data.username || ''
      userInfo.level = data.level || '普通会员'; userInfo.points = data.points || 0
      userInfo.following = data.following || 0; userInfo.followers = data.followers || 0
      userInfo.travelNotes = data.travelNotes || 0; userInfo.bio = data.bio || ''
      travelStats.citiesVisited = data.citiesVisited || 0; travelStats.totalDays = data.totalDays || 0
      travelStats.totalSpent = data.totalSpent || 0; travelStats.totalPhotos = data.totalPhotos || 0
      // 【多账号隔离】写入当前账号独立存储
      setMyData('userInfo', { ...userInfo })
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    }
  } catch (error) {
    // 【多账号隔离】离线/网络异常时优先从当前账号本地数据恢复
    const accountData = getMyData('userInfo')
    if (accountData) {
      Object.assign(userInfo, accountData)
    } else {
      const saved = localStorage.getItem('userInfo')
      if (saved) { try { Object.assign(userInfo, JSON.parse(saved)) } catch (e) { /* */ } }
    }
  }
}

const loadBadgeCounts = async () => {
  try {
    const [favoriteRes, couponRes, orderRes, noteRes] = await Promise.all([
      favoriteApi.getFavoriteCount(), couponApi.getCouponCount('unused'),
      orderApi.getOrderCount('pending'), noteApi.getNoteCount(),
    ])
    if (favoriteRes.code === 0) serviceList.value[2].badge = favoriteRes.data.count || 0
    if (couponRes.code === 0) serviceList.value[3].badge = couponRes.data.count || 0
    if (orderRes.code === 0) serviceList.value[1].badge = orderRes.data.count || 0
    if (noteRes.code === 0) userInfo.travelNotes = noteRes.data.count || 0
  } catch (error) { /* 角标降级 */ }
}

const goToLogin = () => router.push('/login')

const statCards = [
  { key: 'citiesVisited', label: '去过城市', icon: 'location-o', color: '#8B5CF6' },
  { key: 'totalDays', label: '旅行天数', icon: 'calendar-o', color: '#6366F1' },
  { key: 'totalSpent', label: '累计花费', icon: 'gold-coin-o', color: '#F59E0B', isMoney: true },
  { key: 'totalPhotos', label: '旅行照片', icon: 'photo-o', color: '#34D399' },
]

onMounted(() => { if (isLoggedIn.value) { loadProfile(); loadBadgeCounts() } })

/*
 * 【Bug修复】keep-alive 缓存后，onMounted 只执行一次
 * 每次切回「我的」Tab 时触发 onActivated，刷新用户数据
 * 避免缓存导致的数据过期（如积分、游记数变化）
 */
onActivated(() => {
  // 重新检查登录状态（可能在其他 Tab 登录/退出了）
  const wasLoggedIn = isLoggedIn.value
  isLoggedIn.value = !!getToken()
  // 登录状态变化时重新加载
  if (isLoggedIn.value && !wasLoggedIn) {
    loadProfile(); loadBadgeCounts()
  } else if (isLoggedIn.value) {
    // 已登录：轻量刷新角标
    loadBadgeCounts()
  } else if (!isLoggedIn.value && wasLoggedIn) {
    resetUserInfo()
  }
})

/* 【性能优化】离开个人中心时清理弹窗状态 */
onDeactivated(() => {
  showEditPopup.value = false
  showInvitePopup.value = false
})
</script>

<template>
  <div class="profile-page">
    <!-- 漂浮粒子 -->
    <div class="clouds-layer" aria-hidden="true">
      <span class="cloud-dot c1"></span><span class="cloud-dot c2"></span><span class="cloud-dot c3"></span>
    </div>
    <div class="profile-wrap">

      <!-- ======== 用户信息头图 ======== -->
      <div class="hero-card entrance-item entrance-d1">
        <!-- 背景装饰 -->
        <div class="hero-decor">
          <svg viewBox="0 0 400 180" preserveAspectRatio="none" class="hero-decor-svg">
            <ellipse cx="320" cy="30" rx="140" ry="90" fill="rgba(255,255,255,0.06)" />
            <ellipse cx="60" cy="150" rx="120" ry="70" fill="rgba(255,255,255,0.04)" />
            <circle cx="380" cy="160" r="50" fill="rgba(255,255,255,0.03)" />
            <circle cx="30" cy="20" r="30" fill="rgba(255,255,255,0.05)" />
          </svg>
        </div>

        <!-- 登录态 -->
        <template v-if="isLoggedIn">
          <div class="hero-user">
            <div class="hero-avatar" @click="handleAvatarClick">
              <van-image v-if="userInfo.avatar" round width="72" height="72" :src="userInfo.avatar" fit="cover" />
              <van-icon v-else name="user-o" size="36" color="#fff" />
              <div class="avatar-ring" />
            </div>
            <div class="hero-info">
              <div class="hero-name-row">
                <span class="hero-name">{{ sanitizeHtml(userInfo.nickname) }}</span>
                <span class="hero-level" @click="handleLevelClick">{{ userInfo.level }}</span>
                <van-icon name="edit" size="16" color="rgba(255,255,255,0.7)" class="hero-edit" @click="handleEditClick" />
              </div>
              <div v-if="userInfo.bio" class="hero-bio">{{ sanitizeHtml(userInfo.bio) }}</div>
              <div class="hero-meta">
                <span @click="handleMetaClick('following')">{{ formatNumber(userInfo.following) }} 关注</span>
                <i>·</i>
                <span @click="handleMetaClick('followers')">{{ formatNumber(userInfo.followers) }} 粉丝</span>
                <i>·</i>
                <span @click="handleMetaClick('notes')">{{ formatNumber(userInfo.travelNotes) }} 游记</span>
              </div>
            </div>
          </div>

          <!-- 旅行统计 -->
          <div class="stats-row">
            <div v-for="(s, i) in statCards" :key="i" class="stat-cell" :style="{ '--stat-color': s.color }">
              <div class="stat-icon-wrap">
                <van-icon :name="s.icon" :color="s.color" size="18" />
              </div>
              <div class="stat-val">
                {{ s.isMoney ? '¥' + formatMoney(travelStats[s.key]) : formatNumber(travelStats[s.key]) }}
              </div>
              <div class="stat-lbl">{{ s.label }}</div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="hero-actions">
            <button class="hero-act primary-act btn-tap-scale" @click="handleWriteNote">
              <van-icon name="edit" size="16" /> 写游记
            </button>
            <button class="hero-act second-act btn-tap-scale" @click="() => router.push('/trips')">
              <van-icon name="bookmark-o" size="16" /> 我的规划
            </button>
          </div>
        </template>

        <!-- 未登录态 -->
        <template v-else>
          <div class="hero-user guest-hero">
            <div class="hero-avatar guest-avatar">
              <van-icon name="user-o" size="40" color="#A78BFA" />
              <div class="avatar-ring" />
            </div>
            <div class="hero-info">
              <div class="hero-name-row">
                <span class="hero-name">旅行者</span>
              </div>
              <div class="hero-bio">登录后享受更多专属旅行服务</div>
            </div>
          </div>
          <div class="hero-actions">
            <button class="hero-act primary-act btn-tap-scale" @click="goToLogin">立即登录</button>
            <button class="hero-act second-act btn-tap-scale" @click="router.push('/register')">注册账号</button>
          </div>
        </template>
      </div>

      <!-- ======== 快捷操作 ======== -->
      <div class="section-card entrance-item entrance-d2">
        <div class="sec-head"><span class="sec-title">快捷操作</span></div>
        <div class="quick-row">
          <div v-for="(item, i) in quickActions" :key="i" class="quick-block" @click="handleQuickAction(item)">
            <div class="quick-block-icon" :style="{ background: `${item.color}18` }">
              <van-icon :name="item.icon" :color="item.color" size="22" />
            </div>
            <span class="quick-block-label">{{ item.name }}</span>
          </div>
        </div>
      </div>

      <!-- ======== 我的服务 ======== -->
      <div class="section-card">
        <div class="sec-head"><span class="sec-title">我的服务</span></div>
        <div class="service-list">
          <div v-for="(item, i) in serviceList" :key="i" class="svc-item" @click="handleServiceClick(item)">
            <div class="svc-left">
              <div class="svc-icon-box" :style="{ background: `${item.color}14` }">
                <van-icon :name="item.icon" :color="item.color" size="20" />
              </div>
              <div class="svc-text">
                <div class="svc-name">{{ item.name }}</div>
                <div class="svc-desc">{{ item.desc }}</div>
              </div>
            </div>
            <div class="svc-right">
              <van-badge v-if="item.badge > 0" :content="getBadgeContent(item.badge)" />
              <van-icon name="arrow" size="16" color="#CBD5E1" />
            </div>
          </div>
        </div>
      </div>

      <!-- ======== 退出登录 ======== -->
      <div v-if="isLoggedIn" class="logout-wrap">
        <button class="logout-btn btn-tap-scale" @click="handleLogout">退出登录</button>
      </div>

      <div style="height: 8px;" />
    </div>

    <!-- ======== 编辑资料弹窗 ======== -->
    <van-popup v-model:show="showEditPopup" position="bottom" :style="{ height: '42%' }" round>
      <div class="pop-header">
        <span class="pop-title">编辑资料</span>
        <van-icon name="cross" size="20" @click="showEditPopup = false" />
      </div>
      <div class="pop-body">
        <van-cell-group inset>
          <van-field v-model="editForm.nickname" label="昵称" placeholder="请输入昵称" maxlength="20" />
          <van-field v-model="editForm.bio" label="个性签名" placeholder="介绍一下自己吧" maxlength="100" type="textarea" :rows="3" />
        </van-cell-group>
        <div class="pop-btns">
          <van-button type="default" block class="pop-btn" @click="showEditPopup = false">取消</van-button>
          <van-button type="primary" block class="pop-btn pop-btn-primary" @click="saveProfile">保存</van-button>
        </div>
      </div>
    </van-popup>

    <!-- ======== 邀请好友弹窗 ======== -->
    <van-popup v-model:show="showInvitePopup" position="center" :style="{ width: '82%', borderRadius: '22px' }">
      <div class="invite-pop">
        <div class="invite-head">
          <van-icon name="gift-o" size="48" color="#8B5CF6" />
          <h3>邀请好友</h3>
          <p>邀请好友注册，双方获得专属旅行优惠券</p>
        </div>
        <div class="invite-link-box">
          <span class="invite-link">{{ window.location.origin }}/register?invite={{ userInfo.username || 'traveler' }}</span>
        </div>
        <div class="invite-btns">
          <van-button type="default" block class="invite-btn" @click="copyInviteLink">复制链接</van-button>
          <van-button type="primary" block class="invite-btn invite-btn-primary" @click="shareInvite">分享好友</van-button>
        </div>
        <van-icon name="cross" size="20" class="invite-close" @click="showInvitePopup = false" />
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
/* ==================== 页面 ==================== */
.profile-page {
  width: 100%; min-height: 100vh;
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
  padding-bottom: calc(var(--tabbar-height) + var(--safe-area-bottom) + 8px);
}
.profile-wrap { max-width: 480px; margin: 0 auto; padding: 16px 14px 0; }

/* ==================== Hero 卡片 ==================== */
.hero-card {
  position: relative; overflow: hidden;
  background: linear-gradient(145deg, #8B5CF6 0%, #7C3AED 35%, #6366F1 70%, #5B8DEF 100%);
  border-radius: 24px; padding: 24px; margin-bottom: 14px;
  color: #fff; box-shadow: 0 12px 32px rgba(139,92,246,0.3);
}
.hero-decor { position: absolute; inset: 0; pointer-events: none; }
.hero-decor-svg { width: 100%; height: 100%; }

/* 用户行 */
.hero-user { display: flex; gap: 14px; position: relative; z-index: 2; }
.hero-avatar { position: relative; flex-shrink: 0; cursor: pointer; }
.hero-avatar :deep(.van-image) { border: 3px solid rgba(255,255,255,0.3); }
.avatar-ring {
  position: absolute; inset: -4px; border-radius: 50%;
  border: 2px dashed rgba(255,255,255,0.2); pointer-events: none;
}
.guest-avatar {
  width: 72px; height: 72px; background: rgba(255,255,255,0.18);
  border-radius: 50%; display: flex; align-items: center; justify-content: center;
  border: 3px solid rgba(255,255,255,0.25);
}

.hero-info { flex: 1; display: flex; flex-direction: column; justify-content: center; }
.hero-name-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.hero-name { font-size: 21px; font-weight: 700; }
.hero-level {
  background: rgba(255,255,255,0.22); padding: 3px 10px; border-radius: 10px;
  font-size: 11px; font-weight: 600; cursor: pointer;
}
.hero-edit { cursor: pointer; margin-left: auto; }
.hero-bio { font-size: 13px; color: rgba(255,255,255,0.8); margin-bottom: 6px; line-height: 1.4; }
.hero-meta { display: flex; align-items: center; gap: 6px; font-size: 12px; color: rgba(255,255,255,0.7); }
.hero-meta span { cursor: pointer; }
.hero-meta i { color: rgba(255,255,255,0.3); font-style: normal; }

/* 统计 */
.stats-row {
  display: flex; margin-top: 18px; padding: 14px 8px;
  background: rgba(255,255,255,0.1); border-radius: 16px;
  backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px);
  position: relative; z-index: 2;
}
.stat-cell { flex: 1; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 4px; will-change: transform; }
.stat-icon-wrap {
  width: 32px; height: 32px; border-radius: 10px;
  background: rgba(255,255,255,0.15); display: flex; align-items: center; justify-content: center;
  margin-bottom: 2px;
}
.stat-val { font-size: 15px; font-weight: 700; }
.stat-lbl { font-size: 10px; color: rgba(255,255,255,0.7); }

/* 操作按钮 */
.hero-actions { display: flex; gap: 10px; margin-top: 14px; position: relative; z-index: 2; }
.hero-act {
  flex: 1; padding: 12px 0; border-radius: 14px; border: none;
  font-size: 14px; font-weight: 600; cursor: pointer;
  display: flex; align-items: center; justify-content: center; gap: 6px;
  transition: transform 0.18s, opacity 0.2s;
}
.hero-act:active { transform: scale(0.95); }
.primary-act { background: #fff; color: #7C3AED; }
.second-act { background: rgba(255,255,255,0.18); color: #fff; border: 1px solid rgba(255,255,255,0.25); }

.guest-hero { margin-bottom: 14px; }

/* ==================== 通用卡片 ==================== */
.section-card {
  background: #fff; border-radius: 20px; padding: 20px;
  margin-bottom: 14px; box-shadow: 0 4px 18px rgba(0,0,0,0.04);
  border: 1px solid rgba(139,92,246,0.06);
}
.sec-head { margin-bottom: 14px; }
.sec-title { font-size: 16px; font-weight: 700; color: #1E293B; }

/* ==================== 快捷操作 ==================== */
.quick-row { display: flex; justify-content: space-around; }
.quick-block {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  cursor: pointer; transition: transform 0.2s;
}
.quick-block:active { transform: scale(0.94); }
.quick-block-icon {
  width: 52px; height: 52px; border-radius: 16px;
  display: flex; align-items: center; justify-content: center;
}
.quick-block-label { font-size: 12px; color: #475569; font-weight: 500; }

/* ==================== 服务列表 ==================== */
.service-list { display: flex; flex-direction: column; }
.svc-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 15px 4px; border-bottom: 1px solid #F8FAFC;
  cursor: pointer; transition: background 0.15s;
}
.svc-item:last-child { border-bottom: none; }
.svc-item:active { background: #faf5ff; margin: 0 -8px; padding-left: 12px; padding-right: 12px; border-radius: 10px; }

.svc-left { display: flex; align-items: center; gap: 12px; }
.svc-icon-box {
  width: 42px; height: 42px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.svc-text { display: flex; flex-direction: column; }
.svc-name { font-size: 15px; font-weight: 500; color: #1E293B; }
.svc-desc { font-size: 12px; color: #94A3B8; margin-top: 2px; }
.svc-right { display: flex; align-items: center; gap: 8px; }

/* ==================== 退出登录 ==================== */
.logout-wrap { padding: 4px 0; }
.logout-btn {
  width: 100%; padding: 14px; border: none; border-radius: 18px;
  background: linear-gradient(135deg, #FECACA 0%, #FCA5A5 100%);
  color: #DC2626; font-size: 15px; font-weight: 600;
  cursor: pointer; transition: all 0.25s;
  box-shadow: 0 4px 14px rgba(239,68,68,0.15);
  letter-spacing: 1px;
}
.logout-btn:hover { box-shadow: 0 8px 24px rgba(239,68,68,0.25); }
.logout-btn:active { transform: scale(0.96); }

/* ==================== 弹窗 ==================== */
.pop-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18px 20px; border-bottom: 1px solid #F1F5F9;
}
.pop-title { font-size: 17px; font-weight: 600; }
.pop-body { padding: 16px 20px; }
.pop-btns { display: flex; gap: 12px; margin-top: 20px; }
.pop-btn { flex: 1; border-radius: 14px !important; }
.pop-btn-primary { background: linear-gradient(135deg, #8B5CF6, #6366F1) !important; border: none !important; color: #fff !important; }

.invite-pop { padding: 32px 20px; position: relative; }
.invite-head { text-align: center; margin-bottom: 24px; }
.invite-head h3 { font-size: 18px; font-weight: 700; color: #1E293B; margin: 14px 0 8px; }
.invite-head p { font-size: 13px; color: #94A3B8; }
.invite-link-box { background: #F8FAFC; border-radius: 12px; padding: 16px; margin-bottom: 22px; word-break: break-all; }
.invite-link { font-size: 13px; color: #64748B; }
.invite-btns { display: flex; gap: 12px; }
.invite-btn { flex: 1; border-radius: 12px !important; }
.invite-btn-primary { background: linear-gradient(135deg, #8B5CF6, #6366F1) !important; border: none !important; }
.invite-close { position: absolute; top: 16px; right: 16px; cursor: pointer; color: #94A3B8; }

/*
 * ================================================================
 * 个人中心页专属动效
 * ================================================================
 */
.clouds-layer { position: fixed; inset: 0; z-index: 0; pointer-events: none; overflow: hidden; }
.cloud-dot { position: absolute; border-radius: 50%; background: rgba(139,92,246,0.05); animation: cloudDriftSlow linear infinite; }
.c1 { width: 40px; height: 40px; top: 12%; left: 10%; animation-duration: 28s; }
.c2 { width: 50px; height: 50px; top: 50%; right: 8%; animation-duration: 34s; animation-delay: -10s; }
.c3 { width: 30px; height: 30px; top: 80%; left: 55%; animation-duration: 24s; animation-delay: -5s; }
/* 头像环渐变旋转微光 */
.avatar-ring {
  animation: ringRotate 6s linear infinite;
  border-style: dashed !important;
}
/* 顶部hero卡片渐变流动 */
.hero-card { background-size: 200% 200%; }
/* hero-card不设animation避免覆盖entrance-item的entranceUp */
/* 服务菜单项hover左滑高亮 */
.svc-item { transition: transform 0.3s cubic-bezier(0.4,0,0.2,1), background 0.3s ease, padding-left 0.3s ease; }
.svc-item:hover { transform: translateX(4px); padding-left: 8px; background: rgba(139,92,246,0.04); border-radius: 10px; }
.svc-item:active { transform: scale(0.98); }
</style>
