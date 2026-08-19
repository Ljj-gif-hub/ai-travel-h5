<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onActivated, onDeactivated, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showToast, showLoadingToast, Swipe, SwipeItem } from 'vant'

/*
 * 【Bug修复】显式声明组件名，供 keep-alive 的 include 白名单匹配
 * 缺失会导致 Tab 切换时组件无法命中缓存，每次销毁重建 → 空白
 */
defineOptions({ name: 'HomeView' })
import { areaList } from '@vant/area-data'
import SearchBar from '../components/SearchBar.vue'
import { defineAsyncComponent } from 'vue'
const AIChatDialog = defineAsyncComponent(() => import('../components/AIChatDialog.vue'))
import EmptyState from '../components/EmptyState.vue'
import { getHotDestinations } from '../api/destination'
import { noteApi, followApi, commentApi, uploadApi } from '../api'
import { getToken } from '../utils/auth'
import { avatarUrl } from '../utils/avatar'

const router = useRouter()
const { t } = useI18n()

/* ==================== 表单数据 ==================== */
const destination = ref('')
const budget = ref('')
const days = ref('')
const people = ref('')
const showCityPicker = ref(false)
const cityAreaRef = ref(null)
const wheelHandlers = ref([])

/* ==================== 更多产品弹出层 ==================== */
const showMoreProducts = ref(false)
const moreProductList = [
  { key: 'visa', icon: 'idcard', color: '#6366F1' },
  { key: 'guide', icon: 'flag-o', color: '#3B82F6' },
  { key: 'cruise', icon: 'guide-o', color: '#0891B2' },
  { key: 'wifi', icon: 'phone-o', color: '#8B5CF6' },
  { key: 'insurance', icon: 'shield-o', color: '#22C55E' },
  { key: 'postcard', icon: 'envelop-o', color: '#F59E0B' },
  { key: 'localGoods', icon: 'gift-o', color: '#EF4444' },
  { key: 'travelPhoto', icon: 'photo-o', color: '#EC4899' },
  { key: 'selfDrive', icon: 'car-o', color: '#F97316' },
  { key: 'luggage', icon: 'bag-o', color: '#14B8A6' },
  { key: 'currency', icon: 'gold-coin-o', color: '#EAB308' },
  { key: 'lounge', icon: 'star-o', color: '#A855F7' },
  { key: 'localExperience', icon: 'location-o', color: '#06B6D4' },
  { key: 'healthCheck', icon: 'first-aid', color: '#84CC16' },
]

/* ==================== 热门目的地快捷标签 ==================== */
const hotTags = ['北京', '上海', '成都', '三亚', '西安', '杭州', '重庆', '大理']

/* ==================== 城市的快捷标签（Layer 4） ==================== */
const cityQuickTags = ['北京', '杭州', '西安', '成都', '南京', '青岛', '上海']

/* ==================== 输入框独立 ref ==================== */
const budgetInputRef = ref(null)
const daysInputRef = ref(null)
const peopleInputRef = ref(null)

const handleBudgetInput = (e) => {
  const raw = e.target.value
  const filtered = raw.replace(/[^\d.]/g, '')
  const parts = filtered.split('.')
  const cleaned = parts[0] + (parts.length > 1 ? '.' + parts.slice(1).join('') : '')
  budget.value = cleaned
  if (e.target.value !== cleaned) e.target.value = cleaned
}

const handleDaysInput = (e) => {
  const raw = e.target.value
  const filtered = raw.replace(/\D/g, '')
  days.value = filtered
  if (e.target.value !== filtered) e.target.value = filtered
}

const handlePeopleInput = (e) => {
  const raw = e.target.value
  let filtered = raw.replace(/\D/g, '')
  if (filtered && parseInt(filtered) > 50) { filtered = '50'; showToast({ message: t('home.peopleMax50'), position: 'middle', duration: 1500 }) }
  people.value = filtered
  if (e.target.value !== filtered) e.target.value = filtered
}

const handleBudgetBlur = (e) => {
  const val = e.target.value.trim()
  budget.value = val
  if (val && parseFloat(val) < 100) showToast({ message: t('home.budgetMin100Suggest'), position: 'middle', duration: 1500 })
}

const handleDaysBlur = (e) => {
  const val = e.target.value.trim()
  days.value = val
  if (val && parseInt(val) < 1) { showToast({ message: t('home.daysMin1'), position: 'middle', duration: 1500 }); days.value = ''; e.target.value = '' }
}

const handlePeopleBlur = (e) => {
  const val = e.target.value.trim()
  people.value = val
  if (val && (parseInt(val) < 1 || parseInt(val) > 50)) showToast({ message: t('home.peopleRange'), position: 'middle', duration: 1500 })
}

/* ==================== 快捷入口（6宫格 — 2×3） ==================== */
const quickEntries = [
  { name: 'AI对话', icon: 'chat-o', color: '#8B5CF6', path: '/chat' },
  { name: '机票预订', icon: 'plane-o', color: '#34D399', path: '/flight-booking' },
  { name: '酒店预订', icon: 'hotel-o', color: '#F59E0B', path: '/hotel-booking' },
  { name: '景点门票', icon: 'orders-o', color: '#FB7185', path: '/orders' },
  { name: '美食攻略', icon: 'star-o', color: '#F97316', path: '/destinations' },
  { name: '游记社区', icon: 'file-text-o', color: '#3B82F6', path: '/notes' },
]

/* ==================== Layer 2: 服务图标网格 Row 1 ==================== */
const serviceRow1 = [
  { key: 'hotel', icon: 'hotel-o', color: '#8B5CF6' },
  { key: 'guide', icon: 'guide-o', color: '#6366F1' },
  { key: 'flight', icon: 'plane-o', color: '#3B82F6' },
  { key: 'train', icon: 'train-o', color: '#F59E0B' },
  { key: 'custom', icon: 'backpack-o', color: '#34D399' },
]

/* ==================== Layer 2b: 服务图标网格 Row 2 ==================== */
const serviceRow2 = [
  { key: 'homestay', icon: 'home-o', color: '#8B5CF6' },
  { key: 'tickets', icon: 'orders-o', color: '#F59E0B' },
  { key: 'pickup', icon: 'bus-o', color: '#3B82F6' },
  { key: 'car', icon: 'car-o', color: '#F97316' },
  { key: 'tour', icon: 'flag-o', color: '#34D399' },
]

/* ==================== Layer 5: 快捷功能标签 ==================== */
const quickTabs = [
  { name: '特价/直播', icon: 'coupon-o' },
  { name: '演出/展览', icon: 'music-o' },
  { name: '行程规划', icon: 'compass-o' },
  { name: '旅行热点', icon: 'fire-o' },
  { name: '旅游榜单', icon: 'medal-o' },
]

/* ==================== 图片API ==================== */
// 携程模式：后端 API（POI 图片 / DB 缓存 / 第三方源）→ 静态 JSON 兜底
const staticImageMap = ref({})

const loadStaticImageMap = async () => {
  try {
    const resp = await fetch('/city-images.json')
    if (resp.ok) Object.assign(staticImageMap.value, await resp.json())
  } catch {}
  // 合并景点图映射：推广轮播/种草笔记里引用的景点名（黄果树瀑布、都江堰等）也能命中本地图
  try {
    const resp = await fetch('/attraction-images.json')
    if (resp.ok) {
      const attrMap = await resp.json()
      for (const [k, v] of Object.entries(attrMap)) {
        if (v && !staticImageMap.value[k]) staticImageMap.value[k] = v
      }
    }
  } catch {}
}

/** 获取图片：优先本地静态 JSON（真实地标），兜底后端 API */
const getImageUrl = (keyword) => staticImageMap.value[keyword] || `/api/city/image?name=${encodeURIComponent(keyword)}`

/** 兜底：静态 JSON（API 未覆盖时用） */
const resolveImage = (keyword) => staticImageMap.value[keyword] || getImageUrl(keyword)

/* ==================== Layer 6: 双列卡片 ==================== */
// BUGID L-HOME-1 修复：默认封面改为 computed，staticImageMap 加载完成后自动重算，避免首屏裂图
const eventBanner = computed(() => ({
  image: getImageUrl('三亚'),
  title: 'home.summerTravel',
  label: 'home.hotActivity',
  link: '/destination-detail?city=三亚',
}))
const citySeedCard = computed(() => ({
  image: getImageUrl('北京'),
  label: 'home.citySeed',
  cta: 'home.aiPlanForMe',
}))

/* ==================== 默认数据 ==================== */
// BUGID L-HOME-1 修复：默认 Banners/目的地封面改为 computed，staticImageMap 就绪后自动重算
const defaultBanners = computed(() => [
  { id: 1, image: getImageUrl('大理'), title: '云南大理', subtitle: 'home.bannerSubtitle1', link: '/destination-detail?city=大理' },
  { id: 2, image: getImageUrl('拉萨'), title: '西藏拉萨', subtitle: 'home.bannerSubtitle2', link: '/destination-detail?city=拉萨' },
  { id: 3, image: getImageUrl('天山'), title: '新疆天山', subtitle: 'home.bannerSubtitle3', link: '/destination-detail?city=乌鲁木齐' },
  { id: 4, image: getImageUrl('三亚'), title: '海南三亚', subtitle: 'home.bannerSubtitle4', link: '/destination-detail?city=三亚' },
])

const defaultDestinations = computed(() => [
  { name: '北京', tag: '经典必去', image: getImageUrl('北京') },
  { name: '上海', tag: '都市潮流', image: getImageUrl('上海') },
  { name: '广州', tag: '美食之都', image: getImageUrl('广州') },
  { name: '深圳', tag: '创新之城', image: getImageUrl('深圳') },
  { name: '成都', tag: '网红打卡', image: getImageUrl('成都') },
  { name: '杭州', tag: '诗画江南', image: getImageUrl('杭州') },
  { name: '西安', tag: '千年古都', image: getImageUrl('西安') },
  { name: '重庆', tag: '8D魔幻', image: getImageUrl('重庆') },
])

