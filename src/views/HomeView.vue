<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onActivated, onDeactivated, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showLoadingToast, Swipe, SwipeItem } from 'vant'

/*
 * 【Bug修复】显式声明组件名，供 keep-alive 的 include 白名单匹配
 * 缺失会导致 Tab 切换时组件无法命中缓存，每次销毁重建 → 空白
 */
defineOptions({ name: 'HomeView' })
import { areaList } from '@vant/area-data'
import SearchBar from '../components/SearchBar.vue'
import AIChatDialog from '../components/AIChatDialog.vue'  // 【5Tab架构】AI对话弹窗
import EmptyState from '../components/EmptyState.vue'
import { getHotDestinations } from '../api/destination'
import { noteApi, followApi, commentApi, uploadApi } from '../api'
import { getToken } from '../utils/auth'

const router = useRouter()

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
  { name: '签证', icon: 'idcard', color: '#6366F1' },
  { name: '导游', icon: 'flag-o', color: '#3B82F6' },
  { name: '游船', icon: 'guide-o', color: '#0891B2' },
  { name: 'WiFi/电话卡', icon: 'phone-o', color: '#8B5CF6' },
  { name: '保险', icon: 'shield-o', color: '#22C55E' },
  { name: '邮寄明信片', icon: 'envelop-o', color: '#F59E0B' },
  { name: '特产商城', icon: 'gift-o', color: '#EF4444' },
  { name: '旅拍', icon: 'photo-o', color: '#EC4899' },
  { name: '自驾服务', icon: 'car-o', color: '#F97316' },
  { name: '行李寄存', icon: 'bag-o', color: '#14B8A6' },
  { name: '外币兑换', icon: 'gold-coin-o', color: '#EAB308' },
  { name: '机场贵宾厅', icon: 'star-o', color: '#A855F7' },
  { name: '当地体验', icon: 'location-o', color: '#06B6D4' },
  { name: '健康体检', icon: 'first-aid', color: '#84CC16' },
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
  if (filtered && parseInt(filtered) > 50) { filtered = '50'; showToast({ message: '人数最多50人', position: 'middle', duration: 1500 }) }
  people.value = filtered
  if (e.target.value !== filtered) e.target.value = filtered
}

const handleBudgetBlur = (e) => {
  const val = e.target.value.trim()
  budget.value = val
  if (val && parseFloat(val) < 100) showToast({ message: '预算建议不低于100元', position: 'middle', duration: 1500 })
}

const handleDaysBlur = (e) => {
  const val = e.target.value.trim()
  days.value = val
  if (val && parseInt(val) < 1) { showToast({ message: '天数不能低于1天', position: 'middle', duration: 1500 }); days.value = ''; e.target.value = '' }
}

const handlePeopleBlur = (e) => {
  const val = e.target.value.trim()
  people.value = val
  if (val && (parseInt(val) < 1 || parseInt(val) > 50)) showToast({ message: '人数请输入1-50之间', position: 'middle', duration: 1500 })
}

/* ==================== 快捷入口（6宫格 — 2×3） ==================== */
const quickEntries = [
  { name: 'AI对话', icon: 'chat-o', color: '#8B5CF6', path: '/messages' },
  { name: '机票预订', icon: 'plane-o', color: '#34D399', path: '/orders' },
  { name: '酒店预订', icon: 'hotel-o', color: '#F59E0B', path: '/orders' },
  { name: '景点门票', icon: 'orders-o', color: '#FB7185', path: '/orders' },
  { name: '美食攻略', icon: 'star-o', color: '#F97316', path: '/destinations' },
  { name: '游记社区', icon: 'file-text-o', color: '#3B82F6', path: '/notes' },
]

/* ==================== Layer 2: 服务图标网格 Row 1 ==================== */
const serviceRow1 = [
  { name: '酒店', icon: 'hotel-o', color: '#8B5CF6' },
  { name: '攻略/景点', icon: 'guide-o', color: '#6366F1' },
  { name: '机票', icon: 'plane-o', color: '#3B82F6' },
  { name: '火车票', icon: 'train-o', color: '#F59E0B' },
  { name: '旅游定制', icon: 'backpack-o', color: '#34D399' },
]

/* ==================== Layer 2b: 服务图标网格 Row 2 ==================== */
const serviceRow2 = [
  { name: '民宿/客栈', icon: 'home-o', color: '#8B5CF6' },
  { name: '门票玩乐', icon: 'orders-o', color: '#F59E0B' },
  { name: '接送机', icon: 'bus-o', color: '#3B82F6' },
  { name: '租车', icon: 'car-o', color: '#F97316' },
  { name: '跟团游', icon: 'flag-o', color: '#34D399' },
]

/* ==================== Layer 5: 快捷功能标签 ==================== */
const quickTabs = [
  { name: '特价/直播', icon: 'coupon-o' },
  { name: '演出/展览', icon: 'music-o' },
  { name: '行程规划', icon: 'compass-o' },
  { name: '旅行热点', icon: 'fire-o' },
  { name: '旅游榜单', icon: 'medal-o' },
]

/* ==================== 图片API（必须定义在调用上方，const箭头函数不提升） ==================== */
const IMAGE_API = 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image'
const getImageUrl = (keyword) => `${IMAGE_API}?prompt=${encodeURIComponent(`${keyword} 风景 旅游摄影 高清`)}&image_size=landscape_4_3`
const getBannerImageUrl = (keyword) => `${IMAGE_API}?prompt=${encodeURIComponent(`${keyword} 旅游宣传海报 精美 高清`)}&image_size=landscape_16_9`

/* ==================== Layer 6: 双列卡片 ==================== */
const eventBanner = {
  image: getBannerImageUrl('旅行热门活动'),
  title: '夏日旅行季',
  label: '热门活动',
  link: '/destination-detail?city=三亚',
}
const citySeedCard = {
  image: getImageUrl('城市种草推荐'),
  label: '城市种草',
  cta: 'AI帮我规划',
}

/* ==================== 默认数据 ==================== */
const defaultBanners = [
  { id: 1, image: getBannerImageUrl('云南大理洱海旅游'), title: '云南大理', subtitle: '风花雪月，苍山洱海', link: '/destination-detail?city=大理' },
  { id: 2, image: getBannerImageUrl('西藏拉萨布达拉宫'), title: '西藏拉萨', subtitle: '日光之城，心灵之旅', link: '/destination-detail?city=拉萨' },
  { id: 3, image: getBannerImageUrl('新疆天山天池'), title: '新疆天山', subtitle: '塞外江南，大美新疆', link: '/destination-detail?city=乌鲁木齐' },
  { id: 4, image: getBannerImageUrl('三亚海滩度假天堂'), title: '海南三亚', subtitle: '热带天堂，椰风海韵', link: '/destination-detail?city=三亚' },
]

