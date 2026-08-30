<script setup>
import { ref, computed, onMounted, reactive, onActivated, onDeactivated, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog, showLoadingToast, closeToast } from 'vant'

/*
 * 【Bug修复】显式声明组件名，供 keep-alive 的 include 白名单匹配
 * 缺失会导致 Tab 切换时组件无法命中缓存，每次销毁重建 → 空白
 */
defineOptions({ name: 'ProfileView' })
import { getToken, removeToken, getCurrentUsername } from '../utils/auth'
import { sanitizeHtml } from '../utils/security'
import { userApi, favoriteApi, couponApi, orderApi, noteApi, collectionApi } from '../api'
import {
  getCurrentUser,
  setCurrentUser,
  getMyData,
  setMyData,
  clearSession as clearAccountSession,
} from '../utils/userAccountStorage'
import EmptyState from '../components/EmptyState.vue'
import LazyImage from '../components/LazyImage.vue'
import { defineAsyncComponent } from 'vue'
const AIChatDialog = defineAsyncComponent(() => import('../components/AIChatDialog.vue'))
import { getAllSessions, deleteSession, switchToSession } from '../utils/chatSession'
import { useTheme } from '../utils/theme'
import { useI18n } from 'vue-i18n'
import { setLanguage } from '../i18n'
// BUGID ACCT-1 修复：引入行程共享状态 store，登出时清空跨账号残留行程数据
import { useTripStore } from '../stores/trip'

const router = useRouter()
const tripStore = useTripStore()

/* ==================== 深色模式（B4） ==================== */
const { themeMode, setTheme } = useTheme()
const themeOptions = [
  { value: 'system', labelKey: 'themeSystem' },
  { value: 'light', labelKey: 'themeLight' },
  { value: 'dark', labelKey: 'themeDark' },
]
const changeTheme = (v) => setTheme(v)

/* ==================== 语言切换（B5 i18n） ==================== */
const { t, locale } = useI18n()
const langOptions = [
  { value: 'zh-CN', labelKey: 'langZh' },
  { value: 'en-US', labelKey: 'langEn' },
]
const changeLanguage = (v) => setLanguage(v)

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
  { nameKey: 'myPlans', icon: 'bookmark-o', descKey: 'myPlansDesc', badge: 0, path: '/trips', color: '#8B5CF6' },
  { nameKey: 'myOrders', icon: 'orders-o', descKey: 'myOrdersDesc', badge: 0, path: '/orders', color: '#6366F1' },
  { nameKey: 'myFavorites', icon: 'star-o', descKey: 'myFavoritesDesc', badge: 0, path: '/favorites', color: '#F59E0B' },
  { nameKey: 'myCoupons', icon: 'coupon-o', descKey: 'myCouponsDesc', badge: 0, path: '/coupons', color: '#34D399' },
  { nameKey: 'myCollections', icon: 'label-o', descKey: 'myCollectionsDesc', badge: 0, action: 'collections', color: '#F472B6' },
])

/* ==================== 快捷操作 ==================== */
const quickActions = [
  { nameKey: 'writeNote', icon: 'edit', color: '#8B5CF6', path: '/write-note' },
  { nameKey: 'post', icon: 'photograph', color: '#FB7185', path: '/post' },
  { nameKey: 'invite', icon: 'friends-o', color: '#34D399', action: 'invite' },
  { nameKey: 'feedback', icon: 'smile-comment-o', color: '#F59E0B', path: '/feedback' },
]

/* ==================== 消息分类入口（从消息页集成） ==================== */
const categoryItems = reactive([
  { key: 'order', labelKey: 'categoryOrder', icon: 'orders-o', color: '#3B82F6', badge: 0 },
  { key: 'chat', labelKey: 'categoryChat', icon: 'chat-o', color: '#F59E0B', badge: 2 },
  { key: 'notify', labelKey: 'categoryNotify', icon: 'bell-o', color: '#F97316', badge: 1 },
  { key: 'vip', labelKey: 'categoryVip', icon: 'gem-o', color: '#EAB308', badge: 0 },
])

const conversations = ref([])
const loadConversations = () => { conversations.value = getAllSessions() }

const showAIChat = ref(false)
const activeConv = ref(null)
const aiInitialMessages = ref([])
const openConversation = (conv) => { activeConv.value = conv; aiInitialMessages.value = switchToSession(conv.id) || []; showAIChat.value = true }
const deleteConversation = async (id) => { try { await showConfirmDialog({ title: t('profile.deleteConversationTitle'), message: t('profile.deleteConversationConfirm') }); deleteSession(id); loadConversations(); showToast({ message: t('profile.deleted'), position: 'middle' }) } catch (e) {} }
const onAIChatClose = () => { showAIChat.value = false; activeConv.value = null; aiInitialMessages.value = []; loadConversations() }

const notifications = ref([])
const loadNotifications = () => { notifications.value = [{ id:1, type:'order', icon:'orders-o', iconColor:'#3B82F6', title:'行程规划已完成', preview:'您的"北京5日游"行程已生成', time: Date.now()-1800000, unread:true }, { id:2, type:'system', icon:'bell-o', iconColor:'#F97316', title:'系统通知', preview:'新版本已上线，新增AI智能对话功能', time: Date.now()-10800000, unread:true }, { id:3, type:'coupon', icon:'coupon-o', iconColor:'#F59E0B', title:'优惠券到账', preview:'恭喜您获得新人专享优惠券', time: Date.now()-86400000, unread:false }] }

