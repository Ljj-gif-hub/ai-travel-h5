<script setup>
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getToken } from '../utils/auth'
import { noteApi, followApi, commentApi, uploadApi } from '../api'
import EmptyState from '../components/EmptyState.vue'

defineOptions({ name: 'CommunityView' })

const router = useRouter()

/* ==================== Tab 切换 ==================== */
const activeTab = ref('all')

/* ==================== 城市选择 ==================== */
const currentCity = ref('深圳')
const showCityPicker = ref(false)
const cityColumns = [
  { text: '深圳', value: '深圳' },
  { text: '北京', value: '北京' },
  { text: '上海', value: '上海' },
  { text: '成都', value: '成都' },
  { text: '西安', value: '西安' },
  { text: '杭州', value: '杭州' },
  { text: '重庆', value: '重庆' },
  { text: '大理', value: '大理' },
  { text: '三亚', value: '三亚' },
  { text: '广州', value: '广州' },
  { text: '南京', value: '南京' },
  { text: '武汉', value: '武汉' },
]

const onCityConfirm = ({ selectedOptions }) => {
  if (selectedOptions && selectedOptions[0]) {
    currentCity.value = selectedOptions[0].text
  }
  showCityPicker.value = false
}

/* ==================== 笔记数据 ==================== */
const notes = ref([])
const isLoading = ref(true)
const page = ref(1)
const hasMore = ref(true)
const loadingMore = ref(false)
const scrollContainer = ref(null)

const empty = computed(() => !isLoading.value && notes.value.length === 0)