const defaultDestinations = [
  { name: '北京', tag: '经典必去', image: getImageUrl('北京故宫') },
  { name: '上海', tag: '都市潮流', image: getImageUrl('上海外滩') },
  { name: '广州', tag: '美食之都', image: getImageUrl('广州塔') },
  { name: '深圳', tag: '创新之城', image: getImageUrl('深圳城市夜景') },
  { name: '成都', tag: '网红打卡', image: getImageUrl('成都大熊猫') },
  { name: '杭州', tag: '诗画江南', image: getImageUrl('杭州西湖') },
  { name: '西安', tag: '千年古都', image: getImageUrl('西安兵马俑') },
  { name: '重庆', tag: '8D魔幻', image: getImageUrl('重庆山城夜景') },
]

const ph = (hue, label) => `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="hsl(${hue},60%,65%)"/><stop offset="100%" stop-color="hsl(${hue+30},70%,45%)"/></linearGradient></defs><rect fill="url(#g)" width="400" height="300"/><text fill="rgba(255,255,255,0.7)" font-size="28" font-family="sans-serif" x="200" y="150" text-anchor="middle" dominant-baseline="middle">${label}</text></svg>`)}`

/* ==================== 社区种子数据（fallback） ==================== */
const seedNotes = [
  {
    id: 1,
    author: { nickname: '带着娃看世界', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=family', city: '上海', isFollowing: false, online: true, userId: 'u6' },
    title: '🎠 上海迪士尼亲子二日游全攻略！轻松带娃不踩雷',
    content: '带4岁娃的上海迪士尼亲子攻略！轻松版游玩路线，避开人潮不用排长队。重点推荐旋转木马、小飞象和冰雪奇缘表演，孩子玩得超开心。附上园区儿童餐推荐和午睡tips。',
    images: [getImageUrl('上海迪士尼乐园'), getImageUrl('迪士尼亲子游玩'), getImageUrl('迪士尼城堡夜景')],
    viewCount: 12800, tag: '亲子', time: '2小时前',
    hasVideo: false,
  },
  {
    id: 2,
    author: { nickname: '贵州旅行家小杨', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=guizhou', city: '贵阳', isFollowing: true, online: false, userId: 'u11' },
    title: '🏞️ 贵州旅游超全攻略｜7天6晚玩转黔东南',
    content: '刚回来！贵州太美了！黄果树瀑布气势磅礴，荔波小七孔水绿如翡翠，西江千户苗寨万家灯火震撼心灵。这份攻略整理了吃住行全攻略，人均不到3000玩转贵州精华景点！',
    images: [getImageUrl('贵州黄果树瀑布'), getImageUrl('荔波小七孔'), getImageUrl('西江千户苗寨')],
    videoUrl: 'https://www.w3schools.com/html/mov_bbb.mp4',
    viewCount: 35600, tag: '贵州', time: '4小时前',
    hasVideo: true,
  },
  {
    id: 3,
    author: { nickname: '历史迷小王', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=history', city: '成都', isFollowing: false, online: true, userId: 'u12' },
    title: '📜 都江堰景区一日游｜千年水利工程太震撼了',
    content: '都江堰到底值不值得去？答案是：绝对值得！亲眼看到两千多年前李冰父子修建的水利工程至今仍在发挥作用，鱼嘴分水、飞沙堰溢洪、宝瓶口引水，古人的智慧让人叹服。附上门票交通全攻略。',
    images: [getImageUrl('都江堰景区'), getImageUrl('都江堰水利工程'), getImageUrl('都江堰南桥')],
    viewCount: 22100, tag: '都江堰', time: '6小时前',
    hasVideo: false,
  },
  {
    id: 4,
    author: { nickname: '酒店控小鹿', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=hotellover', city: '上海', isFollowing: false, online: false, userId: 'u13' },
    title: '🏨 上海外滩周边高性价比酒店推荐｜睡在风景里',
    content: '整理了上海外滩/南京路周边5家高性价比酒店，从网红民宿到五星级酒店都有实测。关键看江景、交通便利度和性价比。和平饭店的下午茶、华尔道夫的老上海风情，每一家都有独特体验！',
    images: [getImageUrl('上海外滩酒店'), getImageUrl('上海酒店江景'), getImageUrl('上海豪华酒店')],
    viewCount: 18900, tag: '上海', time: '8小时前',
    hasVideo: false,
  },
  {
    id: 5,
    author: { nickname: '贵阳本地通', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=guiyang', city: '贵阳', isFollowing: true, online: true, userId: 'u14' },
    title: '🌄 贵阳周边绝美风景｜本地人私藏的小众打卡地',
    content: '贵阳不止有甲秀楼！花溪十里河滩骑行、青岩古镇品猪蹄、天河潭看溶洞瀑布、黔灵山看野生猕猴…这些本地人常去的地方才是贵阳的正确打开方式。美食推荐：肠旺面、丝娃娃、酸汤鱼，好吃到哭！',
    images: [getImageUrl('贵阳花溪公园'), getImageUrl('青岩古镇'), getImageUrl('贵阳黔灵山')],
    viewCount: 15200, tag: '贵阳', time: '12小时前',
    hasVideo: false,
  },
  {
    id: 6,
    author: { nickname: '背包客阿飞', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=backpack', city: '大理', isFollowing: true, online: true, userId: 'u5' },
    title: '🌾 大理旅居一个月｜环洱海自驾保姆级攻略',
    content: '大理旅居一个月，整理了这份环洱海自驾攻略。路线：古城-喜洲-双廊-挖色-海东。全程130公里，建议分两天慢慢玩。喜洲的稻田、双廊的日落、海东的悬崖公路，每一段都让人不想离开。',
    images: [getImageUrl('大理洱海'), getImageUrl('大理喜洲古镇'), getImageUrl('大理双廊日落')],
    viewCount: 45600, tag: '大理', time: '1天前',
    hasVideo: false,
  },
  {
    id: 7,
    author: { nickname: '吃货小分队', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=foodie', city: '成都', isFollowing: true, online: false, userId: 'u2' },
    title: '🍲 重庆本地人私藏的火锅地图｜这12家必须吃',
    content: '避开网红店，整理了12家藏在居民楼里的老火锅。每家都有特色招牌菜，从人均40到80都有。特别推荐弹子石的巷子火锅和观音桥的防空洞火锅，麻辣鲜香巴适得板！建议收藏！',
    images: [getImageUrl('重庆火锅'), getImageUrl('重庆洪崖洞'), getImageUrl('重庆夜景')],
    videoUrl: 'https://media.w3.org/2010/05/sintel/trailer.mp4',
    viewCount: 38900, tag: '重庆', time: '1天前',
    hasVideo: true,
  },
  {
    id: 8,
    author: { nickname: '摄影师Mr陈', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=photoc', city: '杭州', isFollowing: false, online: false, userId: 'u15' },
    title: '📸 杭州西湖边的绝美咖啡馆合集｜拍照超出片',
    content: '整理了环西湖最值得去的5家独立咖啡馆，从断桥边的民国老宅到龙井山上的玻璃房。每一家都有独特的设计美学和出品，附上每家的推荐饮品和最佳拍摄机位，文艺青年必收藏！',
    images: [getImageUrl('杭州西湖'), getImageUrl('杭州咖啡馆'), getImageUrl('龙井茶园')],
    viewCount: 27300, tag: '杭州', time: '2天前',
    hasVideo: false,
  },
  {
    id: 9,
    author: { nickname: '户外探险家', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=hiker', city: '西安', isFollowing: false, online: true, userId: 'u3' },
    title: '⛰️ 华山一日游挑战长空栈道｜云海翻涌太震撼',
    content: '早上5点出发，索道上北峰，一路徒步经过苍龙岭、金锁关，最后挑战长空栈道。虽然腿软但风景绝美，云海翻涌，值得一生铭记的体验。附登山装备清单和体力分配建议！',
    images: [getImageUrl('华山长空栈道'), getImageUrl('华山日出'), getImageUrl('华山云海')],
    viewCount: 31200, tag: '华山', time: '2天前',
    hasVideo: false,
  },
  {
    id: 10,
    author: { nickname: '旅行者小明', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=ming', city: '深圳', isFollowing: false, online: true, userId: 'u1' },
    title: '🚴 深圳湾公园骑行｜沿海岸线看绝美日落',
    content: '深圳湾公园骑行真的太舒服了！沿着海岸线一路骑行，海风轻拂，视野开阔。推荐傍晚时分出发，可以看到绝美的海上日落，沿途还有很多拍照打卡点。全程15公里左右，新手也完全能驾驭。',
    images: [getImageUrl('深圳湾公园'), getImageUrl('深圳湾日落'), getImageUrl('深圳海滨骑行')],
    viewCount: 19500, tag: '深圳', time: '3天前',
    hasVideo: false,
  },
]

const defaultExperiences = [
  { id: 1, name: '过桥米线', icon: 'food-o', color: '#FCA5A5' },
  { id: 2, name: '四川火锅', icon: 'flower-o', color: '#EF4444' },
  { id: 3, name: '潜水体验', icon: 'guide-o', color: '#3B82F6' },
  { id: 4, name: '滑雪运动', icon: 'photo-o', color: '#60A5FA' },
  { id: 5, name: '温泉度假', icon: 'smile-o', color: '#FB923C' },
  { id: 6, name: '徒步旅行', icon: 'flag-o', color: '#22C55E' },
]

/* ==================== 响应式数据 ==================== */
const banners = ref([])
const hotDestinations = ref([])
const experiences = ref([])

const isLoading = ref({ destinations: true, notes: true })

const loadBanners = () => { banners.value = defaultBanners }
const loadHotDestinations = async () => {
  isLoading.value.destinations = true
  try {
    const res = await getHotDestinations()
    const list = res.data || []
    hotDestinations.value = list.length ? list.map(item => ({ ...item, image: item.imageUrl || getImageUrl(item.name), tag: item.tag || '热门推荐' })) : defaultDestinations
  } catch (e) {
    hotDestinations.value = defaultDestinations
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

// 携程风瀑布流：左列轮播卡片做小 → 左列紧凑更高，右列正常
const displayNotes = computed(() => notes.value.slice(0, 10))

// 【修复】左列：偶数索引（包含0号，原逻辑丢弃索引0导致首条视频不显示）
// 推广轮播作为左列顶部额外卡片，不挤占任何一条笔记
const leftColumnNotes = computed(() => displayNotes.value.filter((_, index) => index % 2 === 0))

// 右列：奇数索引
const rightColumnNotes = computed(() => displayNotes.value.filter((_, index) => index % 2 === 1))

const goToCommunity = () => {
  try { router.push('/notes') } catch (e) { console.error('goToCommunity 失败:', e) }
}

const promotionSlides = ref([
  { image: getImageUrl('亲子游乐园'), title: '亲子出游季', subtitle: '带娃玩转全国乐园攻略', tag: '👨‍👩‍👧 亲子' },
  { image: getImageUrl('贵州黄果树瀑布'), title: '贵州深度游', subtitle: '7-8月避暑胜地全攻略', tag: '💦 避暑胜地' },
  { image: getImageUrl('都江堰景区'), title: '都江堰', subtitle: '千年水利工程值不值得去？', tag: '📜 历史文化' },
])

const loadNotes = async (reset = false) => {
  if (reset) {
    page.value = 1
    hasMore.value = true
    notesLoading.value = true
  }
  if (!hasMore.value && !reset) return
  try {
    loadingMore.value = !reset
    // 【修复】社区板块应加载所有用户的游记，而非仅当前用户的
    const res = await noteApi.getAllNotes()
    if (res && res.code === 0 && res.data) {
      const list = Array.isArray(res.data) ? res.data : (res.data.records || res.data.list || [])
      const mapped = list.map(mapNoteItem)
      if (reset) {
        notes.value = mapped
      } else {
        notes.value = [...notes.value, ...mapped]
      }
      hasMore.value = list.length >= 10
      page.value += 1
    } else {
      if (reset) notes.value = [...seedNotes]
      hasMore.value = false
    }
  } catch (e) {
    console.warn('加载社区笔记失败，使用本地种子数据:', e.message)
    if (reset) notes.value = [...seedNotes]
    hasMore.value = false
  } finally {
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
  const authorNickname = isSeedData ? item.author.nickname : (item.authorName || item.nickname || '旅行者')
  const authorAvatar = isSeedData ? item.author.avatar : (item.authorAvatar || item.avatar || `https://api.dicebear.com/7.x/avataaars/svg?seed=${item.id || 'fallback'}`)
  const authorCity = isSeedData ? item.author.city : (item.city || '')
  const authorIsFollowing = isSeedData ? item.author.isFollowing : (item.isFollowing || false)
  const authorOnline = isSeedData ? item.author.online : (item.online !== undefined ? item.online : Math.random() > 0.4)
  const authorUserId = isSeedData ? item.author.userId : (item.userId || item.authorId || item.id)
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
    time: item.time || item.date || item.createTime || '刚刚',
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
    showToast({ message: '请先登录', position: 'middle', duration: 1500 })
    return
  }
  const prevLiked = note.isLiked
  note.isLiked = !note.isLiked
  note.likeCount += note.isLiked ? 1 : -1
  try {
    const res = await noteApi.likeNote(note.id)
    if (res.code !== 0) throw new Error(res.message)
  } catch (e) {
    note.isLiked = prevLiked
    note.likeCount += prevLiked ? 1 : -1
    showToast({ message: '操作失败，请重试', position: 'middle', duration: 1500 })
  }
}

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