const ph = (hue, label) => `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="hsl(${hue},60%,65%)"/><stop offset="100%" stop-color="hsl(${hue+30},70%,45%)"/></linearGradient></defs><rect fill="url(#g)" width="400" height="300"/><text fill="rgba(255,255,255,0.7)" font-size="28" font-family="sans-serif" x="200" y="150" text-anchor="middle" dominant-baseline="middle">${label}</text></svg>`)}`

/* ==================== 社区种子数据（fallback） ==================== */
const seedNotes = [
  {
    id: 1,
    author: { nickname: '带着娃看世界', avatar: avatarUrl('family', ''), city: '上海', isFollowing: false, online: true, userId: 'u6' },
    title: '🎠 上海迪士尼亲子二日游全攻略！轻松带娃不踩雷',
    content: '带4岁娃的上海迪士尼亲子攻略！轻松版游玩路线，避开人潮不用排长队。重点推荐旋转木马、小飞象和冰雪奇缘表演，孩子玩得超开心。附上园区儿童餐推荐和午睡tips。',
    images: [getImageUrl('上海'), getImageUrl('迪士尼'), getImageUrl('外滩')],
    viewCount: 12800, likeCount: 0, isLiked: false, tag: '亲子', time: '2小时前',
    hasVideo: false,
  },
  {
    id: 2,
    author: { nickname: '贵州旅行家小杨', avatar: avatarUrl('guizhou', ''), city: '贵阳', isFollowing: true, online: false, userId: 'u11' },
    title: '🏞️ 贵州旅游超全攻略｜7天6晚玩转黔东南',
    content: '刚回来！贵州太美了！黄果树瀑布气势磅礴，荔波小七孔水绿如翡翠，西江千户苗寨万家灯火震撼心灵。这份攻略整理了吃住行全攻略，人均不到3000玩转贵州精华景点！',
    images: [getImageUrl('黄果树瀑布'), getImageUrl('荔波'), getImageUrl('千户苗寨')],
    videoUrl: 'https://www.w3schools.com/html/mov_bbb.mp4',
    viewCount: 35600, likeCount: 0, isLiked: false, tag: '贵州', time: '4小时前',
    hasVideo: true,
  },
  {
    id: 3,
    author: { nickname: '历史迷小王', avatar: avatarUrl('history', ''), city: '成都', isFollowing: false, online: true, userId: 'u12' },
    title: '📜 都江堰景区一日游｜千年水利工程太震撼了',
    content: '都江堰到底值不值得去？答案是：绝对值得！亲眼看到两千多年前李冰父子修建的水利工程至今仍在发挥作用，鱼嘴分水、飞沙堰溢洪、宝瓶口引水，古人的智慧让人叹服。附上门票交通全攻略。',
    images: [getImageUrl('都江堰'), getImageUrl('成都'), getImageUrl('青城山')],
    viewCount: 22100, likeCount: 0, isLiked: false, tag: '都江堰', time: '6小时前',
    hasVideo: false,
  },
  {
    id: 4,
    author: { nickname: '酒店控小鹿', avatar: avatarUrl('hotellover', ''), city: '上海', isFollowing: false, online: false, userId: 'u13' },
    title: '🏨 上海外滩周边高性价比酒店推荐｜睡在风景里',
    content: '整理了上海外滩/南京路周边5家高性价比酒店，从网红民宿到五星级酒店都有实测。关键看江景、交通便利度和性价比。和平饭店的下午茶、华尔道夫的老上海风情，每一家都有独特体验！',
    images: [getImageUrl('上海'), getImageUrl('南京路'), getImageUrl('陆家嘴')],
    viewCount: 18900, likeCount: 0, isLiked: false, tag: '上海', time: '8小时前',
    hasVideo: false,
  },
  {
    id: 5,
    author: { nickname: '贵阳本地通', avatar: avatarUrl('guiyang', ''), city: '贵阳', isFollowing: true, online: true, userId: 'u14' },
    title: '🌄 贵阳周边绝美风景｜本地人私藏的小众打卡地',
    content: '贵阳不止有甲秀楼！花溪十里河滩骑行、青岩古镇品猪蹄、天河潭看溶洞瀑布、黔灵山看野生猕猴…这些本地人常去的地方才是贵阳的正确打开方式。美食推荐：肠旺面、丝娃娃、酸汤鱼，好吃到哭！',
    images: [getImageUrl('贵阳'), getImageUrl('青岩古镇'), getImageUrl('黔灵山')],
    viewCount: 15200, likeCount: 0, isLiked: false, tag: '贵阳', time: '12小时前',
    hasVideo: false,
  },
  {
    id: 6,
    author: { nickname: '背包客阿飞', avatar: avatarUrl('backpack', ''), city: '大理', isFollowing: true, online: true, userId: 'u5' },
    title: '🌾 大理旅居一个月｜环洱海自驾保姆级攻略',
    content: '大理旅居一个月，整理了这份环洱海自驾攻略。路线：古城-喜洲-双廊-挖色-海东。全程130公里，建议分两天慢慢玩。喜洲的稻田、双廊的日落、海东的悬崖公路，每一段都让人不想离开。',
    images: [getImageUrl('大理'), getImageUrl('喜洲'), getImageUrl('双廊')],
    viewCount: 45600, likeCount: 0, isLiked: false, tag: '大理', time: '1天前',
    hasVideo: false,
  },
  {
    id: 7,
    author: { nickname: '吃货小分队', avatar: avatarUrl('foodie', ''), city: '成都', isFollowing: true, online: false, userId: 'u2' },
    title: '🍲 重庆本地人私藏的火锅地图｜这12家必须吃',
    content: '避开网红店，整理了12家藏在居民楼里的老火锅。每家都有特色招牌菜，从人均40到80都有。特别推荐弹子石的巷子火锅和观音桥的防空洞火锅，麻辣鲜香巴适得板！建议收藏！',
    images: [getImageUrl('重庆'), getImageUrl('洪崖洞'), getImageUrl('解放碑')],
    videoUrl: 'https://media.w3.org/2010/05/sintel/trailer.mp4',
    viewCount: 38900, likeCount: 0, isLiked: false, tag: '重庆', time: '1天前',
    hasVideo: true,
  },
  {
    id: 8,
    author: { nickname: '摄影师Mr陈', avatar: avatarUrl('photoc', ''), city: '杭州', isFollowing: false, online: false, userId: 'u15' },
    title: '📸 杭州西湖边的绝美咖啡馆合集｜拍照超出片',
    content: '整理了环西湖最值得去的5家独立咖啡馆，从断桥边的民国老宅到龙井山上的玻璃房。每一家都有独特的设计美学和出品，附上每家的推荐饮品和最佳拍摄机位，文艺青年必收藏！',
    images: [getImageUrl('杭州'), getImageUrl('西湖'), getImageUrl('龙井')],
    viewCount: 27300, likeCount: 0, isLiked: false, tag: '杭州', time: '2天前',
    hasVideo: false,
  },
  {
    id: 9,
    author: { nickname: '户外探险家', avatar: avatarUrl('hiker', ''), city: '西安', isFollowing: false, online: true, userId: 'u3' },
    title: '⛰️ 华山一日游挑战长空栈道｜云海翻涌太震撼',
    content: '早上5点出发，索道上北峰，一路徒步经过苍龙岭、金锁关，最后挑战长空栈道。虽然腿软但风景绝美，云海翻涌，值得一生铭记的体验。附登山装备清单和体力分配建议！',
    images: [getImageUrl('华山'), getImageUrl('西安'), getImageUrl('渭南')],
    viewCount: 31200, likeCount: 0, isLiked: false, tag: '华山', time: '2天前',
    hasVideo: false,
  },
  {
    id: 10,
    author: { nickname: '旅行者小明', avatar: avatarUrl('ming', ''), city: '深圳', isFollowing: false, online: true, userId: 'u1' },
    title: '🚴 深圳湾公园骑行｜沿海岸线看绝美日落',
    content: '深圳湾公园骑行真的太舒服了！沿着海岸线一路骑行，海风轻拂，视野开阔。推荐傍晚时分出发，可以看到绝美的海上日落，沿途还有很多拍照打卡点。全程15公里左右，新手也完全能驾驭。',
    images: [getImageUrl('深圳'), getImageUrl('深圳湾'), getImageUrl('大梅沙')],
    viewCount: 19500, likeCount: 0, isLiked: false, tag: '深圳', time: '3天前',
    hasVideo: false,
  },
]

const defaultExperiences = [
  { id: 1, key: 'riceNoodles', icon: 'food-o', color: '#FCA5A5' },
  { id: 2, key: 'hotpot', icon: 'flower-o', color: '#EF4444' },
  { id: 3, key: 'diving', icon: 'guide-o', color: '#3B82F6' },
  { id: 4, key: 'skiing', icon: 'photo-o', color: '#60A5FA' },
  { id: 5, key: 'hotSpring', icon: 'smile-o', color: '#FB923C' },
  { id: 6, key: 'hiking', icon: 'flag-o', color: '#22C55E' },
]

/* ==================== 响应式数据 ==================== */
const banners = ref([])
const hotDestinations = ref([])
const experiences = ref([])

const isLoading = ref({ destinations: true, notes: true })

const loadBanners = () => { banners.value = defaultBanners.value }
const loadHotDestinations = async () => {
  isLoading.value.destinations = true
  try {
    const res = await getHotDestinations()
    const list = res.data || []
    hotDestinations.value = list.length ? list.map(item => ({ ...item, image: item.imageUrl || getImageUrl(item.name), tag: item.tag || '热门推荐' })) : defaultDestinations.value
  } catch (e) {
    hotDestinations.value = defaultDestinations.value
  } finally { isLoading.value.destinations = false }
}

const loadExperiences = () => { experiences.value = defaultExperiences }

/* ==================== 携程风格模块 ==================== */
const ctripTabs = ref([
  { key: 'all', label: '全部', icon: 'star-o' },
  { key: 'hot', label: '热门', icon: 'flame-o' },
  { key: 'recommend', label: '推荐', icon: 'thumbs-up-o' },
  { key: 'new', label: '最新', icon: 'clock-o' },
])
const ctripActiveTab = ref('all')

const formatNumber = (num) => {
  if (!num || num < 0) return '0'
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  } else if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'k'
  }
  return num.toString()
}

/* ==================== 社区模块（复刻自CommunityView） ==================== */
const activeTab = ref('all')
const currentCity = ref('深圳')
const showCommunityCityPicker = ref(false)
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

const onCommunityCityConfirm = ({ selectedOptions }) => {
  if (selectedOptions && selectedOptions[0]) {
    currentCity.value = selectedOptions[0].text
  }
  showCommunityCityPicker.value = false
}

const notes = ref([])
const notesLoading = ref(true)
const page = ref(1)
const hasMore = ref(true)
const loadingMore = ref(false)

const empty = computed(() => !notesLoading.value && notes.value.length === 0)

// 瀑布流：图片卡片统一 3:4 竖置，视频卡片自适应
const getCardAspectClass = (index) => {
  return 'aspect-3-4'
}

// 图片卡片：统一 3:4 竖置比例
const getImageCardAspect = () => 'aspect-3-4'

// 视频卡片：根据 note 里的视频自适应（默认 16:9）
const getVideoCardAspect = (note) => {
  return 'aspect-video'
}

// 左右列统一用 3:4 竖置（视频卡片会用不同 class）
const getLeftCardAspect = (index) => 'aspect-3-4'
const getRightCardAspect = (index) => 'aspect-3-4'

// 携程风瀑布流：显示所有已加载的笔记（随滚动加载逐步增多，不再固定前10条）
const displayNotes = computed(() => notes.value)

// 【修复】左列：偶数索引（包含0号，原逻辑丢弃索引0导致首条视频不显示）
// 推广轮播作为左列顶部额外卡片，不挤占任何一条笔记
const leftColumnNotes = computed(() => displayNotes.value.filter((_, index) => index % 2 === 0))

// 右列：奇数索引
const rightColumnNotes = computed(() => displayNotes.value.filter((_, index) => index % 2 === 1))

const goToCommunity = () => {
  try { router.push('/notes') } catch (e) { console.error('goToCommunity 失败:', e) }
}

// BUGID L-HOME-1 修复：推广轮播改为 computed，staticImageMap 就绪后自动重算，避免首屏裂图
const promotionSlides = computed(() => [
  { image: getImageUrl('迪士尼'), title: 'home.promoFamilyTitle', subtitle: 'home.promoFamilySubtitle', tag: 'home.promoFamilyTag' },
  { image: getImageUrl('黄果树瀑布'), title: 'home.promoGuizhouTitle', subtitle: 'home.promoGuizhouSubtitle', tag: 'home.promoGuizhouTag' },
  { image: getImageUrl('都江堰'), title: 'home.promoDujiangyanTitle', subtitle: 'home.promoDujiangyanSubtitle', tag: 'home.promoDujiangyanTag' },
])

/* BUGID PAGE-1 修复：加载序号守卫，防止触底滚动与首屏加载并发重复拉页（旧的 loadingMore 无并发守卫） */
const loadSeq = ref(0)
const loadNotes = async (reset = false) => {
  if (reset) {
    page.value = 1
    hasMore.value = true
    notesLoading.value = true
  }
  if (!hasMore.value && !reset) return
  const seq = ++loadSeq.value
  try {
    loadingMore.value = !reset
    // 服务端分页加载
    const res = await noteApi.getAllNotes(page.value)
    if (seq !== loadSeq.value) return  // 已有更新的加载请求，丢弃本次结果
    if (res && res.code === 0 && res.data) {
      const list = Array.isArray(res.data) ? res.data : (res.data.list || [])
      const mapped = list.map(mapNoteItem)
      if (reset) {
        notes.value = mapped
      } else {
        notes.value = [...notes.value, ...mapped].slice(-50)  // 最多保留50条防OOM
      }
      hasMore.value = res.data.hasMore !== undefined ? res.data.hasMore : (list.length >= (res.data.size || 10))
      page.value += 1
    } else {
      if (reset) notes.value = [...seedNotes]
      hasMore.value = false
    }
  } catch (e) {
    if (seq !== loadSeq.value) return
    console.warn('加载社区笔记失败，使用本地种子数据:', e.message)
    if (reset) notes.value = [...seedNotes]
    hasMore.value = false
  } finally {
    if (seq !== loadSeq.value) return
    notesLoading.value = false
    loadingMore.value = false
    isLoading.value.notes = false
  }
}

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

const extractVideos = (html) => {
  if (!html) return []
  const result = []
  const r1 = /<video[^>]*\bsrc="([^">]+)"[^>]*>/gi
  let m
  while ((m = r1.exec(html)) !== null) {
    if (m[1]) result.push(m[1])
  }
  const r2 = /<source[^>]*\bsrc="([^">]+)"[^>]*>/gi
  while ((m = r2.exec(html)) !== null) {
    if (m[1]) result.push(m[1])
  }
  return result
}

const stripHtml = (html) => {
  if (!html) return ''
  // <br> 标签转换为换行符，保留用户输入的换行格式
  let text = html.replace(/<br\s*\/?>/gi, '\n')
  // 移除img标签（不显示占位文字，图片已在卡片封面展示）
  text = text.replace(/<img[^>]*>/gi, '')
  // 移除其他标签
  text = text.replace(/<[^>]+>/g, '')
  // 解码常见实体
  text = text.replace(/&nbsp;/g, ' ').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
  return text.trim()
}

const mapNoteItem = (item) => {
  const isSeedData = !!item.author && typeof item.author === 'object'
  const authorNickname = isSeedData ? item.author.nickname : (item.authorName || item.nickname || t('home.traveler'))
  const authorAvatar = isSeedData ? item.author.avatar : (item.authorAvatar || item.avatar || avatarUrl(String(item.id || 'fallback'), authorNickname))
  const authorCity = isSeedData ? item.author.city : (item.city || '')
  const authorIsFollowing = isSeedData ? item.author.isFollowing : (item.isFollowing || false)
  const authorOnline = isSeedData ? item.author.online : (item.online !== undefined ? item.online : Math.random() > 0.4)
  // 作者ID：API 数据用后端返回的 userId（作者本人），绝不可回退到笔记 id，否则关注错人
  const authorUserId = isSeedData ? item.author.userId : (item.userId || item.authorId || null)
  let images = []
  if (isSeedData) {
    images = item.images || []
  } else {
    const contentImages = extractImages(item.content)
    const contentVideos = extractVideos(item.content)
    if (item.cover) images.push(item.cover)
    images = images.concat(contentImages).concat(contentVideos)
    images = [...new Set(images)]
  }
  const plainContent = isSeedData ? item.content : stripHtml(item.content)
  const rawCover = item.cover || ''
  const rawContent = item.content || ''
  const hasVideo = rawCover.match(/\.(mp4|webm|mov)(\?|$)/i)
    || /<video[^>]*src=/i.test(rawContent)
    || /<source[^>]*src="[^">]+\.(mp4|webm|mov)/i.test(rawContent)
    || images.some(img => isVideoUrl(img))
  // 【修复】videoUrl 从已构建的 images 数组中提取，而非原始 item
  // 根因：API 数据没有 item.images，视频 URL 在 extractVideos 中已提取到 images 数组
  const extractedVideoUrl = item.videoUrl || images.find(img => isVideoUrl(img)) || ''
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
    title: item.title || '',
    content: plainContent || '',
    images: images,
    videoUrl: extractedVideoUrl,
    hasVideo: item.hasVideo || !!hasVideo || !!extractedVideoUrl,
    viewCount: item.viewCount || item.views || 0,
    likeCount: item.likeCount || item.likes || 0,
    isLiked: item.isLiked || false,
    commentCount: item.commentCount || item.comments || 0,
    tag: item.tag || item.author?.city || authorCity || '',
    time: item.time || item.date || item.createTime || t('home.justNow'),
  }
}

const isVideoUrl = (url) => url && /\.(mp4|webm|mov)(\?|$)/i.test(url)

const getNoteCoverImage = (note) => {
  if (!note?.images?.length) return ''
  const cover = note.images.find(img => !isVideoUrl(img))
  return cover || ''
}

const goToDetail = (note) => {
  if (!note || !note.id) return
  if (note.hasVideo) {
    router.push(`/video-detail?id=${note.id}`)
  } else {
    router.push(`/note-detail?id=${note.id}`)
  }
}

const handleLike = async (note) => {
  const token = getToken()
  if (!token) {
    showToast({ message: t('common.notLoggedIn'), position: 'middle', duration: 1500 })
    return
  }
  const prevLiked = note.isLiked
  note.isLiked = !note.isLiked
  // BUGID L-HOME-2 修复：种子数据可能缺失 likeCount，对缺失值兜底，避免 NaN
  note.likeCount = (Number(note.likeCount) || 0) + (note.isLiked ? 1 : -1)
  try {
    const res = await noteApi.likeNote(note.id)
    if (res.code !== 0) throw new Error(res.message)
  } catch (e) {
    note.isLiked = prevLiked
    note.likeCount = (Number(note.likeCount) || 0) + (prevLiked ? 1 : -1)
    showToast({ message: t('home.operationFailedRetry'), position: 'middle', duration: 1500 })
  }
}

const handleFollow = async (author) => {
  const token = getToken()
  if (!token) {
    showToast({ message: t('common.notLoggedIn'), position: 'middle', duration: 1500 })
    return
  }
  const newState = !author.isFollowing
  // 同步所有该用户的笔记
  notes.value.forEach(n => {
    if (n.author?.userId === author.userId) n.author.isFollowing = newState
  })
  try {
    if (newState) {
      await followApi.follow(author.userId)
    } else {
      await followApi.unfollow(author.userId)
    }
  } catch (e) {
    notes.value.forEach(n => {
      if (n.author?.userId === author.userId) n.author.isFollowing = !newState
    })
    showToast({ message: t('home.operationFailedRetry'), position: 'middle', duration: 1500 })
  }
}

const commentInputs = reactive({})
const commentImages = reactive({})
const commentVideos = reactive({})
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
      showToast({ message: res.data.type === 'image' ? t('home.imageUploaded') : t('home.videoUploaded'), position: 'middle', duration: 1200 })
    } else {
      showToast({ message: res.message || t('home.uploadFailed'), position: 'middle', duration: 1500 })
    }
  } catch (e) {
    showToast({ message: t('home.uploadFailed'), position: 'middle', duration: 1500 })
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
    showToast({ message: t('common.notLoggedIn'), position: 'middle', duration: 1500 })
    return
  }
  try {
    const res = await commentApi.addComment(note.id, text || null, img || null, vid || null)
    if (res.code === 0) {
      note.commentCount = (note.commentCount || 0) + 1
      commentInputs[note.id] = ''
      commentImages[note.id] = ''
      commentVideos[note.id] = ''
      showToast({ message: t('home.commentSuccess'), position: 'middle', duration: 1200 })
    } else {
      showToast({ message: res.message || t('home.commentFailed'), position: 'middle', duration: 1500 })
    }
  } catch (e) {
    showToast({ message: t('home.commentFailedRetry'), position: 'middle', duration: 1500 })
  }
}

const handleCommunitySearch = () => {
  showToast({ message: t('home.searchInDevelopment'), position: 'middle', duration: 1500 })
}

const onTabChange = (key) => {
  activeTab.value = key
  if (key === 'following') {
    const token = getToken()
    if (!token) {
      showToast({ message: t('home.loginToViewFollowing'), position: 'middle', duration: 1500 })
      activeTab.value = 'all'
      return
    }
  }
}

/* ==================== 事件 ==================== */
let planningTimer = null // 开始规划跳转定时器：onUnmounted 时清理
const startPlanning = () => {
  try {
    if (!destination.value || String(destination.value).trim() === '') return showToast({ message: t('home.enterDestination'), position: 'middle' })
    if (!budget.value || String(budget.value).trim() === '') return showToast({ message: t('home.enterBudget'), position: 'middle' })
    if (!days.value || String(days.value).trim() === '') return showToast({ message: t('home.enterDays'), position: 'middle' })
    if (Number(days.value) < 1) return showToast({ message: t('home.daysMin1'), position: 'middle' })
    if (Number(budget.value) < 100) return showToast({ message: t('home.budgetMin100'), position: 'middle' })
    if (!people.value || String(people.value).trim() === '') return showToast({ message: t('home.enterPeople'), position: 'middle' })
    if (Number(people.value) < 1 || Number(people.value) > 50) return showToast({ message: t('home.peopleRange'), position: 'middle' })
    showLoadingToast({ message: t('home.aiPlanning'), duration: 500, forbidClick: true, loadingType: 'spinner' })
    clearTimeout(planningTimer)
    planningTimer = setTimeout(() => router.push({ path: '/agent-map', query: { destination: destination.value, budget: budget.value, days: days.value, people: people.value } }), 500)
  } catch (e) { console.error('startPlanning 失败:', e); showToast({ message: t('home.operationFailedRetry'), position: 'middle' }) }
}

const handleQuickEntry = (entry) => {
  try {
    if (entry?.path) router.push(entry.path)
    else showToast({ message: t('home.featureInDevelopment'), position: 'middle' })
  } catch (e) { console.error('handleQuickEntry 失败:', e) }
}

/* 头部按钮点击：会员 / 积分 */
const handleHeaderBtn = (type) => {
  try {
    if (type === 'vip') router.push('/profile')
    else showToast({ message: t('home.pointsInDevelopment'), position: 'middle' })
  } catch (e) { console.error('handleHeaderBtn 失败:', e) }
}
/*
 * 【修复】热门目的地卡片点击 → 跳转城市详情页
 * 根因：此前只设置 destination.value = dest.name，页面无任何可见变化，用户以为点击无效
 */
const handleDestination = (dest) => {
  try {
    if (!dest || !dest.name) { showToast({ message: t('home.destinationError'), position: 'middle' }); return }
    router.push({ path: '/destination-detail', query: { city: dest.name } })
  } catch (e) { console.error('handleDestination 跳转失败:', e); showToast({ message: t('home.jumpFailed'), position: 'middle' }) }
}
const goToDestinations = () => {
  try { router.push('/destinations') } catch (e) { console.error('goToDestinations 失败:', e) }
}
const handleSearchSelect = (item) => { if (item?.text) destination.value = item.text; else if (item?.name) destination.value = item.name }
const searchHistory = computed(() => hotTags.map(c => ({ text: c, url: '' })))
const handleBannerClick = (banner) => {
  try {
    if (!banner || !banner.link) { showToast({ message: t('home.activityError'), position: 'middle' }); return }
    router.push(banner.link)
  } catch (e) { console.error('handleBannerClick 失败:', e) }
}
const handleExperienceClick = (exp) => {
  try { showToast({ message: t('home.featureNamedInDevelopment', { name: exp?.key ? t(`home.experiences.${exp.key}`) : t('home.thatFeature') }), position: 'middle' }) } catch (e) {}
}
const selectHotTag = (tag) => { destination.value = tag }

/* Layer 4: 城市快捷标签点击 */
const handleCityTagClick = (city) => {
  try {
    if (!city) return
    router.push({ path: '/destination-detail', query: { city } })
  } catch (e) { console.error('handleCityTagClick 失败:', e) }
}

/* Layer 2: 服务入口点击 */
const handleServiceClick = (item) => {
  try {
    const routes = {
      hotel: '/hotel-booking',
      guide: '/destinations',
      flight: '/flight-booking',
      train: '/orders',
      custom: '/chat',
      homestay: '/orders',
      tickets: '/orders',
      pickup: '/orders',
      car: '/orders',
      tour: '/orders',
    }
    if (routes[item?.key]) {
      router.push(routes[item.key])
    } else {
      showToast({ message: t('home.featureInDevelopment'), position: 'middle' })
    }
  } catch (e) { console.error('handleServiceClick 失败:', e) }
}

/* Layer 5: 快捷功能标签点击 */
const handleQuickTab = (tab) => {
  try {
    if (tab?.name === '行程规划') { goToAIChat() }
    else { showToast({ message: t('home.featureInDevelopment'), position: 'middle' }) }
  } catch (e) { console.error('handleQuickTab 失败:', e) }
}

/* Layer 6: 双列卡片点击 */
const handleEventBannerClick = () => {
  try { router.push(eventBanner.value.link) } catch (e) { console.error('handleEventBannerClick 失败:', e) }
}

const handleCitySeedClick = () => {
  try { goToAIChat() } catch (e) { console.error('handleCitySeedClick 失败:', e) }
}

/* Layer 3: 更多产品点击 */
const handleMoreProductClick = (product) => {
  try { showToast({ message: t('home.featureNamedInDevelopment', { name: product?.key ? t(`home.products.${product.key}`) : t('home.thatFeature') }), position: 'middle' }) } catch (e) {}
}

/* 【悬浮按钮】点击防抖：500ms内重复点击忽略，避免快速跳转多次 */
let aiBtnDebounce = false
const showAIChat = ref(false)  // 【5Tab架构】AI对话弹窗显隐
const goToAIChat = () => {
  if (aiBtnDebounce) return
  aiBtnDebounce = true
  setTimeout(() => { aiBtnDebounce = false }, 500)
  showAIChat.value = true       // 打开内嵌AI对话弹窗，不跳转页面
}

const onCityConfirm = (value) => {
  if (value && value.selectedOptions) destination.value = value.selectedOptions[1]?.text || value.selectedOptions[0]?.text || ''
  showCityPicker.value = false
}

const openCityPicker = () => { showCityPicker.value = true }

/* ==================== 城市选择器滚轮 ==================== */
const wheelGesture = new WeakMap()
const dispatchTouch = (el, type, x, y) => {
  const touch = new Touch({ identifier: 0, target: el, clientX: x, clientY: y })
  el.dispatchEvent(new TouchEvent(type, { cancelable: true, bubbles: true, touches: type === 'touchend' ? [] : [touch], targetTouches: type === 'touchend' ? [] : [touch], changedTouches: [touch] }))
}

const handlePickerWheel = (e) => {
  e.preventDefault(); e.stopPropagation()
  if (!(window.TouchEvent && typeof Touch === 'function')) return
  const picker = document.querySelector('.van-popup .van-picker')
  if (!picker) return
  const columns = picker.querySelectorAll('.van-picker-column')
  if (columns.length === 0) return
  const col = Array.from(columns).find(c => { const r = c.getBoundingClientRect(); return e.clientX >= r.left && e.clientX <= r.right }) || columns[0]
  if (!col) return
  const r = col.getBoundingClientRect()
  const cx = r.left + r.width / 2; const cy = r.top + r.height / 2
  const itemHeight = 44
  let st = wheelGesture.get(col)
  if (!st) { dispatchTouch(col, 'touchstart', cx, cy); st = { targetY: 0, currentY: 0, timer: null, rafId: 0, animating: false }; wheelGesture.set(col, st) }
  st.targetY -= Math.sign(e.deltaY) * itemHeight
  if (!st.animating) {
    st.animating = true
    const animate = () => {
      const diff = st.targetY - st.currentY
      if (Math.abs(diff) > 0.5) { st.currentY += diff * 0.35; dispatchTouch(col, 'touchmove', cx, cy + st.currentY); st.rafId = requestAnimationFrame(animate) }
      else { st.currentY = st.targetY; dispatchTouch(col, 'touchmove', cx, cy + st.currentY); st.animating = false }
    }
    st.rafId = requestAnimationFrame(animate)
  }
  clearTimeout(st.timer)
  st.timer = setTimeout(() => { if (st.rafId) cancelAnimationFrame(st.rafId); st.currentY = st.targetY; dispatchTouch(col, 'touchmove', cx, cy + st.currentY); dispatchTouch(col, 'touchend', cx, cy + st.currentY); wheelGesture.delete(col) }, 320)
}

let wheelTimer = null  // BUGID L-HOME-3 修复：setTimeout 句柄，弹窗关闭时取消挂载任务
const addWheelListeners = () => {
  clearTimeout(wheelTimer)  // 重复打开时先取消上一次未执行的挂载任务
  wheelTimer = setTimeout(() => {
    // BUGID FEAT-5 修复：用组件自身 popup 的 id 精确定位，不再全局抓取第一个 .van-popup
    const popup = document.getElementById('city-picker-popup')
    if (popup) { const picker = popup.querySelector('.van-picker'); if (picker) { const handler = (e) => handlePickerWheel(e); popup.addEventListener('wheel', handler, { passive: false }); wheelHandlers.value.push({ column: popup, handler }) } }
  }, 500)
}

const removeWheelListeners = () => {
  clearTimeout(wheelTimer); wheelTimer = null  // BUGID L-HOME-3 修复：关闭时取消未执行的挂载，避免挂到残留 popup
  wheelHandlers.value.forEach(({ column, handler }) => column.removeEventListener('wheel', handler))
  wheelHandlers.value = []
}
watch(showCityPicker, (newVal) => {
  if (newVal) addWheelListeners()
  else removeWheelListeners()
})

/* ==================== 滚动触底加载（和社区页一致） ==================== */
const handleScroll = () => {
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const scrollHeight = document.documentElement.scrollHeight
  const clientHeight = window.innerHeight
  // BUGID PAGE-1 修复：首屏加载中或翻页中不触发，避免重复拉页
  if (scrollHeight - scrollTop - clientHeight < 200 && hasMore.value && !loadingMore.value && !notesLoading.value) {
    loadNotes()
  }
}

let hasLoadedOnce = false

onMounted(async () => {
  await loadStaticImageMap()
  loadBanners(); loadHotDestinations(); loadExperiences()
  loadNotes(true).then(() => { hasLoadedOnce = true })
  // 滚动监听统一在 onActivated 注册（keep-alive 首挂载会触发 onActivated，避免重复注册）
})

onActivated(() => {
  // keep-alive 缓存恢复，数据不重新加载
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onDeactivated(() => {
  removeWheelListeners()
  window.removeEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  clearTimeout(planningTimer); planningTimer = null
})
</script>

<template>
  <div class="page-shell">

    <!-- 漂浮云朵粒子 — 已禁用（GPU消耗过高） -->

    <!-- ==================== LAYER 1: Hero Header ==================== -->
    <div class="hero-header entrance-item entrance-d1">
      <!-- 全屏山水背景图 -->
      <img
        class="hero-bg-img"
        src="https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800&q=80"
        alt=""
      />
      <!-- 底部渐变遮罩，保证白色文字可读 -->
      <div class="hero-overlay"></div>

      <!-- 左下：主文案区 -->
      <div class="hero-text-area">
        <h1 class="hero-title">旅迹</h1>
        <p class="hero-sub-en">TRAVEL TRACE</p>
        <p class="hero-tagline">{{ t('home.heroTagline') }}</p>
      </div>

      <!-- 右下：两个磨砂半透按钮 -->
      <div class="hero-actions-right">
        <button class="hero-glass-btn-right" @click="handleHeaderBtn('vip')">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="3"/>
          </svg>
          <span>{{ t('home.vip') }}</span>
        </button>
        <button class="hero-glass-btn-right" @click="handleHeaderBtn('points')">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
          </svg>
          <span>{{ t('home.points') }}</span>
        </button>
      </div>
    </div>

    <!-- ==================== AI 智能规划 ==================== -->
    <div class="content-card plan-card">
      <!-- 标题 -->
      <div class="plan-header">
        <span class="plan-icon-wrap">🧭</span>
        <div class="plan-header-text">
          <span class="plan-title">{{ t('home.planTitle') }}</span>
          <span class="plan-subtitle">{{ t('home.planSubtitle') }}</span>
        </div>
      </div>

      <!-- 目的地搜索 — 核心输入 -->
      <div class="plan-search-row">
        <div class="plan-search-wrap">
          <SearchBar v-model="destination" :placeholder="t('home.searchDestinationPlaceholder')" :history="searchHistory" @select="handleSearchSelect" />
        </div>
        <button class="plan-loc-btn" @click="openCityPicker">
          <van-icon name="location-o" size="18" color="#7C3AED" />
        </button>
      </div>

      <!-- 热门目的地快捷选择 -->
      <div class="hot-tags">
        <span
          v-for="tag in hotTags" :key="'ht-' + tag"
          class="hot-tag"
          :class="{ active: destination === tag }"
          @click="selectHotTag(tag)"
        >{{ tag }}</span>
      </div>

      <!-- 预算/天数/人数 — 轻量选择器 -->
      <div class="plan-meta-row">
        <div class="plan-meta-item" :class="{ filled: budget }">
          <span class="plan-meta-label">{{ t('home.budget') }}</span>
          <input ref="budgetInputRef" :value="budget || undefined" type="text" inputmode="decimal" :placeholder="t('home.unlimited')" class="plan-meta-input" @input="handleBudgetInput" @blur="handleBudgetBlur" />
          <span v-if="budget" class="plan-meta-unit">{{ t('common.yuan') }}</span>
        </div>
        <div class="plan-meta-item" :class="{ filled: days }">
          <span class="plan-meta-label">{{ t('home.days') }}</span>
          <input ref="daysInputRef" :value="days" type="text" inputmode="numeric" :placeholder="t('home.unlimited')" class="plan-meta-input" @input="handleDaysInput" @blur="handleDaysBlur" />
          <span v-if="days" class="plan-meta-unit">{{ t('common.days') }}</span>
        </div>
        <div class="plan-meta-item" :class="{ filled: people }">
          <span class="plan-meta-label">{{ t('home.people') }}</span>
          <input ref="peopleInputRef" :value="people" type="text" inputmode="numeric" :placeholder="t('home.unlimited')" class="plan-meta-input" @input="handlePeopleInput" @blur="handlePeopleBlur" />
          <span v-if="people" class="plan-meta-unit">{{ t('common.people') }}</span>
        </div>
      </div>

      <!-- 提交按钮 -->
      <button class="plan-submit btn-tap-scale" @click="startPlanning">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M12 2l9 4.5v3.8c0 5.3-3.5 10.2-9 11.7-5.5-1.5-9-6.4-9-11.7V6.5L12 2z"/></svg>
        <span>{{ t('home.startPlanning') }}</span>
      </button>
    </div>

    <!-- ==================== Banner 轮播 ==================== -->
    <div class="content-card banner-wrap">
      <Swipe class="banner-swipe" :autoplay="4000" indicator-color="rgba(255,255,255,0.65)" indicator-active-color="#ffffff" :circular="true">
        <SwipeItem v-for="banner in banners" :key="'bn-' + banner.id" class="banner-slide" @click="handleBannerClick(banner)">
          <img :src="banner.image" :alt="banner.title" class="banner-img" loading="lazy" decoding="async" @error="e=>e.target.style.opacity='0'" />
          <div class="banner-info">
            <span class="banner-name">{{ banner.title }}</span>
            <span class="banner-slogan">{{ t(banner.subtitle) }}</span>
          </div>
        </SwipeItem>
      </Swipe>
    </div>

    <!-- ==================== 服务入口：双行合并 ==================== -->
    <div class="section-card">
      <div class="service-grid">
        <div v-for="(item, idx) in serviceRow1" :key="'s1-'+idx" class="service-item" @click="handleServiceClick(item)">
          <div class="service-icon-circle" :style="{ background: `${item.color}15` }">
            <van-icon :name="item.icon" :color="item.color" size="22" />
          </div>
          <span class="service-label">{{ t('home.serviceItems.' + item.key) }}</span>
        </div>
        <div v-for="(item, idx) in serviceRow2" :key="'s2-'+idx" class="service-item" @click="handleServiceClick(item)">
          <div class="service-icon-circle" :style="{ background: `${item.color}12` }">
            <van-icon :name="item.icon" :color="item.color" size="20" />
          </div>
          <span class="service-label">{{ t('home.serviceItems.' + item.key) }}</span>
        </div>
      </div>
      <!-- 更多 -->
      <div class="more-products-bar" @click="showMoreProducts = true">
        <div class="more-products-left">
          <div class="mini-icon-row">
            <span class="mini-icon" style="background:#ede9fe;color:#8B5CF6;">签</span>
            <span class="mini-icon" style="background:#dbeafe;color:#3B82F6;">导</span>
            <span class="mini-icon" style="background:#fef3c7;color:#F59E0B;">W</span>
            <span class="mini-icon" style="background:#d1fae5;color:#34D399;">保</span>
          </div>
          <span class="more-products-text">{{ t('home.moreProducts') }}</span>
        </div>
        <van-icon name="arrow" size="14" color="var(--text-hint)" />
      </div>
    </div>

    <!-- ==================== 热门目的地 ==================== -->
    <div class="section-card">
      <div class="sec-head">
        <span class="sec-title">🔥 {{ t('home.hotDestinations') }}</span>
        <span class="sec-more" @click="goToDestinations">{{ t('common.viewAll') }} <van-icon name="arrow" size="12" /></span>
      </div>
      <div class="h-scroll">
        <div v-for="(d, i) in hotDestinations" :key="'hd-'+i" class="dest-card" @click="handleDestination(d)">
          <img :src="d.image" :alt="d.name" class="dest-img" loading="lazy" decoding="async" @error="e=>e.target.style.opacity='0'" />
          <div class="dest-mask" />
          <span class="dest-name">{{ d.name }}</span>
        </div>
      </div>
    </div>

    <!-- ==================== 双列活动卡片 ==================== -->
    <div class="dual-cards-scroll entrance-item entrance-d3">
      <div class="event-card" @click="handleEventBannerClick">
        <img :src="eventBanner.image" :alt="eventBanner.title" class="event-img" loading="lazy" decoding="async" @error="e=>e.target.style.opacity='0'" />
        <div class="event-overlay">
          <span class="event-badge">{{ t(eventBanner.label) }}</span>
          <span class="event-title">{{ t(eventBanner.title) }}</span>
        </div>
      </div>
      <div class="city-card" @click="handleCitySeedClick">
        <img :src="citySeedCard.image" :alt="citySeedCard.label" class="city-img" loading="lazy" decoding="async" @error="e=>e.target.style.opacity='0'" />
        <div class="city-overlay">
          <span class="city-badge">{{ t(citySeedCard.label) }}</span>
          <span class="city-cta">{{ t(citySeedCard.cta) }}</span>
        </div>
      </div>
    </div>

    <!-- ==================== 携程风格：优质游记（复刻社区功能） ==================== -->
    <div class="ctrip-section">
      <!-- 笔记 Feed -->
      <div class="ctrip-feed">
        <!-- 左列 -->
        <div class="ctrip-feed-column">
          <!-- 自动翻页宣传卡片（矮卡片 → 左列更高） -->
          <div class="ctrip-promotion-card">
            <Swipe :autoplay="3000" indicator-color="rgba(255,255,255,0.65)" indicator-active-color="#ffffff" :circular="true" style="height:150px;border-radius:12px;overflow:hidden;">
              <SwipeItem v-for="(slide, i) in promotionSlides" :key="'promo-'+i">
                <div class="ctrip-promo-slide">
                  <img :src="slide.image" class="ctrip-promo-img" loading="lazy" @error="e => { e.target.src = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22400%22 height=%22300%22><rect fill=%22%23e2e8f0%22 width=%22400%22 height=%22300%22/><text fill=%22%2394a3b8%22 font-size=%2220%22 font-family=%22sans-serif%22 x=%22200%22 y=%22150%22 text-anchor=%22middle%22 dominant-baseline=%22middle%22>' + encodeURIComponent(slide.title) + '</text></svg>') }" />
                  <div class="ctrip-promo-mask" />
                  <div class="ctrip-promo-tag">{{ t(slide.tag) }}</div>
                  <div class="ctrip-promo-title">{{ t(slide.title) }}</div>
                  <div class="ctrip-promo-subtitle">{{ t(slide.subtitle) }}</div>
                </div>
              </SwipeItem>
            </Swipe>
          </div>

          <template v-if="notesLoading">
            <div class="ctrip-skeleton-card" v-for="i in 3" :key="'ctrip-sk-left-'+i" :style="{ '--aspect': i % 2 === 0 ? '4/3' : '1/1' }">
              <div class="ctrip-sk-image"></div>
              <div class="ctrip-sk-info"><van-skeleton :row="1" /></div>
            </div>
          </template>
          <template v-else>
            <div
              class="ctrip-note-card"
              v-for="(note, colIndex) in leftColumnNotes"
              :key="note.id"
              @click="goToDetail(note)"
            >
              <!-- 卡片封面 -->
              <div class="ctrip-card-image-wrapper aspect-3-4">
                <!-- 视频：用 <video> 加载第一帧作为封面（和社区页一致） -->
                <video
                  v-if="note.hasVideo && note.videoUrl"
                  :src="note.videoUrl"
                  class="ctrip-card-main-img"
                  preload="metadata"
                  muted
                  playsinline
                  @loadedmetadata="(e) => { const v = e.target; v.currentTime = 0.1; }"
                  @seeked="(e) => { e.target.pause(); }"
                ></video>
                <!-- 图片 -->
                <img
                  v-else-if="getNoteCoverImage(note)"
                  :src="getNoteCoverImage(note)"
                  class="ctrip-card-main-img"
                  loading="lazy"
                />
                <!-- 占位 -->
                <div v-else class="ctrip-card-placeholder"></div>

                <!-- 视频播放标识 -->
                <div v-if="note.hasVideo" class="ctrip-video-play-overlay">
                  <van-icon name="play" size="16" color="rgba(255,255,255,0.95)" />
                </div>

                <!-- 标签 -->
                <div v-if="note.tag" class="ctrip-card-tag">{{ note.tag }}</div>
              </div>

              <div class="ctrip-card-body">
                <div class="ctrip-card-title" :title="note.title || note.content">{{ note.title || note.content }}</div>
                <div class="ctrip-card-footer">
                  <div class="ctrip-card-author">
                    <van-image round width="18" height="18" :src="note.author.avatar" fit="cover" />
                    <span>{{ note.author.nickname }}</span>
                  </div>
                  <div class="ctrip-card-views">
                    <van-icon name="eye-o" size="12" color="var(--text-hint)" />
                    <span>{{ formatNumber(note.viewCount) }}{{ t('home.views') }}</span>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- 右列 -->
        <div class="ctrip-feed-column">
          <template v-if="notesLoading">
            <div class="ctrip-skeleton-card" v-for="i in 3" :key="'ctrip-sk-right-'+i" :style="{ '--aspect': i % 2 === 0 ? '1/1' : '4/3' }">
              <div class="ctrip-sk-image"></div>
              <div class="ctrip-sk-info"><van-skeleton :row="1" /></div>
            </div>
          </template>
          <template v-else>
            <div
              class="ctrip-note-card"
              v-for="(note, colIndex) in rightColumnNotes"
              :key="note.id"
              @click="goToDetail(note)"
            >
              <!-- 卡片封面 -->
              <div class="ctrip-card-image-wrapper aspect-3-4">
                <!-- 视频：用 <video> 加载第一帧作为封面（和社区页一致） -->
                <video
                  v-if="note.hasVideo && note.videoUrl"
                  :src="note.videoUrl"
                  class="ctrip-card-main-img"
                  preload="metadata"
                  muted
                  playsinline
                  @loadedmetadata="(e) => { const v = e.target; v.currentTime = 0.1; }"
                  @seeked="(e) => { e.target.pause(); }"
                ></video>
                <!-- 图片 -->
                <img
                  v-else-if="getNoteCoverImage(note)"
                  :src="getNoteCoverImage(note)"
                  class="ctrip-card-main-img"
                  loading="lazy"
                />
                <!-- 占位 -->
                <div v-else class="ctrip-card-placeholder"></div>

                <!-- 视频播放标识 -->
                <div v-if="note.hasVideo" class="ctrip-video-play-overlay">
                  <van-icon name="play" size="16" color="rgba(255,255,255,0.95)" />
                </div>

                <!-- 标签 -->
                <div v-if="note.tag" class="ctrip-card-tag">{{ note.tag }}</div>
              </div>

              <div class="ctrip-card-body">
                <div class="ctrip-card-title" :title="note.title || note.content">{{ note.title || note.content }}</div>
                <div class="ctrip-card-footer">
                  <div class="ctrip-card-author">
                    <van-image round width="18" height="18" :src="note.author.avatar" fit="cover" />
                    <span>{{ note.author.nickname }}</span>
                  </div>
                  <div class="ctrip-card-views">
                    <van-icon name="eye-o" size="12" color="var(--text-hint)" />
                    <span>{{ formatNumber(note.viewCount) }}{{ t('home.views') }}</span>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- 加载更多/没有更多（全宽居中） -->
      <div class="ctrip-feed-footer">
        <div v-if="loadingMore" class="ctrip-loading-more"><van-loading size="20" color="#8B5CF6" /><span>{{ t('common.loading') }}</span></div>
        <div v-else-if="!hasMore && notes.length > 0" class="ctrip-no-more">— {{ t('common.noMore') }} —</div>
      </div>

      <!-- 城市选择器弹窗 -->
      <van-popup
        v-model:show="showCommunityCityPicker"
        position="bottom"
        round
        :style="{ borderRadius: '20px 20px 0 0' }"
      >
        <van-picker
          :columns="cityColumns"
          :default-index="cityColumns.findIndex(c => c.value === currentCity)"
          @confirm="onCommunityCityConfirm"
          @cancel="showCommunityCityPicker = false"
          :title="t('home.selectCity')"
        />
      </van-popup>
    </div>

    <!-- Bottom spacer for floating bar -->
    <div class="bottom-spacer" />

    <!-- ==================== LAYER 7: Bottom Floating AI Input Bar ==================== -->
    <Transition name="fab-pop">
      <button v-if="!showAIChat" class="fab-ai-btn" @click="goToAIChat">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="#7C3AED" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" fill="#7C3AED" fill-opacity="0.15"/>
          <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
          <circle cx="18" cy="5" r="1.5" fill="#A78BFA" stroke="none"/>
          <circle cx="6" cy="19" r="1.5" fill="#A78BFA" stroke="none"/>
        </svg>
      </button>
    </Transition>

    <!-- ==================== AIChatDialog ==================== -->
    <AIChatDialog
      v-model:visible="showAIChat"
      :context-query="{ destination: destination.trim(), budget: budget.trim(), days: days.trim() }"
    />

    <!-- ==================== 更多产品 VanPopup ==================== -->
    <van-popup v-model:show="showMoreProducts" position="bottom" round safe-area-inset-bottom :style="{ maxHeight: '60vh' }">
      <div class="more-popup-header">
        <span class="more-popup-title">{{ t('home.moreProductsTitle') }}</span>
        <van-icon name="cross" size="20" color="var(--text-hint)" @click="showMoreProducts = false" />
      </div>
      <div class="more-popup-grid">
        <div
          v-for="(product, idx) in moreProductList"
          :key="'mp-' + idx"
          class="more-popup-item"
          @click="handleMoreProductClick(product)"
        >
          <div class="more-popup-icon" :style="{ background: `${product.color}14` }">
            <van-icon :name="product.icon" :color="product.color" size="22" />
          </div>
          <span class="more-popup-label">{{ t('home.products.' + product.key) }}</span>
        </div>
      </div>
    </van-popup>

    <!-- ==================== 城市选择器 ==================== -->
    <van-popup id="city-picker-popup" v-model:show="showCityPicker" position="bottom" round safe-area-inset-bottom>
      <van-area ref="cityAreaRef" :title="t('home.selectCity')" :columns-num="2" :area-list="areaList" @confirm="onCityConfirm" @cancel="showCityPicker = false" />
    </van-popup>
  </div>
</template>

<style scoped>
/* ==================== CSS Variables ==================== */
.page-shell {
  --primary: #8B5CF6;
  --primary-2: #6366F1;
  --primary-3: #5B8DEF;
  --text-primary: var(--text-primary);
  --text-secondary: var(--text-secondary);
  --text-hint: var(--text-hint);
  --card-bg: rgba(255, 255, 255, 0.58);
  --card-radius: 18px;
  --card-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  --tabbar-height: 56px;
  --safe-area-bottom: 0px;
  --float-bar-height: 52px;
  --float-bar-gap: 8px;

  width: 100%;
  min-height: 100vh;
  background: transparent;
  padding-bottom: calc(10px + 48px + 60px + var(--safe-area-bottom, 0px));
}

/* ==================== LAYER 1: Hero — 山水大图卡片 ==================== */
.hero-header {
  position: relative;
  aspect-ratio: 8 / 5;
  margin: 0;
  border-radius: 0 0 22px 22px;
  overflow: hidden;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12);
}

/* 全屏山水背景 */
.hero-bg-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 底部渐变遮罩 — 保证白色文字可读 */
.hero-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgba(0,0,0,0.05) 0%,
    rgba(0,0,0,0.02) 30%,
    rgba(0,0,0,0.15) 65%,
    rgba(0,0,0,0.45) 100%
  );
  pointer-events: none;
  z-index: 1;
}

/* 左下：竖向主文案 */
.hero-text-area {
  position: absolute;
  left: 20px;
  bottom: 28px;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.hero-title {
  font-size: 48px;
  font-weight: 900;
  color: #fff;
  line-height: 1;
  margin: 0;
  letter-spacing: 4px;
  text-shadow: 0 2px 12px rgba(0,0,0,0.3);
}

.hero-sub-en {
  font-size: 11px;
  font-weight: 500;
  color: rgba(255,255,255,0.85);
  margin: 6px 0 10px;
  letter-spacing: 3px;
  text-shadow: 0 1px 6px rgba(0,0,0,0.3);
}

.hero-tagline {
  font-size: 13px;
  font-weight: 400;
  color: rgba(255,255,255,0.78);
  margin: 0;
  letter-spacing: 1px;
  text-shadow: 0 1px 6px rgba(0,0,0,0.25);
}

/* 右下：两个磨砂半透深色按钮 — 与"旅迹"顶部齐平 */
.hero-actions-right {
  position: absolute;
  right: 16px;
  bottom: 90px;
  z-index: 2;
  display: flex;
  gap: 10px;
}

.hero-glass-btn-right {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 8px 16px;
  background: rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(14px) saturate(150%);
  -webkit-backdrop-filter: blur(14px) saturate(150%);
  border: 0.5px solid rgba(255,255,255,0.18);
  border-radius: 20px;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 2px 10px rgba(0,0,0,0.15);
  transition: background 0.2s, transform 0.15s;
}
.hero-glass-btn-right:active {
  background: rgba(0, 0, 0, 0.45);
  transform: scale(0.94);
}

/* ==================== 统一卡片容器 ==================== */
.section-card {
  margin: 0 12px 12px;
  padding: 16px;
  background:
    linear-gradient(160deg, rgba(255,255,255,0.65) 0%, rgba(255,255,255,0.12) 35%, rgba(255,255,255,0.02) 60%, rgba(255,255,255,0.3) 100%),
    rgba(255,255,255,0.55);
  backdrop-filter: blur(14px) saturate(160%);
  -webkit-backdrop-filter: blur(14px) saturate(160%);
  border-radius: 18px;
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.6),
    0 2px 12px rgba(0,0,0,0.03);
  border: 1px solid rgba(255,255,255,0.65);
}

/* ==================== 服务入口：双行 5 列 ==================== */
.service-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px 4px;
  padding: 4px 0 12px;
}

.service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: transform 0.2s;
}
.service-item:active { transform: scale(0.92); }

.service-icon-circle {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
}
.service-label {
  font-size: 11px;
  color: #475569;
  font-weight: 500;
}

/* ==================== 更多产品入口条 ==================== */
.more-products-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0 0;
  margin-top: 2px;
  border-top: 1px solid rgba(0,0,0,0.04);
  cursor: pointer;
  transition: opacity 0.15s;
}
.more-products-bar:active { opacity: 0.6; }
.more-products-left { display: flex; align-items: center; gap: 10px; }
.mini-icon-row { display: flex; }
.mini-icon {
  width: 24px; height: 24px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 7px;
  font-size: 10px; font-weight: 700;
  margin-right: -4px;
  border: 2px solid #fff;
}
.more-products-text { font-size: 12px; color: var(--text-hint); font-weight: 500; }

/* ==================== 横向滚动容器 ==================== */
.h-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  scrollbar-width: none;
  padding-bottom: 2px;
}
.h-scroll::-webkit-scrollbar { display: none; }

/* ==================== 双列活动卡片 ==================== */
.dual-cards-scroll {
  display: flex;
  gap: 12px;
  padding: 0 12px;
  margin-bottom: 12px;
  overflow-x: auto;
  scrollbar-width: none;
}
.dual-cards-scroll::-webkit-scrollbar { display: none; }

.event-card, .city-card {
  flex-shrink: 0;
  width: 180px;
  height: 120px;
  border-radius: 16px;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  box-shadow: 0 2px 10px rgba(0,0,0,0.06);
}
.event-img, .city-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.event-overlay, .city-overlay {
  position: absolute; bottom: 0; left: 0; right: 0;
  padding: 28px 12px 10px;
  background: linear-gradient(transparent, rgba(0,0,0,0.55));
}
.event-badge, .city-badge {
  font-size: 10px;
  background: rgba(255,255,255,0.9);
  color: #7C3AED;
  padding: 2px 8px;
  border-radius: 8px;
  font-weight: 600;
  align-self: flex-start;
}
.event-title, .city-cta { font-size: 14px; font-weight: 700; color: #fff; margin-top: 4px; }

/* ==================== Banner — 椭圆卡片包裹 ==================== */
.banner-wrap {
  margin: 12px 12px 10px !important;
  padding: 0 !important;
  max-width: none !important;
  background: transparent !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
  border: none !important;
  box-shadow: none !important;
  border-radius: 0 !important;
}
.banner-swipe { border-radius: 16px; overflow: hidden; }
.banner-slide { position: relative; width: 100%; height: 160px; cursor: pointer; }
.banner-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.banner-info {
  position: absolute; bottom: 0; left: 0; right: 0;
  padding: 28px 16px 14px;
  background: linear-gradient(transparent, rgba(0,0,0,0.5));
  display: flex; flex-direction: column;
}
.banner-name {
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 1px;
  /* 斜向高光 + 底部暗角 = 玻璃反光感 */
  background: linear-gradient(145deg,
    #ffffff 0%,
    rgba(255,255,255,0.9) 15%,
    rgba(220,210,255,0.7) 35%,
    rgba(200,190,245,0.55) 55%,
    rgba(255,255,255,0.85) 75%,
    #ffffff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  text-shadow: none;
  /* 文字外发光强化玻璃折射 */
  filter: drop-shadow(0 1px 3px rgba(0,0,0,0.35)) drop-shadow(0 0 8px rgba(255,255,255,0.15));
}
.banner-slogan {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  margin-top: 2px;
  background: linear-gradient(145deg,
    rgba(255,255,255,0.85) 0%,
    rgba(220,210,255,0.6) 30%,
    rgba(200,195,240,0.45) 55%,
    rgba(255,255,255,0.8) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  filter: drop-shadow(0 1px 2px rgba(0,0,0,0.3));
}

/* ==================== AI 智能规划卡片 ==================== */
.plan-card {
  margin: 16px 8px 0 !important;
  padding: 20px 16px !important;
  background:
    linear-gradient(160deg, rgba(255,255,255,0.55) 0%, rgba(255,255,255,0.15) 35%, rgba(255,255,255,0.02) 60%, rgba(255,255,255,0.65) 100%),
    rgba(255,255,255,0.65);
  backdrop-filter: blur(18px) saturate(170%);
  -webkit-backdrop-filter: blur(18px) saturate(170%);
  border-radius: 20px;
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.65),
    0 2px 16px rgba(0,0,0,0.04);
  border: 1px solid rgba(255,255,255,0.65);
}

/* 标题区：图标 + 主副文案 */
.plan-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.plan-icon-wrap {
  width: 42px; height: 42px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #ede9fe, #ddd6fe);
  border-radius: 14px;
  font-size: 20px;
  flex-shrink: 0;
}
.plan-header-text {
  display: flex;
  flex-direction: column;
}
.plan-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.3;
}
.plan-subtitle {
  font-size: 12px;
  color: var(--text-hint);
  margin-top: 1px;
}

/* 目的地搜索行 — 磨砂玻璃 + 聚焦流光 */
.plan-search-row {
  display: flex;
  align-items: center;
  gap: 0;
  background: rgba(255,255,255,0.65);
  backdrop-filter: blur(8px) saturate(150%);
  -webkit-backdrop-filter: blur(8px) saturate(150%);
  border-radius: 14px;
  padding: 2px 4px 2px 14px;
  margin-bottom: 12px;
  border: 1.5px solid rgba(255,255,255,0.55);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.55);
  transition: border-color 0.3s, box-shadow 0.3s;
  position: relative;
}
.plan-search-row:focus-within {
  border-color: rgba(139,92,246,0.5);
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.55),
    0 0 0 3px rgba(139,92,246,0.08),
    0 0 20px rgba(139,92,246,0.1);
  animation: borderGlow 2s ease-in-out infinite;
}
@keyframes borderGlow {
  0%, 100% { border-color: rgba(139,92,246,0.4); }
  50%      { border-color: rgba(167,139,250,0.7); }
}
.plan-search-wrap {
  flex: 1; min-width: 0;
}
.plan-search-wrap :deep(.edge-wrap) {
  background: transparent;
  padding: 6px 0;
  border-radius: 0;
  box-shadow: none;
}
.plan-search-wrap :deep(.edge-inp) {
  font-size: 14px;
  color: var(--text-primary);
}
.plan-search-wrap :deep(.edge-inp::placeholder) {
  color: var(--text-hint);
}
.plan-loc-btn {
  flex-shrink: 0;
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  border: none;
  background: rgba(139,92,246,0.08);
  border-radius: 10px;
  cursor: pointer;
  margin-left: 4px;
}

/* 热门目的地标签 */
.hot-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  overflow-x: auto;
  scrollbar-width: none;
}
.hot-tags::-webkit-scrollbar { display: none; }
.hot-tag {
  flex-shrink: 0;
  padding: 7px 14px;
  background: rgba(255,255,255,0.6);
  backdrop-filter: blur(6px) saturate(140%);
  -webkit-backdrop-filter: blur(6px) saturate(140%);
  border: 1px solid rgba(255,255,255,0.55);
  border-radius: 14px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.03);
}
.hot-tag.active {
  background: #7C3AED;
  color: #fff;
  border-color: #7C3AED;
  box-shadow: 0 2px 8px rgba(124,58,237,0.25);
}
.hot-tag:active { transform: scale(0.95); }

/* 预算/天数/人数 — 三个独立椭圆玻璃框 */
.plan-meta-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
.plan-meta-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 11px 6px;
  background: rgba(255,255,255,0.65);
  backdrop-filter: blur(8px) saturate(150%);
  -webkit-backdrop-filter: blur(8px) saturate(150%);
  border: 1.5px solid rgba(255,255,255,0.55);
  border-radius: 14px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.55);
  transition: border-color 0.3s, box-shadow 0.3s;
}
.plan-meta-item:focus-within {
  border-color: rgba(139,92,246,0.5);
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.55),
    0 0 0 3px rgba(139,92,246,0.08),
    0 0 20px rgba(139,92,246,0.1);
  animation: borderGlow 2s ease-in-out infinite;
}
.plan-meta-item.filled {
  border-color: rgba(139,92,246,0.25);
}
.plan-meta-label {
  font-size: 12px;
  color: var(--text-hint);
  flex-shrink: 0;
}
.plan-meta-input {
  width: 32px;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  text-align: center;
  padding: 0;
}
.plan-meta-input::placeholder {
  color: #CBD5E1;
  font-weight: 400;
  font-size: 13px;
}
.plan-meta-unit {
  font-size: 11px;
  color: var(--text-hint);
  font-weight: 500;
}

/* 提交按钮 */
.plan-submit {
  width: 100%;
  padding: 14px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(139, 92, 246, 0.3);
  transition: transform 0.18s;
  letter-spacing: 0.5px;
}
.plan-submit:active { transform: scale(0.97); }

/* ==================== 通用区块头 ==================== */
.sec-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.sec-title { font-size: 16px; font-weight: 700; color: var(--text-primary); }
.sec-more { font-size: 12px; color: var(--text-hint); cursor: pointer; display: flex; align-items: center; gap: 2px; }
.sec-more:active { opacity: 0.6; }

/* ==================== 热门目的地卡片 ==================== */
.dest-card {
  flex-shrink: 0; width: 120px; height: 150px; border-radius: 16px;
  overflow: hidden; position: relative; cursor: pointer;
  box-shadow: 0 2px 10px rgba(0,0,0,0.06);
}
.dest-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.dest-mask { position: absolute; inset: 0; background: linear-gradient(transparent 45%, rgba(0,0,0,0.5)); }
.dest-name { position: absolute; bottom: 12px; left: 12px; font-size: 15px; font-weight: 700; color: #fff; }

/* ==================== 骨架屏 ==================== */
.skeleton-card {
  background: #fff;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(139, 92, 246, 0.06);
}
.sk-img-wrap {
  width: 100%;
  height: 95px;
}
.sk-img {
  width: 100%;
  height: 100%;
  border-radius: 0;
  background: linear-gradient(90deg, #f0f0f0 25%, #f8f8f8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}
.sk-body {
  padding: 12px;
}
.sk-row {
  height: 12px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #f8f8f8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  margin-bottom: 8px;
}
.sk-row:last-child {
  margin-bottom: 0;
}
.sk-row-title {
  width: 70%;
  height: 14px;
}
.sk-row-desc {
  width: 85%;
}
.sk-row-meta {
  width: 60%;
  height: 10px;
}
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

/* ==================== 携程风格：优质游记（复刻社区功能） ==================== */
.ctrip-section {
  width: 100%;
  max-width: 480px;
  margin: 0 auto 14px;
  padding: 0 10px;
  box-sizing: border-box;
  background: transparent;
}

/* 标题栏 */
.ctrip-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 16px 12px;
}
.ctrip-section-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}
.ctrip-section-more {
  font-size: 13px;
  color: var(--text-secondary);
}

/* 导航筛选栏 */
.ctrip-nav-filter {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  gap: 10px;
  border-bottom: 1px solid rgba(139, 92, 246, 0.06);
}

.ctrip-nav-filter .ctrip-city-selector {
  display: flex; align-items: center; gap: 5px;
  background: #f8fafc; border-radius: 16px; padding: 8px 12px;
  flex-shrink: 0; cursor: pointer; transition: all 0.2s ease;
}

.ctrip-nav-filter .ctrip-city-selector:active { transform: scale(0.96); background: #f1f5f9; }
.ctrip-nav-filter .ctrip-city-text { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.ctrip-nav-filter .ctrip-center-tabs {
  flex: 1; display: flex; justify-content: center; gap: 6px;
  background: #f8fafc; border-radius: 16px; padding: 4px;
}
.ctrip-nav-filter .ctrip-tab-chip {
  flex: 1; text-align: center; padding: 6px 0; border-radius: 13px;
  font-size: 13px; font-weight: 500; color: var(--text-secondary);
  cursor: pointer; transition: all 0.3s ease; user-select: none;
}
.ctrip-nav-filter .ctrip-tab-chip.active {
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  color: #fff; box-shadow: 0 3px 10px rgba(139, 92, 246, 0.3);
}
.ctrip-nav-filter .ctrip-search-btn {
  display: flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; background: #f8fafc; border-radius: 16px;
  flex-shrink: 0; cursor: pointer; transition: all 0.2s ease;
}
.ctrip-nav-filter .ctrip-search-btn:active { transform: scale(0.92); }

/* 笔记 Feed - 双列瀑布流 */
.ctrip-feed {
  padding: 10px 0 16px;
  display: flex;
  gap: 8px;
}

.ctrip-feed-column {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 宣传轮播卡片 - 缩小高度实现左高右低 */
.ctrip-promotion-card {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.ctrip-promotion-card .van-swipe {
  height: 150px;
  border-radius: 12px;
  overflow: hidden;
}
.ctrip-promo-slide {
  position: relative;
  width: 100%;
  height: 100%;
}
.ctrip-promo-img {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ctrip-promo-mask {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60%;
  background: linear-gradient(transparent, rgba(0,0,0,0.7));
}
.ctrip-promo-tag {
  position: absolute;
  top: 8px;
  left: 8px;
  background: rgba(255, 255, 255, 0.95);
  color: #f59e0b;
  font-size: 10px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 6px;
  backdrop-filter: blur(4px);
}
.ctrip-promo-title {
  position: absolute;
  bottom: 24px;
  left: 10px;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}
.ctrip-promo-subtitle {
  position: absolute;
  bottom: 8px;
  left: 10px;
  color: rgba(255,255,255,0.85);
  font-size: 11px;
}

/* Feed底部提示 */
.ctrip-feed-footer {
  padding: 16px;
  text-align: center;
}

/* 骨架屏 */
.ctrip-skeleton-card {
  background: #fff; border-radius: 12px; overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.ctrip-skeleton-card .ctrip-sk-image {
  width: 100%;
  aspect-ratio: var(--aspect, 3/4);
  background: linear-gradient(90deg, #f0f0f0 25%, #f8f8f8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}
.ctrip-sk-info { padding: 10px; }

/* 笔记卡片 */
.ctrip-note-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  display: flex;
  flex-direction: column;
}
.ctrip-note-card:active { transform: scale(0.97); }

/* 图片区域 - 自然宽高比错落 */
.ctrip-card-image-wrapper {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: #1a1a1a; /* 视频加载时提供暗色背景，避免白屏闪烁 */
}
.ctrip-card-image-wrapper.aspect-3-4 { aspect-ratio: 3 / 4; }
.ctrip-card-image-wrapper.aspect-4-5 { aspect-ratio: 4 / 5; }
.ctrip-card-image-wrapper.aspect-1-1 { aspect-ratio: 1 / 1; }
.ctrip-card-image-wrapper.aspect-4-3 { aspect-ratio: 4 / 3; }
.ctrip-card-image-wrapper.aspect-3-2 { aspect-ratio: 3 / 2; }
.ctrip-card-image-wrapper.aspect-2-3 { aspect-ratio: 2 / 3; }
.ctrip-card-image-wrapper.aspect-5-3 { aspect-ratio: 5 / 3; }

.ctrip-card-main-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
/* 视频播放按钮（叠在封面图右上角） */
.ctrip-video-play-overlay {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  backdrop-filter: blur(4px);
  z-index: 2;
}
.ctrip-card-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.ctrip-card-placeholder::after {
  content: '📷';
  font-size: 28px;
  opacity: 0.4;
}

/* 图片标签（城市/种草标签） */
.ctrip-card-tag {
  position: absolute;
  bottom: 8px;
  left: 8px;
  background: rgba(255, 255, 255, 0.9);
  color: #334155;
  font-size: 10px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 10px;
  backdrop-filter: blur(4px);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  z-index: 2;
}

/* 卡片内容 */
.ctrip-card-body {
  padding: 10px 10px 8px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ctrip-card-title {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-weight: 600;
}

/* 卡片底部（作者+浏览量） */
.ctrip-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

/* 作者信息 */
.ctrip-card-author {
  display: flex;
  align-items: center;
  gap: 5px;
  flex: 1;
  min-width: 0;
}
.ctrip-card-author span {
  font-size: 11px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 浏览量 */
.ctrip-card-views {
  display: flex;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
}
.ctrip-card-views span {
  font-size: 10px;
  color: var(--text-hint);
}

/* 加载更多 */
.ctrip-loading-more, .ctrip-no-more { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 20px 0; font-size: 13px; color: var(--text-hint); }
.ctrip-no-more { font-size: 12px; }

/* Vant 组件覆盖 */
.ctrip-comment-field :deep(.van-field__control) { font-size: 13px; color: #334155; }
.ctrip-comment-field :deep(.van-field__control::placeholder) { color: var(--text-hint); }
.ctrip-comment-field :deep(.van-cell) { padding: 0 !important; }
.ctrip-skeleton-card :deep(.van-skeleton) { padding: 6px 0; }

/* ==================== 美食玩乐 ==================== */
.exp-chip {
  flex-shrink: 0; display: flex; align-items: center; gap: 8px;
  padding: 10px 18px; background: #fff; border-radius: 25px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03); cursor: pointer;
  transition: transform 0.2s; font-size: 13px; color: #475569; font-weight: 500;
}
.exp-chip:active { transform: scale(0.95); }
.exp-chip-icon {
  width: 36px; height: 36px; display: flex; align-items: center;
  justify-content: center; border-radius: 10px;
}

/* ==================== LAYER 7: Bottom Floating AI Input Bar — 高级磨砂玻璃 ==================== */
/* FAB — AI闪电按钮 */
.fab-ai-btn {
  position: fixed;
  bottom: calc(10px + 48px + 16px + var(--safe-area-bottom, 0px));
  right: 16px;
  z-index: 500;
  width: 48px; height: 48px;
  border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.55);
  background: rgba(255,255,255,0.65);
  backdrop-filter: blur(18px) saturate(180%);
  -webkit-backdrop-filter: blur(18px) saturate(180%);
  box-shadow: 0 0 24px rgba(139,92,246,0.3), 0 0 48px rgba(139,92,246,0.12);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.25s;
}
.fab-ai-btn:active { transform: scale(0.9); }

/* FAB 动画 */
.fab-pop-enter-active { transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1); }
.fab-pop-leave-active { transition: all 0.25s cubic-bezier(0.4, 0, 1, 1); }
.fab-pop-enter-from { opacity: 0; transform: scale(0.3) translateY(20px); }
.fab-pop-leave-to   { opacity: 0; transform: scale(0.5) translateY(30px); }

/* ==================== Bottom Spacer ==================== */
.bottom-spacer {
  height: 8px;
}

/* ==================== More Products Popup ==================== */
.more-popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 20px 10px;
}

.more-popup-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.more-popup-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px 8px;
  padding: 10px 20px 30px;
}

.more-popup-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: transform 0.15s;
}
.more-popup-item:active {
  transform: scale(0.92);
}

.more-popup-icon {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
}

.more-popup-label {
  font-size: 11px;
  color: #475569;
  font-weight: 500;
  text-align: center;
}

/* ==================== Vant 覆盖 ==================== */
:deep(.van-swipe__indicators) { bottom: 14px; }
:deep(.van-swipe__indicator) { width: 6px; height: 6px; opacity: 0.5; }
:deep(.van-swipe__indicator--active) { width: 18px; border-radius: 3px; opacity: 1; }
:deep(.van-picker-column) { touch-action: pan-y; overflow-y: auto; }

/*
 * ================================================================
 * 首页专属动效（追加拿满，不动原有样式）
 * ================================================================
 */

/* ---------- 云端粒子层 ---------- */
.clouds-layer {
  position: fixed; inset: 0; z-index: 0; pointer-events: none; overflow: hidden;
}
.cloud-dot {
  position: absolute; border-radius: 50%;
  background: rgba(139, 92, 246, 0.08);
  /* animation 已禁用 — 6个fixed粒子持续translate导致GPU过载 */
}
.c1 { width: 60px; height: 60px; top: 12%; left: 5%; animation-duration: 24s; animation-delay: 0s; }
.c2 { width: 40px; height: 40px; top: 25%; right: 10%; animation-duration: 30s; animation-delay: -6s; background: rgba(99,102,241,0.06); }
.c3 { width: 80px; height: 80px; top: 50%; left: 70%; animation-duration: 36s; animation-delay: -12s; }
.c4 { width: 30px; height: 30px; top: 65%; left: 15%; animation-duration: 20s; animation-delay: -3s; background: rgba(167,139,250,0.07); }
.c5 { width: 50px; height: 50px; top: 78%; right: 25%; animation-duration: 28s; animation-delay: -18s; }
.c6 { width: 35px; height: 35px; top: 40%; left: 35%; animation-duration: 22s; animation-delay: -9s; background: rgba(139,92,246,0.05); }

/* hero-header 不设 animation 避免覆盖 entrance-item 的 entranceUp */

/* ---------- 圆形图标常驻呼吸 + hover发光 ---------- */
.service-icon-circle {
  /* animation 已禁用 — 5个图标同时呼吸动画导致GPU持续负载 */
  transition: transform 0.35s ease, box-shadow 0.35s ease;
}
.service-item:hover .service-icon-circle {
  transform: scale(1.12);
  box-shadow: 0 0 20px rgba(139,92,246,0.2);
}

/* ---------- 活动卡片上下悬浮 ---------- */
.float-card {
  animation: floatUpDown 4s ease-in-out infinite;
  animation-delay: 0.8s; /* 等 entranceUp 播完再开始浮动 */
}

/* ---------- 底部AI悬浮栏麦克风脉冲 ---------- */
.float-mic-icon {
  animation: pulseGlow 2.2s ease-in-out infinite;
}

/* ---------- 输入框聚焦扫光（搜索栏） ---------- */
.search-row :deep(input):focus {
  background: linear-gradient(90deg, transparent 0%, rgba(139,92,246,0.06) 50%, transparent 100%);
  background-size: 200% 100%;
  animation: inputShimmer 2s ease-in-out infinite;
}

/* ---------- 热门目标卡片hover上浮 ---------- */
.dest-card {
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.35s ease;
}
.dest-card:hover { transform: translateY(-6px); box-shadow: 0 12px 28px rgba(0,0,0,0.10); }
.dest-card:active { transform: scale(0.95); }

/* ---------- 横向标签顺滑滚动缓冲 ---------- */
.city-tags, .h-scroll {
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
}

/* ==================== Responsive ==================== */
@media (max-width: 375px) {
  .hero-title { font-size: 38px; }
  .service-grid {
    padding: 16px 10px 6px;
    gap: 10px 2px;
  }
  .service-grid-row2 {
    padding: 0 10px 12px;
    gap: 8px 2px;
  }
  .service-icon-circle {
    width: 44px;
    height: 44px;
  }
  .service-icon-circle-sm {
    width: 36px;
    height: 36px;
  }
  .service-label {
    font-size: 10px;
  }
  .service-label-sm {
    font-size: 9px;
  }
  .content-card {
    margin: 0 auto 12px;
    padding: 0 16px;
  }
  .quick-tabs-card {
    margin: 0 10px;
    padding: 8px 10px;
  }
  .dual-cards-scroll {
    padding: 14px 16px;
  }
  .event-card {
    width: 170px;
    height: 115px;
  }
  .city-card {
    width: 140px;
    height: 115px;
  }
  .ai-float-bar {
    max-width: 180px;
  }
}
/* ==================== 深色模式（B4） ==================== */
html[data-theme='dark'] .section-card,
html[data-theme='dark'] .plan-card {
  background: var(--bg-card);
  border-color: var(--glass-border);
  box-shadow: var(--shadow-md);
}
html[data-theme='dark'] .ctrip-note-card {
  background: var(--bg-card-solid);
  border-color: var(--glass-border);
  box-shadow: var(--shadow-md);
}
html[data-theme='dark'] .ctrip-card-title { color: var(--text-primary); }
html[data-theme='dark'] .ctrip-card-views,
html[data-theme='dark'] .event-title { color: var(--text-secondary); }
html[data-theme='dark'] .quick-item,
html[data-theme='dark'] .dest-card { background: var(--bg-card); }
</style>
