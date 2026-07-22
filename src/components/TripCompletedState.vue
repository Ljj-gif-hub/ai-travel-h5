<template>
  <div class="completed-state">
    <!-- ===== 行程标题栏 (所有档位可见) ===== -->
    <div class="trip-title-bar">
      <div class="title-left">
        <h2 class="trip-dest">{{ planData?.destination || '...' }}</h2>
        <span class="trip-meta">{{ planData?.days || 0 }}天 · {{ planData?.people || 0 }}人</span>
      </div>
      <div class="title-right" v-if="false"></div>
    </div>

    <!-- ===== mid+max 可见区域 ===== -->
    <template v-if="drawerState !== 'min'">
      <!-- 行程概览 -->
      <div class="trip-overview">
        <p class="overview-text">{{ planData?.overview || 'AI 智能生成行程' }}</p>
        <div class="ai-badge"><van-icon name="service-o" size="14" /><span>AI 生成 · 仅供参考</span></div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-row">
        <div class="action-btn" @click="$emit('share')"><van-icon name="share-o" size="20" /><span>分享</span></div>
        <div class="action-btn" @click="$emit('like')"><van-icon name="good-job-o" size="20" /><span>有用</span></div>
        <div class="action-btn" @click="$emit('dislike')"><van-icon name="good-job-o" size="20" style="transform:rotate(180deg)" /><span>踩</span></div>
        <div class="action-btn" @click="$emit('save')"><van-icon name="bookmark-o" size="20" /><span>保存</span></div>
      </div>

      <!-- ===== 线路预览 ===== -->
      <div class="route-preview" v-if="(planData?.dayPlans || []).length > 0">
        <div class="section-title">🗺️ 线路预览</div>
        <div class="route-scroll">
          <div v-for="(day, i) in (planData?.dayPlans || [])" :key="'rp'+i" class="route-card" @click="scrollToDay(i)">
            <div class="route-day">Day{{ day.day || i+1 }}</div>
            <div class="route-dest">{{ (day.timeSlots||[])[0]?.attraction || day.dayTitle || '第'+(i+1)+'天' }}</div>
            <div class="route-spots">{{ (day.timeSlots||[]).map(s=>s.attraction).filter(Boolean).slice(0,2).join(' · ') || '探索中' }}</div>
          </div>
        </div>
      </div>

      <!-- ===== 每日行程快速导航 ===== -->
      <div class="day-nav" v-if="(planData?.dayPlans || []).length > 0">
        <div v-for="(day, i) in (planData?.dayPlans || [])" :key="'nav'+i" class="day-nav-btn" :class="{ 'day-nav-btn--active': activeDayIndex === i }" @click="scrollToDay(i)">Day{{ day.day || i+1 }}</div>
      </div>

      <!-- ===== 出程卡片 ===== -->
      <div class="transport-card transport-depart" v-if="planData?.destination">
        <div class="transport-icon">{{ planData?.transport?.departIcon || '✈️' }}</div>
        <div class="transport-info">
          <div class="transport-title">{{ planData?.transport?.departTitle || '出发前往 ' + planData.destination }}</div>
          <div class="transport-detail">{{ planData?.transport?.departDetail || '建议选上午航班 · 提前2小时到机场' }}</div>
          <div class="transport-price" v-if="planData?.transport?.departPrice">¥{{ planData.transport.departPrice }}起</div>
        </div>
      </div>

      <!-- ===== AI 生成的 Markdown 行程内容 ===== -->
      <div class="markdown-content" v-if="planData?.content" v-html="renderMd(planData.content)"></div>

      <!-- ===== 每日行程 (JSON卡片格式，兼容旧版) ===== -->
      <div class="daily-itinerary" v-if="!planData?.content && (planData?.dayPlans || []).length > 0">
        <div class="section-title">📅 每日行程</div>
        <div v-for="(day, i) in (planData?.dayPlans || [])" :key="'day'+i" class="day-card" :ref="el => setDayRef(el, i)">
          <div class="day-header">
            <div class="day-num">Day{{ day.day || i+1 }}</div>
            <div class="day-title">{{ day.dayTitle || '第'+(i+1)+'天' }}</div>
          </div>
          <div class="timeline">
            <div v-for="(slot, si) in (day.timeSlots || [])" :key="'slot'+si" class="tl-item" :class="{ 'tl-item--last': si === (day.timeSlots||[]).length-1 }">
              <div class="tl-dot" :class="slot.timeOfDay === '上午' ? 'morning' : slot.timeOfDay === '下午' ? 'afternoon' : 'evening'">
                <van-icon :name="slot.timeOfDay==='上午'?'sun-o':slot.timeOfDay==='下午'?'cloud-o':'moon-o'" size="12" />
              </div>
              <div class="tl-line" />
              <div class="tl-content">
                <div class="tl-time"><span class="tl-time-badge">{{ slot.timeOfDay }}</span><span class="tl-time-text">{{ slot.time }}</span></div>
                <div class="tl-attraction">{{ slot.attraction }}</div>
                <div class="tl-desc">{{ slot.activity }}</div>
                <div class="tl-meta">
                  <span class="tl-meta-item"><van-icon name="clock-o" size="12" />{{ slot.duration }}</span>
                  <span class="tl-meta-item tl-meta-cost"><van-icon name="balance-o" size="12" color="#52c41a" />{{ slot.cost }}</span>
                  <span v-if="slot.transport" class="tl-meta-item"><van-icon name="guide-o" size="12" color="#8B5CF6" />{{ slot.transport }}</span>
                </div>
                <div v-if="slot.tips" class="tl-tip"><van-icon name="info-o" size="12" color="#faad14" /><span>{{ slot.tips }}</span></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 返程卡片 ===== -->
      <div class="transport-card transport-return" v-if="planData?.destination">
        <div class="transport-icon">{{ planData?.transport?.returnIcon || '🛬' }}</div>
        <div class="transport-info">
          <div class="transport-title">{{ planData?.transport?.returnTitle || '从 ' + planData.destination + ' 返程' }}</div>
          <div class="transport-detail">{{ planData?.transport?.returnDetail || '建议选傍晚航班 · 预留时间前往机场' }}</div>
          <div class="transport-price" v-if="planData?.transport?.returnPrice">¥{{ planData.transport.returnPrice }}起</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, linkify: true, typographer: true })
