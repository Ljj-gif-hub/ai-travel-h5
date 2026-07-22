<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'

defineOptions({ name: 'AITripPlannerProgress' })
const router = useRouter()
const route = useRoute()

const progress = ref(0)
const currentStep = ref('')
const summary = ref('')
const allSteps = ref([])
const userPref = ref({})
const previewData = ref({})
const isFinish = ref(false)
const collapsed = ref(false)
const hasError = ref(false)
const isGenerating = ref(false)
const isStopping = ref(false) // 停止中锁
const taskId = ref('')
let abortCtrl = null

const statusIcon = (s) => s === 'done' ? '✓' : s === 'doing' ? '◌' : '○'
const statusClass = (s) => 'step-dot ' + s

// SSE 连接（POST + ReadableStream + 自动重试2次）
const dest = ref(route.query.destination || '')
const origin = ref(route.query.origin || '深圳')
const days = ref(parseInt(route.query.days) || 1)
const preferences = ref({})
try { preferences.value = JSON.parse(route.query.preferences || '{}') } catch (e) {}

const connect = () => {
  const token = localStorage.getItem('TOKEN') || ''
  abortCtrl = new AbortController()

  fetch('/api/travel/planner/progress', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream', 'Authorization': `Bearer ${token}` },
    body: JSON.stringify({ destination: dest.value, days: days.value, origin: origin.value, budget: 5000, preferences: preferences.value }),
    signal: abortCtrl.signal,
  }).then(async (resp) => {
    if (!resp.ok) {
      const errText = await resp.text().catch(() => '')
      throw new Error(`HTTP ${resp.status}${errText ? ': ' + errText.slice(0, 100) : ''}`)
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const lines = buf.split('\n')
      buf = lines.pop() || ''
      for (const line of lines) {
        const t = line.trim()
        if (!t) { continue }
        // SseEmitter 格式: data:{json}（无空格）或 data: {json}（有空格）
        if (!t.startsWith('data:')) { continue }
        const payload = t.startsWith('data: ') ? t.slice(6) : t.slice(5)
        if (!payload || payload === '[DONE]') { continue }
        try { handleEvent(JSON.parse(payload.trim())) } catch (e) {}
      }
    }
  }).catch(async (e) => {
    if (e.name === 'AbortError') return
    console.error('SSE连接失败', e)
    // 自动重试2次
    for (let retry = 1; retry <= 2; retry++) {
      if (isFinish.value || isStopping.value) return
      summary.value = `连接失败，正在重试(${retry}/2)...`
      await new Promise(r => setTimeout(r, 2000))
      try {
        abortCtrl = new AbortController()
        const r2 = await fetch('/api/travel/planner/progress', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream', 'Authorization': `Bearer ${localStorage.getItem('TOKEN') || ''}` },
          body: JSON.stringify({ destination: dest.value, days: days.value, origin: origin.value, budget: 5000, preferences: preferences.value }),
          signal: abortCtrl.signal,
        })
        if (!r2.ok) continue
        const reader = r2.body.getReader(); const decoder = new TextDecoder(); let buf = ''
        while (true) { const { done, value } = await reader.read(); if (done) break; buf += decoder.decode(value,{stream:true}); const lines = buf.split('\n'); buf = lines.pop()||''; for (const line of lines) { const t = line.trim(); if (!t.startsWith('data:')) continue; const p = t.startsWith('data: ')?t.slice(6):t.slice(5); if (!p||p==='[DONE]') continue; try { handleEvent(JSON.parse(p.trim())) } catch(e2) {} } }
        return // 重连成功
      } catch (e3) { if (e3.name === 'AbortError') return }
    }
    hasError.value = true; summary.value = '❌ 连接失败，请返回重新规划'
    showToast({ message: 'AI服务连接失败，请稍后重试', position: 'middle', duration: 2500 })
  })
}

const handleEvent = (data) => {
  if (!data || !data.eventType) return
  switch (data.eventType) {
    case 'progress-update':
      progress.value = data.progress || 0
      currentStep.value = data.stepName || ''
      summary.value = data.summary || ''
      allSteps.value = data.allStepList || []
      userPref.value = data.userPref || {}
      // 累积预览数据（各阶段不互相覆盖）
      if (data.previewData && Object.keys(data.previewData).length) {
        previewData.value = { ...previewData.value, ...data.previewData }
      }
      if (data.taskId) { taskId.value = data.taskId }
      break
    case 'generate-finish':
      isFinish.value = true; progress.value = 100
      setTimeout(() => {
        try {
          router.push({ path: '/planning', query: { destination: data.destination || route.query.destination || '', days: route.query.days || '1', budget: '5000', people: '2', taskId: taskId.value, streamMode: 'true' } })
        } catch (e) { console.error(e) }
      }, 1200)
      break
    case 'task-stop':
      if (abortCtrl) { abortCtrl.abort(); abortCtrl = null }
      isFinish.value = true; isStopping.value = false
      summary.value = '行程生成已终止'
      showToast({ message: '行程生成已终止', position: 'middle', duration: 1500 })
      setTimeout(() => { try { router.back() } catch (e) { router.push('/ai-planner') } }, 800)
      break
    case 'stream-error':
      hasError.value = true
      summary.value = '❌ ' + (data.message || '生成失败')
      showToast({ message: data.message || 'AI生成失败，请重试', position: 'middle', duration: 2500 })
      break
  }
}