/* ==================== 种子数据（fallback） ==================== */
// 内联SVG占位图（不依赖外部网络，picsum在国内可能被墙）
const ph = (hue, label) => `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="hsl(${hue},60%,65%)"/><stop offset="100%" stop-color="hsl(${hue+30},70%,45%)"/></linearGradient></defs><rect fill="url(#g)" width="400" height="300"/><text fill="rgba(255,255,255,0.7)" font-size="28" font-family="sans-serif" x="200" y="150" text-anchor="middle" dominant-baseline="middle">${label}</text></svg>`)}`

const seedNotes = [
  {
    id: 1,
    author: { nickname: '旅行者小明', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=ming', city: '深圳', isFollowing: false, online: true, userId: 'u1' },
    content: '深圳湾公园骑行真的太舒服了！沿着海岸线一路骑行，海风轻拂，视野开阔。推荐傍晚时分出发，可以看到绝美的海上日落，沿途还有很多拍照打卡点。全程15公里左右，新手也完全能驾驭。',
    images: [ph(200,'🏖️ 深圳湾'), ph(210,'🚴 骑行'), ph(220,'🌅 日落')],
    likeCount: 328, isLiked: false, commentCount: 56, time: '2小时前',
  },
  {
    id: 2,
    author: { nickname: '吃货小分队', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=foodie', city: '成都', isFollowing: true, online: false, userId: 'u2' },
    content: '成都美食攻略来啦！建设巷从头吃到尾，烤脑花、糖油果子、蛋烘糕、钵钵鸡...每一样都让人欲罢不能。建议空着肚子来，人均50吃到撑。本地人强推巷子深处的老店，游客少味道正！',
    images: [ph(15,'🍜 成都美食')],
    likeCount: 892, isLiked: true, commentCount: 134, time: '5小时前',
  },
  {
    id: 3,
    author: { nickname: '户外探险家', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=hiker', city: '西安', isFollowing: false, online: true, userId: 'u3' },
    content: '华山一日游，长空栈道挑战成功！早上5点出发，索道上北峰，然后一路徒步经过苍龙岭、金锁关，最后挑战长空栈道。虽然腿软但风景绝美，云海翻涌，值得一生铭记的体验。',
    images: [ph(40,'⛰️ 华山'), ph(50,'🧗 栈道'), ph(60,'☁️ 云海'), ph(70,'🗻 北峰')],
    likeCount: 1567, isLiked: false, commentCount: 289, time: '8小时前',
  },
  {
    id: 4,
    author: { nickname: '文艺摄影师', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=photo', city: '杭州', isFollowing: false, online: false, userId: 'u4' },
    content: '西湖边的绝美咖啡馆合集。整理了环西湖最值得去的5家独立咖啡馆，每一家都有独特的设计美学和出品。从断桥边的民国老宅到龙井山上的玻璃房，拍照超出片。附上每家的推荐饮品和最佳拍摄机位。',
    images: [ph(160,'☕ 西湖'), ph(170,'📸 咖啡'), ph(180,'🌿 龙井')],
    likeCount: 645, isLiked: false, commentCount: 92, time: '12小时前',
  },
  {
    id: 5,
    author: { nickname: '背包客阿飞', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=backpack', city: '大理', isFollowing: true, online: true, userId: 'u5' },
    content: '大理旅居一个月，整理了这份环洱海自驾攻略。路线：古城-喜洲-双廊-挖色-海东。全程130公里，建议分两天慢慢玩。喜洲的稻田、双廊的日落、海东的悬崖公路，每一段都让人不想离开。',
    images: [ph(190,'🌾 大理'), ph(200,'🚗 洱海')],
    likeCount: 2034, isLiked: true, commentCount: 367, time: '1天前',
  },
  {
    id: 6,
    author: { nickname: '带着娃看世界', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=family', city: '上海', isFollowing: false, online: false, userId: 'u6' },
    content: '带4岁娃的上海迪士尼亲子攻略！轻松版游玩路线，避开人潮不用排长队。重点推荐旋转木马、小飞象和冰雪奇缘表演，孩子玩得超开心。附上园区儿童餐推荐和午睡tips。',
    images: [ph(280,'🏰 迪士尼'), ph(290,'🎠 旋转木马'), ph(300,'🎢 小飞象'), ph(310,'👨‍👩‍👧 亲子')],
    likeCount: 1123, isLiked: false, commentCount: 178, time: '1天前',
  },
  {
    id: 7,
    author: { nickname: '潜水教练大刘', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=diver', city: '三亚', isFollowing: false, online: true, userId: 'u7' },
    content: '三亚潜水点全解析！对比了蜈支洲岛、分界洲岛和西岛的水下能见度、珊瑚状态和鱼群密度。新手最推荐分界洲岛，水流平缓海底景观好。附上潜水装备推荐和考证攻略。',
    images: [ph(200,'🤿 三亚潜水')],
    likeCount: 1890, isLiked: false, commentCount: 245, time: '2天前',
  },
  {
    id: 8,
    author: { nickname: '美食猎人KK', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=hunter', city: '重庆', isFollowing: true, online: true, userId: 'u8' },
    content: '重庆本地人私藏的火锅地图！避开网红店，整理了12家藏在居民楼里的老火锅。每家都有特色招牌菜，从人均40到80都有。特别推荐弹子石的巷子火锅和观音桥的防空洞火锅，麻辣鲜香巴适得板！',
    images: [ph(0,'🍲 火锅'), ph(10,'🌶️ 麻辣'), ph(20,'🏘️ 巷子')],
    likeCount: 3102, isLiked: true, commentCount: 498, time: '2天前',
  },
  {
    id: 9,
    author: { nickname: '骑行在路上', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=cycling', city: '北京', isFollowing: false, online: false, userId: 'u9' },
    content: '京郊最美骑行路线：十三陵水库环湖路线。全程38公里，途经明十三陵、水库大坝和柿子林。秋天柿子树挂满果实景色超美。路线平坦难度低，非常适合新手和亲子骑行。',
    images: [ph(30,'🚴 十三陵'), ph(40,'🍂 秋色')],
    likeCount: 756, isLiked: false, commentCount: 103, time: '3天前',
  },
  {
    id: 10,
    author: { nickname: '野生摄影师', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=wildphoto', city: '广州', isFollowing: false, online: true, userId: 'u10' },
    content: '广州老城区的时光漫步。从恩宁路到永庆坊，走过骑楼老街、趟栊门和青砖墙，感受最地道的西关风情。推荐下午三四点出发，光影穿过骑楼缝隙洒下，随手拍都是人文大片。沿途还有超多老字号糖水铺。',
    images: [ph(50,'📷 老城区'), ph(60,'🏛️ 骑楼'), ph(70,'🍧 糖水')],
    likeCount: 1432, isLiked: false, commentCount: 201, time: '3天前',
  },
]

/* ==================== 加载笔记 ==================== */
const loadNotes = async (reset = false) => {
  if (reset) {
    page.value = 1
    hasMore.value = true
    isLoading.value = true
  }

  if (!hasMore.value && !reset) return

  try {
    loadingMore.value = !reset
    // 【修复】社区广场应加载所有用户的游记，而非仅当前用户的
    const res = await noteApi.getAllNotes()

    if (res && res.code === 0 && res.data) {
      const list = Array.isArray(res.data) ? res.data : (res.data.records || res.data.list || [])
      // 适配后端返回格式
      const mapped = list.map(mapNoteItem)
      if (reset) {
        notes.value = mapped
      } else {
        notes.value = [...notes.value, ...mapped]
      }
      hasMore.value = list.length >= 10
      page.value += 1
    } else {
      if (reset) {
        notes.value = [...seedNotes]
      }
      hasMore.value = false
    }
  } catch (e) {
    console.warn('加载社区笔记失败，使用本地种子数据:', e.message)
    if (reset) {
      notes.value = [...seedNotes]
    }
    hasMore.value = false
  } finally {
    isLoading.value = false
    loadingMore.value = false
  }
}

// 提取HTML中的图片src
const extractImages = (html) => {
  if (!html) return []
  const regex = /<img[^>]*\bsrc="([^">]+)"[^>]*>/gi
  const result = []
  let match
  while ((match = regex.exec(html)) !== null) {
    if (match[1]) result.push(match[1])
  }
  return result
}

// 提取HTML中的视频src
const extractVideos = (html) => {
  if (!html) return []
  const result = []
  // <video src="...">
  const r1 = /<video[^>]*\bsrc="([^">]+)"[^>]*>/gi
  let m
  while ((m = r1.exec(html)) !== null) {
    if (m[1]) result.push(m[1])
  }
  // <source src="...">
  const r2 = /<source[^>]*\bsrc="([^">]+)"[^>]*>/gi
  while ((m = r2.exec(html)) !== null) {
    if (m[1]) result.push(m[1])
  }
  return result
}

// 去掉HTML标签，提取纯文本
const stripHtml = (html) => {
  if (!html) return ''
  // <br> 标签转换为换行符，保留用户输入的换行格式
  let text = html.replace(/<br\s*\/?>/gi, '\n')
  // 移除img标签（不显示占位文字，图片已在下方网格中展示）
  text = text.replace(/<img[^>]*>/gi, '')
  // 移除其他标签
  text = text.replace(/<[^>]+>/g, '')
  // 解码常见实体
  text = text.replace(/&nbsp;/g, ' ').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
  return text.trim()
}

const mapNoteItem = (item) => {
  // 兼容两种数据格式：
  // API直出：{ authorName, authorAvatar, cover, content(HTML), ... }
  // 种子数据：{ author: { nickname, avatar, ... }, images: [...], ... }
  const isSeedData = !!item.author && typeof item.author === 'object'

  const authorNickname = isSeedData ? item.author.nickname : (item.authorName || item.nickname || '旅行者')
  const authorAvatar = isSeedData ? item.author.avatar : (item.authorAvatar || item.avatar || `https://api.dicebear.com/7.x/avataaars/svg?seed=${item.id || 'fallback'}`)
  const authorCity = isSeedData ? item.author.city : (item.city || '')
  const authorIsFollowing = isSeedData ? item.author.isFollowing : (item.isFollowing || false)
  const authorOnline = isSeedData ? item.author.online : (item.online !== undefined ? item.online : Math.random() > 0.4)
  const authorUserId = isSeedData ? item.author.userId : (item.userId || item.authorId || item.id)

  // 图片+视频：种子数据直接用images数组，API数据从cover+正文提取
  let images = []
  if (isSeedData) {
    images = item.images || []
  } else {
    // 提取正文中的图片
    const contentImages = extractImages(item.content)
    // 提取正文中的视频
    const contentVideos = extractVideos(item.content)
    if (item.cover) images.push(item.cover)
    images = images.concat(contentImages).concat(contentVideos)
    // 去重
    images = [...new Set(images)]
  }

  // 纯文本
  const plainContent = isSeedData ? item.content : stripHtml(item.content)

  // 检测是否有视频
  const rawCover = item.cover || ''
  const rawContent = item.content || ''
  const hasVideo = rawCover.match(/\.(mp4|webm|mov)(\?|$)/i)
    || /<video[^>]*src=/i.test(rawContent)
    || /<source[^>]*src="[^">]+\.(mp4|webm|mov)/i.test(rawContent)

  return {
    id: item.id,
    author: {
      nickname: authorNickname,
      avatar: authorAvatar,
      city: authorCity,
      isFollowing: authorIsFollowing,
      online: authorOnline,
      userId: authorUserId,
    },
    content: plainContent || '',
    images: images,
    hasVideo: !!hasVideo,
    likeCount: item.likeCount || item.likes || 0,
    isLiked: item.isLiked || false,
    commentCount: item.commentCount || item.comments || 0,
    time: item.time || item.date || item.createTime || '刚刚',
  }
}

