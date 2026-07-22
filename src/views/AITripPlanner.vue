<script setup>
/**
 * AITripPlanner.vue — AI 行程助手（单页3状态：表单→生成中→完成）
 * 全程不跳转路由，进度和结果内联渲染
 */
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'

defineOptions({ name: 'AITripPlanner' })
const router = useRouter()

// ====== 页面状态：form / generating / finish / error ======
const pageState = ref('form')

const origin = ref('深圳')
const destination = ref('')
const selectedDays = ref(null)
const selectedMonths = ref([])
const showDatePopup = ref(false)
const showPrefPopup = ref(false)
const isGenerating = ref(false)

const preferences = reactive({
  companion: '', styles: [], hotelLevel: '', cabinClass: '', pace: '', schedule: '',
})

const quickTags = ['曼谷', '普吉岛', '巴黎', '墨尔本']
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
const styleOpts = ['文化体验', '经典必去', '自然风光', '城市景观', '历史古迹']
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

// 从城市选择页/景点选择页返回时读取结果
onMounted(() => {
  const city = sessionStorage.getItem('selected_origin_city')
  if (city) { origin.value = city; sessionStorage.removeItem('selected_origin_city') }
  const spot = sessionStorage.getItem('selected_destination_spot')
  if (spot) { destination.value = spot; sessionStorage.removeItem('selected_destination_spot') }
})
const prefDone = () => { showPrefPopup.value = false }
const prefLabel = computed(() => {
  const parts = []
  if (preferences.companion) { parts.push(preferences.companion) }
  if (preferences.styles.length) { parts.push(preferences.styles[0] + (preferences.styles.length > 1 ? `+${preferences.styles.length - 1}` : '')) }
  return parts.length ? parts.join(' · ') : '请选择 >'
})

const canSubmit = computed(() => destination.value.trim() && selectedDays.value)

const isSubmitting = ref(false)

// ====== 内联进度 SSE（不跳转路由） ======
const progress = ref(0); const currentStep = ref(''); const stepSummary = ref('')
const allSteps = ref([]); const previewData = ref({}); const isStopping = ref(false)
let abortCtrl = null; const taskId = ref('')

const startGeneration = () => {
  if (!canSubmit.value || isSubmitting.value) return
  isSubmitting.value = true
  // 跳转到 trip-map 页面，携带全部偏好参数
  const query = {
    destination: destination.value.trim(),
    days: selectedDays.value,
    origin: origin.value,
    budget: 5000,
    people: '2',
    companion: preferences.companion || '',
    styles: (preferences.styles || []).join(','),
    hotel: preferences.hotelLevel || '',
    pace: preferences.pace || '',
    schedule: preferences.schedule || '',
  }
  router.push({ path: '/trip-map', query })
}

const resetToForm = () => {
  if (abortCtrl) { abortCtrl.abort(); abortCtrl = null }
  pageState.value = 'form'; progress.value = 0; previewData.value = {}; allSteps.value = []
}

const handleSubmit = () => { startGeneration() }

const quickFill = () => {
  destination.value = '三亚'
  selectedDays.value = 4
  selectedMonths.value = [10, 11]
  preferences.companion = '情侣出行'
  preferences.styles = ['自然风光', '文化体验']
  preferences.hotelLevel = '四钻/星高档型'
  preferences.pace = '适中'
}

const goBack = () => { try { router.back() } catch (e) { router.push('/trips') } }

const savedLines = ref([])
try {
  const raw = localStorage.getItem('ai_trip_draft')
  if (raw) { savedLines.value = [JSON.parse(raw)] }
} catch (e) { console.warn('加载草稿失败', e) }
</script>