const handleCommunitySearch = () => {
  showToast({ message: '搜索功能开发中', position: 'middle', duration: 1500 })
}

const onTabChange = (key) => {
  activeTab.value = key
  if (key === 'following') {
    const token = getToken()
    if (!token) {
      showToast({ message: '请先登录查看关注动态', position: 'middle', duration: 1500 })
      activeTab.value = 'all'
      return
    }
  }
}

/* ==================== 事件 ==================== */
const startPlanning = () => {
  try {
    if (!destination.value || String(destination.value).trim() === '') return showToast({ message: '请输入目的地', position: 'middle' })
    if (!budget.value || String(budget.value).trim() === '') return showToast({ message: '请输入预算', position: 'middle' })
    if (!days.value || String(days.value).trim() === '') return showToast({ message: '请输入天数', position: 'middle' })
    if (Number(days.value) < 1) return showToast({ message: '天数不能低于1天', position: 'middle' })
    if (Number(budget.value) < 100) return showToast({ message: '预算不能低于100元', position: 'middle' })
    if (!people.value || String(people.value).trim() === '') return showToast({ message: '请输入人数', position: 'middle' })
    if (Number(people.value) < 1 || Number(people.value) > 50) return showToast({ message: '人数请输入1-50之间', position: 'middle' })
    showLoadingToast({ message: 'AI正在规划...', duration: 500, forbidClick: true, loadingType: 'spinner' })
    setTimeout(() => router.push({ path: '/trip-map', query: { destination: destination.value, budget: budget.value, days: days.value, people: people.value } }), 500)
  } catch (e) { console.error('startPlanning 失败:', e); showToast({ message: '操作失败，请重试', position: 'middle' }) }
}