// 判断URL是否为视频
const isVideoUrl = (url) => url && /\.(mp4|webm|mov)(\?|$)/i.test(url)

/* ==================== 跳转详情页 ==================== */
const goToDetail = (note) => {
  if (!note || !note.id) return
  if (note.hasVideo) {
    router.push(`/video-detail?id=${note.id}`)
  } else {
    router.push(`/note-detail?id=${note.id}`)
  }
}

/* ==================== 点赞 ==================== */
const handleLike = async (note) => {
  const token = getToken()
  if (!token) {
    showToast({ message: '请先登录', position: 'middle', duration: 1500 })
    return
  }

  // 乐观更新
  const prevLiked = note.isLiked
  note.isLiked = !note.isLiked
  note.likeCount += note.isLiked ? 1 : -1

  try {
    const res = await noteApi.likeNote(note.id)
    if (res.code !== 0) throw new Error(res.message)
  } catch (e) {
    // 失败回滚
    note.isLiked = prevLiked
    note.likeCount += prevLiked ? 1 : -1
    showToast({ message: '操作失败，请重试', position: 'middle', duration: 1500 })
  }
}

/* ==================== 关注 ==================== */
const handleFollow = async (author) => {
  const token = getToken()
  if (!token) {
    showToast({ message: '请先登录', position: 'middle', duration: 1500 })
    return
  }

  author.isFollowing = !author.isFollowing

  try {
    if (author.isFollowing) {
      await followApi.follow(author.userId)
    } else {
      await followApi.unfollow(author.userId)
    }
  } catch (e) {
    author.isFollowing = !author.isFollowing
    showToast({ message: '操作失败，请重试', position: 'middle', duration: 1500 })
  }
}