function renderMd(t) { return t ? md.render(t) : '' }

const props = defineProps({
  planData: { type: Object, default: null },
  costBreakdown: { type: Object, default: null },
  hotelList: { type: Array, default: () => [] },
  drawerState: { type: String, default: 'mid' },
})

defineEmits(['share', 'like', 'dislike', 'save'])

/* ===== 每日行程导航 ===== */
const activeDayIndex = ref(0)
const dayRefs = ref([])
function setDayRef(el, i) { if (el) dayRefs.value[i] = el }
function scrollToDay(i) {
  activeDayIndex.value = i
  nextTick(() => {
    const el = dayRefs.value[i]
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}
</script>

<style scoped>
.completed-state { display:flex; flex-direction:column; padding-bottom:100px; }
.trip-title-bar { display:flex; align-items:center; justify-content:space-between; padding:16px; border-bottom:1px solid rgba(139,92,246,.06); flex-shrink:0; }
.title-left { display:flex; flex-direction:column; gap:4px; min-width:0; }
.trip-dest { font-size:20px; font-weight:700; color:#1e293b; margin:0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:180px; }
.trip-meta { font-size:13px; color:#94a3b8; }
.title-right { display:flex; flex-direction:column; align-items:flex-end; gap:2px; flex-shrink:0; }
.total-price { font-size:22px; font-weight:700; color:#8b5cf6; }
.price-label { font-size:11px; color:#94a3b8; }

/* 概览 */
.trip-overview { padding:12px 16px; display:flex; flex-direction:column; gap:10px; }
.overview-text { font-size:14px; color:#64748b; line-height:1.6; margin:0; }
.ai-badge { display:inline-flex; align-items:center; gap:6px; align-self:flex-start; padding:6px 12px; background:rgba(139,92,246,.06); border-radius:12px; font-size:12px; color:#8b5cf6; }

/* 操作按钮 */
.action-row { display:flex; justify-content:space-around; padding:8px 12px; margin:0 12px; background:#f8fafc; border-radius:14px; }
.action-btn { display:flex; flex-direction:column; align-items:center; gap:4px; padding:10px 16px; cursor:pointer; color:#64748b; font-size:11px; border-radius:12px; transition:all .2s; user-select:none; }
.action-btn:active { background:rgba(139,92,246,.08); color:#8b5cf6; transform:scale(.95); }

/* 日期导航 */
.day-nav { display:flex; gap:8px; padding:12px 16px; overflow-x:auto; -webkit-overflow-scrolling:touch; }
.day-nav::-webkit-scrollbar { display:none; }
.day-nav-btn { flex-shrink:0; padding:8px 16px; border-radius:20px; font-size:13px; font-weight:600; background:#f1f5f9; color:#64748b; cursor:pointer; transition:all .2s; }
.day-nav-btn--active { background:linear-gradient(135deg,#8B5CF6,#6366F1); color:#fff; box-shadow:0 2px 8px rgba(139,92,246,.3); }

/* Markdown 内容 */
.markdown-content { padding:12px 16px; font-size:14px; color:#334155; line-height:1.8; }
.markdown-content :deep(h2) { font-size:18px; font-weight:700; color:#1e293b; margin:20px 0 10px; padding-bottom:8px; border-bottom:2px solid rgba(139,92,246,.15); }
.markdown-content :deep(h3) { font-size:16px; font-weight:600; color:#334155; margin:16px 0 8px; }
.markdown-content :deep(p) { margin:6px 0; }
.markdown-content :deep(ul), .markdown-content :deep(ol) { padding-left:18px; margin:8px 0; }
.markdown-content :deep(li) { margin:4px 0; }
.markdown-content :deep(strong) { color:#1e293b; font-weight:600; }
.markdown-content :deep(table) { width:100%; border-collapse:collapse; margin:12px 0; font-size:13px; }
.markdown-content :deep(th) { background:rgba(139,92,246,.08); padding:8px 12px; text-align:left; font-weight:600; color:#8b5cf6; }
.markdown-content :deep(td) { padding:8px 12px; border-bottom:1px solid #f1f5f9; }
.markdown-content :deep(blockquote) { border-left:3px solid #8b5cf6; padding:8px 14px; margin:12px 0; background:rgba(139,92,246,.04); border-radius:0 8px 8px 0; color:#64748b; }
.markdown-content :deep(code) { background:#f1f5f9; padding:2px 6px; border-radius:4px; font-size:13px; }

/* 线路预览 */
.route-preview { padding:0 12px; }
.route-scroll { display:flex; gap:10px; overflow-x:auto; -webkit-overflow-scrolling:touch; padding-bottom:4px; }
.route-scroll::-webkit-scrollbar { display:none; }
.route-card { flex-shrink:0; width:130px; background:#fff; border-radius:14px; padding:12px; box-shadow:0 2px 8px rgba(0,0,0,.04); cursor:pointer; transition:transform .2s; }
.route-card:active { transform:scale(.96); }
.route-day { font-size:11px; font-weight:700; color:#8b5cf6; margin-bottom:6px; }
.route-dest { font-size:13px; font-weight:600; color:#1e293b; margin-bottom:4px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.route-spots { font-size:11px; color:#94a3b8; line-height:1.4; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }

/* 出程/返程卡片 */
.transport-card { display:flex; gap:12px; margin:12px 16px; padding:14px; border-radius:14px; align-items:center; }
.transport-depart { background:linear-gradient(135deg,#eff6ff,#dbeafe); border:1px solid #bfdbfe; }
.transport-return { background:linear-gradient(135deg,#fef3c7,#fde68a); border:1px solid #fcd34d; }
.transport-icon { font-size:32px; flex-shrink:0; }
.transport-info { flex:1; }
.transport-title { font-size:14px; font-weight:600; color:#1e293b; margin-bottom:4px; }
.transport-detail { font-size:12px; color:#64748b; line-height:1.5; }
.transport-price { font-size:14px; font-weight:700; color:#8b5cf6; margin-top:6px; }

/* 每日行程 */
.daily-itinerary { padding:0 12px; }
.section-title { font-size:16px; font-weight:700; color:#1e293b; padding:16px 4px 12px; }
.section-title--mt { margin-top:20px; }
.day-card { background:#fff; border-radius:18px; padding:18px; margin-bottom:14px; box-shadow:0 4px 16px rgba(0,0,0,.05); border:1px solid rgba(139,92,246,.05); }
.day-header { display:flex; align-items:center; gap:12px; margin-bottom:18px; }
.day-num { font-size:18px; font-weight:700; color:#8b5cf6; background:rgba(139,92,246,.08); padding:6px 14px; border-radius:12px; }
.day-title { font-size:16px; font-weight:600; color:#1e293b; }
.timeline { position:relative; padding-left:0; }
.tl-item { display:flex; gap:10px; position:relative; padding-bottom:18px; }
.tl-item--last { padding-bottom:0; }
.tl-dot { width:24px; height:24px; border-radius:50%; display:flex; align-items:center; justify-content:center; flex-shrink:0; color:#fff; z-index:1; margin-top:2px; }
.tl-dot.morning { background:linear-gradient(135deg,#fbbf24,#f59e0b); box-shadow:0 2px 8px rgba(245,158,11,.3); }
.tl-dot.afternoon { background:linear-gradient(135deg,#60a5fa,#3b82f6); box-shadow:0 2px 8px rgba(59,130,246,.3); }
.tl-dot.evening { background:linear-gradient(135deg,#a78bfa,#8b5cf6); box-shadow:0 2px 8px rgba(139,92,246,.3); }
.tl-line { position:absolute; left:11px; top:28px; bottom:0; width:2px; background:linear-gradient(180deg,#ddd6fe,#e0e7ff); }
.tl-item--last .tl-line { display:none; }
.tl-content { flex:1; min-width:0; padding-bottom:4px; }
.tl-time { display:flex; align-items:center; gap:8px; margin-bottom:6px; }
.tl-time-badge { background:rgba(139,92,246,.08); color:#8b5cf6; font-size:11px; font-weight:500; padding:2px 10px; border-radius:10px; }
.tl-time-text { font-size:12px; color:#94a3b8; }
.tl-attraction { font-size:15px; font-weight:600; color:#1e293b; margin-bottom:4px; }
.tl-desc { font-size:13px; color:#64748b; line-height:1.5; margin-bottom:8px; }
.tl-meta { display:flex; flex-wrap:wrap; gap:12px; }
.tl-meta-item { display:flex; align-items:center; gap:4px; font-size:12px; color:#94a3b8; }
.tl-meta-cost { color:#52c41a; font-weight:500; }
.tl-tip { display:flex; align-items:flex-start; gap:6px; margin-top:8px; padding:8px 10px; background:#fffbeb; border-radius:8px; font-size:12px; color:#f59e0b; line-height:1.5; }

</style>