const handleQuickEntry = (entry) => {
  try {
    if (entry?.path) router.push(entry.path)
    else showToast({ message: '功能开发中', position: 'middle' })
  } catch (e) { console.error('handleQuickEntry 失败:', e) }
}

/* 头部按钮点击：会员 / 积分 */
const handleHeaderBtn = (type) => {
  try {
    if (type === 'vip') router.push('/profile')
    else showToast({ message: '积分功能开发中', position: 'middle' })
  } catch (e) { console.error('handleHeaderBtn 失败:', e) }
}
/*
 * 【修复】热门目的地卡片点击 → 跳转城市详情页
 * 根因：此前只设置 destination.value = dest.name，页面无任何可见变化，用户以为点击无效
 */
const handleDestination = (dest) => {
  try {
    if (!dest || !dest.name) { showToast({ message: '目的地信息异常', position: 'middle' }); return }
    router.push({ path: '/destination-detail', query: { city: dest.name } })
  } catch (e) { console.error('handleDestination 跳转失败:', e); showToast({ message: '跳转失败', position: 'middle' }) }
}
const goToDestinations = () => {
  try { router.push('/destinations') } catch (e) { console.error('goToDestinations 失败:', e) }
}
const handleSearchSelect = (item) => { if (item?.name) destination.value = item.name }
const handleBannerClick = (banner) => {
  try {
    if (!banner || !banner.link) { showToast({ message: '活动信息异常', position: 'middle' }); return }
    router.push(banner.link)
  } catch (e) { console.error('handleBannerClick 失败:', e) }
}
const handleExperienceClick = (exp) => {
  try { showToast({ message: `${exp?.name || '该功能'}开发中`, position: 'middle' }) } catch (e) {}
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
      '酒店': '/orders',
      '攻略/景点': '/destinations',
      '机票': '/orders',
      '火车票': '/orders',
      '旅游定制': '/messages',
      '民宿/客栈': '/orders',
      '门票玩乐': '/orders',
      '接送机': '/orders',
      '租车': '/orders',
      '跟团游': '/orders',
    }
    if (routes[item?.name]) {
      router.push(routes[item.name])
    } else {
      showToast({ message: '功能开发中', position: 'middle' })
    }
  } catch (e) { console.error('handleServiceClick 失败:', e) }
}

/* Layer 5: 快捷功能标签点击 */
const handleQuickTab = (tab) => {
  try {
    if (tab?.name === '行程规划') { goToAIChat() }
    else { showToast({ message: '功能开发中', position: 'middle' }) }
  } catch (e) { console.error('handleQuickTab 失败:', e) }
}

/* Layer 6: 双列卡片点击 */
const handleEventBannerClick = () => {
  try { router.push(eventBanner.link) } catch (e) { console.error('handleEventBannerClick 失败:', e) }
}

const handleCitySeedClick = () => {
  try { goToAIChat() } catch (e) { console.error('handleCitySeedClick 失败:', e) }
}