/* ==================== 评论 ==================== */
const commentInputs = reactive({})
const commentImages = reactive({})  // 每个笔记的评论图片
const commentVideos = reactive({})  // 每个笔记的评论视频
const commentUploading = reactive({})

const handleCommentUpload = async (noteId, e) => {
  const file = e.target.files?.[0]
  if (!file) return
  commentUploading[noteId] = true
  try {
    const res = await uploadApi.uploadFile(file)
    if (res.code === 0) {
      if (res.data.type === 'image') {
        commentImages[noteId] = res.data.url
        commentVideos[noteId] = ''
      } else {
        commentVideos[noteId] = res.data.url
        commentImages[noteId] = ''
      }
      showToast({ message: res.data.type === 'image' ? '图片已上传' : '视频已上传', position: 'middle', duration: 1200 })
    } else {
      showToast({ message: res.message || '上传失败', position: 'middle', duration: 1500 })
    }
  } catch (e) {
    showToast({ message: '上传失败', position: 'middle', duration: 1500 })
  } finally {
    commentUploading[noteId] = false
  }
}

const handleSendComment = async (note) => {
  const text = (commentInputs[note.id] || '').trim()
  const img = commentImages[note.id] || ''
  const vid = commentVideos[note.id] || ''
  if (!text && !img && !vid) return
  const token = getToken()
  if (!token) {
    showToast({ message: '请先登录', position: 'middle', duration: 1500 })
    return
  }
  try {
    const res = await commentApi.addComment(note.id, text || null, img || null, vid || null)
    if (res.code === 0) {
      note.commentCount = (note.commentCount || 0) + 1
      commentInputs[note.id] = ''
      commentImages[note.id] = ''
      commentVideos[note.id] = ''
      showToast({ message: '评论成功', position: 'middle', duration: 1200 })
    } else {
      showToast({ message: res.message || '评论失败', position: 'middle', duration: 1500 })
    }
  } catch (e) {
    showToast({ message: '评论失败，请重试', position: 'middle', duration: 1500 })
  }
}

/* ==================== 搜索 ==================== */
const handleSearch = () => {
  showToast({ message: '搜索功能开发中', position: 'middle', duration: 1500 })
}

/* ==================== 滚动触底加载 ==================== */
const handleScroll = (e) => {
  const el = e.target
  if (!el) return
  const { scrollTop, scrollHeight, clientHeight } = el
  if (scrollHeight - scrollTop - clientHeight < 120 && hasMore.value && !loadingMore.value) {
    loadNotes()
  }
}

/* ==================== Tab 切换 ==================== */
const onTabChange = (key) => {
  activeTab.value = key
  // 切换时从缓存取或重新加载
  if (key === 'following') {
    const token = getToken()
    if (!token) {
      showToast({ message: '请先登录查看关注动态', position: 'middle', duration: 1500 })
      activeTab.value = 'all'
      return
    }
  }
}

const goToWrite = () => {
  if (!getToken()) {
    router.push('/login')
    return
  }
  router.push('/write-note')
}

/* ==================== 生命周期 ==================== */
let hasLoadedOnce = false

onMounted(() => {
  loadNotes(true).then(() => {
    hasLoadedOnce = true
    // 调试：打印图片数据
    console.log('📷 Community notes loaded:', notes.value.length, 'items')
    notes.value.forEach((n, i) => {
      console.log(`  [${i}] id=${n.id} images=${n.images?.length || 0} urls=`, n.images?.slice(0, 2))
    })
  })
})

onActivated(() => {
  if (hasLoadedOnce) {
    loadNotes(true)
  }
})
</script>