const formatMsgTime = (ts) => { if(!ts) return ''; const d=new Date(ts), n=new Date(), h=Math.floor((n-d)/3600000); if(h<1) return t('profile.timeJustNow'); if(h<24) return t('profile.timeHoursAgo', { n: h }); if(h<48) return t('profile.timeYesterday'); if(h<168) return t('profile.timeDaysAgo', { n: Math.floor(h/24) }); return String(d.getMonth()+1).padStart(2,'0')+'/'+String(d.getDate()).padStart(2,'0') }
const getConvPreview = (c) => { if(!c.messages||!c.messages.length) return t('profile.newChat'); const m=[...c.messages].reverse(); const a=m.find(x=>x.type==='ai'&&x.content); if(a) return a.content.slice(0,50)+(a.content.length>50?'...':''); const u=m.find(x=>x.type==='user'&&x.content); return u?u.content.slice(0,50)+(u.content.length>50?'...':''):t('profile.newChat') }
const handleCategoryClick = (cat) => { if(!isLoggedIn.value){router.push('/login');return}; if(cat.key==='order') router.push('/orders'); else showToast({message:t('profile.featureDeveloping'),position:'middle'}) }
const handleContactService = () => { showToast({message:t('profile.serviceDeveloping'),position:'middle'}) }

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
  try { if (!isLoggedIn.value) { router.push('/login'); return }; showToast(t('profile.memberUpgradeDeveloping')) }
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
  if (!editForm.value.nickname.trim()) { showToast(t('profile.nicknameRequired')); return }
  const toast = showLoadingToast({ message: t('profile.saving'), duration: 0, forbidClick: true })
  try {
    const response = await userApi.updateProfile({ nickname: editForm.value.nickname, bio: editForm.value.bio })
    if (response.code === 0) {
      const data = response.data; userInfo.nickname = data.nickname; userInfo.bio = data.bio || ''
      // 【多账号隔离】写入当前账号独立存储
      setMyData('userInfo', { ...userInfo })
      localStorage.setItem('userInfo', JSON.stringify(userInfo)); showEditPopup.value = false; closeToast(); showToast(t('profile.updateSuccess'))
    } else { closeToast(); showToast(response.message || t('profile.saveFailed')) }
  } catch (error) {
    // 保存失败：不写本地、不回退弹窗，提示重试（避免误报"已保存到本地"导致数据丢失）
    closeToast(); showToast(t('profile.saveFailedRetry'))
  }
}

const handleWriteNote = () => { if (!isLoggedIn.value) { router.push('/login'); return }; router.push('/write-note') }
const handleInvite = () => { if (!isLoggedIn.value) { router.push('/login'); return }; showInvitePopup.value = true }
const inviteOrigin = ref(window.location.origin)
const inviteLink = computed(() => `${inviteOrigin.value}/register?invite=${userInfo.username || 'traveler'}`)
const inviteShareOptions = [
  { key: 'wechat', nameKey: 'shareWechatFriend', icon: 'wechat', color: '#07C160' },
  { key: 'moments', nameKey: 'shareMoments', icon: 'cluster-o', color: '#07C160' },
  { key: 'copyLink', nameKey: 'copyLink', icon: 'link-o', color: '#8B5CF6' },
  { key: 'qq', nameKey: 'shareQQ', icon: 'chat-o', color: '#12B7F5' },
  { key: 'weibo', nameKey: 'shareWeibo', icon: 'share-o', color: '#E6162D' },
  { key: 'saveQr', nameKey: 'saveQr', icon: 'qr', color: '#3B82F6' },
]
const handleInviteShare = async (opt) => {
  if (opt.key === 'copyLink') {
    try { await navigator.clipboard.writeText(inviteLink.value); showToast(t('profile.inviteLinkCopied')) } catch { showToast(t('profile.copyFailed')) }
  } else if (opt.key === 'saveQr') {
    showToast(t('profile.qrDeveloping'))
  } else if (navigator.share) {
    try {
      await navigator.share({ title: t('app.name'), text: t('profile.inviteShareText'), url: inviteLink.value })
    } catch {}
  } else {
    try { await navigator.clipboard.writeText(inviteLink.value); showToast(t('profile.copyShareFor', { platform: t('profile.' + opt.nameKey) })) } catch { showToast(t('profile.shareFailed')) }
  }
}
const copyInviteLink = async () => {
  try { await navigator.clipboard.writeText(inviteLink.value); showToast(t('profile.inviteLinkCopied')) } catch { showToast(t('profile.copyFailed')) }
}
const shareInvite = () => { showInvitePopup.value = true }
const handleQuickAction = (item) => {
  if (!isLoggedIn.value) { router.push('/login'); return }
  if (item.action === 'invite') handleInvite(); else if (item.path) router.push(item.path)
}
const handleServiceClick = (item) => {
  if (!isLoggedIn.value) { router.push('/login'); return }
  if (item.action === 'collections') { openCollections(); return }
  if (item.path) router.push(item.path)
}

/* ==================== 我的收藏夹（新功能） ==================== */
const showCollectionsPopup = ref(false)
const collectionsView = ref('list') // list | create | detail
const collections = ref([])
const collectionsLoading = ref(false)
const collectionsFailed = ref(false)
const detailCollection = ref(null)
const detailLoading = ref(false)
const detailFailed = ref(false)
const removingNoteId = ref(null)
const createForm = ref({ name: '', description: '', isPublic: false })
const creating = ref(false)

const openCollections = () => {
  collectionsView.value = 'list'
  showCollectionsPopup.value = true
  loadCollections()
}

const closeCollections = () => {
  showCollectionsPopup.value = false
  collectionsView.value = 'list'
  detailCollection.value = null
}

const loadCollections = async () => {
  collectionsLoading.value = true
  collectionsFailed.value = false
  try {
    const res = await collectionApi.getMine()
    if (res.code === 0) collections.value = res.data || []
    else { collections.value = []; collectionsFailed.value = true }
  } catch (e) {
    collections.value = []
    collectionsFailed.value = true
  } finally {
    collectionsLoading.value = false
  }
}