/* Layer 3: 更多产品点击 */
const handleMoreProductClick = (product) => {
  try { showToast({ message: `${product?.name || '该功能'}开发中`, position: 'middle' }) } catch (e) {}
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

const addWheelListeners = () => {
  setTimeout(() => {
    const popup = document.querySelector('.van-popup')
    if (popup) { const picker = popup.querySelector('.van-picker'); if (picker) { const handler = (e) => handlePickerWheel(e); popup.addEventListener('wheel', handler, { passive: false }); wheelHandlers.value.push({ column: popup, handler }) } }
  }, 500)
}

const removeWheelListeners = () => { wheelHandlers.value.forEach(({ column, handler }) => column.removeEventListener('wheel', handler)); wheelHandlers.value = [] }
watch(showCityPicker, (newVal) => { newVal ? addWheelListeners() : removeWheelListeners() })

/* ==================== 滚动触底加载（和社区页一致） ==================== */
const handleScroll = () => {
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const scrollHeight = document.documentElement.scrollHeight
  const clientHeight = window.innerHeight
  if (scrollHeight - scrollTop - clientHeight < 200 && hasMore.value && !loadingMore.value) {
    loadNotes()
  }
}

let hasLoadedOnce = false

onMounted(() => {
  loadBanners(); loadHotDestinations(); loadExperiences()
  loadNotes(true).then(() => { hasLoadedOnce = true })
  window.addEventListener('scroll', handleScroll, { passive: true })
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
})
</script>

<template>
  <div class="page-shell">

    <!-- 漂浮云朵粒子（纯氛围，不挡点击） -->
    <div class="clouds-layer" aria-hidden="true">
      <span class="cloud-dot c1"></span><span class="cloud-dot c2"></span><span class="cloud-dot c3"></span>
      <span class="cloud-dot c4"></span><span class="cloud-dot c5"></span><span class="cloud-dot c6"></span>
    </div>

    <!-- ==================== LAYER 1: Hero Header ==================== -->
    <div class="hero-header entrance-item entrance-d1">
      <!-- 背景装饰 -->
      <div class="hero-bg-decor">
        <svg class="hero-svg" viewBox="0 0 400 200" preserveAspectRatio="none">
          <ellipse cx="350" cy="30" rx="120" ry="80" fill="rgba(255,255,255,0.06)" />
          <ellipse cx="50" cy="140" rx="100" ry="70" fill="rgba(255,255,255,0.04)" />
          <circle cx="330" cy="160" r="40" fill="rgba(255,255,255,0.03)" />
        </svg>
      </div>

      <div class="hero-top-row">
        <div class="brand">
          <span class="brand-text">智能旅游助手</span>
        </div>
        <div class="header-btns">
          <button class="hdr-btn" @click="handleHeaderBtn('vip')">
            <van-icon name="vip-card-o" size="15" />
            <span>会员</span>
          </button>
          <button class="hdr-btn" @click="handleHeaderBtn('points')">
            <van-icon name="gold-coin-o" size="15" />
            <span>积分</span>
          </button>
        </div>
      </div>

      <!-- SearchBar -->
      <div class="search-row">
        <SearchBar v-model="destination" @select="handleSearchSelect" />
      </div>
    </div>

    <!-- ==================== LAYER 2: Service Icon Grid Row 1 (5 cols) ==================== -->
    <div class="service-grid entrance-item entrance-d2">
      <div
        v-for="(item, idx) in serviceRow1"
        :key="'s1-' + idx"
        class="service-item"
        @click="handleServiceClick(item)"
      >
        <div class="service-icon-circle" :style="{ background: `${item.color}18` }">
          <van-icon :name="item.icon" :color="item.color" size="24" />
        </div>
        <span class="service-label">{{ item.name }}</span>
      </div>
    </div>

    <!-- ==================== LAYER 2b: Service Icon Grid Row 2 (5 cols) ==================== -->
    <div class="service-grid-row2">
      <div
        v-for="(item, idx) in serviceRow2"
        :key="'s2-' + idx"
        class="service-item-sm"
        @click="handleServiceClick(item)"
      >
        <div class="service-icon-circle-sm" :style="{ background: `${item.color}14` }">
          <van-icon :name="item.icon" :color="item.color" size="20" />
        </div>
        <span class="service-label-sm">{{ item.name }}</span>
      </div>
    </div>

    <!-- ==================== LAYER 3: More Products Collapsible Bar ==================== -->
    <div class="more-products-bar" @click="showMoreProducts = true">
      <div class="more-products-left">
        <div class="mini-icon-row">
          <span class="mini-icon" style="background:#ede9fe;color:#8B5CF6;">签</span>
          <span class="mini-icon" style="background:#dbeafe;color:#3B82F6;">导</span>
          <span class="mini-icon" style="background:#fef3c7;color:#F59E0B;">W</span>
          <span class="mini-icon" style="background:#d1fae5;color:#34D399;">保</span>
        </div>
        <span class="more-products-text">+14个更多产品</span>
      </div>
      <van-icon name="arrow" size="14" color="#94A3B8" />
    </div>

    <!-- ==================== LAYER 4: City Quick Tags ==================== -->
    <div class="city-tags-section">
      <div class="city-tags">
        <span
          v-for="(city, idx) in cityQuickTags"
          :key="'city-' + idx"
          class="city-tag-chip"
          @click="handleCityTagClick(city)"
        >{{ city }}</span>
      </div>
    </div>

    <!-- ==================== LAYER 5: Quick Function Tabs ==================== -->
    <div class="quick-tabs-card">
      <button
        v-for="(tab, idx) in quickTabs"
        :key="'tab-' + idx"
        class="quick-tab-btn"
        :class="{ 'quick-tab-primary': tab.name === '行程规划' }"
        @click="handleQuickTab(tab)"
      >
        <van-icon :name="tab.icon" size="16" />
        <span>{{ tab.name }}</span>
      </button>
    </div>

    <!-- ==================== LAYER 6: Dual-Column Horizontal Scroll Cards ==================== -->
    <div class="dual-cards-scroll entrance-item entrance-d3">
      <div class="event-card float-card" @click="handleEventBannerClick">
        <img :src="eventBanner.image" :alt="eventBanner.title" class="event-img" loading="lazy" decoding="async" @error="e=>e.target.style.opacity='0'" />
        <div class="event-overlay">
          <span class="event-badge">{{ eventBanner.label }}</span>
          <span class="event-title">{{ eventBanner.title }}</span>
        </div>
      </div>
      <div class="city-card" @click="handleCitySeedClick">
        <img :src="citySeedCard.image" :alt="citySeedCard.label" class="city-img" loading="lazy" decoding="async" @error="e=>e.target.style.opacity='0'" />
        <div class="city-overlay">
          <span class="city-badge">{{ citySeedCard.label }}</span>
          <span class="city-cta">{{ citySeedCard.cta }}</span>
        </div>
      </div>
    </div>

    <!-- ==================== EXISTING: Banner 轮播 ==================== -->
    <div class="content-card banner-wrap">
      <Swipe class="banner-swipe" :autoplay="4000" indicator-color="rgba(255,255,255,0.5)" indicator-active-color="#ffffff" :circular="true">
        <SwipeItem v-for="banner in banners" :key="'bn-' + banner.id" class="banner-slide" @click="handleBannerClick(banner)">
          <img :src="banner.image" :alt="banner.title" class="banner-img" loading="lazy" decoding="async" @error="e=>e.target.style.opacity='0'" />
          <div class="banner-info">
            <span class="banner-name">{{ banner.title }}</span>
            <span class="banner-slogan">{{ banner.subtitle }}</span>
          </div>
        </SwipeItem>
      </Swipe>
    </div>

    <!-- ==================== EXISTING: AI 一键规划行程 ==================== -->
    <div class="content-card plan-card">
      <div class="plan-header">
        <div class="plan-title-row">
          <span class="plan-icon">🧭</span>
          <span class="plan-title">AI 一键规划行程</span>
        </div>
        <span class="plan-badge">✨ AI智能推荐</span>
      </div>
      <!-- 热门目的地快捷标签 -->
      <div class="hot-tags">
        <span
          v-for="tag in hotTags" :key="'ht-' + tag"
          class="hot-tag"
          :class="{ active: destination === tag }"
          @click="selectHotTag(tag)"
        >{{ tag }}</span>
      </div>
      <!-- 表单：2列网格 -->
      <div class="plan-form">
        <div class="plan-field plan-field-full" @click="openCityPicker">
          <label class="plan-label">📍 目的地</label>
          <input v-model="destination" type="text" placeholder="选择或输入目的地" class="plan-input" readonly />
          <van-icon name="arrow" size="14" color="#94A3B8" />
        </div>
        <div class="plan-row">
          <div class="plan-field plan-half">
            <label class="plan-label">💰 预算(元)</label>
            <input ref="budgetInputRef" :value="budget || undefined" type="text" inputmode="decimal" placeholder="如3000" class="plan-input" @input="handleBudgetInput" @blur="handleBudgetBlur" />
          </div>
          <div class="plan-field plan-half">
            <label class="plan-label">📅 天数</label>
            <input ref="daysInputRef" :value="days" type="text" inputmode="numeric" placeholder="如5" class="plan-input" @input="handleDaysInput" @blur="handleDaysBlur" />
          </div>
        </div>
        <div class="plan-field">
          <label class="plan-label">👥 人数</label>
          <input ref="peopleInputRef" :value="people" type="text" inputmode="numeric" placeholder="出行人数" class="plan-input" @input="handlePeopleInput" @blur="handlePeopleBlur" />
        </div>
      </div>
      <button class="plan-submit btn-tap-scale" @click="startPlanning">
        <van-icon name="compass-o" size="20" />
        <span>开始规划</span>
      </button>
    </div>

    <!-- ==================== EXISTING: 快捷入口 6宫格 ==================== -->
    <div class="content-card">
      <div class="sec-head">
        <span class="sec-title">快捷服务</span>
      </div>
      <div class="quick-grid">
        <div v-for="(item, i) in quickEntries" :key="'qe-' + i" class="quick-cell" @click="handleQuickEntry(item)">
          <div class="quick-cell-icon" :style="{ background: `${item.color}18` }">
            <van-icon :name="item.icon" :color="item.color" size="24" />
          </div>
          <span class="quick-cell-label">{{ item.name }}</span>
        </div>
      </div>
    </div>

    <!-- ==================== EXISTING: 热门目的地 ==================== -->
    <div class="content-card">
      <div class="sec-head">
        <span class="sec-title">🔥 热门目的地</span>
        <span class="sec-more" @click="goToDestinations">更多 <van-icon name="arrow" size="12" /></span>
      </div>
      <div class="h-scroll">
        <div v-for="(d, i) in hotDestinations" :key="'hd-' + i" class="dest-card" @click="handleDestination(d)">
          <img :src="d.image" :alt="d.name" class="dest-img" loading="lazy" decoding="async" @error="e=>e.target.style.opacity='0'" />
          <div class="dest-mask" />
          <span v-if="d.tag" class="dest-tag">{{ d.tag }}</span>
          <span class="dest-name">{{ d.name }}</span>
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
            <Swipe :autoplay="3000" indicator-color="rgba(255,255,255,0.5)" indicator-active-color="#ffffff" :circular="true" style="height:150px;border-radius:12px;overflow:hidden;">
              <SwipeItem v-for="(slide, i) in promotionSlides" :key="'promo-'+i">
                <div class="ctrip-promo-slide">
                  <img :src="slide.image" class="ctrip-promo-img" loading="lazy" @error="e => { e.target.src = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%22400%22 height=%22300%22><rect fill=%22%23e2e8f0%22 width=%22400%22 height=%22300%22/><text fill=%22%2394a3b8%22 font-size=%2220%22 font-family=%22sans-serif%22 x=%22200%22 y=%22150%22 text-anchor=%22middle%22 dominant-baseline=%22middle%22>' + encodeURIComponent(slide.title) + '</text></svg>') }" />
                  <div class="ctrip-promo-mask" />
                  <div class="ctrip-promo-tag">{{ slide.tag }}</div>
                  <div class="ctrip-promo-title">{{ slide.title }}</div>
                  <div class="ctrip-promo-subtitle">{{ slide.subtitle }}</div>
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
                    <van-icon name="eye-o" size="12" color="#94A3B8" />
                    <span>{{ formatNumber(note.viewCount) }}阅读</span>
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
                    <van-icon name="eye-o" size="12" color="#94A3B8" />
                    <span>{{ formatNumber(note.viewCount) }}阅读</span>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- 加载更多/没有更多（全宽居中） -->
      <div class="ctrip-feed-footer">
        <div v-if="loadingMore" class="ctrip-loading-more"><van-loading size="20" color="#8B5CF6" /><span>加载中...</span></div>
        <div v-else-if="!hasMore && notes.length > 0" class="ctrip-no-more">没有更多了</div>
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
          title="选择城市"
        />
      </van-popup>
    </div>

    <!-- Bottom spacer for floating bar -->
    <div class="bottom-spacer" />

    <!-- ==================== LAYER 7: Bottom Floating AI Input Bar ==================== -->
    <div class="ai-float-bar" @click="goToAIChat">
      <div class="float-bar-inner">
        <div class="float-ai-icon">
          <van-icon name="service-o" size="20" color="#fff" />
        </div>
        <span class="float-placeholder">问AI或按住说话</span>
        <div class="float-mic-icon">
          <van-icon name="audio" size="18" color="#8B5CF6" />
        </div>
      </div>
    </div>

    <!-- ==================== AIChatDialog ==================== -->
    <AIChatDialog
      v-model:visible="showAIChat"
      :context-query="{ destination: destination.trim(), budget: budget.trim(), days: days.trim() }"
    />

    <!-- ==================== 更多产品 VanPopup ==================== -->
    <van-popup v-model:show="showMoreProducts" position="bottom" round safe-area-inset-bottom :style="{ maxHeight: '60vh' }">
      <div class="more-popup-header">
        <span class="more-popup-title">更多产品</span>
        <van-icon name="cross" size="20" color="#94A3B8" @click="showMoreProducts = false" />
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
          <span class="more-popup-label">{{ product.name }}</span>
        </div>
      </div>
    </van-popup>

    <!-- ==================== 城市选择器 ==================== -->
    <van-popup v-model:show="showCityPicker" position="bottom" round safe-area-inset-bottom>
      <van-area ref="cityAreaRef" title="选择城市" :columns-num="2" :area-list="areaList" @confirm="onCityConfirm" @cancel="showCityPicker = false" />
    </van-popup>
  </div>
</template>

<style scoped>
/* ==================== CSS Variables ==================== */
.page-shell {
  --primary: #8B5CF6;
  --primary-2: #6366F1;
  --primary-3: #5B8DEF;
  --text-primary: #1E293B;
  --text-secondary: #64748B;
  --text-hint: #94A3B8;
  --card-bg: #ffffff;
  --card-radius: 18px;
  --card-shadow: 0 4px 18px rgba(0, 0, 0, 0.04);
  --tabbar-height: 56px;
  --safe-area-bottom: 0px;
  --float-bar-height: 52px;
  --float-bar-gap: 8px;

  width: 100%;
  min-height: 100vh;
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
  padding-bottom: calc(var(--tabbar-height, 56px) + var(--safe-area-bottom, 0px) + 80px);
}

/* ==================== LAYER 1: Hero Header ==================== */
.hero-header {
  position: relative;
  background: linear-gradient(160deg, #8B5CF6 0%, #7C3AED 30%, #6366F1 60%, #5B8DEF 100%);
  padding: calc(48px + env(safe-area-inset-top, 0px)) 20px 28px;
  border-radius: 0 0 32px 32px;
}

.hero-bg-decor {
  position: absolute; inset: 0; pointer-events: none;
}
.hero-svg { width: 100%; height: 100%; }

.hero-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  position: relative;
  z-index: 2;
}

.brand {
  display: flex;
  align-items: center;
}

.brand-text {
  font-size: 24px;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.3px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  background: linear-gradient(180deg, #ffffff 0%, #e2e0ff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hdr-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 7px 14px;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 20px;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s, transform 0.15s;
}
.hdr-btn:active {
  background: rgba(255, 255, 255, 0.32);
  transform: scale(0.95);
}

.search-row {
  position: relative;
  z-index: 2;
  max-width: 480px;
  margin: 0 auto;
}

/* ==================== LAYER 2: Service Icon Grid Row 1 ==================== */
.service-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px 4px;
  padding: 20px 16px 8px;
  max-width: 480px;
  margin: 0 auto;
}

.service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: transform 0.2s;
}
.service-item:active {
  transform: scale(0.92);
}