<template>
  <div class="page-shell" @scroll.passive="handleScroll">
    <!-- 漂浮粒子 -->
    <div class="clouds-layer" aria-hidden="true">
      <span class="cloud-dot c1"></span><span class="cloud-dot c2"></span><span class="cloud-dot c3"></span>
    </div>
    <!-- ==================== 1. Hero Banner ==================== -->
    <div class="hero-banner entrance-item entrance-d1">
      <div class="banner-content">
        <h1 class="banner-title">同路人社区</h1>
        <p class="banner-subtitle">真点评&middot;真感受</p>
      </div>
      <div class="banner-illustration">
        <svg viewBox="0 0 200 120" width="100%" height="100%" preserveAspectRatio="xMidYMid meet" class="banner-svg">
          <!-- 热气球 -->
          <g transform="translate(8, 5)">
            <ellipse cx="28" cy="24" rx="18" ry="20" fill="rgba(255,255,255,0.22)" />
            <line x1="12" y1="44" x2="14" y2="58" stroke="rgba(255,255,255,0.35)" stroke-width="1.4" />
            <line x1="44" y1="44" x2="42" y2="58" stroke="rgba(255,255,255,0.35)" stroke-width="1.4" />
            <rect x="15" y="56" width="26" height="7" rx="3" fill="rgba(255,255,255,0.4)" />
            <path d="M12 10 Q20 4 28 10 Q36 4 44 10" fill="none" stroke="rgba(255,255,255,0.3)" stroke-width="1" />
          </g>
          <!-- 越野车 -->
          <g transform="translate(72, 68)">
            <rect x="4" y="8" width="48" height="18" rx="4" fill="rgba(255,255,255,0.25)" />
            <rect x="12" y="0" width="32" height="12" rx="3" fill="rgba(255,255,255,0.22)" />
            <circle cx="14" cy="28" r="6" fill="none" stroke="rgba(255,255,255,0.35)" stroke-width="2.5" />
            <circle cx="42" cy="28" r="6" fill="none" stroke="rgba(255,255,255,0.35)" stroke-width="2.5" />
            <circle cx="14" cy="28" r="2.5" fill="rgba(255,255,255,0.35)" />
            <circle cx="42" cy="28" r="2.5" fill="rgba(255,255,255,0.35)" />
          </g>
          <!-- 旅行者人物 -->
          <g transform="translate(145, 72)">
            <circle cx="16" cy="4" r="5" fill="rgba(255,255,255,0.25)" />
            <path d="M8 12 L24 12 L22 32 L10 32 Z" fill="rgba(255,255,255,0.2)" />
            <rect x="22" y="10" width="8" height="12" rx="3" fill="rgba(255,255,255,0.18)" />
            <line x1="10" y1="16" x2="3" y2="22" stroke="rgba(255,255,255,0.2)" stroke-width="2" stroke-linecap="round" />
            <line x1="22" y1="16" x2="30" y2="12" stroke="rgba(255,255,255,0.2)" stroke-width="2" stroke-linecap="round" />
          </g>
          <!-- 远处云朵 -->
          <g transform="translate(115, 8) scale(0.35)">
            <ellipse cx="40" cy="20" rx="40" ry="14" fill="rgba(255,255,255,0.12)" />
            <ellipse cx="65" cy="14" rx="30" ry="12" fill="rgba(255,255,255,0.12)" />
          </g>
          <!-- 远处山脉 -->
          <path d="M0 120 L30 84 L55 100 L75 74 L95 92 L115 78 L140 90 L160 72 L200 95 L200 120 Z" fill="rgba(255,255,255,0.08)" />
        </svg>
      </div>
    </div>

    <!-- ==================== 2. Navigation Filter Bar ==================== -->
    <div class="nav-filter">
      <!-- 城市选择 -->
      <div class="city-selector" @click="showCityPicker = true">
        <van-icon name="location-o" size="14" color="#8B5CF6" />
        <span class="city-text">{{ currentCity }}</span>
        <van-icon name="arrow-down" size="10" color="#94A3B8" />
      </div>

      <!-- 中间 Tab -->
      <div class="center-tabs">
        <span
          class="tab-chip"
          :class="{ active: activeTab === 'all' }"
          @click="onTabChange('all')"
        >广场</span>
        <span
          class="tab-chip"
          :class="{ active: activeTab === 'following' }"
          @click="onTabChange('following')"
        >关注</span>
      </div>

      <!-- 搜索按钮 -->
      <div class="search-btn" @click="handleSearch">
        <van-icon name="search" size="18" color="#64748B" />
      </div>
    </div>

    <!-- ==================== 3. Notes Feed ==================== -->
    <div class="notes-feed entrance-item entrance-d2">
      <!-- 加载骨架屏 -->
      <template v-if="isLoading">
        <div class="skeleton-card" v-for="i in 4" :key="'sk-'+i">
          <div class="sk-header">
            <van-skeleton avatar :avatar-size="30" :row="1" />
          </div>
          <van-skeleton :row="2" class="sk-body" />
          <div class="sk-images">
            <div class="sk-img" v-for="j in 2" :key="j"></div>
          </div>
        </div>
      </template>

      <!-- 空状态 -->
      <EmptyState
        v-else-if="empty"
        title="还没有笔记"
        desc="去分享你的旅行故事吧"
        icon="notes-o"
        btn-text="去发表"
        btn-type="gradient"
        @btn-click="goToWrite"
      />

      <!-- 笔记卡片列表 -->
      <template v-else>
        <div
          class="note-card"
          v-for="note in notes"
          :key="note.id"
          @click="goToDetail(note)"
        >
          <!-- 作者信息行 -->
          <div class="card-author">
            <van-image
              round
              width="40"
              height="40"
              :src="note.author.avatar"
              fit="cover"
              class="author-avatar"
            />
            <div class="author-info">
              <div class="author-name-row">
                <span class="author-nickname">{{ note.author.nickname }}</span>
                <span v-if="note.author.online" class="online-dot" title="在线"></span>
              </div>
              <span class="author-meta">{{ note.time }} &middot; {{ note.author.city || '' }}</span>
            </div>
            <div
              class="follow-btn"
              :class="{ followed: note.author.isFollowing }"
              @click.stop="handleFollow(note.author)"
            >
              {{ note.author.isFollowing ? '已关注' : '+ 关注' }}
            </div>
          </div>

          <!-- 文字内容 -->
          <div class="card-content">
            {{ note.content }}
          </div>

          <!-- 图片/视频网格 -->
          <div
            v-if="note.images && note.images.length"
            class="card-images"
            :class="'grid-' + Math.min(note.images.length, 4)"
          >
            <template v-for="(url, idx) in note.images.slice(0, 4)" :key="idx">
              <!-- 视频显示带播放按钮的缩略图 -->
              <div v-if="isVideoUrl(url)" class="card-video-thumb">
                <video :src="url" class="card-img" preload="metadata" muted></video>
                <div class="video-play-icon">
                  <van-icon name="play-circle-o" size="32" color="rgba(255,255,255,0.9)" />
                </div>
              </div>
              <!-- 图片 -->
              <img v-else :src="url" class="card-img" loading="lazy" />
            </template>
            <!-- 超过4张的蒙层 -->
            <div v-if="note.images.length > 4" class="img-overlay" @click.stop>
              <span>+{{ note.images.length - 4 }}</span>
            </div>
          </div>

          <!-- 互动栏 -->
          <div class="card-actions">
            <!-- 媒体预览 -->
            <div class="comment-media-mini" v-if="commentImages[note.id] || commentVideos[note.id]">
              <van-image v-if="commentImages[note.id]" :src="commentImages[note.id]" fit="cover" width="36" height="36" radius="4" />
              <video v-if="commentVideos[note.id]" :src="commentVideos[note.id]" class="mini-video" />
            </div>
            <!-- 评论输入 -->
            <div class="comment-input-row">
              <input
                type="file"
                accept="image/*,video/*"
                :ref="el => { if (el) el.dataset.noteId = note.id }"
                style="display:none"
                @change="(e) => { handleCommentUpload(note.id, e); e.target.value = '' }"
              />
              <div class="upload-btn-mini" @click.stop="(e) => { const inp = e.target.parentElement.querySelector('input[type=file]'); if(inp) inp.click() }">
                <van-icon name="photograph" size="16" color="#A78BFA" />
              </div>
              <van-field
                v-model="commentInputs[note.id]"
                placeholder="评论或传图..."
                :border="false"
                input-align="left"
                class="comment-field"
                @keyup.enter="handleSendComment(note)"
              />
              <div class="send-icon-wrap" @click.stop="handleSendComment(note)">
                <van-icon name="guide-o" size="16" color="#8B5CF6" />
              </div>
            </div>

            <!-- 操作按钮组 -->
            <div class="action-btns">
              <div class="action-btn" :class="{ liked: note.isLiked }" @click.stop="handleLike(note)">
                <van-icon :name="note.isLiked ? 'like' : 'like-o'" size="18" />
                <span>{{ note.likeCount || 0 }}</span>
              </div>
              <div class="action-btn">
                <van-icon name="chat-o" size="18" />
                <span>{{ note.commentCount || 0 }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 加载更多 -->
        <div v-if="loadingMore" class="loading-more">
          <van-loading size="20" color="#8B5CF6" />
          <span>加载中...</span>
        </div>
        <div v-else-if="!hasMore && notes.length > 0" class="no-more">
          没有更多了
        </div>
      </template>
    </div>

    <!-- ==================== 4. Floating Publish Button ==================== -->
    <div class="fab-publish" @click="goToWrite">
      <van-icon name="add" size="26" color="#fff" />
    </div>

    <!-- ==================== 城市选择器弹窗 ==================== -->
    <van-popup
      v-model:show="showCityPicker"
      position="bottom"
      round
      :style="{ borderRadius: '20px 20px 0 0' }"
    >
      <van-picker
        :columns="cityColumns"
        :default-index="cityColumns.findIndex(c => c.value === currentCity)"
        @confirm="onCityConfirm"
        @cancel="showCityPicker = false"
        title="选择城市"
      />
    </van-popup>
  </div>
</template>

<style scoped>
/* ==================== 页面容器 ==================== */
.page-shell {
  width: 100%;
  height: calc(100dvh - var(--tabbar-height) - env(safe-area-inset-bottom, 0px));
  overflow-y: auto;
  overflow-x: hidden;
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
  -webkit-overflow-scrolling: touch;
  scroll-behavior: smooth;
  position: relative;
}

/* ==================== 1. Hero Banner ==================== */
.hero-banner {
  position: relative;
  width: calc(100% - 32px);
  margin: 16px auto 0;
  border-radius: 20px;
  background: linear-gradient(135deg, #8B5CF6 0%, #6366F1 50%, #5B8DEF 100%);
  padding: 22px 24px 18px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 8px 28px rgba(139, 92, 246, 0.3);
}

.banner-content {
  position: relative;
  z-index: 2;
}

.banner-title {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 6px;
  letter-spacing: 0.5px;
}

.banner-subtitle {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.78);
  margin: 0;
  letter-spacing: 1px;
  font-weight: 400;
}

.banner-illustration {
  position: absolute;
  right: 8px;
  top: 0;
  bottom: 0;
  width: 170px;
  pointer-events: none;
  z-index: 1;
}

.banner-svg {
  width: 100%;
  height: 100%;
}

/* ==================== 2. Navigation Filter Bar ==================== */
.nav-filter {
  display: flex;
  align-items: center;
  width: calc(100% - 32px);
  margin: 14px auto 0;
  padding: 8px 0;
  gap: 10px;
}

.city-selector {
  display: flex;
  align-items: center;
  gap: 5px;
  background: #fff;
  border-radius: 16px;
  padding: 8px 12px;
  box-shadow: 0 2px 10px rgba(139, 92, 246, 0.06);
  border: 1px solid rgba(139, 92, 246, 0.08);
  flex-shrink: 0;
  cursor: pointer;
  transition: all 0.2s ease;
}
.city-selector:active {
  transform: scale(0.96);
  background: #faf5ff;
}
.city-text {
  font-size: 13px;
  font-weight: 600;
  color: #1E293B;
}

.center-tabs {
  flex: 1;
  display: flex;
  justify-content: center;
  gap: 6px;
  background: #fff;
  border-radius: 16px;
  padding: 4px;
  box-shadow: 0 2px 10px rgba(139, 92, 246, 0.06);
  border: 1px solid rgba(139, 92, 246, 0.08);
}

.tab-chip {
  flex: 1;
  text-align: center;
  padding: 6px 0;
  border-radius: 13px;
  font-size: 13px;
  font-weight: 500;
  color: #64748B;
  cursor: pointer;
  transition: all 0.3s ease;
  user-select: none;
}
.tab-chip.active {
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  color: #fff;
  box-shadow: 0 3px 10px rgba(139, 92, 246, 0.3);
}

.search-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 10px rgba(139, 92, 246, 0.06);
  border: 1px solid rgba(139, 92, 246, 0.08);
  flex-shrink: 0;
  cursor: pointer;
  transition: all 0.2s ease;
}
.search-btn:active {
  transform: scale(0.92);
  background: #faf5ff;
}

