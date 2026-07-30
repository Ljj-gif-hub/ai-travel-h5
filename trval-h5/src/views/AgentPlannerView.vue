<script setup>
/**
 * AgentPlannerView.vue — AI Agent 智能规划（融合版）
 *
 * 表单 UI：继承旧版 AITripPlanner 的城市选择 + 日期弹窗 + 偏好弹窗
 * 后端：新版 Agent SSE 流式（5 阶段：调研→规划→校验→调整→生成）
 * 结果：新版结构化展示（每日卡片 + 预算条 + 酒店 + 贴士 + 二次优化）
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'

defineOptions({ name: 'AgentPlannerView' })
const router = useRouter()

// ====== 页面状态 ======
// ====== 表单数据 ======
const origin = ref('深圳')
const destination = ref('')
const selectedDays = ref(null)
const selectedMonths = ref([])
const budget = ref(5000)
const people = ref(2)
const showDatePopup = ref(false)
const showPrefPopup = ref(false)
const showBudgetPopup = ref(false)

const preferences = reactive({
  companion: '', styles: [], hotelLevel: '', cabinClass: '', pace: '', schedule: '',
})

const quickTags = ['成都', '北京', '上海', '杭州', '大理', '三亚', '西安', '重庆', '长沙', '厦门']
const selectQuickTag = (tag) => { destination.value = tag }

const dayOptions = [1, 2, 3, 4, 5, 6, 7]
const monthGroups = [[7, 8, 9], [10, 11, 12], [1, 2, 3], [4, 5, 6]]
const selectDay = (d) => { selectedDays.value = d }
const toggleMonth = (m) => {
  const i = selectedMonths.value.indexOf(m)
  if (i >= 0) { selectedMonths.value.splice(i, 1) } else { selectedMonths.value.push(m) }
}
const dateDone = () => {
  if (!selectedDays.value) { showToast('请选择天数'); return }
  showDatePopup.value = false
}
const dateLabel = computed(() => {
  if (!selectedDays.value) { return '请选择 >' }
  let label = `${selectedDays.value}天`
  if (selectedMonths.value.length) { label += ` · ${selectedMonths.value.join('/')}月` }
  return label
})

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
const openOriginSelect = () => { router.push('/city-select') }
const openDestinationSelect = () => { router.push('/attraction-select') }
const clearOrigin = () => { origin.value = '' }

onMounted(() => {
  const city = sessionStorage.getItem('selected_origin_city')
  if (city) { origin.value = city; sessionStorage.removeItem('selected_origin_city') }
  const spot = sessionStorage.getItem('selected_destination_spot')
  if (spot) { destination.value = spot; sessionStorage.removeItem('selected_destination_spot') }
})

const prefDone = () => { showPrefPopup.value = false }

const budgetOptions = [2000, 3000, 5000, 8000, 10000, 15000, 20000]
const selectBudget = (b) => { budget.value = b }
const budgetDone = () => { showBudgetPopup.value = false }
const budgetLabel = computed(() => {
  if (!budget.value) return '请选择 >'
  if (budget.value >= 10000) return `${(budget.value / 10000).toFixed(1)}万/人`
  return `${budget.value.toLocaleString()}元/人`
})
const prefLabel = computed(() => {
  const parts = []
  if (preferences.companion) { parts.push(preferences.companion) }
  if (preferences.styles.length) { parts.push(preferences.styles[0] + (preferences.styles.length > 1 ? `+${preferences.styles.length - 1}` : '')) }
  return parts.length ? parts.join(' · ') : '请选择 >'
})

const canSubmit = computed(() => destination.value.trim() && selectedDays.value)
const isGenerating = ref(false)

// ====== 跳转到 Agent 地图页 ======
function startPlanning() {
  if (!canSubmit.value || isGenerating.value) return

  const query = {
    destination: destination.value.trim(),
    origin: origin.value.trim(),
    days: selectedDays.value,
    budget: budget.value,
    people: people.value,
    companion: preferences.companion || '',
    styles: (preferences.styles || []).join(','),
    hotel_level: (preferences.hotelLevel || '舒适型').replace(/三钻\/星|四钻\/星|五钻\/星/g, m => ({'三钻/星':'经济型','四钻/星':'舒适型','五钻/星':'豪华型'}[m] || '舒适型')),
    pace: preferences.pace || '适中',
    schedule: preferences.schedule || '',
    cabin: preferences.cabinClass || '',
    months: (selectedMonths.value || []).join(','),
  }

  // 保存草稿
  try { localStorage.setItem('agent_trip_draft', JSON.stringify(query)) } catch (e) {}

  router.push({ path: '/agent-map', query })
}

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
  people.value = 2
  preferences.companion = '情侣出行'
  preferences.styles = ['美食探索', '文化体验']
  preferences.hotelLevel = '四钻/星高档型'
  preferences.pace = '适中'
}

const goBack = () => { try { router.back() } catch (e) { router.push('/trips') } }
</script>

<template>
  <div class="planner-page">
    <!-- ====== 导航栏 ====== -->
    <div class="planner-nav">
      <button class="nav-back" @click="goBack">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><polyline points="15 18 9 12 15 6"/></svg>
      </button>
      <span class="nav-title">🤖 AI Agent 智能规划</span>
      <button class="nav-quick-fill" @click="quickFill">一键填充</button>
    </div>

    <div class="planner-scroll">
      <!-- ====== 表单 ====== -->
        <div class="form-section card-base">
          <div class="form-row clickable" @click="openOriginSelect">
            <span class="form-label">出发地</span>
            <div class="form-value-tag" v-if="origin">{{ origin }} <button class="tag-close" @click.stop="clearOrigin">&times;</button></div>
            <span class="form-value-hint" v-else>请选择出发地</span>
            <svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="#cbd5e1" stroke-width="2"><polyline points="7 4 13 10 7 16"/></svg>
          </div>
          <div class="row-divider" />
          <div class="form-row clickable" @click="openDestinationSelect">
            <span class="form-label">目的地</span>
            <div class="form-value-tag" v-if="destination">{{ destination }} <button class="tag-close" @click.stop="destination = ''">&times;</button></div>
            <span class="form-value-hint" v-else>你想去哪里</span>
            <svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="#cbd5e1" stroke-width="2"><polyline points="7 4 13 10 7 16"/></svg>
          </div>
          <div class="quick-tags-row">
            <span v-for="tag in quickTags" :key="tag" class="quick-tag-chip" :class="{ active: destination === tag }" @click="selectQuickTag(tag)">{{ tag }}</span>
          </div>
          <div class="row-divider" />
          <div class="form-row clickable" @click="showDatePopup = true">
            <span class="form-label">日期 / 时间</span><span class="form-value-hint">{{ dateLabel }}</span>
            <svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="#cbd5e1" stroke-width="2"><polyline points="7 4 13 10 7 16"/></svg>
          </div>
          <div class="row-divider" />
          <div class="form-row clickable" @click="showBudgetPopup = true">
            <span class="form-label">预算</span><span class="form-value-hint">{{ budgetLabel }}</span>
            <svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="#cbd5e1" stroke-width="2"><polyline points="7 4 13 10 7 16"/></svg>
          </div>
          <div class="row-divider" />
          <div class="form-row clickable" @click="showPrefPopup = true">
            <span class="form-label">旅行偏好</span><span class="form-value-hint">{{ prefLabel }}</span>
            <svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="#cbd5e1" stroke-width="2"><polyline points="7 4 13 10 7 16"/></svg>
          </div>
        </div>
        <button class="submit-btn-main" :class="{ disabled: !canSubmit }" :disabled="!canSubmit" @click="startPlanning">
          <svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor"><path d="M10 2 L12 8 L18 10 L12 12 L10 18 L8 12 L2 10 L8 8 Z"/></svg>
          <span>🚀 AI Agent 规划旅程</span>
        </button>
    </div>

    <!-- ====== 日期弹窗 ====== -->
    <van-popup v-model:show="showDatePopup" position="bottom" :style="{ borderRadius: '20px 20px 0 0' }" round safe-area-inset-bottom>
      <div class="popup-root">
        <div class="popup-header">
          <button class="popup-close" @click="showDatePopup = false">&times;</button>
          <span class="popup-title">日期</span>
          <span class="popup-tag active">灵活时间</span>
        </div>
        <div class="popup-block">
          <div class="block-head"><span>天数</span><span class="block-hint">任意天数</span></div>
          <div class="grid-7">
            <button v-for="d in dayOptions" :key="d" class="grid-btn" :class="{ active: selectedDays === d }" @click="selectDay(d)">{{ d }}天</button>
          </div>
        </div>
        <div class="popup-block">
          <div class="block-head"><span>月份</span></div>
          <div class="month-rows">
            <div v-for="(group, gi) in monthGroups" :key="gi" class="month-row">
              <button v-for="m in group" :key="m" class="grid-btn" :class="{ active: selectedMonths.includes(m) }" @click="toggleMonth(m)">{{ m }}月</button>
            </div>
          </div>
        </div>
        <button class="popup-done" :class="{ disabled: !selectedDays }" :disabled="!selectedDays" @click="dateDone">完成</button>
      </div>
    </van-popup>

    <!-- ====== 预算弹窗 ====== -->
    <van-popup v-model:show="showBudgetPopup" position="bottom" :style="{ borderRadius: '20px 20px 0 0' }" round safe-area-inset-bottom>
      <div class="popup-root">
        <div class="popup-header">
          <button class="popup-close" @click="showBudgetPopup = false">&times;</button>
          <span class="popup-title">人均预算</span>
        </div>
        <div class="popup-block">
          <div class="grid-7">
            <button v-for="b in budgetOptions" :key="b" class="grid-btn" :class="{ active: budget === b }" @click="selectBudget(b)">
              {{ b >= 10000 ? (b/10000).toFixed(1) + '万' : b.toLocaleString() }}
            </button>
          </div>
        </div>
        <button class="popup-done" @click="budgetDone">完成</button>
      </div>
    </van-popup>

    <!-- ====== 偏好弹窗 ====== -->
    <van-popup v-model:show="showPrefPopup" position="bottom" :style="{ height: '75%', borderRadius: '20px 20px 0 0' }" round safe-area-inset-bottom>
      <div class="popup-root popup-scroll">
        <div class="popup-header">
          <button class="popup-close" @click="showPrefPopup = false">&times;</button>
          <span class="popup-title">选择出行偏好</span>
        </div>
        <div class="popup-block"><div class="block-head"><span>同行伙伴</span></div>
          <div class="chip-row"><button v-for="c in companionOpts" :key="c" class="chip-btn" :class="{ active: preferences.companion === c }" @click="preferences.companion = c">{{ c }}</button></div>
        </div>
        <div class="popup-block"><div class="block-head"><span>风格偏好</span><span class="block-hint">可多选</span></div>
          <div class="chip-row"><button v-for="s in styleOpts" :key="s" class="chip-btn multi" :class="{ active: preferences.styles.includes(s) }" @click="toggleStyle(s)">{{ s }}</button></div>
        </div>
        <div class="popup-block"><div class="block-head"><span>酒店星级</span></div>
          <div class="chip-row"><button v-for="h in hotelOpts" :key="h" class="chip-btn" :class="{ active: preferences.hotelLevel === h }" @click="preferences.hotelLevel = h">{{ h }}</button></div>
        </div>
        <div class="popup-block"><div class="block-head"><span>飞机舱位</span></div>
          <div class="chip-row"><button v-for="c in cabinOpts" :key="c" class="chip-btn" :class="{ active: preferences.cabinClass === c }" @click="preferences.cabinClass = c">{{ c }}</button></div>
        </div>
        <div class="popup-block"><div class="block-head"><span>行程节奏</span></div>
          <div class="chip-row"><button v-for="p in paceOpts" :key="p" class="chip-btn" :class="{ active: preferences.pace === p }" @click="preferences.pace = p">{{ p }}</button></div>
        </div>
        <div class="popup-block"><div class="block-head"><span>时间安排</span></div>
          <div class="chip-row"><button v-for="s in scheduleOpts" :key="s" class="chip-btn" :class="{ active: preferences.schedule === s }" @click="preferences.schedule = s">{{ s }}</button></div>
        </div>
        <button class="popup-done" @click="prefDone">完成</button>
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

/* ====== Form ====== */
.card-base { background: rgba(255,255,255,0.5); backdrop-filter: blur(14px) saturate(160%); -webkit-backdrop-filter: blur(14px) saturate(160%); border-radius: 16px; padding: 4px 16px; box-shadow: inset 0 1px 0 rgba(255,255,255,0.45), 0 2px 10px rgba(0,0,0,0.03); border: 1px solid rgba(255,255,255,0.5); margin-bottom: 16px; }
.form-row { display: flex; align-items: center; padding: 14px 0; }
.form-row.clickable { cursor: pointer; }
.form-row.clickable:active { opacity: 0.6; }
.form-label { font-size: 15px; color: #1e293b; font-weight: 500; width: 80px; flex-shrink: 0; }
.form-value-tag { display: flex; align-items: center; gap: 6px; background: #f1f5f9; border-radius: 16px; padding: 4px 12px; font-size: 14px; color: #475569; }
.tag-close { border: none; background: transparent; font-size: 16px; color: #94a3b8; cursor: pointer; padding: 0 2px; }
.form-value-hint { flex: 1; font-size: 14px; color: #cbd5e1; }
.row-divider { height: 1px; background: #f1f5f9; margin-left: 80px; }
.quick-tags-row { display: flex; gap: 6px; padding: 8px 0 12px 80px; flex-wrap: wrap; }
.quick-tag-chip { padding: 5px 12px; border-radius: 14px; font-size: 12px; color: #64748b; background: rgba(255,255,255,0.45); backdrop-filter: blur(6px) saturate(140%); -webkit-backdrop-filter: blur(6px) saturate(140%); border: 1px solid rgba(255,255,255,0.4); cursor: pointer; transition: all 0.2s; }
.quick-tag-chip.active { background: #7C3AED; border-color: #7C3AED; color: #fff; }
.submit-btn-main { width: 100%; height: 52px; border: none; border-radius: 26px; background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; font-size: 17px; font-weight: 600; display: flex; align-items: center; justify-content: center; gap: 8px; cursor: pointer; box-shadow: 0 8px 24px rgba(99,102,241,0.35); margin-bottom: 8px; }
.submit-btn-main.disabled { background: #cbd5e1; box-shadow: none; cursor: not-allowed; color: #fff; }

/* ====== Popup ====== */
.popup-root { padding: 16px 20px; }
.popup-scroll { overflow-y: auto; max-height: 100%; }
.popup-header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.popup-close { border: none; background: transparent; font-size: 24px; color: #94a3b8; cursor: pointer; padding: 0; }
.popup-title { flex: 1; font-size: 18px; font-weight: 600; color: #1e293b; }
.popup-tag { font-size: 12px; color: #8b5cf6; border-bottom: 2px solid #8b5cf6; padding-bottom: 2px; font-weight: 500; }
.popup-block { margin-bottom: 20px; }
.block-head { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 500; color: #1e293b; margin-bottom: 10px; }
.block-hint { font-size: 12px; color: #94a3b8; font-weight: 400; }
.grid-7 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
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
:deep(.van-popup) { z-index: 10000 !important; }
</style>