<template>
  <div class="planner-page">
    <div class="planner-nav">
      <button class="nav-back" @click="goBack">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><polyline points="15 18 9 12 15 6"/></svg>
      </button>
      <span class="nav-title">AI 行程助手</span>
      <button class="nav-old-ver" @click="quickFill">一键填充</button>
    </div>

    <div class="planner-scroll">
      <!-- ====== 状态1：表单视图 ====== -->
      <template v-if="pageState === 'form'">
        <div class="form-section card-base">
          <div class="form-row clickable" @click="openOriginSelect"><span class="form-label">出发地</span><div class="form-value-tag">{{ origin || '请选择出发地' }} <button class="tag-close" @click.stop="clearOrigin">&times;</button></div><svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="#cbd5e1" stroke-width="2"><polyline points="7 4 13 10 7 16"/></svg></div>
          <div class="row-divider" />
          <div class="form-row clickable" @click="openDestinationSelect"><span class="form-label">目的地</span><div class="form-value-tag" v-if="destination">{{ destination }} <button class="tag-close" @click.stop="destination = ''">&times;</button></div><span class="form-value-hint" v-else>你想去哪里</span><svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="#cbd5e1" stroke-width="2"><polyline points="7 4 13 10 7 16"/></svg></div>
          <div class="quick-tags-row"><span v-for="tag in quickTags" :key="tag" class="quick-tag-chip" :class="{ active: destination === tag }" @click="selectQuickTag(tag)">{{ tag }}</span></div>
          <div class="row-divider" />
          <div class="form-row clickable" @click="showDatePopup = true"><span class="form-label">日期 / 时间</span><span class="form-value-hint">{{ dateLabel }}</span><svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="#cbd5e1" stroke-width="2"><polyline points="7 4 13 10 7 16"/></svg></div>
          <div class="row-divider" />
          <div class="form-row clickable" @click="showPrefPopup = true"><span class="form-label">旅行偏好</span><span class="form-value-hint">{{ prefLabel }}</span><svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="#cbd5e1" stroke-width="2"><polyline points="7 4 13 10 7 16"/></svg></div>
        </div>
        <button class="submit-btn-main" :class="{ disabled: !canSubmit }" :disabled="!canSubmit" @click="handleSubmit"><svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor"><path d="M10 2 L12 8 L18 10 L12 12 L10 18 L8 12 L2 10 L8 8 Z"/></svg><span>AI 规划旅程</span></button>
      </template>

      <!-- ====== 状态2：生成中视图（内联进度） ====== -->
      <template v-if="pageState === 'generating'">
        <div class="inline-progress">
          <div class="prog-bar-wrap"><div class="prog-bar"><div class="prog-fill" :style="{ width: progress + '%' }"></div></div><span class="prog-text">{{ progress }}%</span></div>
          <div class="prog-steps">
            <div v-for="s in allSteps" :key="s.name" class="prog-step">
              <span :class="'prog-dot ' + s.status">{{ s.status==='done'?'✓':s.status==='doing'?'◌':'○' }}</span>
              <span class="prog-name">{{ s.name }}</span>
              <span class="prog-sub" v-if="s.status==='done' && s.name===currentStep"> {{ stepSummary }}</span>
            </div>
          </div>
          <!-- 预览卡片 -->
          <div v-if="previewData.spots" class="preview-card"><div class="pc-title">🏔️ 热门景点</div><div class="spot-scroll"><div v-for="(s,i) in previewData.spots" :key="i" class="spot-item"><div class="spot-img-plc"></div><div class="spot-name">{{ s.name }}</div><div class="spot-rating">⭐ {{ s.rating }}</div></div></div></div>
          <div v-if="previewData.count && previewData.range" class="preview-card"><div class="pc-title">🏨 {{ previewData.count }}家酒店 · {{ previewData.range }}</div><div class="map-plc"><svg viewBox="0 0 200 120"><rect width="200" height="120" rx="10" fill="#e8f4f8"/><circle cx="100" cy="60" r="30" fill="#14b8a6" opacity="0.2"/><circle cx="100" cy="60" r="5" fill="#14b8a6"/></svg></div></div>
          <div v-if="previewData.flights" class="preview-card"><div class="pc-title">✈️ 交通方案</div><div v-for="(f,i) in previewData.flights" :key="i" class="flight-row"><span>{{ f.from }} → {{ f.to }}</span><span class="flight-info">{{ f.type }} · {{ f.duration }}</span></div></div>
          <div v-if="previewData.tips" class="preview-card"><div class="pc-title">💡 旅行贴士</div><div v-for="(t,i) in previewData.tips" :key="i" class="tip-row"><div class="tip-t">{{ t.title }}</div><div class="tip-c">{{ t.content }}</div></div></div>
          <div class="stop-bar-inline"><button class="sb-btn" @click="stopGenerate">停止生成</button></div>
        </div>
      </template>

      <!-- ====== 状态3：完成视图 ====== -->
      <template v-if="pageState === 'finish'">
        <div class="finish-inline">
          <div class="finish-badge">✓ 行程生成完毕</div>
          <div class="finish-info">📍 {{ destination }} · {{ selectedDays }}天 · ¥5000 · {{ origin }}出发</div>
          <div class="finish-actions">
            <button class="fa-btn primary" @click="router.push({path:'/planning',query:{destination:destination.trim(),days:selectedDays,budget:'5000',people:'2',taskId:taskId,streamMode:'true'}})">📋 查看结构化行程</button>
            <button class="fa-btn" @click="resetToForm">🔄 重新规划</button>
          </div>
        </div>
      </template>

      <!-- ====== 错误状态 ====== -->
      <template v-if="pageState === 'error'">
        <div class="error-inline">
          <div class="err-icon">⚠️</div><div class="err-text">生成失败，请重试</div>
          <button class="fa-btn primary" @click="resetToForm">重新规划</button>
        </div>
      </template>
    </div>

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
.planner-page { width: 100%; min-height: 100vh; background: #f5f7fa; display: flex; flex-direction: column; padding-bottom: calc(var(--tabbar-height) + 20px); }
.planner-nav { display: flex; align-items: center; padding: calc(env(safe-area-inset-top) + 8px) 16px 12px; background: #fff; gap: 12px; }
.nav-back { width: 36px; height: 36px; min-width: 36px; border: none; background: transparent; color: #333; cursor: pointer; display: flex; align-items: center; justify-content: center; }
.nav-back:active { transform: scale(0.9); }
.nav-title { flex: 1; font-size: 18px; font-weight: 600; color: #1e293b; }
.nav-old-ver { padding: 6px 14px; border: 1px solid #e2e8f0; border-radius: 16px; background: #fff; font-size: 12px; color: #64748b; cursor: pointer; }
.nav-old-ver:active { background: #f1f5f9; }
.planner-scroll { flex: 1; overflow-y: auto; padding: 16px; }
.card-base { background: #fff; border-radius: 16px; padding: 4px 16px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); margin-bottom: 16px; }
.form-row { display: flex; align-items: center; padding: 14px 0; }
.form-row.clickable { cursor: pointer; }
.form-row.clickable:active { opacity: 0.6; }
.form-label { font-size: 15px; color: #1e293b; font-weight: 500; width: 80px; flex-shrink: 0; }
.form-value-tag { display: flex; align-items: center; gap: 6px; background: #f1f5f9; border-radius: 16px; padding: 4px 12px; font-size: 14px; color: #475569; }
.tag-close { border: none; background: transparent; font-size: 16px; color: #94a3b8; cursor: pointer; padding: 0 2px; }
.form-input-wrap { flex: 1; display: flex; align-items: center; gap: 8px; }
.form-input-inline { flex: 1; border: none; outline: none; font-size: 15px; color: #1e293b; background: transparent; }
.form-input-inline::placeholder { color: #cbd5e1; }
.form-value-hint { flex: 1; font-size: 14px; color: #cbd5e1; }
.row-divider { height: 1px; background: #f1f5f9; margin-left: 80px; }
.quick-tags-row { display: flex; gap: 8px; padding: 8px 0 12px 80px; }
.quick-tag-chip { padding: 5px 14px; border-radius: 16px; font-size: 12px; color: #64748b; background: #f8fafc; border: 1px solid #e2e8f0; cursor: pointer; transition: all 0.2s; }
.quick-tag-chip.active { background: #eff6ff; border-color: #3b82f6; color: #3b82f6; font-weight: 500; }
.quick-tag-chip:active { transform: scale(0.95); }
.submit-btn-main { width: 100%; height: 52px; border: none; border-radius: 26px; background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; font-size: 17px; font-weight: 600; display: flex; align-items: center; justify-content: center; gap: 8px; cursor: pointer; box-shadow: 0 8px 24px rgba(99,102,241,0.35); transition: all 0.25s; margin-bottom: 8px; }
.submit-btn-main:hover { transform: translateY(-2px); box-shadow: 0 12px 32px rgba(99,102,241,0.45); }
.submit-btn-main:active { transform: scale(0.96); }
.submit-btn-main.disabled { background: #cbd5e1; box-shadow: none; cursor: not-allowed; color: #fff; }
.submit-btn-main.disabled:hover { transform: none; }
.manual-link { text-align: center; font-size: 13px; color: #8b5cf6; cursor: pointer; margin-bottom: 24px; }
.import-bar { display: flex; align-items: center; background: #fff; border-radius: 24px; padding: 12px 16px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); gap: 10px; }
.import-left { flex: 1; display: flex; align-items: center; gap: 8px; font-size: 13px; color: #64748b; }
.voice-btn { width: 40px; height: 40px; border: none; border-radius: 50%; background: #f5f3ff; display: flex; align-items: center; justify-content: center; cursor: pointer; }
.voice-btn:active { background: #ede9fe; transform: scale(0.92); }
.popup-root { padding: 16px 20px; }
.popup-scroll { overflow-y: auto; max-height: 100%; }
.popup-header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.popup-close { border: none; background: transparent; font-size: 24px; color: #94a3b8; cursor: pointer; padding: 0; line-height: 1; }
.popup-close:active { color: #333; }
.popup-title { flex: 1; font-size: 18px; font-weight: 600; color: #1e293b; }
.popup-tag { font-size: 12px; color: #8b5cf6; border-bottom: 2px solid #8b5cf6; padding-bottom: 2px; font-weight: 500; }
.popup-block { margin-bottom: 20px; }
.block-head { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 500; color: #1e293b; margin-bottom: 10px; }
.block-hint { font-size: 12px; color: #94a3b8; font-weight: 400; }
.grid-7 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.grid-btn { padding: 10px; border: 1.5px solid #e2e8f0; border-radius: 12px; background: #fff; font-size: 14px; color: #475569; cursor: pointer; transition: all 0.2s; }
.grid-btn.active { background: #eff6ff; border-color: #3b82f6; color: #3b82f6; font-weight: 600; }
.grid-btn:active { transform: scale(0.95); }
.month-rows { display: flex; flex-direction: column; gap: 8px; }
.month-row { display: flex; gap: 8px; }
.month-row .grid-btn { flex: 1; }
.chip-row { display: flex; flex-wrap: wrap; gap: 8px; }
.chip-btn { padding: 8px 16px; border: 1.5px solid #e2e8f0; border-radius: 20px; background: #fff; font-size: 13px; color: #475569; cursor: pointer; transition: all 0.2s; }
.chip-btn.active { background: #eff6ff; border-color: #3b82f6; color: #3b82f6; font-weight: 500; }
.chip-btn:active { transform: scale(0.95); }
.popup-done { width: 100%; height: 48px; border: none; border-radius: 24px; background: #3b82f6; color: #fff; font-size: 16px; font-weight: 600; cursor: pointer; transition: all 0.25s; margin-top: 8px; }
.popup-done:hover { background: #2563eb; }
.popup-done:active { transform: scale(0.96); }
.popup-done.disabled { background: #e2e8f0; color: #94a3b8; cursor: not-allowed; }
.popup-done.disabled:hover { background: #e2e8f0; }
/* 内联进度 */
.inline-progress { padding: 0 16px; }
.prog-bar-wrap { display: flex; align-items: center; gap: 10px; background: #fff; padding: 12px 16px; border-radius: 14px; margin-bottom: 12px; }
.prog-bar { flex: 1; height: 6px; background: #e2e8f0; border-radius: 3px; overflow: hidden; }
.prog-fill { height: 100%; background: linear-gradient(90deg,#6366f1,#8b5cf6); transition: width 0.5s ease; border-radius: 3px; }
.prog-text { font-size: 14px; font-weight: 700; color: #7c3aed; white-space: nowrap; }
.prog-steps { background: #fff; border-radius: 14px; padding: 12px 16px; margin-bottom: 12px; display: flex; flex-direction: column; gap: 6px; }
.prog-step { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.prog-dot { width: 22px; height: 22px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 11px; flex-shrink: 0; }
.prog-dot.wait { background: #f1f5f9; color: #94a3b8; }
.prog-dot.doing { background: #ede9fe; color: #7c3aed; }
.prog-dot.done { background: #d1fae5; color: #10b981; }
.prog-name { color: #334155; flex: 1; }
.prog-sub { font-size: 11px; color: #10b981; }
.preview-card { background: #fff; border-radius: 16px; padding: 16px; margin-bottom: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); animation: cardIn 0.4s ease; }
@keyframes cardIn { from { opacity:0; transform:translateY(10px); } to { opacity:1; transform:translateY(0); } }
.pc-title { font-size: 15px; font-weight: 600; color: #1e293b; margin-bottom: 10px; }
.spot-scroll { display: flex; gap: 10px; overflow-x: auto; scrollbar-width: none; }
.spot-scroll::-webkit-scrollbar { display: none; }
.spot-item { flex-shrink: 0; width: 110px; text-align: center; }
.spot-img-plc { width: 110px; height: 75px; background: linear-gradient(135deg,#e8f4f8,#d4ecf2); border-radius: 10px; }
.spot-name { font-size: 11px; color: #334155; margin-top: 6px; }
.spot-rating { font-size: 10px; color: #f59e0b; }
.map-plc svg { width: 100%; border-radius: 10px; }
.flight-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f1f5f9; font-size: 13px; }
.flight-info { color: #64748b; }
.tip-row { padding: 8px 0; border-bottom: 1px solid #f1f5f9; }
.tip-t { font-size: 13px; font-weight: 600; color: #1e293b; }
.tip-c { font-size: 12px; color: #64748b; margin-top: 2px; }
.stop-bar-inline { display: flex; justify-content: center; padding: 20px 0; }
.sb-btn { padding: 10px 40px; border: 1.5px solid #e2e8f0; border-radius: 24px; background: #fff; font-size: 14px; color: #64748b; cursor: pointer; }
.sb-btn:active { background: #f1f5f9; }

/* 完成视图 */
.finish-inline { text-align: center; padding: 30px 16px; }
.finish-badge { font-size: 22px; font-weight: 700; color: #10b981; margin-bottom: 12px; }
.finish-info { font-size: 14px; color: #64748b; margin-bottom: 24px; }
.finish-actions { display: flex; gap: 12px; justify-content: center; }
.fa-btn { padding: 12px 28px; border: 1.5px solid #e2e8f0; border-radius: 24px; background: #fff; font-size: 14px; color: #64748b; cursor: pointer; transition: all 0.2s; }
.fa-btn:active { transform: scale(0.96); }
.fa-btn.primary { background: linear-gradient(135deg,#6366f1,#8b5cf6); color: #fff; border: none; font-weight: 600; }

/* 错误 */
.error-inline { text-align: center; padding: 60px 16px; }
.err-icon { font-size: 48px; margin-bottom: 12px; }
.err-text { font-size: 16px; color: #64748b; margin-bottom: 20px; }

:deep(.van-popup) { z-index: 10000 !important; }
:deep(.van-overlay) { z-index: 9990 !important; }
</style>