/* ==================== 3. Notes Feed ==================== */
.notes-feed {
  width: calc(100% - 32px);
  margin: 6px auto 110px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ==================== 骨架屏 ==================== */
.skeleton-card {
  background: #fff;
  border-radius: 20px;
  padding: 16px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(139, 92, 246, 0.06);
}
.sk-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}
.sk-body {
  margin-bottom: 10px;
}
.sk-images {
  display: flex;
  gap: 6px;
}
.sk-img {
  flex: 1;
  height: 90px;
  border-radius: 10px;
  background: #f1f5f9;
}

/* ==================== 笔记卡片 ==================== */
.note-card {
  background: #fff;
  border-radius: 20px;
  padding: 16px 16px 12px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(139, 92, 246, 0.06);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.note-card:active {
  transform: scale(0.985);
}

/* --- 作者信息行 --- */
.card-author {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.author-avatar {
  flex-shrink: 0;
  border: 2px solid rgba(139, 92, 246, 0.12);
}

.author-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.author-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.author-nickname {
  font-size: 14px;
  font-weight: 600;
  color: #1E293B;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.online-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22C55E;
  flex-shrink: 0;
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.4);
}

.author-meta {
  font-size: 11px;
  color: #94A3B8;
}

/* --- 关注按钮 --- */
.follow-btn {
  flex-shrink: 0;
  font-size: 12px;
  padding: 5px 14px;
  border-radius: 14px;
  border: 1px solid #C4B5FD;
  color: #7C3AED;
  font-weight: 500;
  background: #fff;
  cursor: pointer;
  transition: all 0.25s ease;
  user-select: none;
  white-space: nowrap;
}
.follow-btn:active {
  transform: scale(0.93);
}
.follow-btn.followed {
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.25);
}