const stopFlow = async () => {
  if (isStopping.value) { return }
  isStopping.value = true
  // 1. 断开前端连接
  if (abortCtrl) { abortCtrl.abort(); abortCtrl = null }
  // 2. 通知后端终止
  if (taskId.value) {
    fetch('/api/travel/planner/stop', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ taskId: taskId.value }),
    }).catch(() => {})
  }
  showToast({ message: '正在终止行程生成', position: 'middle', duration: 1200 })
  isFinish.value = true
  summary.value = '行程生成已终止'
  // 3. 延迟回退
  setTimeout(() => {
    try { router.back() } catch (e) { router.push('/ai-planner') }
  }, 800)
}

const goBack = () => { stopFlow() }
const stopGenerate = () => { stopFlow() }

const progressPercent = computed(() => progress.value + '%')
const alreadyStarted = ref(false)

onMounted(() => {
  if (alreadyStarted.value) { return }
  alreadyStarted.value = true
  connect()
})
onUnmounted(() => {
  if (abortCtrl) { abortCtrl.abort(); abortCtrl = null }
})
</script>

<template>
  <div class="progress-page">
    <!-- 顶部 -->
    <div class="pg-header">
      <button class="pg-back" @click="goBack">←</button>
      <span class="pg-title">AI 行程助手</span>
    </div>

    <!-- 偏好标签 -->
    <div class="pref-tags" v-if="userPref.companion || userPref.styles?.length">
      <span v-if="userPref.companion" class="pref-chip">{{ userPref.companion }}</span>
      <span v-for="s in userPref.styles" :key="s" class="pref-chip">{{ s }}</span>
      <span v-if="userPref.hotelLevel" class="pref-chip">{{ userPref.hotelLevel }}</span>
      <span v-if="userPref.pace" class="pref-chip">{{ userPref.pace }}</span>
    </div>

    <!-- 进度条 -->
    <div class="progress-bar-wrap" @click="collapsed = !collapsed">
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: progressPercent }"></div>
      </div>
      <span class="progress-label">线路正在生成中...({{ progressPercent }})</span>
      <span class="collapse-arrow">{{ collapsed ? '▸' : '▾' }}</span>
    </div>

    <!-- 阶段列表（无数据时显示骨架） -->
    <div class="stage-list" v-show="!collapsed">
      <div v-if="allSteps.length === 0" class="stage-skeleton">
        <div class="sk-stage" v-for="i in 7" :key="i">
          <span class="sk-dot"></span><span class="sk-label"></span>
        </div>
      </div>
      <div v-for="(s, i) in allSteps" :key="i" class="stage-row">
        <span :class="statusClass(s.status)">
          <span v-if="s.status==='doing'" class="spinner"></span>
          <span v-else>{{ statusIcon(s.status) }}</span>
        </span>
        <span class="stage-name">{{ s.name }}</span>
        <span class="stage-sub" v-if="s.status==='done' && s.name===currentStep"> {{ summary }}</span>
      </div>
    </div>

    <!-- 预览卡片 -->
    <div class="preview-area" v-if="previewData && Object.keys(previewData).length">
      <!-- 景点 -->
      <div v-if="previewData.spots" class="preview-card spots-card">
        <div class="pc-title">🏔️ 热门景点</div>
        <div class="spot-scroll">
          <div v-for="(s, i) in previewData.spots" :key="i" class="spot-item">
            <div class="spot-img-plc"></div>
            <div class="spot-name">{{ s.name }}</div>
            <div class="spot-rating">⭐ {{ s.rating }}</div>
          </div>
        </div>
      </div>
      <!-- 酒店 -->
      <div v-if="previewData.count && previewData.range" class="preview-card hotel-card">
        <div class="pc-title">🏨 酒店筛选</div>
        <div class="hotel-stat">{{ previewData.count }}家酒店 · {{ previewData.range }}</div>
        <div class="map-placeholder">
          <svg viewBox="0 0 200 120" fill="none">
            <rect width="200" height="120" rx="10" fill="#e8f4f8"/>
            <circle cx="100" cy="60" r="30" fill="#14b8a6" opacity="0.2"/>
            <circle cx="100" cy="60" r="15" fill="#14b8a6" opacity="0.3"/>
            <circle cx="100" cy="60" r="5" fill="#14b8a6"/>
          </svg>
        </div>
      </div>
      <!-- 交通 -->
      <div v-if="previewData.flights" class="preview-card traffic-card">
        <div class="pc-title">✈️ 交通方案</div>
        <div v-for="(f, i) in previewData.flights" :key="i" class="flight-row">
          <span>{{ f.from }} → {{ f.to }}</span>
          <span class="flight-info">{{ f.type }} · {{ f.duration }}</span>
        </div>
      </div>
      <!-- 贴士 -->
      <div v-if="previewData.tips" class="preview-card tips-card">
        <div class="pc-title">💡 旅行贴士</div>
        <div v-for="(t, i) in previewData.tips" :key="i" class="tip-item">
          <div class="tip-title">{{ t.title }}</div>
          <div class="tip-content">{{ t.content }}</div>
        </div>
      </div>
      <!-- 城市信息 -->
      <div v-if="previewData.description" class="preview-card info-card">
        <div class="pc-title">📍 {{ previewData.city }}</div>
        <div class="info-desc">{{ previewData.description }}</div>
      </div>
    </div>

    <!-- 完成动画 -->
    <div v-if="isFinish && progress >= 100" class="finish-overlay">
      <div class="finish-check">✓</div>
      <div class="finish-text">行程生成完毕，即将跳转...</div>
    </div>

    <!-- 停止 / 重试 -->
    <div class="stop-bar" v-if="!isFinish || hasError">
      <button class="stop-btn" :disabled="isStopping" @click="stopFlow" v-if="!hasError">
        {{ isStopping ? '正在终止...' : '停止生成' }}
      </button>
      <button class="stop-btn retry" @click="$router.back()" v-else>返回重新规划</button>
    </div>
  </div>