const openCollectionDetail = async (c) => {
  collectionsView.value = 'detail'
  detailCollection.value = c
  detailLoading.value = true
  detailFailed.value = false
  try {
    const res = await collectionApi.getDetail(c.id)
    if (res.code === 0) detailCollection.value = res.data
    else detailFailed.value = true
  } catch (e) {
    detailFailed.value = true
  } finally {
    detailLoading.value = false
  }
}

const backToList = () => {
  collectionsView.value = 'list'
  detailCollection.value = null
  loadCollections()
}

const removeCollection = async (c) => {
  try {
    await showConfirmDialog({ title: t('collection.delete'), message: t('collection.confirmDelete', { name: c.name }) })
  } catch (e) { return }
  try {
    const res = await collectionApi.remove(c.id)
    if (res.code === 0) {
      showToast(t('collection.deleteSuccess'))
      loadCollections()
    } else {
      showToast(res.message || t('collection.deleteFailed'))
    }
  } catch (e) {
    showToast(t('collection.deleteFailed'))
  }
}

const removeNoteFromCollection = async (n) => {
  if (!detailCollection.value || removingNoteId.value) return
  try {
    await showConfirmDialog({ title: t('collection.removeNote'), message: t('collection.removeNoteConfirm') })
  } catch (e) { return }
  removingNoteId.value = n.id
  try {
    const res = await collectionApi.removeNote(detailCollection.value.id, n.id)
    if (res.code === 0) {
      showToast(t('collection.removeNoteSuccess'))
      const notes = detailCollection.value.notes || []
      detailCollection.value.notes = notes.filter(x => x.id !== n.id)
      if (detailCollection.value.noteCount > 0) detailCollection.value.noteCount -= 1
    } else {
      showToast(res.message || t('collection.deleteFailed'))
    }
  } catch (e) {
    showToast(t('collection.deleteFailed'))
  } finally {
    removingNoteId.value = null
  }
}

const goNoteDetail = (n) => {
  closeCollections()
  router.push({ path: '/note-detail', query: { id: n.id } })
}

const submitCreateCollection = async () => {
  if (creating.value) return
  const name = createForm.value.name.trim()
  if (!name) { showToast(t('collection.nameRequired')); return }
  creating.value = true
  try {
    const res = await collectionApi.create({
      name,
      description: createForm.value.description.trim() || null,
      isPublic: createForm.value.isPublic,
    })
    if (res.code === 0) {
      showToast(t('collection.createSuccess'))
      createForm.value = { name: '', description: '', isPublic: false }
      collectionsView.value = 'list'
      loadCollections()
    } else {
      showToast(res.message || t('collection.createFailed'))
    }
  } catch (e) {
    showToast(t('collection.createFailed'))
  } finally {
    creating.value = false
  }
}

/* ==================== 退出登录 ==================== */
let logoutTimer = null // 退出跳转定时器：onUnmounted 时清理
const handleLogout = async () => {
  try {
    await showConfirmDialog({ title: t('profile.logoutConfirmTitle'), message: t('profile.logoutConfirmMessage') })
    showLoadingToast({ message: t('profile.loggingOut'), duration: 0, forbidClick: true, loadingType: 'spinner' })
    try { await userApi.logout() } catch (error) { /* 后端失败继续清除 */ }
    finally {
      removeToken()
      // 【多账号隔离】退出仅清空会话缓存，保留账号持久化数据
      clearAccountSession()
      // BUGID ACCT-1 修复：登出时清空行程 store 的跨账号残留数据（planData/酒店/地图点等）
      tripStore.resetState()
      // BUGID ACCT-2 修复：登出时一并清除全局 localStorage.userInfo，防止切换账号残留上一账号信息
      localStorage.removeItem('userInfo')
      isLoggedIn.value = false; resetUserInfo(); closeToast()
      showToast({ message: t('profile.loggedOut'), position: 'middle' })
      clearTimeout(logoutTimer)
      logoutTimer = setTimeout(() => router.push('/login'), 800)
    }
  } catch (e) { /* 取消 */ }
}

const resetUserInfo = () => {
  userInfo.avatar = ''; userInfo.nickname = t('profile.traveler'); userInfo.username = ''; userInfo.level = t('profile.regularMember')
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
      userInfo.nickname = data.nickname || t('profile.traveler'); userInfo.username = data.username || ''
      userInfo.level = data.level || t('profile.regularMember'); userInfo.points = data.points || 0
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
  { key: 'citiesVisited', labelKey: 'statCities', icon: 'location-o', color: '#8B5CF6' },
  { key: 'totalDays', labelKey: 'statDays', icon: 'calendar-o', color: '#6366F1' },
  { key: 'totalSpent', labelKey: 'statSpent', icon: 'gold-coin-o', color: '#F59E0B', isMoney: true },
  { key: 'totalPhotos', labelKey: 'statPhotos', icon: 'photo-o', color: '#34D399' },
]

onMounted(() => { if (isLoggedIn.value) { loadProfile(); loadBadgeCounts() } })

onUnmounted(() => { clearTimeout(logoutTimer); logoutTimer = null })

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
    loadConversations(); loadNotifications();
  } else if (!isLoggedIn.value && wasLoggedIn) {
    resetUserInfo()
  }
})

/* 【性能优化】离开个人中心时清理弹窗状态 */
onDeactivated(() => {
  showEditPopup.value = false
  showInvitePopup.value = false
  showAIChat.value = false; activeConv.value = null;
  showCollectionsPopup.value = false
})
</script>