.service-icon-circle {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: transform 0.2s;
}
.service-item:hover .service-icon-circle {
  transform: scale(1.08);
}

.service-label {
  font-size: 11px;
  color: #475569;
  font-weight: 500;
  text-align: center;
}

/* ==================== LAYER 2b: Service Icon Grid Row 2 ==================== */
.service-grid-row2 {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px 4px;
  padding: 0 16px 16px;
  max-width: 480px;
  margin: 0 auto;
}

.service-item-sm {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: transform 0.2s;
}
.service-item-sm:active {
  transform: scale(0.92);
}

.service-icon-circle-sm {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: transform 0.2s;
}
.service-item-sm:hover .service-icon-circle-sm {
  transform: scale(1.06);
}

.service-label-sm {
  font-size: 10px;
  color: #64748B;
  font-weight: 400;
  text-align: center;
}

/* ==================== LAYER 3: More Products Bar ==================== */
.more-products-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 14px;
  padding: 12px 18px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  max-width: 480px;
  margin-left: auto;
  margin-right: auto;
  transition: transform 0.15s;
}
.more-products-bar:active {
  transform: scale(0.98);
}

.more-products-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mini-icon-row {
  display: flex;
  gap: -6px;
}

.mini-icon {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 700;
  margin-right: -6px;
  border: 2px solid #fff;
}