</template>

<style scoped>
.progress-page { width: 100%; min-height: 100vh; background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 50%, #f5f7fa 100%); display: flex; flex-direction: column; padding-bottom: 100px; }
.pg-header { display: flex; align-items: center; padding: calc(env(safe-area-inset-top) + 8px) 16px 12px; background: rgba(255,255,255,0.9); backdrop-filter: blur(12px); gap: 12px; border-bottom: 1px solid rgba(139,92,246,0.06); }
.pg-back { border: none; background: transparent; font-size: 22px; cursor: pointer; color: #333; width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center; transition: background 0.2s; }
.pg-back:active { background: #f1f5f9; transform: scale(0.92); }
.pg-title { font-size: 18px; font-weight: 700; color: #1e293b; letter-spacing: 0.3px; }

.pref-tags { display: flex; flex-wrap: wrap; gap: 6px; padding: 12px 16px; }
.pref-chip { padding: 4px 12px; background: #ede9fe; color: #7c3aed; border-radius: 14px; font-size: 12px; font-weight: 500; }

.progress-bar-wrap { padding: 16px; display: flex; align-items: center; gap: 12px; cursor: pointer; background: #fff; margin: 0 16px; border-radius: 16px; box-shadow: 0 2px 8px rgba(0,0,0,0.03); }
.progress-bar { flex: 1; height: 6px; background: #e2e8f0; border-radius: 3px; overflow: hidden; }
.progress-fill { height: 100%; background: linear-gradient(90deg, #6366f1, #8b5cf6, #a78bfa); background-size: 200% 100%; animation: barGlow 2s ease infinite; transition: width 0.6s cubic-bezier(0.4,0,0.2,1); border-radius: 3px; }
@keyframes barGlow { 0%,100% { background-position: 0% 50%; } 50% { background-position: 100% 50%; } }
.progress-label { font-size: 13px; color: #64748b; white-space: nowrap; font-weight: 500; }
.collapse-arrow { font-size: 14px; color: #94a3b8; transition: transform 0.3s; }

.stage-list { padding: 8px 16px; display: flex; flex-direction: column; gap: 4px; }
.stage-row { display: flex; align-items: center; gap: 12px; font-size: 14px; padding: 8px 12px; border-radius: 12px; transition: background 0.2s; }
.stage-row:has(.done) { background: rgba(16,185,129,0.03); }
.step-dot { width: 24px; height: 24px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 12px; flex-shrink: 0; transition: all 0.3s cubic-bezier(0.4,0,0.2,1); }
.step-dot.wait { background: #f1f5f9; color: #94a3b8; border: 2px solid #e2e8f0; }
.step-dot.doing { background: #ede9fe; color: #7c3aed; border: 2px solid #c4b5fd; box-shadow: 0 0 0 4px rgba(139,92,246,0.1); }
.step-dot.done { background: #d1fae5; color: #10b981; border: 2px solid #6ee7b7; }
.spinner { width: 12px; height: 12px; border: 2px solid #c4b5fd; border-top-color: #7c3aed; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.stage-name { flex: 1; color: #1e293b; }
.stage-sub { font-size: 11px; color: #10b981; white-space: nowrap; }

.preview-area { padding: 16px; }
.preview-card { background: #fff; border-radius: 18px; padding: 18px; box-shadow: 0 4px 16px rgba(0,0,0,0.04); animation: cardIn 0.5s cubic-bezier(0.16,1,0.3,1); }
@keyframes cardIn { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: translateY(0); } }
.pc-title { font-size: 16px; font-weight: 700; color: #1e293b; margin-bottom: 14px; }
.spot-scroll { display: flex; gap: 12px; overflow-x: auto; scrollbar-width: none; padding-bottom: 4px; }
.spot-scroll::-webkit-scrollbar { display: none; }
.spot-item { flex-shrink: 0; width: 120px; text-align: center; transition: transform 0.2s; }
.spot-item:active { transform: scale(0.95); }
.spot-img-plc { width: 120px; height: 80px; background: linear-gradient(135deg,#e8f4f8,#d4ecf2); border-radius: 12px; }
.spot-name { font-size: 12px; color: #334155; margin-top: 8px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.spot-rating { font-size: 11px; color: #f59e0b; margin-top: 2px; }
.hotel-stat { font-size: 28px; font-weight: 800; color: #14b8a6; margin-bottom: 10px; letter-spacing: -0.5px; }
.map-placeholder { width: 100%; border-radius: 14px; overflow: hidden; }
.map-placeholder svg { width: 100%; display: block; }
.flight-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #f1f5f9; font-size: 13px; color: #334155; }
.flight-row:last-child { border-bottom: none; }
.flight-info { color: #64748b; font-weight: 500; }
.tip-item { padding: 10px 0; border-bottom: 1px solid #f1f5f9; }
.tip-item:last-child { border-bottom: none; }
.tip-title { font-size: 13px; font-weight: 600; color: #1e293b; }
.tip-content { font-size: 12px; color: #64748b; margin-top: 4px; line-height: 1.5; }
.info-desc { font-size: 14px; color: #475569; line-height: 1.7; }

.finish-overlay { position: fixed; inset: 0; background: rgba(255,255,255,0.95); backdrop-filter: blur(8px); display: flex; flex-direction: column; align-items: center; justify-content: center; z-index: 200; animation: fadeIn 0.3s ease; }
.finish-check { width: 88px; height: 88px; background: linear-gradient(135deg,#10b981,#34d399); border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 44px; color: #fff; animation: popIn 0.5s cubic-bezier(0.16,1,0.3,1); box-shadow: 0 12px 32px rgba(16,185,129,0.3); }
@keyframes popIn { from { transform: scale(0) rotate(-90deg); } to { transform: scale(1) rotate(0deg); } }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
.finish-text { margin-top: 24px; font-size: 17px; color: #1e293b; font-weight: 500; }

.stop-bar { position: fixed; bottom: calc(var(--tabbar-height) + 24px); left: 50%; transform: translateX(-50%); }
.stop-btn { padding: 12px 44px; border: 1.5px solid #e2e8f0; border-radius: 26px; background: #fff; font-size: 14px; color: #64748b; cursor: pointer; box-shadow: 0 2px 8px rgba(0,0,0,0.04); transition: all 0.2s; }
.stop-btn:hover { box-shadow: 0 4px 14px rgba(0,0,0,0.08); }
.stop-btn:active { background: #f1f5f9; transform: scale(0.96); }
.stop-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.stop-btn.retry { background: #eff6ff; border-color: #3b82f6; color: #3b82f6; font-weight: 600; box-shadow: 0 2px 12px rgba(59,130,246,0.15); }
.stage-skeleton { display: flex; flex-direction: column; gap: 10px; }
.sk-stage { display: flex; align-items: center; gap: 10px; }
.sk-dot { width: 22px; height: 22px; border-radius: 50%; background: #e2e8f0; animation: shimmer 1.5s infinite; }
.sk-label { width: 120px; height: 12px; border-radius: 4px; background: #e2e8f0; animation: shimmer 1.5s infinite; }
@keyframes shimmer { 0% { opacity: 1; } 50% { opacity: 0.5; } 100% { opacity: 1; } }
</style>
