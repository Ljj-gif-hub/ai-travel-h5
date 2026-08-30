<script setup>
/**
 * AgentPlannerView.vue — AI 行程助手 · 下一站（图标抽屉版）
 *
 * 设计：参考「下一站想去哪?」英雄卡——顶部目的地快捷标签 + 输入区 + 底部图标行
 * 图标：📅 日期时间 / 👥 旅行人数 / 💰 预算 / 💗 旅行偏好，点击弹出底部抽屉
 * 组合：用户选择后，把配置拼成一句自然语言展示在输入区（含清空）
 * 后端：沿用 Agent SSE 流式（5 阶段），query 结构与旧版保持一致
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showToast } from 'vant'

defineOptions({ name: 'AgentPlannerView' })
const router = useRouter()
const { t } = useI18n()

// ====== 出发地 / 目的地 ======
const origin = ref('深圳')
const destination = ref('')
const openOriginSelect = () => { router.push('/city-select') }
const openDestinationSelect = () => { router.push('/attraction-select') }
const clearOrigin = () => { origin.value = '' }
const clearDestination = () => { destination.value = '' }

const quickTags = ['北京', '曼谷', '普吉岛', '吉隆坡']
const selectQuickTag = (tag) => { destination.value = destination.value === tag ? '' : tag }

// ====== 日期时间 ======
const selectedDays = ref(null)
const selectedMonths = ref([])
const showDatePopup = ref(false)
const dayOptions = [1, 2, 3, 4, 5, 6, 7]
const monthGroups = [[7, 8, 9], [10, 11, 12], [1, 2, 3], [4, 5, 6]]
const selectDay = (d) => { selectedDays.value = selectedDays.value === d ? null : d }
const dayMax = 30
const incDay = () => { selectedDays.value = Math.min((selectedDays.value || 0) + 1, dayMax) }
const decDay = () => { if (selectedDays.value) selectedDays.value = Math.max(selectedDays.value - 1, 1) }
const dayInput = computed({
  get: () => selectedDays.value ?? '',
  set: (v) => { const n = parseInt(v, 10); selectedDays.value = Number.isNaN(n) ? null : Math.max(1, Math.min(n, dayMax)) }
})
const toggleMonth = (m) => {
  const i = selectedMonths.value.indexOf(m)
  if (i >= 0) { selectedMonths.value.splice(i, 1) } else { selectedMonths.value.push(m) }
}
const dateDone = () => {
  if (!selectedDays.value) { showToast(t('agent.selectDays')); return }
  showDatePopup.value = false
}
const hasDate = computed(() => !!selectedDays.value)
const dateBadge = computed(() => selectedDays.value ? `${selectedDays.value}${t('common.days')}` : '')

// ====== 旅行人数（成人 / 儿童 / 老人） ======
const travelers = reactive({ adult: 2, child: 0, senior: 0 })
const travelerTouched = ref(false)
const showTravelerPopup = ref(false)
const travelerMax = 20
const incTraveler = (k) => { if (travelers[k] < travelerMax) { travelers[k]++; travelerTouched.value = true } }
const decTraveler = (k) => { if (travelers[k] > 0) { travelers[k]--; travelerTouched.value = true } }
// 每个分类都可直接编辑，夹在 0–travelerMax
const makeTravelerInput = (key) => computed({
  get: () => travelers[key],
  set: (v) => { const n = parseInt(v, 10); travelers[key] = Number.isNaN(n) ? 0 : Math.max(0, Math.min(n, travelerMax)); travelerTouched.value = true }
})
const adultInput = makeTravelerInput('adult')
const childInput = makeTravelerInput('child')
const seniorInput = makeTravelerInput('senior')
const totalPeople = computed(() => travelers.adult + travelers.child + travelers.senior)
const travelerDone = () => { showTravelerPopup.value = false }
const hasTravelers = computed(() => travelerTouched.value || travelers.child > 0 || travelers.senior > 0)
const travelerBadge = computed(() => hasTravelers.value ? `${totalPeople.value}${t('common.people')}` : '')

// ====== 预算（人均 / 总预算） ======
const budget = ref(0)            // 人均预算
const totalBudget = ref(0)       // 总预算
const showBudgetPopup = ref(false)
const personBudgetOptions = [500, 1000, 2000, 3000, 5000]         // 人均：贴近现实（穷游→小资）
const totalBudgetOptions = [3000, 5000, 8000, 12000, 20000]       // 总预算：贴近现实（家庭/团队）
const personBudgetMax = 8000
const totalBudgetMax = 30000
const fmtBudget = (v) => v >= 10000 ? (v / 10000).toFixed(1) + t('agent.unitWan') : v.toLocaleString()
const selectBudget = (b) => { budget.value = budget.value === b ? 0 : b }
const incBudget = () => { budget.value = Math.min((budget.value || 0) + 100, personBudgetMax) }
const decBudget = () => { budget.value = Math.max(budget.value - 100, 0) }
const budgetInput = computed({
  get: () => budget.value || '',
  set: (v) => { const n = parseInt(v, 10); budget.value = Number.isNaN(n) ? 0 : Math.max(0, Math.min(n, personBudgetMax)) }
})
const selectTotalBudget = (b) => { totalBudget.value = totalBudget.value === b ? 0 : b }
const incTotalBudget = () => { totalBudget.value = Math.min((totalBudget.value || 0) + 500, totalBudgetMax) }
const decTotalBudget = () => { totalBudget.value = Math.max(totalBudget.value - 500, 0) }
const totalBudgetInput = computed({
  get: () => totalBudget.value || '',
  set: (v) => { const n = parseInt(v, 10); totalBudget.value = Number.isNaN(n) ? 0 : Math.max(0, Math.min(n, totalBudgetMax)) }
})
const budgetDone = () => { showBudgetPopup.value = false }
const hasBudget = computed(() => !!budget.value || !!totalBudget.value)
const budgetBadge = computed(() => {
  const v = budget.value || totalBudget.value
  return v ? fmtBudget(v) : ''
})

// ====== 旅行偏好 ======
const preferences = reactive({ companion: '', styles: [], hotelLevel: '', cabinClass: '', pace: '', schedule: '' })
const showPrefPopup = ref(false)
const companionOpts = ['独自出行', '家庭出行', '情侣出行', '朋友出行', '老人同行']
const styleOpts = ['文化体验', '经典必去', '自然风光', '城市景观', '历史古迹', '美食探索', '网红打卡', '休闲度假']
const hotelOpts = ['三钻/星舒适型', '四钻/星高档型', '五钻/星豪华型']
const cabinOpts = ['公务/头等舱', '经济舱']
const paceOpts = ['紧凑', '适中', '宽松']
const scheduleOpts = ['偏早出', '偏晚归']
const toggleStyle = (s) => {
  const i = preferences.styles.indexOf(s)
  if (i >= 0) { preferences.styles.splice(i, 1) } else { preferences.styles.push(s) }
}
// 单选偏好：点击已选项再点一次取消
const togglePref = (key, val) => { preferences[key] = preferences[key] === val ? '' : val }
const toggleCompanion = (c) => togglePref('companion', c)
const toggleHotel = (h) => togglePref('hotelLevel', h)
const toggleCabin = (c) => togglePref('cabinClass', c)
const togglePace = (p) => togglePref('pace', p)
const toggleSchedule = (s) => togglePref('schedule', s)
const prefDone = () => { showPrefPopup.value = false }
const hasPref = computed(() => !!(preferences.companion || preferences.styles.length || preferences.hotelLevel || preferences.cabinClass || preferences.pace || preferences.schedule))
const prefBadge = computed(() => {
  if (!hasPref.value) return ''
  const n = (preferences.companion ? 1 : 0) + (preferences.styles.length ? preferences.styles.length : 0) + (preferences.hotelLevel ? 1 : 0) + (preferences.cabinClass ? 1 : 0) + (preferences.pace ? 1 : 0) + (preferences.schedule ? 1 : 0)
  return n
})
const prefLabel = computed(() => {
  const parts = []
  if (preferences.companion) parts.push(preferences.companion)
  if (preferences.styles.length) parts.push(preferences.styles[0] + (preferences.styles.length > 1 ? `+${preferences.styles.length - 1}` : ''))
  if (preferences.hotelLevel) parts.push(preferences.hotelLevel)
  if (preferences.cabinClass) parts.push(preferences.cabinClass)
  if (preferences.pace) parts.push(preferences.pace)
  if (preferences.schedule) parts.push(preferences.schedule)
  return parts.length ? parts.join(' · ') : t('agent.pleaseSelect')
})

// ====== 组合成一句（标签普通 + 值高亮） ======
// t() 返回的是已插值字符串（{ph} 被替换掉），所以用哨兵字符把「值」包起来，
// 渲染出最终字符串后按哨兵拆段：偶数下标=普通文案，奇数下标=用户选中的值（高亮）
const HILITE = ''
function spliceSegments(interpolated) {
  return interpolated.split(HILITE).map((text, i) => ({ text, highlight: i % 2 === 1 && text !== '' }))
}
const summarySegments = computed(() => {
  const segs = []
  // 目的地：我准备前往北京。
  if (destination.value.trim()) {
    const dest = destination.value.trim()
    segs.push(...spliceSegments(t('agent.sentenceDestination', { dest: HILITE + dest + HILITE })))
  }
  // 成员：同行成员有2成人，3老人。
  if (hasTravelers.value) {
    const m = []
    if (travelers.adult) m.push(t('agent.peopleAdult', { n: travelers.adult }))
    if (travelers.child) m.push(t('agent.peopleChild', { n: travelers.child }))
    if (travelers.senior) m.push(t('agent.peopleElderly', { n: travelers.senior }))
    if (m.length) segs.push(...spliceSegments(t('agent.sentenceMembers', { people: HILITE + m.join('，') + HILITE })))
  }
  // 时间：旅行时间是9月，3天。
  if (selectedDays.value) {
    const mon = selectedMonths.value.length ? selectedMonths.value.join('/') + t('agent.monthUnit') : ''
    segs.push(...spliceSegments(t('agent.sentenceTime', { months: HILITE + mon + HILITE, days: HILITE + selectedDays.value + t('common.days') + HILITE })))
  }
  // 偏好：旅行偏好是…
  const p = []
  if (preferences.companion) p.push(preferences.companion)
  if (preferences.styles.length) p.push(...preferences.styles)
  if (preferences.hotelLevel) p.push(preferences.hotelLevel)
  if (preferences.cabinClass) p.push(preferences.cabinClass)
  if (preferences.pace) p.push(preferences.pace)
  if (preferences.schedule) p.push(preferences.schedule)
  if (p.length) segs.push(...spliceSegments(t('agent.sentencePrefs', { prefs: HILITE + p.join('，') + HILITE })))
  // 预算：人均预算5000元。
  if (budget.value) {
    const amount = fmtBudget(budget.value)
    segs.push(...spliceSegments(t('agent.sentenceBudget', { amount: HILITE + amount + HILITE })))
  }
  if (totalBudget.value) {
    const amount = fmtBudget(totalBudget.value)
    segs.push(...spliceSegments(t('agent.sentenceTotalBudget', { amount: HILITE + amount + HILITE })))
  }
  return segs
})

// 清空所有配置
const resetConfig = () => {
  destination.value = ''
  travelers.adult = 2; travelers.child = 0; travelers.senior = 0
  travelerTouched.value = false
  selectedDays.value = null; selectedMonths.value = []
  budget.value = 0
  totalBudget.value = 0
  preferences.companion = ''; preferences.styles = []; preferences.hotelLevel = ''; preferences.cabinClass = ''; preferences.pace = ''; preferences.schedule = ''
}

// ====== 配置持久化：跳选择页/离开本页再回来，不丢已选内容 ======
const CONFIG_KEY = 'agent_trip_config'
const saveConfig = () => {
  const snapshot = {
    origin: origin.value,
    destination: destination.value,
    selectedDays: selectedDays.value,
    selectedMonths: [...selectedMonths.value],
    budget: budget.value,
    totalBudget: totalBudget.value,
    travelers: { ...travelers },
    travelerTouched: travelerTouched.value,
    preferences: {
      companion: preferences.companion,
      styles: [...preferences.styles],
      hotelLevel: preferences.hotelLevel,
      cabinClass: preferences.cabinClass,
      pace: preferences.pace,
      schedule: preferences.schedule,
    },
  }
  try { sessionStorage.setItem(CONFIG_KEY, JSON.stringify(snapshot)) } catch (e) {}
}
const loadConfig = () => {
  try {
    const raw = sessionStorage.getItem(CONFIG_KEY)
    if (!raw) return
    const s = JSON.parse(raw)
    if (typeof s.origin === 'string') origin.value = s.origin
    if (typeof s.destination === 'string') destination.value = s.destination
    if (s.selectedDays) selectedDays.value = s.selectedDays
    if (Array.isArray(s.selectedMonths)) selectedMonths.value = s.selectedMonths
    if (typeof s.budget === 'number') budget.value = s.budget
    if (typeof s.totalBudget === 'number') totalBudget.value = s.totalBudget
    if (s.travelers) Object.assign(travelers, s.travelers)
    if (typeof s.travelerTouched === 'boolean') travelerTouched.value = s.travelerTouched
    if (s.preferences) {
      preferences.companion = s.preferences.companion || ''
      preferences.styles = Array.isArray(s.preferences.styles) ? s.preferences.styles : []
      preferences.hotelLevel = s.preferences.hotelLevel || ''
      preferences.cabinClass = s.preferences.cabinClass || ''
      preferences.pace = s.preferences.pace || ''
      preferences.schedule = s.preferences.schedule || ''
    }
  } catch (e) {}
}
watch([origin, destination, selectedDays, selectedMonths, budget, totalBudget, travelerTouched, travelers, preferences], saveConfig, { deep: true })

const canSubmit = computed(() => destination.value.trim() && (selectedDays.value || summarySegments.value.length))
const isGenerating = ref(false)

// 把前端选项值（如'四钻/星高档型'）按前缀映射为后端枚举：经济型/舒适型/豪华型
function mapHotelLevel(v) {
  const raw = v || '舒适型'
  if (raw.startsWith('三钻')) return '经济型'
  if (raw.startsWith('四钻')) return '舒适型'
  if (raw.startsWith('五钻')) return '豪华型'
  return '舒适型'
}

function startPlanning() {
  if (!canSubmit.value || isGenerating.value) return

  const query = {
    destination: destination.value.trim(),
    origin: origin.value.trim(),
    days: selectedDays.value,
    budget: budget.value || 5000,
    total_budget: totalBudget.value || 0,
    people: totalPeople.value,
    adults: travelers.adult,
    children: travelers.child,
    seniors: travelers.senior,
    companion: preferences.companion || '',
    styles: (preferences.styles || []).join(','),
    hotel_level: mapHotelLevel(preferences.hotelLevel),
    pace: preferences.pace || '适中',
    schedule: preferences.schedule || '',
    cabin: preferences.cabinClass || '',
    months: (selectedMonths.value || []).join(','),
  }

  // 保存草稿
  try { localStorage.setItem('agent_trip_draft', JSON.stringify(query)) } catch (e) {}

  router.push({ path: '/agent-map', query })
}

// 麦克风：占位
const onVoiceTap = () => { showToast(t('agent.voiceHint')) }

function scrollLogs() {
  const el = document.querySelector('.agent-logs')
  if (el) el.scrollTop = el.scrollHeight
}

// ====== 一键填充 ======
const quickFill = () => {
  destination.value = '成都'
  selectedDays.value = 3
  selectedMonths.value = [4, 5]
  budget.value = 5000
  travelers.adult = 2; travelers.child = 1; travelers.senior = 0; travelerTouched.value = true
  preferences.companion = '情侣出行'
  preferences.styles = ['美食探索', '文化体验']
  preferences.hotelLevel = '四钻/星高档型'
  preferences.pace = '适中'
}

const goBack = () => { try { router.back() } catch (e) { router.push('/trips') } }
const goOld = () => { router.push('/agent-planner-old') }

// 挂载：从城市/景点选择页返回时读取结果
onMounted(() => {
  // 先恢复整份配置（人数/天数/预算/偏好/目的地），再让选择页带回来的值覆盖目的地与出发地
  loadConfig()
  const city = sessionStorage.getItem('selected_origin_city')
  if (city) { origin.value = city; sessionStorage.removeItem('selected_origin_city') }
  const spot = sessionStorage.getItem('selected_destination_spot')
  if (spot) { destination.value = spot; sessionStorage.removeItem('selected_destination_spot') }
})
</script>

<template>
  <div class="planner-page">
    <!-- ====== 导航栏 ====== -->
    <div class="planner-nav">
      <button class="nav-back" @click="goBack">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><polyline points="15 18 9 12 15 6"/></svg>
      </button>
      <span class="nav-title">{{ t('agent.title') }}</span>
      <button class="nav-quick-fill" @click="goOld">{{ t('agent.backToOld') }}</button>
      <button class="nav-quick-fill" @click="quickFill">{{ t('agent.quickFill') }}</button>
    </div>

    <div class="planner-scroll">
      <!-- ====== 英雄卡 ====== -->
      <div class="hero-card">
        <div class="hero-title"><span class="hero-wave">👋</span> {{ t('agent.nextStop') }}</div>

        <!-- 目的地快捷标签 -->
        <div class="quick-tags">
          <span v-for="tag in quickTags" :key="tag" class="quick-tag" :class="{ active: destination === tag }" @click="selectQuickTag(tag)">
            {{ tag }} <span class="tag-plus">+</span>
          </span>
        </div>

        <!-- 输入展示区（点开目的地选择 / 点击清空） -->
        <div class="prompt-box" @click="openDestinationSelect">
          <span v-if="summarySegments.length" class="prompt-text"><template v-for="(seg, i) in summarySegments" :key="i"><span :class="{ 'hl': seg.highlight }">{{ seg.text }}</span></template></span>
          <span v-else class="prompt-placeholder">{{ t('agent.whereToGo') }}</span>
          <p v-if="summarySegments.length" class="prompt-clear" @click.stop="resetConfig">&times;</p>
        </div>
        <!-- 出发地 -->
        <div class="origin-line" @click="openOriginSelect">
          <span class="origin-pin">📍</span>
          <span>{{ origin || t('agent.selectOrigin') }}</span>
          <span v-if="origin" class="tag-close" @click.stop="clearOrigin">&times;</span>
        </div>

        <!-- 图标行：日期 / 人数 / 预算 / 偏好 · 麦克风 -->
        <div class="icon-row">
          <button class="icon-btn" :class="{ active: hasDate }" @click="showDatePopup = true" :aria-label="t('agent.dateTime')">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="17" rx="3"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="3" y1="9" x2="21" y2="9"/></svg>
            <span v-if="dateBadge" class="icon-badge">{{ dateBadge }}</span>
          </button>
          <button class="icon-btn" :class="{ active: hasTravelers }" @click="showTravelerPopup = true" :aria-label="t('agent.travelerCount')">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="8" r="3.5"/><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6"/><path d="M16 4.6a3.5 3.5 0 0 1 0 6.8"/><path d="M17 14.5c2.5.5 4 2.5 4 5.5"/></svg>
            <span v-if="travelerBadge" class="icon-badge">{{ travelerBadge }}</span>
          </button>
          <button class="icon-btn" :class="{ active: hasBudget }" @click="showBudgetPopup = true" :aria-label="t('agent.budget')">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M8 8l4 4-4 4"/><path d="M16 8l-4 4 4 4"/></svg>
            <span v-if="budgetBadge" class="icon-badge">{{ budgetBadge }}</span>
          </button>
          <button class="icon-btn" :class="{ active: hasPref }" @click="showPrefPopup = true" :aria-label="t('agent.preferences')">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20.5S3 15 3 8.8C3 6 5.2 4 7.8 4c1.7 0 3.2.9 4.2 2.3C13 4.9 14.5 4 16.2 4 18.8 4 21 6 21 8.8 21 15 12 20.5 12 20.5z"/></svg>
          </button>

          <button class="icon-btn mic" @click="onVoiceTap" :aria-label="t('agent.voiceHint')">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v1c0 3.9 3.1 7 7 7s7-3.1 7-7v-1"/><line x1="12" y1="18" x2="12" y2="22"/></svg>
          </button>
        </div>
      </div>

      <!-- ====== 提交按钮 ====== -->
      <button class="submit-btn-main" :class="{ disabled: !canSubmit }" :disabled="!canSubmit" @click="startPlanning">
        <svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor"><path d="M10 2 L12 8 L18 10 L12 12 L10 18 L8 12 L2 10 L8 8 Z"/></svg>
        <span>{{ t('agent.planJourney') }}</span>
      </button>
      <div class="manual-link">{{ t('agent.manualCreate') }}</div>
    </div>

    <!-- ====== 日期弹窗 ====== -->
    <van-popup v-model:show="showDatePopup" position="bottom" :style="{ borderRadius: '20px 20px 0 0' }" round safe-area-inset-bottom>
      <div class="popup-root">
        <div class="popup-header">
          <button class="popup-close" @click="showDatePopup = false">&times;</button>
          <span class="popup-title">{{ t('agent.dateTitle') }}</span>
          <div class="day-stepper">
            <button class="step-btn" :class="{ disabled: !selectedDays || selectedDays <= 1 }" @click="decDay"><span class="step-minus">−</span></button>
            <div class="num-field">
              <input class="num-input" type="number" :min="1" :max="dayMax" v-model="dayInput" />
              <span class="num-unit">{{ t('common.days') }}</span>
            </div>
            <button class="step-btn plus" :class="{ disabled: selectedDays >= dayMax }" @click="incDay"><span class="step-plus">＋</span></button>
          </div>
        </div>
        <div class="popup-block">
          <div class="block-head"><span>{{ t('agent.daysLabel') }}</span><span class="block-hint">{{ t('agent.anyDays') }}</span></div>
          <div class="grid-4">
            <button v-for="d in dayOptions" :key="d" class="grid-btn" :class="{ active: selectedDays === d }" @click="selectDay(d)">{{ d }}{{ t('common.days') }}</button>
          </div>
        </div>
        <div class="popup-block">
          <div class="block-head"><span>{{ t('agent.months') }}</span></div>
          <div class="month-rows">
            <div v-for="(group, gi) in monthGroups" :key="gi" class="month-row">
              <button v-for="m in group" :key="m" class="grid-btn" :class="{ active: selectedMonths.includes(m) }" @click="toggleMonth(m)">{{ m }}{{ t('agent.monthUnit') }}</button>
            </div>
          </div>
        </div>
        <button class="popup-done" :class="{ disabled: !selectedDays }" :disabled="!selectedDays" @click="dateDone">{{ t('common.done') }}</button>
      </div>
    </van-popup>

    <!-- ====== 旅行人数弹窗 ====== -->
    <van-popup v-model:show="showTravelerPopup" position="bottom" :style="{ borderRadius: '20px 20px 0 0' }" round safe-area-inset-bottom>
      <div class="popup-root">
        <div class="popup-header">
          <button class="popup-close" @click="showTravelerPopup = false">&times;</button>
          <span class="popup-title">{{ t('agent.travelerCount') }}</span>
        </div>
        <div class="traveler-list">
          <div class="traveler-row">
            <div class="traveler-info"><div class="t-name">{{ t('agent.elderly') }}</div><div class="t-range">{{ t('agent.elderlyRange') }}</div></div>
            <div class="stepper">
              <button class="step-btn" :class="{ disabled: travelers.senior === 0 }" @click="decTraveler('senior')"><span class="step-minus">−</span></button>
              <input class="num-input" type="number" :min="0" :max="travelerMax" v-model="seniorInput" />
              <button class="step-btn plus" @click="incTraveler('senior')"><span class="step-plus">＋</span></button>
            </div>
          </div>
          <div class="traveler-row">
            <div class="traveler-info"><div class="t-name">{{ t('agent.adult') }}</div><div class="t-range">{{ t('agent.adultRange') }}</div></div>
            <div class="stepper">
              <button class="step-btn" :class="{ disabled: travelers.adult === 0 }" @click="decTraveler('adult')"><span class="step-minus">−</span></button>
              <input class="num-input" type="number" :min="0" :max="travelerMax" v-model="adultInput" />
              <button class="step-btn plus" @click="incTraveler('adult')"><span class="step-plus">＋</span></button>
            </div>
          </div>
          <div class="traveler-row">
            <div class="traveler-info"><div class="t-name">{{ t('agent.children') }}</div><div class="t-range">{{ t('agent.childrenRange') }}</div></div>
            <div class="stepper">
              <button class="step-btn" :class="{ disabled: travelers.child === 0 }" @click="decTraveler('child')"><span class="step-minus">−</span></button>
              <input class="num-input" type="number" :min="0" :max="travelerMax" v-model="childInput" />
              <button class="step-btn plus" @click="incTraveler('child')"><span class="step-plus">＋</span></button>
            </div>
          </div>
        </div>
        <button class="popup-done" @click="travelerDone">{{ t('common.confirm') }}</button>
      </div>
    </van-popup>

    <!-- ====== 预算弹窗 ====== -->
    <van-popup v-model:show="showBudgetPopup" position="bottom" :style="{ borderRadius: '20px 20px 0 0' }" round safe-area-inset-bottom>
      <div class="popup-root">
        <div class="popup-header">
          <button class="popup-close" @click="showBudgetPopup = false">&times;</button>
          <span class="popup-title">{{ t('agent.budgetTitle') }}</span>
        </div>
        <!-- 人均预算 -->
        <div class="popup-block">
          <div class="block-head">
            <span>{{ t('agent.perPersonBudget') }}</span>
            <div class="day-stepper">
              <button class="step-btn" :class="{ disabled: budget <= 0 }" @click="decBudget"><span class="step-minus">−</span></button>
              <div class="num-field">
                <input class="num-input" type="number" :min="0" :max="personBudgetMax" v-model="budgetInput" />
                <span class="num-unit">{{ t('common.yuan') }}</span>
              </div>
              <button class="step-btn plus" :class="{ disabled: budget >= personBudgetMax }" @click="incBudget"><span class="step-plus">＋</span></button>
            </div>
          </div>
          <div class="grid-4">
            <button v-for="b in personBudgetOptions" :key="b" class="grid-btn" :class="{ active: budget === b }" @click="selectBudget(b)">
              {{ b >= 10000 ? (b/10000).toFixed(1) + t('agent.unitWan') : b.toLocaleString() }}
            </button>
          </div>
        </div>
        <!-- 总预算 -->
        <div class="popup-block">
          <div class="block-head">
            <span>{{ t('agent.totalBudget') }}</span>
            <div class="day-stepper">
              <button class="step-btn" :class="{ disabled: totalBudget <= 0 }" @click="decTotalBudget"><span class="step-minus">−</span></button>
              <div class="num-field">
                <input class="num-input" type="number" :min="0" :max="totalBudgetMax" v-model="totalBudgetInput" />
                <span class="num-unit">{{ t('common.yuan') }}</span>
              </div>
              <button class="step-btn plus" :class="{ disabled: totalBudget >= totalBudgetMax }" @click="incTotalBudget"><span class="step-plus">＋</span></button>
            </div>
          </div>
          <div class="grid-4">
            <button v-for="b in totalBudgetOptions" :key="b" class="grid-btn" :class="{ active: totalBudget === b }" @click="selectTotalBudget(b)">
              {{ b >= 10000 ? (b/10000).toFixed(1) + t('agent.unitWan') : b.toLocaleString() }}
            </button>
          </div>
        </div>
        <button class="popup-done" @click="budgetDone">{{ t('common.done') }}</button>
      </div>
    </van-popup>

    <!-- ====== 偏好弹窗 ====== -->
    <van-popup v-model:show="showPrefPopup" position="bottom" :style="{ height: '75%', borderRadius: '20px 20px 0 0' }" round safe-area-inset-bottom>
      <div class="popup-root popup-scroll">
        <div class="popup-header">
          <button class="popup-close" @click="showPrefPopup = false">&times;</button>
          <span class="popup-title">{{ t('agent.selectPrefs') }}</span>
          <span class="popup-preview">{{ prefLabel }}</span>
        </div>
        <div class="popup-block"><div class="block-head"><span>{{ t('agent.companion') }}</span></div>
          <div class="chip-row"><button v-for="c in companionOpts" :key="c" class="chip-btn" :class="{ active: preferences.companion === c }" @click="toggleCompanion(c)">{{ c }}</button></div>
        </div>
        <div class="popup-block"><div class="block-head"><span>{{ t('agent.style') }}</span><span class="block-hint">{{ t('agent.multiSelect') }}</span></div>
          <div class="chip-row"><button v-for="s in styleOpts" :key="s" class="chip-btn multi" :class="{ active: preferences.styles.includes(s) }" @click="toggleStyle(s)">{{ s }}</button></div>
        </div>
        <div class="popup-block"><div class="block-head"><span>{{ t('agent.hotelLevel') }}</span></div>
          <div class="chip-row"><button v-for="h in hotelOpts" :key="h" class="chip-btn" :class="{ active: preferences.hotelLevel === h }" @click="toggleHotel(h)">{{ h }}</button></div>
        </div>
        <div class="popup-block"><div class="block-head"><span>{{ t('agent.cabinClass') }}</span></div>
          <div class="chip-row"><button v-for="c in cabinOpts" :key="c" class="chip-btn" :class="{ active: preferences.cabinClass === c }" @click="toggleCabin(c)">{{ c }}</button></div>
        </div>
        <div class="popup-block"><div class="block-head"><span>{{ t('agent.pace') }}</span></div>
          <div class="chip-row"><button v-for="p in paceOpts" :key="p" class="chip-btn" :class="{ active: preferences.pace === p }" @click="togglePace(p)">{{ p }}</button></div>
        </div>
        <div class="popup-block"><div class="block-head"><span>{{ t('agent.schedule') }}</span></div>
          <div class="chip-row"><button v-for="s in scheduleOpts" :key="s" class="chip-btn" :class="{ active: preferences.schedule === s }" @click="toggleSchedule(s)">{{ s }}</button></div>
        </div>
        <button class="popup-done" @click="prefDone">{{ t('common.done') }}</button>
        <div style="height:20px" />
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
/* ====== Layout ====== */
.planner-page { width: 100%; min-height: 100vh; background: transparent; display: flex; flex-direction: column; padding-bottom: calc(var(--tabbar-height, 60px) + 20px); }
.planner-nav { display: flex; align-items: center; padding: calc(env(safe-area-inset-top) + 8px) 16px 12px; background: rgba(255,255,255,0.5); backdrop-filter: blur(18px) saturate(170%); -webkit-backdrop-filter: blur(18px) saturate(170%); gap: 12px; border-bottom: 0.5px solid rgba(0,0,0,0.05); }
.nav-back { width: 36px; height: 36px; min-width: 36px; border: none; background: transparent; color: #333; cursor: pointer; display: flex; align-items: center; justify-content: center; }
.nav-back:active { transform: scale(0.9); }
.nav-title { flex: 1; font-size: 17px; font-weight: 600; color: #1e293b; }
.nav-quick-fill { padding: 6px 14px; border: 1px solid rgba(139,92,246,0.3); border-radius: 16px; background: rgba(139,92,246,0.08); font-size: 12px; color: #8b5cf6; cursor: pointer; }
.planner-scroll { flex: 1; overflow-y: auto; padding: 16px; }

/* ====== 英雄卡 ====== */
.hero-card { background: rgba(255,255,255,0.55); backdrop-filter: blur(16px) saturate(160%); -webkit-backdrop-filter: blur(16px) saturate(160%); border-radius: 20px; padding: 18px 16px 16px; box-shadow: inset 0 1px 0 rgba(255,255,255,0.5), 0 4px 20px rgba(0,0,0,0.04); border: 1px solid rgba(255,255,255,0.6); margin-bottom: 16px; }
.hero-title { font-size: 20px; font-weight: 600; color: #1e293b; margin-bottom: 14px; display: flex; align-items: center; gap: 6px; }
.hero-wave { font-size: 22px; }

.quick-tags { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.quick-tag { padding: 6px 14px; border-radius: 16px; font-size: 13px; color: #475569; background: rgba(255,255,255,0.6); border: 1px solid rgba(0,0,0,0.06); display: inline-flex; align-items: center; gap: 4px; cursor: pointer; transition: all 0.2s; }
.quick-tag .tag-plus { color: #94a3b8; font-size: 13px; }
.quick-tag.active { background: rgba(124,58,237,0.1); border-color: #7C3AED; color: #7C3AED; font-weight: 500; }
.quick-tag.active .tag-plus { color: #7C3AED; }
.quick-tag:active { transform: scale(0.95); }

/* 输入展示区 */
.prompt-box { position: relative; min-height: 52px; padding: 14px 16px; border-radius: 14px; background: rgba(255,255,255,0.5); border: 1px solid rgba(124,58,237,0.15); display: flex; align-items: flex-start; cursor: pointer; margin-bottom: 12px; }
.prompt-placeholder { font-size: 15px; color: #cbd5e1; }
.prompt-text { font-size: 14px; color: #64748b; line-height: 1.6; padding-right: 26px; word-break: break-word; }
.prompt-text .hl { color: #7C3AED; font-weight: 600; }
.prompt-clear { position: absolute; top: 8px; right: 12px; font-size: 20px; color: #94a3b8; cursor: pointer; line-height: 1; }
.prompt-clear:hover { color: #64748b; }

/* 出发地 */
.origin-line { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #64748b; margin-bottom: 16px; cursor: pointer; }
.origin-pin { font-size: 14px; }
.origin-line:active { opacity: 0.6; }
.tag-close { border: none; background: transparent; font-size: 15px; color: #94a3b8; cursor: pointer; padding: 0 2px; }

/* 图标行 */
.icon-row { display: flex; align-items: center; gap: 10px; }
.icon-btn { position: relative; width: 48px; height: 44px; border-radius: 14px; border: 1px solid rgba(0,0,0,0.05); background: rgba(255,255,255,0.6); color: #64748b; display: flex; align-items: center; justify-content: center; cursor: pointer; flex-shrink: 0; }
.icon-btn:active { transform: scale(0.92); }
.icon-btn.active { background: rgba(124,58,237,0.1); border-color: #7C3AED; color: #7C3AED; }
.icon-btn.mic { margin-left: auto; border-radius: 50%; width: 44px; }
.icon-badge { position: absolute; top: -7px; right: -7px; min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px; background: #7C3AED; color: #fff; font-size: 10px; font-weight: 600; display: flex; align-items: center; justify-content: center; box-shadow: 0 2px 6px rgba(124,58,237,0.35); }

/* ====== 提交 ====== */
.submit-btn-main { width: 100%; height: 52px; border: none; border-radius: 26px; background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; font-size: 17px; font-weight: 600; display: flex; align-items: center; justify-content: center; gap: 8px; cursor: pointer; box-shadow: 0 8px 24px rgba(99,102,241,0.35); margin-bottom: 12px; }
.submit-btn-main.disabled { background: #cbd5e1; box-shadow: none; cursor: not-allowed; color: #fff; }
.manual-link { text-align: center; font-size: 13px; color: #8b5cf6; cursor: pointer; }

/* ====== 弹窗 ====== */
.popup-root { padding: 16px 20px; }
.popup-scroll { overflow-y: auto; max-height: 100%; }
.popup-header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.day-stepper { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.day-stepper .step-btn { width: 30px; height: 30px; }
.num-field { display: flex; align-items: center; gap: 3px; }
.num-input { width: 62px; padding: 4px 6px; border: 1px solid #e2e8f0; border-radius: 10px; text-align: center; font-size: 15px; font-weight: 600; color: #1e293b; background: #fff; }
.num-input:focus { outline: none; border-color: #7C3AED; }
.num-input::-webkit-outer-spin-button, .num-input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
.num-input { -moz-appearance: textfield; }
.num-unit { font-size: 12px; color: #94a3b8; }
.popup-close { border: none; background: transparent; font-size: 24px; color: #94a3b8; cursor: pointer; padding: 0; }
.popup-title { font-size: 18px; font-weight: 600; color: #1e293b; }
.popup-tag { font-size: 12px; color: #8b5cf6; border-bottom: 2px solid #8b5cf6; padding-bottom: 2px; font-weight: 500; }
.popup-preview { font-size: 12px; color: #94a3b8; flex: 1; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.popup-block { margin-bottom: 20px; }
.block-head { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 500; color: #1e293b; margin-bottom: 10px; }
.block-hint { font-size: 12px; color: #94a3b8; font-weight: 400; }
.grid-4 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.grid-btn { padding: 10px; border: 1.5px solid rgba(0,0,0,0.06); border-radius: 12px; background: rgba(255,255,255,0.5); font-size: 14px; color: #475569; cursor: pointer; }
.grid-btn.active { background: rgba(139,92,246,0.1); border-color: #7C3AED; color: #7C3AED; font-weight: 600; }
.month-rows { display: flex; flex-direction: column; gap: 8px; }
.month-row { display: flex; gap: 8px; }
.month-row .grid-btn { flex: 1; }
.chip-row { display: flex; flex-wrap: wrap; gap: 8px; }
.chip-btn { padding: 8px 16px; border: 1.5px solid rgba(0,0,0,0.06); border-radius: 20px; background: rgba(255,255,255,0.5); font-size: 13px; color: #475569; cursor: pointer; }
.chip-btn.active { background: rgba(139,92,246,0.1); border-color: #7C3AED; color: #7C3AED; font-weight: 500; }
.popup-done { width: 100%; height: 48px; border: none; border-radius: 24px; background: #3b82f6; color: #fff; font-size: 16px; font-weight: 600; cursor: pointer; margin-top: 8px; }
.popup-done.disabled { background: #e2e8f0; color: #94a3b8; cursor: not-allowed; }

/* 人数 stepper */
.traveler-list { display: flex; flex-direction: column; }
.traveler-row { display: flex; align-items: center; padding: 16px 0; border-bottom: 1px solid #f1f5f9; }
.traveler-info { flex: 1; }
.t-name { font-size: 16px; color: #1e293b; font-weight: 500; }
.t-range { font-size: 13px; color: #94a3b8; margin-top: 2px; }
.stepper { display: flex; align-items: center; gap: 16px; }
.step-btn { width: 34px; height: 34px; border-radius: 50%; border: 1px solid #e2e8f0; background: #fff; color: #64748b; display: flex; align-items: center; justify-content: center; cursor: pointer; }
.step-btn.plus { border-color: #3b82f6; background: #fff; color: #3b82f6; }
.step-btn.disabled { opacity: 0.4; cursor: not-allowed; }
.step-btn:not(.disabled):active { transform: scale(0.9); }
.step-minus, .step-plus { font-size: 18px; line-height: 1; }
.step-num { min-width: 20px; text-align: center; font-size: 16px; font-weight: 600; color: #1e293b; }

:deep(.van-popup) { z-index: 10000 !important; }
</style>