<template>
  <div class="profile-page">
    <!-- 漂浮粒子 — 已禁用 -->
    <div class="profile-wrap">

      <!-- ======== 用户信息头图 ======== -->
      <div class="hero-card entrance-item entrance-d1">
        <img class="hero-bg-img" src="https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=1920&q=85" alt="" />
        <div class="hero-overlay"></div>

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
                <span @click="handleMetaClick('following')">{{ formatNumber(userInfo.following) }} {{ t('profile.following') }}</span>
                <i>·</i>
                <span @click="handleMetaClick('followers')">{{ formatNumber(userInfo.followers) }} {{ t('profile.followers') }}</span>
                <i>·</i>
                <span @click="handleMetaClick('notes')">{{ formatNumber(userInfo.travelNotes) }} {{ t('profile.notes') }}</span>
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
              <div class="stat-lbl">{{ t('profile.' + s.labelKey) }}</div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="hero-actions">
            <button class="hero-act primary-act btn-tap-scale" @click="handleWriteNote">
              <van-icon name="edit" size="16" /> {{ t('profile.writeNote') }}
            </button>
            <button class="hero-act second-act btn-tap-scale" @click="() => router.push('/trips')">
              <van-icon name="bookmark-o" size="16" /> {{ t('profile.planShortcut') }}
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
                <span class="hero-name">{{ t('profile.traveler') }}</span>
              </div>
              <div class="hero-bio">{{ t('profile.guestHint') }}</div>
            </div>
          </div>
          <div class="hero-actions">
            <button class="hero-act primary-act btn-tap-scale" @click="goToLogin">{{ t('auth.loginNow') }}</button>
            <button class="hero-act second-act btn-tap-scale" @click="router.push('/register')">{{ t('auth.registerAccount') }}</button>
          </div>
        </template>
      </div>

      <!-- ======== 快捷操作 ======== -->
      <div class="section-card entrance-item entrance-d2">
        <div class="sec-head"><span class="sec-title">{{ t('profile.quickActions') }}</span></div>
        <div class="quick-row">
          <div v-for="(item, i) in quickActions" :key="i" class="quick-block" @click="handleQuickAction(item)">
            <div class="quick-block-icon" :style="{ background: `${item.color}18` }">
              <van-icon :name="item.icon" :color="item.color" size="22" />
            </div>
            <span class="quick-block-label">{{ t('profile.' + item.nameKey) }}</span>
          </div>
        </div>
      </div>

      <!-- ======== 消息分类入口 ======== -->
      <div class="section-card">
        <div class="category-grid">
          <div v-for="cat in categoryItems" :key="cat.key" class="cat-item" @click="handleCategoryClick(cat)">
            <div class="cat-icon-wrap">
              <div class="cat-icon" :style="{ background: `${cat.color}14` }"><van-icon :name="cat.icon" :color="cat.color" size="22" /></div>
              <van-badge v-if="cat.badge > 0" :content="cat.badge" class="cat-badge" />
            </div>
            <span class="cat-label">{{ t('profile.' + cat.labelKey) }}</span>
          </div>
        </div>
      </div>

      <!-- ======== AI 对话记录 ======== -->
      <div class="section-card" v-if="isLoggedIn">
        <div class="sec-head"><span class="sec-title">{{ t('profile.aiChatHistory') }}</span></div>
        <div v-if="conversations.length === 0" class="empty-conv-wrap">
          <EmptyState icon="chat-o" :title="t('profile.noAIHistory')" :desc="t('profile.noAIHistoryDesc')" :btn-text="t('profile.goHomeToTry')" btn-type="gradient" @btn-click="() => router.push('/')" />
        </div>
        <div v-else class="conv-list">
          <div v-for="conv in conversations" :key="conv.id" class="conv-card">
            <div class="conv-accent" />
            <div class="conv-info" @click="openConversation(conv)">
              <div class="conv-top-row"><span class="conv-title">{{ conv.title || t('profile.unnamedChat') }}</span><span class="conv-time">{{ formatMsgTime(conv.updatedAt) }}</span></div>
              <p class="conv-preview">{{ getConvPreview(conv) }}</p>
            </div>
            <div class="conv-actions"><van-icon name="arrow" size="16" color="#CBD5E1" class="conv-arrow" @click="openConversation(conv)" /><van-icon name="delete-o" size="18" color="#EF4444" class="conv-delete" @click.stop="deleteConversation(conv.id)" /></div>
          </div>
        </div>
      </div>

      <!-- ======== 消息通知 ======== -->
      <div class="section-card" v-if="isLoggedIn">
        <div class="sec-head"><span class="sec-title">{{ t('profile.notifications') }}</span></div>
        <div v-if="notifications.length === 0" class="empty-notif"><p class="empty-notif-title">{{ t('profile.noMoreMessages') }}</p><p class="empty-notif-hint">{{ t('profile.notificationsHint') }}</p></div>
        <div v-else class="notif-list">
          <div v-for="item in notifications" :key="item.id" class="notif-item" :class="{ unread: item.unread }">
            <div class="notif-icon-wrap"><div class="notif-icon" :style="{ background: `${item.iconColor}14` }"><van-icon :name="item.icon" :color="item.iconColor" size="20" /></div><span v-if="item.unread" class="notif-dot" /></div>
            <div class="notif-body"><div class="notif-top-row"><span class="notif-title">{{ item.title }}</span><span class="notif-time">{{ formatMsgTime(item.time) }}</span></div><p class="notif-preview">{{ item.preview }}</p></div>
          </div>
        </div>
      </div>

      <!-- ======== 我的服务 ======== -->
      <div class="section-card">
        <div class="sec-head"><span class="sec-title">{{ t('profile.myServices') }}</span></div>
        <div class="service-list">
          <div v-for="(item, i) in serviceList" :key="i" class="svc-item" @click="handleServiceClick(item)">
            <div class="svc-left">
              <div class="svc-icon-box" :style="{ background: `${item.color}14` }">
                <van-icon :name="item.icon" :color="item.color" size="20" />
              </div>
              <div class="svc-text">
                <div class="svc-name">{{ t('profile.' + item.nameKey) }}</div>
                <div class="svc-desc">{{ t('profile.' + item.descKey) }}</div>
              </div>
            </div>
            <div class="svc-right">
              <van-badge v-if="item.badge > 0" :content="getBadgeContent(item.badge)" />
              <van-icon name="arrow" size="16" color="#CBD5E1" />
            </div>
          </div>
        </div>
      </div>

      <!-- ======== 深色模式 ======== -->
      <div class="settings-card">
        <div class="settings-title">{{ t('settings.theme') }}</div>
        <div class="theme-options">
          <button
            v-for="opt in themeOptions"
            :key="opt.value"
            :class="['theme-opt', { active: themeMode === opt.value }]"
            @click="changeTheme(opt.value)"
          >{{ t(`settings.${opt.labelKey}`) }}</button>
        </div>
      </div>

      <!-- ======== 语言切换 ======== -->
      <div class="settings-card">
        <div class="settings-title">{{ t('settings.language') }}</div>
        <div class="theme-options">
          <button
            v-for="opt in langOptions"
            :key="opt.value"
            :class="['theme-opt', { active: locale === opt.value }]"
            @click="changeLanguage(opt.value)"
          >{{ t(`settings.${opt.labelKey}`) }}</button>
        </div>
      </div>

      <!-- ======== 退出登录 ======== -->
      <div v-if="isLoggedIn" class="logout-wrap">
        <button class="logout-btn btn-tap-scale" @click="handleLogout">{{ t('common.logout') }}</button>
      </div>

      <div style="height: 8px;" />
    </div>

    <AIChatDialog v-model:visible="showAIChat" :initial-messages="aiInitialMessages" @close="onAIChatClose" />

    <!-- ======== 编辑资料弹窗 ======== -->
    <van-popup v-model:show="showEditPopup" position="bottom" :style="{ height: '42%' }" round>
      <div class="pop-header">
        <span class="pop-title">{{ t('profile.editProfile') }}</span>
        <van-icon name="cross" size="20" @click="showEditPopup = false" />
      </div>
      <div class="pop-body">
        <van-cell-group inset>
          <van-field v-model="editForm.nickname" :label="t('profile.nickname')" :placeholder="t('profile.enterNickname')" maxlength="20" />
          <van-field v-model="editForm.bio" :label="t('profile.bio')" :placeholder="t('profile.bioIntro')" maxlength="100" type="textarea" :rows="3" />
        </van-cell-group>
        <div class="pop-btns">
          <van-button type="default" block class="pop-btn" @click="showEditPopup = false">{{ t('common.cancel') }}</van-button>
          <van-button type="primary" block class="pop-btn pop-btn-primary" @click="saveProfile">{{ t('common.save') }}</van-button>
        </div>
      </div>
    </van-popup>

    <!-- ======== 邀请好友弹窗 ======== -->
    <van-popup v-model:show="showInvitePopup" position="center" :style="{ width: '82%', borderRadius: '22px' }">
      <div class="invite-pop">
        <van-icon name="cross" size="20" class="invite-close" @click="showInvitePopup = false" />
        <div class="invite-head">
          <van-icon name="gift-o" size="42" color="#8B5CF6" />
          <h3>{{ t('profile.invite') }}</h3>
          <p>{{ t('profile.inviteDesc') }}</p>
        </div>
        <div class="invite-link-box" @click="copyInviteLink">
          <span class="invite-link">{{ inviteLink }}</span>
          <van-icon name="description" size="16" color="#8B5CF6" />
        </div>
        <div class="share-grid">
          <div v-for="opt in inviteShareOptions" :key="opt.key" class="share-option" @click="handleInviteShare(opt)">
            <div class="share-icon-circle" :style="{ background: opt.color }"><van-icon :name="opt.icon" size="22" color="#fff" /></div>
            <span class="share-label">{{ t('profile.' + opt.nameKey) }}</span>
          </div>
        </div>
      </div>
    </van-popup>

    <!-- ======== 我的收藏夹弹窗（新功能） ======== -->
    <van-popup v-model:show="showCollectionsPopup" position="bottom" :style="{ height: '75%' }" round safe-area-inset-bottom @update:show="(val) => { if (!val) closeCollections() }">
      <div class="collections-pop">
        <div class="pop-header">
          <span class="pop-title">
            <van-icon v-if="collectionsView === 'detail'" name="arrow-left" size="16" class="collections-back" @click="backToList" />
            {{ collectionsView === 'create' ? t('collection.createNew') : t('collection.myCollections') }}
          </span>
          <van-icon name="cross" size="20" @click="closeCollections" />
        </div>

        <!-- 列表视图 -->
        <template v-if="collectionsView === 'list'">
          <div class="pop-body collections-list-body">
            <van-loading v-if="collectionsLoading" size="24" color="#8B5CF6" class="collections-loading" />
            <div v-else-if="collectionsFailed" class="collections-fail">
              <p>{{ t('collection.loadFailed') }}</p>
              <van-button size="small" round plain class="collections-retry" @click="loadCollections">{{ t('common.retry') }}</van-button>
            </div>
            <EmptyState v-else-if="collections.length === 0" icon="label-o" icon-size="56" :title="t('collection.empty')" :desc="t('collection.emptyDesc')" />
            <div v-else class="collections-list">
              <div v-for="c in collections" :key="c.id" class="collections-card">
                <div class="collections-main" @click="openCollectionDetail(c)">
                  <div class="collections-icon"><van-icon :name="c.isPublic ? 'eye-o' : 'lock'" size="18" :color="c.isPublic ? '#34D399' : '#94a3b8'" /></div>
                  <div class="collections-info">
                    <div class="collections-name">{{ c.name }}</div>
                    <div class="collections-meta">
                      <span>{{ t('collection.noteCount', { n: c.noteCount || 0 }) }}</span>
                      <span class="collections-vis">{{ c.isPublic ? t('collection.publicLabel') : t('collection.privateLabel') }}</span>
                    </div>
                  </div>
                </div>
                <van-icon name="delete-o" size="18" color="#CBD5E1" class="collections-del" @click="removeCollection(c)" />
              </div>
            </div>
          </div>
          <div class="pop-btns collections-footer">
            <van-button block round plain class="pop-btn collections-create-btn" @click="collectionsView = 'create'">
              <van-icon name="plus" size="16" /> {{ t('collection.createNew') }}
            </van-button>
          </div>
        </template>

        <!-- 新建视图 -->
        <template v-else-if="collectionsView === 'create'">
          <div class="pop-body">
            <van-cell-group inset>
              <van-field v-model="createForm.name" :label="t('collection.collectionName')" :placeholder="t('collection.namePlaceholder')" maxlength="100" />
              <van-field v-model="createForm.description" :label="t('collection.description')" :placeholder="t('collection.descPlaceholder')" maxlength="500" type="textarea" :rows="3" />
            </van-cell-group>
            <div class="public-row">
              <span>{{ t('collection.isPublic') }}</span>
              <van-switch v-model="createForm.isPublic" size="20" />
            </div>
            <p class="public-hint">{{ t('collection.publicHint') }}</p>
          </div>
          <div class="pop-btns collections-footer">
            <van-button type="default" block class="pop-btn" @click="collectionsView = 'list'">{{ t('common.cancel') }}</van-button>
            <van-button type="primary" block class="pop-btn pop-btn-primary" :loading="creating" @click="submitCreateCollection">{{ t('common.save') }}</van-button>
          </div>
        </template>

        <!-- 详情视图 -->
        <template v-else>
          <div class="pop-body collections-list-body">
            <van-loading v-if="detailLoading" size="24" color="#8B5CF6" class="collections-loading" />
            <div v-else-if="detailFailed" class="collections-fail"><p>{{ t('collection.loadFailed') }}</p></div>
            <template v-else-if="detailCollection">
              <div class="collections-detail-head">
                <div class="collections-detail-name">{{ detailCollection.name }}</div>
                <div class="collections-detail-meta">
                  <span>{{ t('collection.noteCount', { n: detailCollection.noteCount || 0 }) }}</span>
                  <span class="collections-vis">{{ detailCollection.isPublic ? t('collection.publicLabel') : t('collection.privateLabel') }}</span>
                </div>
                <p v-if="detailCollection.description" class="collections-detail-desc">{{ detailCollection.description }}</p>
              </div>
              <div v-if="!detailCollection.notes || detailCollection.notes.length === 0" class="collections-empty-notes">{{ t('collection.noNotes') }}</div>
              <div v-else class="collections-note-list">
                <div v-for="n in detailCollection.notes" :key="n.id" class="collections-note" @click="goNoteDetail(n)">
                  <LazyImage v-if="n.cover" :src="n.cover" class="collections-note-cover" />
                  <div v-else class="collections-note-cover collections-note-cover--ph"><van-icon name="photo-o" size="20" color="#CBD5E1" /></div>
                  <div class="collections-note-info">
                    <div class="collections-note-title">{{ n.title || t('collection.untitledNote') }}</div>
                    <div class="collections-note-meta">
                      <span><van-icon name="good-job-o" size="12" /> {{ n.likes || 0 }}</span>
                      <span><van-icon name="eye-o" size="12" /> {{ n.views || 0 }}</span>
                    </div>
                  </div>
                  <van-icon v-if="detailCollection.isOwner" name="delete-o" size="16" color="#CBD5E1" class="collections-note-del" @click.stop="removeNoteFromCollection(n)" />
                </div>
              </div>
            </template>
          </div>
        </template>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