/* --- 文字内容 --- */
.card-content {
  font-size: 14px;
  line-height: 1.6;
  color: #334155;
  margin-bottom: 12px;
  max-height: 8em; /* 5行 × 1.6 line-height，超出隐藏，点击卡片查看全文 */
  max-width: 21em; /* 一行最多约21个中文字 */
  overflow: hidden;
  word-break: break-word;
  white-space: pre-wrap; /* 保留用户输入的换行格式 */
}

/* --- 图片网格 --- */
.card-images {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
  align-items: start; /* 顶部对齐，允许各行自然高度 */
}

/* 1 张图片 - 通栏，自然比例 */
.grid-1 {
  grid-template-columns: 1fr;
}

/* 2 张图片 - 双列，自然比例 */
.grid-2 {
  grid-template-columns: 1fr 1fr;
}

/* 3 张图片 - 左大右二，自然比例 */
.grid-3 {
  grid-template-columns: 1fr 1fr;
}
.grid-3 .card-img:first-child {
  grid-row: 1 / 3;
}

/* 4 张图片 - 2x2，自然比例 */
.grid-4 {
  grid-template-columns: 1fr 1fr;
}

.card-img {
  width: 100%;
  height: auto;
  border-radius: 6px;
  display: block;
}

.card-video-thumb {
  position: relative;
  border-radius: 6px;
  overflow: hidden;
}
.card-video-thumb .card-img {
  width: 100%;
  height: auto;
}
.video-play-icon {
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%,-50%);
  pointer-events: none;
  filter: drop-shadow(0 2px 4px rgba(0,0,0,0.4));
}