.more-products-text {
  font-size: 13px;
  color: #64748B;
  font-weight: 500;
}

/* ==================== LAYER 4: City Quick Tags ==================== */
.city-tags-section {
  padding: 14px 0 6px;
  max-width: 480px;
  margin: 0 auto;
}

.city-tags {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding: 0 16px;
}
.city-tags::-webkit-scrollbar {
  display: none;
}

.city-tag-chip {
  flex-shrink: 0;
  padding: 7px 16px;
  background: #fff;
  border-radius: 20px;
  font-size: 13px;
  color: #475569;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
  transition: all 0.2s;
  white-space: nowrap;
}
.city-tag-chip:hover {
  background: #f5f3ff;
  color: #7C3AED;
}
.city-tag-chip:active {
  transform: scale(0.94);
  background: #ede9fe;
  color: #7C3AED;
}

/* ==================== LAYER 5: Quick Function Tabs ==================== */
.quick-tabs-card {
  display: flex;
  gap: 8px;
  padding: 10px 14px;
  margin: 0 14px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.04);
  max-width: 480px;
  margin-left: auto;
  margin-right: auto;
  overflow-x: auto;
  scrollbar-width: none;
}
.quick-tabs-card::-webkit-scrollbar {
  display: none;
}

.quick-tab-btn {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 6px;
  border: none;
  background: #F8FAFC;
  border-radius: 14px;
  color: #64748B;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
}
.quick-tab-btn:hover {
  background: #f1f5f9;
}
.quick-tab-btn:active {
  transform: scale(0.94);
}

.quick-tab-primary {
  background: linear-gradient(135deg, #ede9fe, #ddd6fe);
  color: #7C3AED;
  font-weight: 600;
  box-shadow: 0 2px 10px rgba(139, 92, 246, 0.2);
}
.quick-tab-primary:hover {
  background: linear-gradient(135deg, #ddd6fe, #c4b5fd);
}

/* ==================== LAYER 6: Dual-Column Horizontal Scroll ==================== */
.dual-cards-scroll {
  display: flex;
  gap: 10px;
  padding: 16px 14px;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  max-width: 480px;
  margin: 0 auto;
}
.dual-cards-scroll::-webkit-scrollbar {
  display: none;
}

.event-card {
  flex-shrink: 0;
  width: 200px;
  height: 130px;
  border-radius: 18px;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.06);
  transition: transform 0.25s;
}
.event-card:hover {
  transform: translateY(-3px);
}
.event-card:active {
  transform: scale(0.97);
}

.event-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.event-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 32px 14px 14px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.6));
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.event-badge {
  font-size: 10px;
  background: rgba(255, 255, 255, 0.85);
  color: #7C3AED;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 600;
  align-self: flex-start;
}

.event-title {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.25);
}

.city-card {
  flex-shrink: 0;
  width: 160px;
  height: 130px;
  border-radius: 18px;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.06);
  transition: transform 0.25s;
}
.city-card:hover {
  transform: translateY(-3px);
}
.city-card:active {
  transform: scale(0.97);
}

.city-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.city-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 28px 12px 12px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.55));
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.city-badge {
  font-size: 10px;
  background: rgba(255, 255, 255, 0.85);
  color: #6366F1;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 600;
  align-self: flex-start;
}

.city-cta {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ==================== Content Card (existing sections) ==================== */
.content-card {
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.04);
  padding: 16px;
  margin: 0 14px 14px;
  max-width: 480px;
  margin-left: auto;
  margin-right: auto;
}

/* ==================== Banner ==================== */
.banner-wrap {
  overflow: hidden;
  padding: 6px;
  margin-top: 2px;
}
.banner-swipe {
  border-radius: 16px;
  overflow: hidden;
}
.banner-slide {
  position: relative;
  width: 100%;
  height: 150px;
  cursor: pointer;
}
.banner-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.banner-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 28px 16px 14px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.65));
  display: flex;
  flex-direction: column;
}
.banner-name {
  font-size: 17px;
  font-weight: 700;
  color: #fff;
}
.banner-slogan {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 2px;
}

/* ==================== AI 规划卡片 ==================== */
.plan-card { position: relative; }