/* ==================== 页面 ==================== */
.profile-page {
  width: 100%; min-height: 100vh;
  background: transparent;
  padding-bottom: calc(10px + 48px + 12px + var(--safe-area-bottom, 0px));
}
.profile-wrap { max-width: 480px; margin: 0 auto; padding: 0 14px; }

/* ==================== Hero 卡片 — 山水大图 + 用户信息 ==================== */
.hero-card {
  position: relative; overflow: hidden;
  border-radius: 0 0 22px 22px; padding: 24px; margin: 0 -14px 14px;
  color: #fff; box-shadow: 0 4px 20px rgba(0,0,0,0.1);
}
.hero-bg-img {
  position: absolute; inset: 0;
  width: 100%; height: 100%; object-fit: cover;
}
.hero-overlay {
  position: absolute; inset: 0;
  background: linear-gradient(160deg, rgba(0,0,0,0.15) 0%, rgba(0,0,0,0.25) 50%, rgba(0,0,0,0.5) 100%);
  pointer-events: none; z-index: 1;
}
.hero-decor, .hero-decor-svg { display: none; }

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
  background: rgba(255,255,255,0.15); border-radius: 16px;
  backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px);
  position: relative; z-index: 2;
  border: 0.5px solid rgba(255,255,255,0.2);
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