.img-overlay {
  position: absolute;
  bottom: 6px;
  right: 6px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 8px;
  backdrop-filter: blur(4px);
  cursor: pointer;
}

/* --- 互动栏 --- */
.card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 6px;
  border-top: 1px solid rgba(139, 92, 246, 0.06);
}

.comment-media-mini {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
}
.mini-video {
  width: 36px;
  height: 36px;
  object-fit: cover;
  border-radius: 4px;
}

.upload-btn-mini {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}
.upload-btn-mini:active {
  background: rgba(139,92,246,0.1);
  transform: scale(0.85);
}

.comment-input-row {
  flex: 1;
  display: flex;
  align-items: center;
  background: #f8fafc;
  border-radius: 20px;
  padding: 0 6px 0 12px;
  border: 1px solid rgba(139, 92, 246, 0.06);
  transition: border-color 0.2s ease;
}
.comment-input-row:focus-within {
  border-color: #C4B5FD;
  background: #faf5ff;
}

.comment-field {
  flex: 1;
  padding: 6px 0 !important;
  font-size: 13px;
  background: transparent !important;
}

.send-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
}
.send-icon-wrap:active {
  transform: scale(0.88);
  background: rgba(139, 92, 246, 0.1);
}

.action-btns {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
  margin-left: 8px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #94A3B8;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
  padding: 4px;
}
.action-btn:active {
  transform: scale(0.9);
}
.action-btn.liked {
  color: #EF4444;
}

/* ==================== 加载更多 ==================== */
.loading-more,
.no-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px 0;
  font-size: 13px;
  color: #94A3B8;
}
.no-more {
  font-size: 12px;
}

/* ==================== 4. Floating Publish Button ==================== */
.fab-publish {
  position: fixed;
  right: 20px;
  bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom, 0px) + 20px);
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 22px rgba(139, 92, 246, 0.4);
  z-index: 9995;
  cursor: pointer;
  transition: all 0.3s ease;
  user-select: none;
}
.fab-publish:active {
  transform: scale(0.9);
  box-shadow: 0 4px 14px rgba(139, 92, 246, 0.35);
}

/* ==================== Vant 组件覆盖 ==================== */
:deep(.van-skeleton) {
  padding: 6px 0;
}
:deep(.van-field__control) {
  font-size: 13px;
  color: #334155;
}
:deep(.van-field__control::placeholder) {
  color: #94A3B8;
}
:deep(.van-cell) {
  padding: 0 !important;
}

/*
 * ================================================================
 * 社区页专属动效
 * ================================================================
 */
.clouds-layer { position: fixed; inset: 0; z-index: 0; pointer-events: none; overflow: hidden; }
.cloud-dot { position: absolute; border-radius: 50%; background: rgba(139,92,246,0.05); animation: cloudDriftSlow linear infinite; }
.c1 { width: 45px; height: 45px; top: 10%; right: 8%; animation-duration: 28s; }
.c2 { width: 55px; height: 55px; top: 55%; left: 5%; animation-duration: 34s; animation-delay: -10s; }
.c3 { width: 35px; height: 35px; top: 80%; left: 70%; animation-duration: 24s; animation-delay: -5s; }
.hero-banner { background-size: 200% 200%; }
/* hero-banner不设animation避免覆盖entrance-item的entranceUp */
.note-card { transition: transform 0.35s cubic-bezier(0.4,0,0.2,1), box-shadow 0.4s ease; }
.note-card:hover { transform: translateY(-5px); box-shadow: 0 16px 40px rgba(139,92,246,0.10), 0 0 30px rgba(139,92,246,0.06); }
.note-card:active { transform: scale(0.98); }
.action-btn:active :deep(.van-icon) { animation: elasticBounce 0.5s cubic-bezier(0.4,0,0.2,1); }
.fab-publish { animation: pulseGlow 2.5s ease-in-out infinite; }
</style>