.plan-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.plan-title-row { display: flex; align-items: center; gap: 8px; }
.plan-icon { font-size: 22px; }
.plan-title { font-size: 18px; font-weight: 700; color: var(--text-primary); }
.plan-badge {
  font-size: 11px;
  background: linear-gradient(135deg, #ede9fe, #ddd6fe);
  color: #7C3AED;
  padding: 4px 10px;
  border-radius: 20px;
  font-weight: 600;
}

/* 热门标签 */
.hot-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}
.hot-tag {
  padding: 6px 14px;
  background: #f8f7ff;
  border-radius: 20px;
  font-size: 12px;
  color: #64748B;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s;
}
.hot-tag.active {
  background: linear-gradient(135deg, #ede9fe, #ddd6fe);
  color: #7C3AED;
  border-color: #c4b5fd;
  font-weight: 600;
}
.hot-tag:active { transform: scale(0.95); }

/* 表单 */
.plan-form { display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; }
.plan-row { display: flex; gap: 10px; }
.plan-field {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #F8FAFC;
  border-radius: 14px;
  padding: 13px 16px;
  flex: 1;
  min-width: 0;
  border: 1.5px solid transparent;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.plan-field:focus-within {
  border-color: rgba(139, 92, 246, 0.4);
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.08);
}
.plan-field-full { cursor: pointer; }
.plan-half { flex: 1; width: calc(50% - 5px); box-sizing: border-box; }
.plan-label {
  font-size: 13px; color: #64748B; font-weight: 500; white-space: nowrap; flex-shrink: 0;
}
.plan-input {
  flex: 1; min-width: 0; border: none; outline: none; background: transparent;
  font-size: 15px; color: #1E293B; text-align: right;
}
.plan-input::placeholder { color: #CBD5E1; opacity: 1; }
.plan-input::-webkit-input-placeholder { color: #CBD5E1; opacity: 1; }

/* 提交按钮 */
.plan-submit {
  width: 100%;
  padding: 15px;
  border: none;
  border-radius: 22px;
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(139, 92, 246, 0.35);
  transition: transform 0.18s, box-shadow 0.25s;
}
.plan-submit:hover { box-shadow: 0 12px 32px rgba(139, 92, 246, 0.45); transform: translateY(-2px); }
.plan-submit:active { transform: scale(0.96); }

/* ==================== 通用区块头 ==================== */
.sec-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.sec-title { font-size: 16px; font-weight: 700; color: #1E293B; }
.sec-more { font-size: 12px; color: #94A3B8; cursor: pointer; display: flex; align-items: center; gap: 2px; }
.sec-more:active { opacity: 0.6; }

/* ==================== 6宫格快捷入口 ==================== */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px 12px;
}
.quick-cell {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  cursor: pointer; transition: transform 0.2s;
}
.quick-cell:hover { transform: translateY(-3px); }
.quick-cell:active { transform: translateY(-1px) scale(0.97); }
.quick-cell-icon {
  width: 56px; height: 56px; display: flex; align-items: center; justify-content: center;
  border-radius: 18px; transition: transform 0.2s;
}
.quick-cell:hover .quick-cell-icon { transform: scale(1.06); }
.quick-cell-label { font-size: 12px; color: #475569; font-weight: 500; }

/* ==================== 横向滚动 ==================== */
.h-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding-bottom: 4px;
}
.h-scroll::-webkit-scrollbar { display: none; }

/* ==================== 热门目的地 ==================== */
.dest-card {
  flex-shrink: 0; width: 110px; height: 130px; border-radius: 16px;
  overflow: hidden; position: relative; cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
  transition: transform 0.25s;
  will-change: transform;
}
.dest-card:hover { transform: translateY(-4px); }
.dest-img { width: 100%; height: 100%; object-fit: cover; }
.dest-mask {
  position: absolute; inset: 0;
  background: linear-gradient(transparent 40%, rgba(0, 0, 0, 0.55));
}
.dest-tag {
  position: absolute; top: 8px; right: 8px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  padding: 2px 8px; border-radius: 10px;
  font-size: 10px; font-weight: 600; color: #7C3AED;
}
.dest-name {
  position: absolute; bottom: 10px; left: 10px;
  font-size: 14px; font-weight: 700; color: #fff;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}

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
  width: calc(100% - 24px);
  margin: 14px auto 0;
  background: #fff;
  border-radius: 20px;
  padding: 0;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(139, 92, 246, 0.06);
  overflow: hidden;
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
  color: #1E293B;
}
.ctrip-section-more {
  font-size: 13px;
  color: #64748B;
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
.ctrip-nav-filter .ctrip-city-text { font-size: 13px; font-weight: 600; color: #1E293B; }
.ctrip-nav-filter .ctrip-center-tabs {
  flex: 1; display: flex; justify-content: center; gap: 6px;
  background: #f8fafc; border-radius: 16px; padding: 4px;
}
.ctrip-nav-filter .ctrip-tab-chip {
  flex: 1; text-align: center; padding: 6px 0; border-radius: 13px;
  font-size: 13px; font-weight: 500; color: #64748B;
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
  padding: 10px 12px 16px;
  display: flex;
  gap: 10px;
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
  color: #1E293B;
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
  color: #64748B;
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
  color: #94A3B8;
}

/* 加载更多 */
.ctrip-loading-more, .ctrip-no-more { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 20px 0; font-size: 13px; color: #94A3B8; }
.ctrip-no-more { font-size: 12px; }

/* Vant 组件覆盖 */
.ctrip-comment-field :deep(.van-field__control) { font-size: 13px; color: #334155; }
.ctrip-comment-field :deep(.van-field__control::placeholder) { color: #94A3B8; }
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

/* ==================== LAYER 7: Bottom Floating AI Input Bar ==================== */
.ai-float-bar {
  position: fixed;
  bottom: calc(var(--tabbar-height, 56px) + var(--safe-area-bottom, 0px) + 8px);
  left: 50%;
  transform: translateX(-50%);
  width: calc(100% - 28px);
  max-width: 452px;
  z-index: 500;
  cursor: pointer;
}

.float-bar-inner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(139, 92, 246, 0.15);
  border-radius: 26px;
  box-shadow: 0 4px 24px rgba(139, 92, 246, 0.12), 0 8px 40px rgba(0, 0, 0, 0.06);
  transition: all 0.25s;
}
.float-bar-inner:hover {
  box-shadow: 0 6px 28px rgba(139, 92, 246, 0.2), 0 10px 44px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
.float-bar-inner:active {
  transform: scale(0.97);
}

.float-ai-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  border-radius: 50%;
  flex-shrink: 0;
}

.float-placeholder {
  flex: 1;
  font-size: 14px;
  color: #94A3B8;
  font-weight: 400;
}

.float-mic-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f3ff;
  border-radius: 50%;
  flex-shrink: 0;
}

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
  color: #1E293B;
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
  animation: cloudDrift linear infinite;
}
.c1 { width: 60px; height: 60px; top: 12%; left: 5%; animation-duration: 24s; animation-delay: 0s; }
.c2 { width: 40px; height: 40px; top: 25%; right: 10%; animation-duration: 30s; animation-delay: -6s; background: rgba(99,102,241,0.06); }
.c3 { width: 80px; height: 80px; top: 50%; left: 70%; animation-duration: 36s; animation-delay: -12s; }
.c4 { width: 30px; height: 30px; top: 65%; left: 15%; animation-duration: 20s; animation-delay: -3s; background: rgba(167,139,250,0.07); }
.c5 { width: 50px; height: 50px; top: 78%; right: 25%; animation-duration: 28s; animation-delay: -18s; }
.c6 { width: 35px; height: 35px; top: 40%; left: 35%; animation-duration: 22s; animation-delay: -9s; background: rgba(139,92,246,0.05); }

/* hero-header保留原有静态渐变，不设animation避免覆盖entrance-item的entranceUp */

/* ---------- 圆形图标常驻呼吸 + hover发光 ---------- */
.service-icon-circle {
  animation: iconBreathe 3s ease-in-out infinite;
  transition: transform 0.35s ease, box-shadow 0.35s ease;
}
.service-item:hover .service-icon-circle {
  transform: scale(1.12);
  box-shadow: 0 0 20px rgba(139,92,246,0.2);
}

/* ---------- 活动卡片上下悬浮 ---------- */
.float-card {
  animation: floatUpDown 4s ease-in-out infinite;
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
  .hero-header {
    padding: calc(44px + env(safe-area-inset-top, 0px)) 14px 22px;
  }
  .brand-text {
    font-size: 20px;
  }
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
    margin: 0 10px 12px;
    padding: 14px;
  }
  .quick-tabs-card {
    margin: 0 10px;
    padding: 8px 10px;
  }
  .dual-cards-scroll {
    padding: 14px 10px;
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
    width: calc(100% - 20px);
  }
}
</style>