/* ==================== 通用卡片 — iOS 透光磨砂玻璃 ==================== */
.section-card {
  background:
    linear-gradient(160deg, rgba(255,255,255,0.65) 0%, rgba(255,255,255,0.12) 35%, rgba(255,255,255,0.02) 60%, rgba(255,255,255,0.3) 100%),
    rgba(255,255,255,0.55);
  backdrop-filter: blur(16px) saturate(160%);
  -webkit-backdrop-filter: blur(16px) saturate(160%);
  border-radius: 20px; padding: 20px;
  margin-bottom: 14px;
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.65),
    0 2px 12px rgba(0,0,0,0.03);
  border: 1px solid rgba(255,255,255,0.65);
}
.sec-head { margin-bottom: 14px; }
.sec-title { font-size: 16px; font-weight: 700; color: var(--text-primary); }

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
.svc-name { font-size: 15px; font-weight: 500; color: var(--text-primary); }
.svc-desc { font-size: 12px; color: var(--text-hint); margin-top: 2px; }
.svc-right { display: flex; align-items: center; gap: 8px; }

/* ==================== 退出登录 ==================== */
.logout-wrap { padding: 4px 0; }

/* 深色模式设置卡 */
.settings-card {
  background: rgba(255,255,255,0.72);
  backdrop-filter: blur(16px);
  border-radius: 18px;
  padding: 14px 16px;
  margin-bottom: 12px;
  border: 1px solid rgba(255,255,255,0.5);
  box-shadow: 0 4px 18px rgba(0,0,0,0.04);
}
.settings-title { font-size: 13px; font-weight: 600; color: var(--text-secondary); margin-bottom: 10px; }
.theme-options { display: flex; gap: 8px; }
.theme-opt {
  flex: 1; padding: 8px 0; border: 1px solid #E2E8F0; border-radius: 12px;
  background: transparent; color: var(--text-secondary); font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all 0.2s;
}
.theme-opt.active {
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  border-color: transparent; color: #fff; font-weight: 600;
  box-shadow: 0 4px 12px rgba(139,92,246,0.25);
}
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

/* ======== 我的收藏夹弹窗（新功能） ======== */
.collections-pop { display: flex; flex-direction: column; height: 100%; background: #fff; border-radius: 20px 20px 0 0; }
.collections-pop .pop-header { flex-shrink: 0; }
.collections-back { margin-right: 8px; cursor: pointer; vertical-align: -3px; }
.collections-list-body { flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; }
.collections-loading { display: block; margin: 80px auto; }
.collections-fail { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 0; color: #94a3b8; font-size: 13px; }
.collections-fail p { margin: 0; }
.collections-retry { color: #8B5CF6 !important; border-color: #C4B5FD !important; }
.collections-list { display: flex; flex-direction: column; gap: 10px; }
.collections-card { display: flex; align-items: center; gap: 10px; padding: 14px; background: #faf8ff; border-radius: 14px; }
.collections-main { display: flex; align-items: center; gap: 12px; flex: 1; min-width: 0; cursor: pointer; }
.collections-icon { width: 40px; height: 40px; border-radius: 12px; background: #fff; display: flex; align-items: center; justify-content: center; flex-shrink: 0; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05); }
.collections-info { flex: 1; min-width: 0; }
.collections-name { font-size: 14px; font-weight: 600; color: #1e293b; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.collections-meta { display: flex; align-items: center; gap: 8px; font-size: 11px; color: #94a3b8; margin-top: 3px; }
.collections-vis { padding: 1px 8px; border-radius: 8px; background: rgba(139, 92, 246, 0.07); color: #8B5CF6; }
.collections-del { padding: 6px; cursor: pointer; }
.collections-footer { margin-top: 0; flex-shrink: 0; padding: 12px 20px; padding-bottom: calc(12px + env(safe-area-inset-bottom, 0px)); }
.collections-create-btn { color: #8B5CF6 !important; border-color: #C4B5FD !important; display: flex; align-items: center; justify-content: center; gap: 6px; }
.collections-detail-head { padding: 4px 2px 14px; border-bottom: 1px solid #f1f5f9; margin-bottom: 12px; }
.collections-detail-name { font-size: 16px; font-weight: 700; color: #1e293b; }
.collections-detail-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #94a3b8; margin-top: 4px; }
.collections-detail-desc { font-size: 13px; color: #64748b; margin: 8px 0 0; line-height: 1.5; }
.collections-empty-notes { text-align: center; color: #94a3b8; font-size: 13px; padding: 40px 0; }
.collections-note-list { display: flex; flex-direction: column; gap: 10px; }
.collections-note { display: flex; align-items: center; gap: 10px; padding: 10px; background: #f8fafc; border-radius: 12px; cursor: pointer; }
.collections-note:active { background: #f1f5f9; }
.collections-note-cover { width: 56px; height: 56px; border-radius: 10px; object-fit: cover; flex-shrink: 0; }
.collections-note-cover--ph { background: #f1f5f9; display: flex; align-items: center; justify-content: center; }
.collections-note-info { flex: 1; min-width: 0; }
.collections-note-title { font-size: 13px; font-weight: 600; color: #334155; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.collections-note-meta { display: flex; gap: 12px; font-size: 11px; color: #94a3b8; margin-top: 4px; }
.collections-note-meta span { display: inline-flex; align-items: center; gap: 3px; }
.collections-note-del { padding: 6px; cursor: pointer; }
.public-row { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px 4px; font-size: 13px; color: #475569; }
.public-hint { font-size: 11px; color: #94a3b8; padding: 0 16px 4px; margin: 6px 0 0; }

.invite-pop { padding: 28px 20px 22px; position: relative; background: #fff; border-radius: 22px; }
.invite-head { text-align: center; margin-bottom: 16px; }
.invite-head h3 { font-size: 18px; font-weight: 700; color: var(--text-primary); margin: 10px 0 6px; }
.invite-head p { font-size: 13px; color: var(--text-hint); }
.invite-link-box { background: #F8FAFC; border-radius: 12px; padding: 12px 14px; margin-bottom: 18px; display:flex; align-items:center; justify-content:space-between; gap:8px; cursor:pointer; }
.invite-link { font-size: 12px; color: var(--text-secondary); word-break: break-all; flex:1; min-width:0; }
.invite-pop .share-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px 8px; }
.invite-pop .share-option { display: flex; flex-direction: column; align-items: center; gap: 6px; cursor: pointer; }
.invite-pop .share-option:active { transform: scale(0.9); }
.invite-pop .share-icon-circle { width: 44px; height: 44px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.invite-pop .share-label { font-size: 11px; color: #64748b; }
.invite-close { position: absolute; top: 14px; right: 14px; cursor: pointer; color: var(--text-hint); }

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
.category-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:4px; }
.cat-item { display:flex; flex-direction:column; align-items:center; gap:8px; cursor:pointer; padding:10px 0; transition:transform 0.2s; }
.cat-item:active { transform:scale(0.94); }
.cat-icon-wrap { position:relative; }
.cat-icon { width:50px; height:50px; border-radius:16px; display:flex; align-items:center; justify-content:center; }
.cat-badge { position:absolute; top:-2px; right:-6px; }
.cat-label { font-size:12px; color:#475569; font-weight:500; }
.conv-list { display:flex; flex-direction:column; gap:10px; }
.conv-card { display:flex; align-items:center; gap:12px; background:linear-gradient(160deg, rgba(255,255,255,0.65) 0%, rgba(255,255,255,0.15) 40%, rgba(255,255,255,0.3) 100%),rgba(255,255,255,0.65); backdrop-filter:blur(12px) saturate(150%); -webkit-backdrop-filter:blur(12px) saturate(150%); border-radius:20px; box-shadow:inset 0 1px 0 rgba(255,255,255,0.6),0 2px 10px rgba(0,0,0,0.03); border:1px solid rgba(255,255,255,0.6); overflow:hidden; }
.conv-accent { width:4px; min-width:4px; align-self:stretch; background:linear-gradient(180deg,#A78BFA,#8B5CF6,#6366F1); border-radius:2px 0 0 2px; }
.conv-info { flex:1; min-width:0; padding:16px 0; cursor:pointer; }
.conv-top-row { display:flex; justify-content:space-between; align-items:center; margin-bottom:6px; gap:8px; }
.conv-title { font-size:15px; font-weight:600; color:var(--text-primary); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; flex:1; }
.conv-time { font-size:11px; color:var(--text-hint); flex-shrink:0; }
.conv-preview { font-size:13px; color:var(--text-secondary); margin:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.conv-actions { display:flex; flex-direction:column; align-items:center; gap:10px; padding:16px 12px 16px 0; flex-shrink:0; }
.conv-arrow { cursor:pointer; padding:4px; border-radius:50%; }
.conv-delete { cursor:pointer; padding:4px; border-radius:50%; }
.empty-conv-wrap { margin:8px 0; }
.empty-notif { display:flex; flex-direction:column; align-items:center; padding:32px 20px 24px; text-align:center; }
.empty-notif-title { font-size:16px; font-weight:600; color:#475569; margin:0 0 6px; }
.empty-notif-hint { font-size:12px; color:var(--text-hint); margin:0; }
.notif-list { display:flex; flex-direction:column; gap:10px; }
.notif-item { display:flex; align-items:flex-start; gap:12px; padding:16px; background:linear-gradient(160deg, rgba(255,255,255,0.65) 0%, rgba(255,255,255,0.15) 40%, rgba(255,255,255,0.3) 100%),rgba(255,255,255,0.65); backdrop-filter:blur(12px) saturate(150%); -webkit-backdrop-filter:blur(12px) saturate(150%); border-radius:20px; box-shadow:inset 0 1px 0 rgba(255,255,255,0.6),0 2px 10px rgba(0,0,0,0.03); border:1px solid rgba(255,255,255,0.6); cursor:pointer; }
.notif-item.unread { border-left:3px solid #8B5CF6; }
.notif-icon-wrap { position:relative; flex-shrink:0; }
.notif-icon { width:44px; height:44px; border-radius:14px; display:flex; align-items:center; justify-content:center; }
.notif-dot { position:absolute; top:-2px; right:-2px; width:10px; height:10px; background:#EF4444; border-radius:50%; border:2px solid #fff; }
.notif-body { flex:1; min-width:0; }
.notif-top-row { display:flex; justify-content:space-between; align-items:center; margin-bottom:4px; gap:8px; }
.notif-title { font-size:15px; font-weight:600; color:var(--text-primary); }
.notif-time { font-size:11px; color:var(--text-hint); flex-shrink:0; }
.notif-preview { font-size:13px; color:var(--text-secondary); margin:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
/* ==================== 深色模式（B4） ==================== */
html[data-theme='dark'] .section-card,
html[data-theme='dark'] .settings-card {
  background: var(--bg-card);
  border-color: var(--glass-border);
  box-shadow: var(--shadow-md);
}
html[data-theme='dark'] .svc-item { background: transparent; }
html[data-theme='dark'] .svc-name { color: var(--text-primary); }
html[data-theme='dark'] .svc-desc { color: var(--text-secondary); }
html[data-theme='dark'] .quick-block-label { color: var(--text-secondary); }
html[data-theme='dark'] .theme-opt:not(.active) { color: var(--text-secondary); border-color: var(--glass-border); background: var(--bg-card); }
</style>
