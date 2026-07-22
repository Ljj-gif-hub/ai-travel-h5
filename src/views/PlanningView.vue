<template>
  <div class="planning-page">
    <div class="page-container">
      <van-nav-bar
        class="page-navbar"
        title="AI 行程规划"
        left-arrow
        @click-left="goBack"
      />

    <div class="trip-overview">
      <div class="overview-card">
        <div class="overview-layout">
          <div class="overview-image-section">
            <img 
              :src="destinationImage"
              :alt="destination"
              class="overview-image"
              loading="lazy"
              @error="handleDestinationImageError"
            />
            <div class="image-badge">热门推荐</div>
          </div>
          
          <div class="overview-info-section">
            <div class="overview-row">
              <div class="overview-item">
                <van-icon name="location-o" color="#7c3aed" size="18" />
                <div class="item-text">
                  <span class="label">目的地</span>
                  <span class="value">{{ destination }}</span>
                </div>
              </div>
              <div class="overview-divider"></div>
              <div class="overview-item">
                <van-icon name="calendar-o" color="#7c3aed" size="18" />
                <div class="item-text">
                  <span class="label">天数</span>
                  <span class="value highlight">{{ days }} 天</span>
                </div>
              </div>
            </div>
            <div class="overview-divider-horizontal"></div>
            <div class="overview-row">
              <div class="overview-item">
                <van-icon name="balance-o" color="#7c3aed" size="18" />
                <div class="item-text">
                  <span class="label">预算</span>
                  <span class="value highlight">¥{{ budget }}</span>
                </div>
              </div>
              <div class="overview-divider"></div>
              <div class="overview-item">
                <van-icon name="friends-o" color="#7c3aed" size="18" />
                <div class="item-text">
                  <span class="label">人数</span>
                  <span class="value">{{ people }}人</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="main-content">
      <div class="section-card ai-plan-card">
        <div class="section-header">
          <div class="header-left">
            <van-icon name="magic-o" color="#7c3aed" size="18" />
            <span class="section-title">AI 行程规划</span>
          </div>
          <div class="header-right">
            <van-tag v-if="!isLoading" type="success" plain round size="small" class="status-badge">已完成</van-tag>
            <van-tag v-else type="primary" plain round size="small" class="status-badge">生成中...</van-tag>
            <van-button
              v-if="structuredPlan && !isLoading"
              size="mini"
              type="primary"
              plain
              round
              class="save-plan-btn"
              :loading="isSavingPlan"
              :disabled="isSaved"
              @click="savePlan"
            >
              <van-icon :name="isSaved ? 'success' : 'bookmark-o'" size="12" />
              {{ isSaved ? '已保存' : '保存' }}
            </van-button>
          </div>
        </div>

        <div v-if="isLoading" class="loading-state">
          <div class="loading-spinner"></div>
          <span>AI 正在为你规划行程...</span>
        </div>

        <!-- 流式模式：逐天渲染（复用原有 CSS class，保证样式一致） -->
        <div v-if="streamMode" class="plan-content">
          <div v-for="(day, i) in dailyTrips" :key="'sd'+i" class="day-card" :style="{ animation: 'fadeInDay 0.4s ease forwards' }">
            <div class="day-card-header">
              <div class="day-number-wrapper">
                <span class="day-number">{{ day.day || (i+1) }}</span>
                <span class="day-label">DAY</span>
              </div>
              <div class="day-info">
                <h3 class="day-title">{{ day.dayTitle || '第'+(i+1)+'天' }}</h3>
                <p class="day-subtitle">探索 {{ destination }}</p>
              </div>
            </div>
            <div v-if="day.timeSlots" class="timeline">
              <div v-for="(slot, si) in day.timeSlots" :key="si" class="timeline-item">
                <div class="timeline-line"></div>
                <div class="timeline-dot" :class="slot.timeOfDay === '上午' ? 'morning' : slot.timeOfDay === '下午' ? 'afternoon' : 'evening'">
                  <van-icon :name="slot.timeOfDay === '上午' ? 'sun-o' : slot.timeOfDay === '下午' ? 'cloud-o' : 'moon-o'" size="14" />
                </div>
                <div class="timeline-content">
                  <div class="time-slot-header">
                    <span class="time-badge">{{ slot.timeOfDay }}</span>
                    <span class="time-text">{{ slot.time }}</span>
                  </div>
                  <div class="attraction-card">
                    <div class="attraction-image">
                      <svg viewBox="0 0 160 120" fill="none">
                        <rect width="160" height="120" rx="12" fill="#e8f4f8"/>
                        <rect x="50" y="32" width="60" height="44" rx="6" fill="#ccdde4"/>
                        <path d="M20 20 Q40 10 60 20" stroke="#ccdde4" stroke-width="1.5" fill="none"/>
                      </svg>
                    </div>
                    <div class="attraction-info">
                      <h4 class="attraction-name">{{ slot.attraction }}</h4>
                      <p class="attraction-activity">{{ slot.activity }}</p>
                      <div class="attraction-details">
                        <div class="detail-item"><van-icon name="clock-o" size="14" color="#999"/><span>{{ slot.duration }}</span></div>
                        <div class="detail-item"><van-icon name="balance-o" size="14" color="#52c41a"/><span class="cost">{{ slot.cost }}</span></div>
                        <div class="detail-item"><van-icon name="guide-o" size="14" color="#999"/><span>{{ slot.transport }}</span></div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div v-if="!isDetailDone" v-for="s in (days - dailyTrips.length)" :key="'sk'+s" class="day-card skeleton-day">
            <div class="sk-header"></div><div class="sk-line"></div><div class="sk-line short"></div>
          </div>

              <!-- 流式预算+贴士卡片：使用统一 class 复用 scoped CSS -->
          <div v-if="budgetData" class="section-card" :style="{ animation: 'fadeInDay 0.4s ease forwards' }">
            <div class="section-header"><div class="header-left"><van-icon name="balance-list-o" color="#52c41a" size="18"/><span class="section-title">预算明细</span></div></div>
            <div class="budget-list">
              <div class="budget-item"><span class="budget-label">交通费用</span><span class="budget-amount">¥{{ budgetData.trafficCost }}</span></div><div class="budget-divider"></div>
              <div class="budget-item"><span class="budget-label">住宿费用</span><span class="budget-amount">¥{{ budgetData.hotelCost }}</span></div><div class="budget-divider"></div>
              <div class="budget-item"><span class="budget-label">餐饮费用</span><span class="budget-amount">¥{{ budgetData.foodCost }}</span></div><div class="budget-divider"></div>
              <div class="budget-item"><span class="budget-label">其他杂费</span><span class="budget-amount">¥{{ budgetData.otherCost }}</span></div><div class="budget-divider"></div>
              <div class="budget-total"><span>总计</span><span class="total-amount">¥{{ budgetData.totalBudget }}</span></div>
            </div>
          </div>
          <div v-if="tipList.length > 0" class="section-card" :style="{ animation: 'fadeInDay 0.4s ease forwards' }">
            <div class="section-header"><div class="header-left"><van-icon name="lightbulb-o" color="#faad14" size="18"/><span class="section-title">温馨提示</span></div></div>
            <div class="tips-list">
              <div v-for="(tip, ti) in tipList" :key="ti" class="tip-item"><van-icon name="check-circle" color="#52c41a" size="16"/><span class="tip-text">{{ tip }}</span></div>
            </div>
          </div>
        </div>

        <div v-else-if="structuredPlan" class="plan-content">
          <div v-for="dayPlan in structuredPlan.dayPlans" :key="dayPlan.day" class="day-card">
            <div class="day-card-header">
              <div class="day-number-wrapper">
                <span class="day-number">{{ dayPlan.day }}</span>
                <span class="day-label">DAY</span>
              </div>
              <div class="day-info">
                <h3 class="day-title">{{ dayPlan.dayTitle || `第${dayPlan.day}天` }}</h3>
                <p class="day-subtitle">探索 {{ destination }}</p>
              </div>
            </div>

            <div class="timeline">
              <div 
                v-for="(slot, index) in dayPlan.timeSlots" 
                :key="index" 
                class="timeline-item"
              >
                <div class="timeline-line"></div>
                <div class="timeline-dot" :class="slot.timeOfDay === '上午' ? 'morning' : slot.timeOfDay === '下午' ? 'afternoon' : 'evening'">
                  <van-icon 
                    :name="slot.timeOfDay === '上午' ? 'sun-o' : slot.timeOfDay === '下午' ? 'cloud-o' : 'moon-o'" 
                    size="14" 
                  />
                </div>
                <div class="timeline-content">
                  <div class="time-slot-header">
                    <span class="time-badge">{{ slot.timeOfDay }}</span>
                    <span class="time-text">{{ slot.time }}</span>
                  </div>
                  
                  <div class="attraction-card">
                    <div class="attraction-image">
                      <!-- pending/loading: 携程风 SVG 占位（行李箱+山水线条） -->
                      <div v-if="slot.imageStatus === 'pending' || slot.imageStatus === 'loading'" class="image-loading">
                        <svg class="shimmer-svg" viewBox="0 0 160 120" fill="none">
                          <rect width="160" height="120" rx="12" fill="#e8f4f8" />
                          <rect x="50" y="32" width="60" height="44" rx="6" fill="#ccdde4" />
                          <rect x="62" y="44" width="8" height="4" rx="2" fill="#bcccd4" />
                          <rect x="74" y="38" width="20" height="3" rx="1.5" fill="#bcccd4" />
                          <rect x="62" y="56" width="12" height="2" rx="1" fill="#d0dde4" />
                          <path d="M82 58 L90 68 L70 68 L78 58" fill="#bcccd4" />
                          <path d="M20 20 Q40 10 60 20" stroke="#ccdde4" stroke-width="1.5" fill="none" />
                          <path d="M100 100 Q120 90 140 100" stroke="#ccdde4" stroke-width="1.5" fill="none" />
                        </svg>
                        <span class="loading-text">图片加载中</span>
                      </div>
                      <!-- success: 渲染图片 -->
                      <img
                        v-else-if="slot.imageStatus === 'success'"
                        :src="slot.imgUrl"
                        :alt="slot.attraction"
                        class="slot-img"
                        loading="lazy"
                        @error="handleImageError(slot)"
                      />
                      <!-- error: 兜底 SVG 占位 -->
                      <div v-else class="image-placeholder">
                        <svg viewBox="0 0 160 120" fill="none">
                          <rect width="160" height="120" rx="12" fill="#f1f5f9" />
                          <rect x="50" y="36" width="60" height="48" rx="6" fill="#e2e8f0" />
                          <path d="M70 48 L80 42 L90 48" stroke="#cbd5e1" stroke-width="1.5" fill="none" />
                          <line x1="60" y1="54" x2="100" y2="54" stroke="#cbd5e1" stroke-width="1.2" />
                          <circle cx="70" cy="66" r="6" stroke="#cbd5e1" stroke-width="1.2" fill="none" />
                          <path d="M75 66 L85 66" stroke="#cbd5e1" stroke-width="1.2" />
                          <text x="80" y="96" text-anchor="middle" fill="#94a3b8" font-size="9">图片加载失败，点击重试</text>
                        </svg>
                      </div>
                    </div>
                    <div class="attraction-info">
                      <h4 class="attraction-name">{{ slot.attraction }}</h4>
                      <p class="attraction-activity">{{ slot.activity }}</p>
                      
                      <div class="attraction-details">
                        <div class="detail-item">
                          <van-icon name="clock-o" size="14" color="#999" />
                          <span>{{ slot.duration }}</span>
                        </div>
                        <div class="detail-item">
                          <van-icon name="balance-o" size="14" color="#52c41a" />
                          <span class="cost">{{ slot.cost }}</span>
                        </div>
                        <div class="detail-item">
                          <van-icon name="car" size="14" color="#7c3aed" />
                          <span>{{ slot.transport }}</span>
                        </div>
                      </div>
                      
                      <div v-if="slot.tips" class="attraction-tips">
                        <van-icon name="info-o" size="14" color="#faad14" />
                        <span>{{ slot.tips }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="errorMsg" class="error-state">
          <van-icon name="cross-circle" size="32" color="#ff4d4f" />
          <p>{{ errorMsg }}</p>
          <van-button type="primary" size="small" @click="fetchTravelPlan">重试</van-button>
        </div>

        <div v-else class="empty-state">
          <van-icon name="info-o" size="24" color="#ccc" />
          <span>暂无行程规划内容</span>
          <p class="empty-hint">AI行程生成失败，请返回重新生成行程</p>
        </div>
      </div>

      <div class="detail-grid" v-if="!streamMode">
        <div class="section-card budget-card">
          <div class="section-header">
            <div class="header-left">
              <van-icon name="balance-list-o" color="#52c41a" size="18" />
              <span class="section-title">预算明细</span>
            </div>
          </div>
          <div class="budget-chart">
            <div v-if="structuredPlan?.budgetDetail" class="chart-bars">
              <div class="bar-item">
                <div class="bar-label">交通</div>
                <div class="bar-track">
                  <div class="bar-fill bar-transport" :style="{ width: getBudgetPercent(structuredPlan.budgetDetail.transportation) + '%' }"></div>
                </div>
                <div class="bar-value">¥{{ structuredPlan.budgetDetail.transportation }}</div>
              </div>
              <div class="bar-item">
                <div class="bar-label">住宿</div>
                <div class="bar-track">
                  <div class="bar-fill bar-accommodation" :style="{ width: getBudgetPercent(structuredPlan.budgetDetail.accommodation) + '%' }"></div>
                </div>
                <div class="bar-value">¥{{ structuredPlan.budgetDetail.accommodation }}</div>
              </div>
              <div class="bar-item">
                <div class="bar-label">餐饮</div>
                <div class="bar-track">
                  <div class="bar-fill bar-food" :style="{ width: getBudgetPercent(structuredPlan.budgetDetail.food) + '%' }"></div>
                </div>
                <div class="bar-value">¥{{ structuredPlan.budgetDetail.food }}</div>
              </div>
              <div class="bar-item">
                <div class="bar-label">门票</div>
                <div class="bar-track">
                  <div class="bar-fill bar-tickets" :style="{ width: getBudgetPercent(structuredPlan.budgetDetail.tickets) + '%' }"></div>
                </div>
                <div class="bar-value">¥{{ structuredPlan.budgetDetail.tickets }}</div>
              </div>
              <div class="bar-item">
                <div class="bar-label">其他</div>
                <div class="bar-track">
                  <div class="bar-fill bar-other" :style="{ width: getBudgetPercent(structuredPlan.budgetDetail.other) + '%' }"></div>
                </div>
                <div class="bar-value">¥{{ structuredPlan.budgetDetail.other }}</div>
              </div>
            </div>
            <div v-else class="budget-list">
              <div class="budget-item">
                <span class="budget-label">交通费用</span>
                <span class="budget-amount">¥{{ Math.round(budget * 0.3) }}</span>
              </div>
              <div class="budget-divider"></div>
              <div class="budget-item">
                <span class="budget-label">住宿费用</span>
                <span class="budget-amount">¥{{ Math.round(budget * 0.4) }}</span>
              </div>
              <div class="budget-divider"></div>
              <div class="budget-item">
                <span class="budget-label">餐饮费用</span>
                <span class="budget-amount">¥{{ Math.round(budget * 0.2) }}</span>
              </div>
              <div class="budget-divider"></div>
              <div class="budget-item">
                <span class="budget-label">其他杂费</span>
                <span class="budget-amount">¥{{ Math.round(budget * 0.1) }}</span>
              </div>
              <div class="budget-divider"></div>
              <div class="budget-total">
                <span>总计</span>
                <span class="total-amount">¥{{ budget }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="section-card tips-card">
          <div class="section-header">
            <div class="header-left">
              <van-icon name="lightbulb-o" color="#faad14" size="18" />
              <span class="section-title">温馨提示</span>
            </div>
          </div>
          <div class="tips-list">
            <div v-for="(tip, index) in displayTips" :key="index" class="tip-item">
              <van-icon name="check-circle" color="#52c41a" size="16" />
              <span class="tip-text">{{ tip }}</span>
            </div>
          </div>
        </div>
      </div>

    </div>
    </div>

    <div class="bottom-safe-area"></div>
  </div>

  <div class="action-bar-integrated" :class="{ hidden: showChatPanel }">
    <div 
      class="action-btn copy-btn"
      :class="{ disabled: !structuredPlan }"
      @click="structuredPlan && copyPlan"
    >
      <van-icon name="documents-o" size="16" />
      <span>复制行程</span>
    </div>
    <div 
      class="action-btn ai-btn"
      @click="openChatPanel"
    >
      <van-icon name="service-o" size="16" />
      <span>咨询AI助手</span>
    </div>
  </div>

    <van-popup
      v-model:show="showChatPanel"
      position="bottom"
      round
      closeable
      close-icon="arrow-down"
      :style="{ height: 'calc(90% - env(safe-area-inset-bottom))', display: 'flex', flexDirection: 'column', paddingBottom: 'env(safe-area-inset-bottom)' }"
      class="chat-popup"
    >
      <div class="chat-panel-header">
        <div class="chat-panel-title">
          <van-icon name="service-o" color="#7c3aed" size="18" />
          <span>AI 旅游助手</span>
        </div>
        <van-button
          size="mini"
          plain
          round
          class="view-plan-btn"
          @click="showChatPanel = false"
        >
          <van-icon name="eye-o" size="12" />
          查看规划
        </van-button>
      </div>

      <div class="chat-panel-body">
        <div v-if="chatMessages.length === 0" class="chat-guide">
          <van-icon name="comment-o" class="chat-guide-icon" />
          <p class="chat-guide-title">向 AI 助手开始对话吧！</p>
          <p class="chat-guide-desc">
            当前行程：{{ destination }} · {{ days }}天 · ¥{{ budget }}<br/>
            可询问景点细节、美食推荐、交通建议等
          </p>
          <div class="chat-quick-questions">
            <van-button
              round
              plain
              type="primary"
              size="small"
              class="chat-q-btn"
              @click="sendQuickQuestion(`${destination}有哪些必去景点？`)"
            >
              必去景点
            </van-button>
            <van-button
              round
              plain
              type="primary"
              size="small"
              class="chat-q-btn"
              @click="sendQuickQuestion(`${destination}有什么特色美食？`)"
            >
              特色美食
            </van-button>
            <van-button
              round
              plain
              type="primary"
              size="small"
              class="chat-q-btn"
              @click="sendQuickQuestion(`${days}天的行程如何安排更合理？`)"
            >
              行程建议
            </van-button>
          </div>
        </div>

        <div v-else ref="chatScrollRef" class="chat-message-list">
          <div
            v-for="msg in chatMessages"
            :key="msg.id"
            :class="['chat-msg-wrapper', msg.type]"
          >
            <div v-if="msg.type === 'user'" class="chat-msg-row chat-user-row">
              <div class="chat-bubble chat-user-bubble">{{ msg.content }}</div>
              <div class="chat-avatar chat-user-avatar">
                <van-icon name="user-o" color="#fff" size="16" />
              </div>
            </div>
            <div v-else-if="msg.type === 'ai'" class="chat-msg-row chat-ai-row">
              <div class="chat-avatar chat-ai-avatar">
                <van-icon name="service-o" color="#7c3aed" size="16" />
              </div>
              <div class="chat-bubble chat-ai-bubble">
                <div
                  v-if="isThinking && msg === chatMessages[chatMessages.length - 1]"
                  class="chat-thinking"
                >
                  <div class="chat-thinking-dots">
                    <span></span><span></span><span></span>
                  </div>
                  <span class="chat-thinking-text">正在思考...</span>
                </div>
                <div
                  v-else
                  class="chat-markdown"
                  v-html="renderMarkdown(msg.content)"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input-footer">
        <div class="chat-input-box">
          <van-field
            v-model="chatInput"
            placeholder="询问景点细节、美食、交通..."
            clearable
            @keyup.enter="sendChatMessage"
            class="chat-input-field"
          />
          <van-button
            type="primary"
            size="small"
            class="chat-send-btn"
            :disabled="!chatInput.trim() || isSending"
            @click="sendChatMessage"
          >
            <van-loading v-if="isSending" size="14px" color="#fff" />
            <span v-else>发送</span>
          </van-button>
        </div>
      </div>
    </van-popup>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  NavBar, Button, Icon, Tag, Field, Loading, Popup, showToast, showLoadingToast, showSuccessToast, closeToast
} from 'vant'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { planApi, sceneApi, chatApi } from '../api'

const router = useRouter()
const route = useRoute()

// ============ 流式模式 ============
const streamMode = ref(route.query.streamMode === 'true')
const taskId = ref(route.query.taskId || '')
const dailyTrips = ref([])       // 逐天追加的行程数组
const budgetData = ref(null)     // 预算数据
const tipList = ref([])          // 温馨提示列表
const isDetailDone = ref(false)  // 全部推送完成
const detailAbort = ref(null)

const connectStreamDetail = () => {
  if (!streamMode.value) { return }
  const ctrl = new AbortController()
  detailAbort.value = ctrl
  fetch('/api/travel/planner/stream-detail', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
    body: JSON.stringify({ destination: destination.value, days: days.value, budget: budget.value, taskId: taskId.value }),
    signal: ctrl.signal,
  }).then(async (resp) => {
    if (!resp.ok) { throw new Error('HTTP ' + resp.status) }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) { break }
      buf += decoder.decode(value, { stream: true })
      const lines = buf.split('\n')
      buf = lines.pop() || ''
      for (const line of lines) {
        const t = line.trim()
        if (!t.startsWith('data:')) { continue }
        const payload = t.startsWith('data: ') ? t.slice(6) : t.slice(5)
        try { handleStreamEvent(JSON.parse(payload.trim())) } catch (e) {}
      }
    }
  }).catch((e) => {
    if (e.name !== 'AbortError') { console.error('详情SSE失败', e) }
  })
}

const handleStreamEvent = (data) => {
  if (!data || !data.eventType) { return }
  switch (data.eventType) {
    case 'day-update':
      dailyTrips.value.push(data.dayData || data)
      break
    case 'budget-update':
      budgetData.value = { trafficCost: data.trafficCost, hotelCost: data.hotelCost, foodCost: data.foodCost, otherCost: data.otherCost, totalBudget: data.totalBudget }
      break
    case 'tips-update':
      tipList.value = data.tipList || []
      break
    case 'detail-finish':
      isDetailDone.value = true
      break
    case 'task-stop':
      if (detailAbort.value) { detailAbort.value.abort() }
      showToast('行程生成已终止，仅展示已生成天数')
      break
    case 'stream-error':
      showToast('生成失败：' + (data.message || ''))
      if (detailAbort.value) { detailAbort.value.abort() }
      break
  }
}

const stopStreamDetail = () => {
  if (detailAbort.value) { detailAbort.value.abort(); detailAbort.value = null }
  fetch('/api/travel/planner/stop', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ taskId: taskId.value }),
  }).catch(() => {})
}
// ============ 流式模式结束 ============

const destination = ref(route.query.destination || '北京')
const budget = ref(Number(route.query.budget) || 5000)
const days = ref(Number(route.query.days) || 2)
const people = ref(Number(route.query.people) || 2)

const IMAGE_API = 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image'

const getCityImageUrl = (keyword) => {
  const encodedKeyword = encodeURIComponent(`${keyword} 风景 旅游摄影`)
  return `${IMAGE_API}?prompt=${encodedKeyword}&image_size=landscape_4_3`
}

const cityImageMap = {
  '北京': getCityImageUrl('北京故宫'),
  '上海': getCityImageUrl('上海外滩'),
  '广州': getCityImageUrl('广州塔'),
  '深圳': getCityImageUrl('深圳城市夜景'),
  '杭州': getCityImageUrl('杭州西湖'),
  '成都': getCityImageUrl('成都大熊猫'),
  '重庆': getCityImageUrl('重庆山城夜景'),
  '西安': getCityImageUrl('西安兵马俑'),
  '厦门': getCityImageUrl('厦门鼓浪屿'),
  '南京': getCityImageUrl('南京中山陵'),
  '武汉': getCityImageUrl('武汉黄鹤楼'),
  '长沙': getCityImageUrl('长沙橘子洲'),
  '苏州': getCityImageUrl('苏州园林'),
  '青岛': getCityImageUrl('青岛海滨'),
  '大连': getCityImageUrl('大连海边'),
  '宁波': getCityImageUrl('宁波天一阁'),
  '无锡': getCityImageUrl('无锡太湖'),
  '大理': getCityImageUrl('大理洱海'),
  '丽江': getCityImageUrl('丽江古城'),
  '三亚': getCityImageUrl('三亚海滩'),
  '昆明': getCityImageUrl('昆明滇池'),
  '桂林': getCityImageUrl('桂林山水'),
  '黄山': getCityImageUrl('黄山风景'),
  '拉萨': getCityImageUrl('拉萨布达拉宫'),
  '香格里拉': getCityImageUrl('香格里拉'),
  '香港': getCityImageUrl('香港维多利亚港'),
  '澳门': getCityImageUrl('澳门大三巴'),
  '台北': getCityImageUrl('台北故宫'),
  '海口': getCityImageUrl('海口海滩'),
  '郑州': getCityImageUrl('郑州少林寺'),
  '济南': getCityImageUrl('济南趵突泉'),
  '哈尔滨': getCityImageUrl('哈尔滨冰雪'),
  '乌鲁木齐': getCityImageUrl('乌鲁木齐天山'),
}

const defaultImage = getCityImageUrl('旅游风景')

const remoteImage = ref('')

const fetchCityImage = async (city) => {
  remoteImage.value = getCityImageUrl(city)
}

const handleDestinationImageError = () => {
  remoteImage.value = defaultImage
}

const destinationImage = computed(() => {
  if (remoteImage.value) return remoteImage.value
  const city = destination.value.replace('市', '')
  return cityImageMap[city] || defaultImage
})

const structuredPlan = ref(null)
const isLoading = ref(false)
const errorMsg = ref('')

/**
 * 处理图片加载失败（防盗链等原因）
 * 切换为error状态，展示兜底占位图
 * @param {Object} slot - 景点时间槽对象
 */
const handleImageError = (slot) => {
  if (!slot || !slot.attraction) return
  
  console.error(`[图片加载失败] ${slot.attraction}, URL: ${slot.imgUrl}`)
  
  for (let dayIndex = 0; dayIndex < structuredPlan.value.dayPlans.length; dayIndex++) {
    const dayPlan = structuredPlan.value.dayPlans[dayIndex]
    for (let slotIndex = 0; slotIndex < dayPlan.timeSlots.length; slotIndex++) {
      const s = dayPlan.timeSlots[slotIndex]
      if (s.attraction === slot.attraction && s.timeOfDay === slot.timeOfDay && s.time === slot.time) {
        structuredPlan.value.dayPlans[dayIndex].timeSlots[slotIndex] = {
          ...structuredPlan.value.dayPlans[dayIndex].timeSlots[slotIndex],
          imageStatus: 'error',
          imgUrl: null
        }
        console.log(`[图片状态更新] ${slot.attraction} -> error (切换兜底图)`)
        return
      }
    }
  }
}

/**
 * 请求防抖函数
 */
const debounceMap = {}
const debounce = (func, wait) => {
  return function(...args) {
    const slot = args[0]
    let key = func.name
    if (slot && slot._dayIndex !== undefined && slot._slotIndex !== undefined) {
      key = `${func.name}_${slot._dayIndex}_${slot._slotIndex}`
    } else if (slot && slot.attraction) {
      key = `${func.name}_${slot.attraction}`
    } else {
      key = `${func.name}_${Date.now()}`
    }
    if (debounceMap[key]) {
      clearTimeout(debounceMap[key])
    }
    debounceMap[key] = setTimeout(() => {
      func.apply(this, args)
      delete debounceMap[key]
    }, wait)
  }
}

/**
 * 请求景点图片接口
 * @param {Object} slot - 景点时间槽对象（直接操作slot内部字段）
 */
const fetchSceneImage = async (slot) => {
  const attractionName = slot.attraction
  
  if (!attractionName || attractionName.trim() === '') {
    console.log(`[图片请求跳过] 景点名称为空`)
    return
  }
  
  const [dayIndex, slotIndex] = findSlotIndex(slot)
  
  if (dayIndex < 0 || slotIndex < 0) {
    console.error(`[图片请求] 未找到景点索引: ${attractionName}`)
    return
  }
  
  const currentSlot = structuredPlan.value.dayPlans[dayIndex].timeSlots[slotIndex]
  
  if (currentSlot.imageStatus === 'loading' || currentSlot.imageStatus === 'success') {
    console.log(`[图片请求跳过] ${attractionName} 已在请求中或已成功`)
    return
  }
  
  structuredPlan.value.dayPlans[dayIndex].timeSlots[slotIndex] = {
    ...currentSlot,
    imageStatus: 'loading'
  }
  console.log(`[图片状态更新] ${attractionName} -> loading`)
  
  try {
    console.log(`[图片请求开始] attractionName: ${attractionName}, activity: ${slot.activity?.substring(0, 30) || '无'}`)
    
    const params = new URLSearchParams()
    params.append('scenicName', attractionName)
    if (slot.activity && slot.activity.trim() !== '') {
      params.append('scenicDesc', slot.activity)
    }
    
    console.log(`[图片请求] attractionName: ${attractionName}, activity: ${slot.activity?.substring(0, 30) || '无'}`)
    
    const result = await sceneApi.getSceneImage(attractionName, slot.activity)
    console.log(`[图片请求响应] code: ${result.code}, data: ${JSON.stringify(result.data)}`)
    
    if (result.code === 0 && result.data && result.data.imgUrl) {
      structuredPlan.value.dayPlans[dayIndex].timeSlots[slotIndex] = {
        ...structuredPlan.value.dayPlans[dayIndex].timeSlots[slotIndex],
        imageStatus: 'success',
        imgUrl: result.data.imgUrl
      }
      console.log(`[图片状态更新] ${attractionName} -> success, url: ${result.data.imgUrl.substring(0, 50)}...`)
    } else {
      structuredPlan.value.dayPlans[dayIndex].timeSlots[slotIndex] = {
        ...structuredPlan.value.dayPlans[dayIndex].timeSlots[slotIndex],
        imageStatus: 'error',
        imgUrl: null
      }
      console.log(`[图片状态更新] ${attractionName} -> error, 接口返回无有效URL`)
    }
  } catch (error) {
    structuredPlan.value.dayPlans[dayIndex].timeSlots[slotIndex] = {
      ...structuredPlan.value.dayPlans[dayIndex].timeSlots[slotIndex],
      imageStatus: 'error',
      imgUrl: null
    }
    console.error(`[图片请求异常] ${attractionName} -> error, error: ${error.message}`)
  }
}

/**
 * 根据景点信息查找索引
 * @param {Object} slot - 景点时间槽对象
 * @returns {Array} [dayIndex, slotIndex]
 */
const findSlotIndex = (slot) => {
  if (slot._dayIndex !== undefined && slot._slotIndex !== undefined) {
    const dayPlan = structuredPlan.value.dayPlans[slot._dayIndex]
    if (dayPlan && dayPlan.timeSlots[slot._slotIndex]) {
      const s = dayPlan.timeSlots[slot._slotIndex]
      if (s.attraction === slot.attraction) {
        console.log(`[快速定位] ${slot.attraction} -> dayIndex: ${slot._dayIndex}, slotIndex: ${slot._slotIndex}`)
        return [slot._dayIndex, slot._slotIndex]
      }
    }
  }
  
  for (let dayIndex = 0; dayIndex < structuredPlan.value.dayPlans.length; dayIndex++) {
    const dayPlan = structuredPlan.value.dayPlans[dayIndex]
    for (let slotIndex = 0; slotIndex < dayPlan.timeSlots.length; slotIndex++) {
      const s = dayPlan.timeSlots[slotIndex]
      if (s.attraction === slot.attraction && s.timeOfDay === slot.timeOfDay && s.time === slot.time) {
        console.log(`[遍历定位] ${slot.attraction} -> dayIndex: ${dayIndex}, slotIndex: ${slotIndex}`)
        return [dayIndex, slotIndex]
      }
    }
  }
  return [-1, -1]
}

const debouncedFetchSceneImage = debounce(fetchSceneImage, 300)

/**
 * 初始化所有景点的图片状态
 * 在行程数据加载完成后调用
 * 给每个景点增加独立的imageStatus字段
 */
const initImageStatus = () => {
  if (!structuredPlan.value?.dayPlans) {
    console.log('[图片状态初始化] 无行程数据')
    return
  }
  
  let totalCount = 0
  let successCount = 0
  let pendingCount = 0
  
  structuredPlan.value.dayPlans.forEach((dayPlan, dayIndex) => {
    dayPlan.timeSlots.forEach((slot, slotIndex) => {
      if (slot.attraction) {
        totalCount++
        
        const hasUrl = slot.imgUrl && slot.imgUrl.trim() !== ''
        const status = hasUrl ? 'success' : 'pending'
        
        structuredPlan.value.dayPlans[dayIndex].timeSlots[slotIndex] = {
          ...slot,
          imageStatus: status
        }
        
        if (hasUrl) {
          successCount++
          console.log(`[图片状态初始化] 第${dayPlan.day}天 ${slot.attraction} -> success, url: ${slot.imgUrl.substring(0, 50)}...`)
        } else {
          pendingCount++
          console.log(`[图片状态初始化] 第${dayPlan.day}天 ${slot.attraction} -> pending`)
        }
      }
    })
  })
  
  console.log(`[图片状态初始化完成] 总景点: ${totalCount}, 后端有图: ${successCount}, 需请求: ${pendingCount}`)
  
  nextTick(() => {
    loadPendingImages()
  })
}

/**
 * 加载所有pending状态的图片
 */
const loadPendingImages = () => {
  const pendingSlots = []
  
  structuredPlan.value?.dayPlans.forEach((dayPlan, dayIndex) => {
    dayPlan.timeSlots.forEach((slot, slotIndex) => {
      if (slot.attraction && slot.imageStatus === 'pending') {
        pendingSlots.push({
          ...slot,
          _dayIndex: dayIndex,
          _slotIndex: slotIndex
        })
      }
    })
  })
  
  console.log(`[开始加载pending图片] 数量: ${pendingSlots.length}`)
  
  pendingSlots.forEach(slot => {
    debouncedFetchSceneImage(slot)
  })
}

const defaultTips = [
  '随身携带身份证件，以备不时之需',
  '提前预订热门景点门票，避免排队',
  '关注当地天气预报，准备合适衣物',
  '准备常用药品，如感冒药、肠胃药',
  '保管好个人财物，注意安全'
]

const displayTips = computed(() => {
  if (structuredPlan.value?.tips?.length) {
    return structuredPlan.value.tips.slice(0, 5)
  }
  return defaultTips
})

const getBudgetPercent = (value) => {
  if (!value || !structuredPlan.value?.budget) return 0
  return Math.min(100, (value / structuredPlan.value.budget) * 100)
}

const fetchTravelPlan = async () => {
  isLoading.value = true
  errorMsg.value = ''
  structuredPlan.value = null

  fetchCityImage(destination.value)

  showLoadingToast({
    message: 'AI 正在为你规划行程...',
    forbidClick: true,
    loadingType: 'spinner',
  })

  const abortController = new AbortController()
  const timeoutId = setTimeout(() => {
    abortController.abort()
  }, 180000)

  try {
    const result = await planApi.generatePlan({
      destination: destination.value.toString().trim(),
      budget: parseInt(budget.value) || 1000,
      days: parseInt(days.value) || 3,
      people: parseInt(people.value) || 2
    })

    clearTimeout(timeoutId)

    if (result.code === 0 && result.data) {
      structuredPlan.value = result.data
      
      nextTick(() => {
        initImageStatus()
      })
      
      closeToast()
      showSuccessToast('行程规划已完成')
    } else {
      throw new Error(result.message || '生成失败')
    }
  } catch (error) {
    clearTimeout(timeoutId)
    console.error('行程规划请求失败:', error)
    
    if (error.name === 'AbortError') {
      errorMsg.value = '请求超时，请稍后重试'
    } else {
      errorMsg.value = error.message || 'AI 规划失败，请稍后重试'
    }
    showToast(errorMsg.value)
  } finally {
    isLoading.value = false
    closeToast()
  }
}

const isSavingPlan = ref(false)
const isSaved = ref(false)

const savePlan = async () => {
  if (!structuredPlan.value || isSavingPlan.value || isSaved.value) return

  isSavingPlan.value = true
  try {
    const result = await planApi.savePlan({
      destination: destination.value,
      days: Number(days.value),
      budget: Number(budget.value),
      people: Number(people.value),
      planData: structuredPlan.value,
    })

    if (result.code === 0) {
      isSaved.value = true
      showSuccessToast('规划已保存')
    } else {
      showToast(result.message || '保存失败')
    }
  } catch (error) {
    console.error('保存规划失败:', error)
    showToast('保存失败，请稍后重试')
  } finally {
    isSavingPlan.value = false
  }
}

const copyPlan = async () => {
  if (!structuredPlan.value) return
  
  let text = `【${destination.value}${days.value}天旅行规划】\n\n`
  structuredPlan.value.dayPlans.forEach(day => {
    text += `--- ${day.dayTitle} ---\n`
    day.timeSlots.forEach(slot => {
      text += `\n${slot.timeOfDay} ${slot.time}\n`
      text += `📍 ${slot.attraction}\n`
      text += `🎯 ${slot.activity}\n`
      text += `⏱️ ${slot.duration}\n`
      text += `💰 ${slot.cost}\n`
      text += `🚗 ${slot.transport}\n`
      if (slot.tips) text += `💡 ${slot.tips}\n`
    })
    text += '\n'
  })

  try {
    await navigator.clipboard.writeText(text)
    showToast({
      message: '已复制到剪贴板',
      position: 'top',
      style: { 
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', 
        color: '#ffffff', 
        fontSize: '14px', 
        borderRadius: '10px', 
        padding: '12px 20px', 
      },
    })
  } catch (err) {
    showToast('复制失败，请手动复制')
  }
}

const showChatPanel = ref(false)
const chatMessages = ref([])
const chatInput = ref('')
const isSending = ref(false)
const isThinking = ref(false)
const chatScrollRef = ref(null)

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre><code class="hljs language-${lang}">${
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
        }</code></pre>`
      } catch (__) {}
    }
    return `<pre><code class="hljs">${md.utils.escapeHtml(str)}</code></pre>`
  }
})

const preprocessMarkdown = (text) => {
  if (!text) return ''
  let result = text
  result = result.replace(/\\n/g, '\n')
  result = result.replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
  result = result.replace(/^(\s*[-*+])(\S)/gm, '$1 $2')
  result = result.replace(/^(\s*\d+\.)([^\s])/gm, '$1 $2')
  result = result.replace(/<strong>([\s\S]*?)<\/strong>/g, '**$1**')
  result = result.replace(/<strong>([\s\S]*?)<strong>/g, '**$1**')
  result = result.replace(/<\/?strong>/g, '**')
  result = result.replace(/<em>(.*?)<\/em>/g, '*$1*')
  result = result.replace(/<\/?em>/g, '*')
  result = result.replace(/<br\s*\/?>/gi, '\n')
  result = result.replace(/\*\*\s*\*\*/g, '')
  return result
}

const renderMarkdown = (text) => {
  if (!text) return ''
  return md.render(preprocessMarkdown(text))
}

const generateChatId = () =>
  `${Date.now()}_${Math.random().toString(36).slice(2, 9)}`

const scrollChatToBottom = async () => {
  await nextTick()
  if (chatScrollRef.value) {
    const scrollContainer = chatScrollRef.value
    const isNearBottom = scrollContainer.scrollHeight - scrollContainer.scrollTop - scrollContainer.clientHeight < 100
    
    if (isNearBottom) {
      scrollContainer.scrollTo({
        top: scrollContainer.scrollHeight,
        behavior: isSending.value ? 'auto' : 'smooth',
      })
    }
  }
}

const openChatPanel = () => {
  showChatPanel.value = true
  nextTick(() => scrollChatToBottom())
}

const sendQuickQuestion = (question) => {
  chatInput.value = question
  sendChatMessage()
}

const buildPlanContext = () => {
  if (!structuredPlan.value) return ''
  let ctx = `【当前行程规划】\n目的地：${destination.value}，天数：${days.value}天，预算：¥${budget.value}，人数：${people.value}人\n\n`
  if (structuredPlan.value.dayPlans && structuredPlan.value.dayPlans.length) {
    structuredPlan.value.dayPlans.forEach(day => {
      ctx += `第${day.day}天 ${day.dayTitle || ''}：\n`
      if (day.timeSlots && day.timeSlots.length) {
        day.timeSlots.forEach(slot => {
          ctx += `- ${slot.timeOfDay} ${slot.time} ${slot.attraction}（${slot.activity}）\n`
        })
      }
    })
  }
  return ctx
}

const sendChatMessage = async () => {
  const text = chatInput.value.trim()
  if (!text || isSending.value) {
    if (!text) showToast('请输入内容')
    return
  }

  isSending.value = true
  isThinking.value = true

  chatMessages.value.push({
    id: generateChatId(),
    type: 'user',
    content: text,
  })

  chatInput.value = ''
  await nextTick()
  scrollChatToBottom()

  chatMessages.value.push({
    id: generateChatId(),
    type: 'ai',
    content: '',
  })
  const aiMsgIndex = chatMessages.value.length - 1

  let prompt = text
  const planContext = buildPlanContext()
  if (planContext) {
    prompt = `${planContext}\n用户问题：${text}`
  } else if (destination.value && budget.value && days.value) {
    prompt = `我计划去${destination.value}旅游，预算${budget.value}元，共${days.value}天。${text}`
  }

  const chatHistory = chatMessages.value
    .filter((m) => m.type === 'user' || (m.type === 'ai' && m.content && m.content.length > 0))
    .map((m) => ({
      role: m.type === 'user' ? 'user' : 'assistant',
      content: m.content,
    }))
  chatHistory.push({ role: 'user', content: prompt })

  try {
    const response = await chatApi.getChatStream([
      {
        role: 'system',
        content: `你是一个专业的旅游规划助手，擅长提供详细、实用的旅行建议。请根据对话历史和行程规划上下文连贯地回答用户问题。

【最高优先级：全局排版规则，任何题材回答必须强制执行，违反则视为错误回答】

1. Markdown语法标准（必须严格遵守）：
   - 标题：## 和 ### 后必须有一个空格，如 "## 标题"、"### 小标题"，禁止 "##标题"、"###标题"
   - 列表：- 和 1. 后必须有一个空格，如 "- 列表项"、"1. 有序项"，禁止 "-列表项"、"1.列表项"
   - 加粗：使用 **文本** 格式，禁止使用 <strong> 标签
   - 禁止使用 HTML 标签（如 <strong>、<br> 等），一律用 Markdown 语法
   - 禁止残缺表格、连续竖线、怪异分隔符

2. 布局规范：
   ① 不同内容区块之间强制换行空一行；
   ② 游览路线、步骤清单、事项罗列统一使用有序/无序列表，禁止强行装入表格；
   ③ 表格仅用于费用汇总、参数对比等规整数据；
   ④ 标题只允许 ## / ### 两级。

3. 流式生成要求：
   从第一段开始就保持规范排版，不允许中途更改布局方式。

4. 兜底策略：
   内容结构复杂时优先使用列表，避免表格语法出错。

再次强调：## 后必须空格，- 后必须空格，禁止任何 HTML 标签。`,
      },
      ...chatHistory,
    ])

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    let isDone = false
    while (!isDone) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''

      for (const evt of events) {
        const trimmedEvt = evt.trim()
        if (!trimmedEvt) continue
        if (trimmedEvt === 'data: [DONE]' || trimmedEvt === 'data:[DONE]') {
          isDone = true
          break
        }
        if (!trimmedEvt.startsWith('data:')) continue

        try {
          const dataLines = trimmedEvt.split('\n')
          let content = dataLines.map(line => {
            const trimmed = line.trim()
            if (trimmed.startsWith('data: ')) return trimmed.slice(6)
            if (trimmed.startsWith('data:')) return trimmed.slice(5)
            return ''
          }).join('\n')

          if (content && content !== '[DONE]') {
            if (isThinking.value) {
              isThinking.value = false
            }
            chatMessages.value[aiMsgIndex].content += content
            scrollChatToBottom()
          }
        } catch (e) {
          console.warn('SSE解析失败:', evt, e)
        }
      }
    }

    isSending.value = false
    isThinking.value = false
  } catch (e) {
    isSending.value = false
    isThinking.value = false
    showToast('请求失败')
  }
}

const goBack = () => {
  stopStreamDetail()
  if (window.history.length <= 1) {
    router.push('/')
  } else {
    router.back()
  }
}

const loadSavedPlan = async (planId) => {
  isLoading.value = true
  errorMsg.value = ''
  try {
    const result = await planApi.getPlanById(planId)
    if (result.code === 0 && result.data) {
      const data = result.data
      destination.value = data.destination || destination.value
      budget.value = data.budget || budget.value
      days.value = data.days || days.value
      people.value = data.people || people.value
      structuredPlan.value = data.planData
      isSaved.value = true
      fetchCityImage(destination.value)
      
      nextTick(() => {
        initImageStatus()
      })
    } else {
      throw new Error(result.message || '加载失败')
    }
  } catch (error) {
    console.error('加载保存的规划失败:', error)
    errorMsg.value = '加载保存的规划失败'
    showToast(errorMsg.value)
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  // 清空路由缓存残留，避免旧数据和新流式数据堆叠
  budgetData.value = null
  tipList.value = []
  if (streamMode.value) {
    setTimeout(() => { connectStreamDetail() }, 200)
  }
  const savedPlanId = route.query.savedPlanId
  if (savedPlanId) {
    loadSavedPlan(savedPlanId)
  } else if (!streamMode.value) {
    fetchTravelPlan()  // 非流式：一次性加载
  }
})
onUnmounted(() => { stopStreamDetail() })
</script>

<style scoped>
.planning-page {
  width: 100%;
  max-width: 100vw;
  min-height: 100vh;
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
  overflow-x: hidden;
}

.page-container {
  width: 100%;
  max-width: 480px;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 0 16px;
  overflow-x: hidden;
}

.page-navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 999;
  background: linear-gradient(135deg, rgba(233,213,255,0.9) 0%, rgba(240,249,255,0.9) 50%, rgba(253,244,255,0.9) 100%);
  backdrop-filter: blur(12px);
}
.page-navbar :deep(.van-nav-bar__title) {
  color: #1f2937;
  font-weight: 600;
  font-size: 16px;
}
.page-navbar :deep(.van-nav-bar__left) {
  color: #4b5563;
}

.trip-overview {
  padding-top: 46px;
}

.overview-card {
  margin: 16px;
  border-radius: 20px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(233, 213, 255, 0.3) 0%, rgba(240, 249, 255, 0.3) 100%);
  box-shadow: 0 8px 24px rgba(147, 51, 234, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.6);
}

.overview-layout {
  display: flex;
}

.overview-image-section {
  position: relative;
  width: 140px;
  flex-shrink: 0;
}

.overview-image {
  width: 100%;
  height: 140px;
  object-fit: cover;
}

.image-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  background: rgba(255, 255, 255, 0.9);
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 11px;
  color: #667eea;
  font-weight: 500;
}

.overview-info-section {
  flex: 1;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.overview-row {
  display: flex;
  align-items: center;
  flex: 1;
}

.overview-item {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-text {
  display: flex;
  flex-direction: column;
}

.item-text .label {
  font-size: 11px;
  color: #888;
}

.item-text .value {
  font-size: 14px;
  color: #444;
  font-weight: 500;
}

.item-text .value.highlight {
  color: #7c3aed;
  font-weight: 700;
}

.overview-divider {
  width: 1px;
  height: 32px;
  background: rgba(147, 51, 234, 0.1);
}

.overview-divider-horizontal {
  height: 1px;
  background: rgba(147, 51, 234, 0.08);
  margin: 12px 0;
}

.main-content {
  flex: 1;
  padding: 0 12px 12px;
  padding-bottom: 120px;
}

.section-card {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.save-plan-btn {
  border-color: #667eea !important;
  color: #667eea !important;
  font-size: 11px;
  padding: 0 10px;
  height: 24px;
}

.save-plan-btn.van-button--disabled {
  opacity: 0.6;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.status-badge {
  font-size: 12px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 0;
  gap: 12px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #f0f0f0;
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-state span {
  font-size: 14px;
  color: #999;
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 0;
  gap: 12px;
}

.error-state p {
  font-size: 14px;
  color: #666;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 0;
  gap: 12px;
  color: #ccc;
  font-size: 14px;
}

.day-card {
  background: #fff;
  border-radius: 20px;
  padding: 20px;
  margin-bottom: 16px;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.05);
}

.day-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(147, 51, 234, 0.1);
}

.day-card-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.day-number-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #ddd6fe 0%, #c4b5fd 100%);
  border-radius: 50%;
  color: #7c3aed;
  box-shadow: 0 4px 12px rgba(147, 51, 234, 0.15);
}

.day-number {
  font-size: 22px;
  font-weight: 700;
}

.day-label {
  font-size: 10px;
  opacity: 0.8;
  font-weight: 500;
}

.day-info {
  flex: 1;
}

.day-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.day-subtitle {
  font-size: 12px;
  color: #999;
  margin: 4px 0 0;
}

.timeline {
  position: relative;
  padding-left: 8px;
}

.timeline-item {
  display: flex;
  gap: 14px;
  position: relative;
  padding-bottom: 20px;
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-line {
  position: absolute;
  left: 5px;
  top: 26px;
  bottom: 0;
  width: 2px;
  background: linear-gradient(180deg, #ddd6fe 0%, #e0e7ff 100%);
}

.timeline-item:last-child .timeline-line {
  display: none;
}

.timeline-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: linear-gradient(135deg, #9333ea 0%, #6366f1 100%);
  color: #fff;
  position: relative;
  z-index: 1;
  box-shadow: 0 2px 8px rgba(147, 51, 234, 0.3);
}

.timeline-dot.morning {
  background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.3);
}

.timeline-dot.afternoon {
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.timeline-dot.evening {
  background: linear-gradient(135deg, #a78bfa 0%, #8b5cf6 100%);
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.3);
}

.timeline-content {
  flex: 1;
}

.time-slot-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.time-badge {
  background: rgba(147, 51, 234, 0.08);
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 11px;
  color: #7c3aed;
  font-weight: 500;
}

.time-text {
  font-size: 12px;
  color: #888;
}

.attraction-card {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
}

.attraction-image {
  height: 120px;
  overflow: hidden;
  position: relative;
  background: #f8f9fa;
  border-radius: 16px 16px 0 0;
}

.slot-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-loading {
  width: 100%; height: 100%;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 10px;
  background: linear-gradient(90deg, #e8f4f8 25%, #d4ecf2 50%, #e8f4f8 75%);
  background-size: 200% 100%;
  animation: imgShimmer 2s ease-in-out infinite;
  border-radius: 12px;
}
@keyframes imgShimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
.image-loading .shimmer-svg { width: 100%; height: 100%; position: absolute; inset: 0; }

.loading-text {
  font-size: 12px; color: #64748b;
  text-align: center; padding: 0 10px;
  position: relative; z-index: 1;
}

.image-placeholder {
  width: 100%; height: 100%;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 6px;
  background: #f1f5f9;
  border-radius: 12px;
}

.attraction-info {
  padding: 12px;
}

.attraction-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin: 0 0 6px;
}

.attraction-activity {
  font-size: 13px;
  color: #666;
  margin: 0 0 10px;
  line-height: 1.5;
}

.attraction-details {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 10px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #888;
}

.detail-item .cost {
  color: #52c41a;
  font-weight: 500;
}

.attraction-tips {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  background: #fff8e1;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  color: #faad14;
}

.budget-chart {
  padding-top: 8px;
}

.chart-bars {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bar-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bar-label {
  width: 40px;
  font-size: 12px;
  color: #666;
  flex-shrink: 0;
}

.bar-track {
  flex: 1;
  height: 8px;
  background: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease;
}

.bar-transport {
  background: linear-gradient(90deg, #667eea, #764ba2);
}

.bar-accommodation {
  background: linear-gradient(90deg, #52c41a, #73d13d);
}

.bar-food {
  background: linear-gradient(90deg, #faad14, #ffc53d);
}

.bar-tickets {
  background: linear-gradient(90deg, #1890ff, #40a9ff);
}

.bar-other {
  background: linear-gradient(90deg, #919191, #bfbfbf);
}

.bar-value {
  width: 70px;
  font-size: 12px;
  color: #666;
  text-align: right;
  flex-shrink: 0;
}

.budget-list {
  padding-top: 8px;
}

.budget-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
}

.budget-label {
  font-size: 13px;
  color: #666;
}

.budget-amount {
  font-size: 13px;
  color: #333;
  font-weight: 500;
}

.budget-divider {
  height: 1px;
  background: #f0f0f0;
}

.budget-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0 4px;
  font-weight: 600;
}

.total-amount {
  color: #667eea;
  font-size: 16px;
}

.tips-list {
  padding-top: 8px;
}

.tip-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 0;
}

.tip-text {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}

/* ==================== 底部操作栏 - 大屏适配加固 ==================== */
/* 关键点：使用 left:50%; transform:translateX(-50%); 实现居中 */
/* 关键点：max-width:480px 约束宽度，与页面内容对齐 */
/* 关键点：padding左右16px恒定，不使用媒体查询缩小，避免小屏挤压 */
/* 关键点：display:flex + gap:14px 实现两个按钮平分宽度 */
.action-bar-integrated {
  display: flex !important;                    /* 强制flex布局，防止被全局样式覆盖 */
  flex-direction: row !important;              /* 强制水平排列 */
  justify-content: stretch !important;         /* 强制拉伸填充 */
  align-items: center !important;              /* 垂直居中 */
  gap: 14px !important;                       /* 按钮间距固定 */
  padding: 14px 16px !important;              /* 左右内边距恒定16px，不随屏幕缩小 */
  padding-bottom: calc(14px + env(safe-area-inset-bottom)) !important;
  position: fixed !important;                 /* 固定定位 */
  bottom: 0 !important;                       /* 底部对齐 */
  left: 50% !important;                       /* 左边缘居中 */
  transform: translateX(-50%) !important;      /* 水平居中偏移 */
  width: 100% !important;                     /* 宽度100% */
  max-width: 480px !important;                /* 最大宽度480px，与页面内容对齐 */
  z-index: 10000 !important;                 /* 提高层级，确保不被遮挡 */
  background: rgba(255,255,255,0.92) !important;
  backdrop-filter: blur(12px) !important;
  -webkit-backdrop-filter: blur(12px) !important;
  border-top: 1px solid rgba(147, 51, 234, 0.08) !important;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.04) !important;
  margin: 0 !important;                       /* 取消外边距 */
}

/* ==================== 底部按钮 - 真机适配加固 ==================== */
/* 关键点：min-width:0 是小屏适配的核心，允许flex子元素收缩 */
/* 关键点：flex:1 确保两个按钮平分可用空间 */
/* 关键点：white-space:nowrap 禁止文字换行，保证布局稳定 */
.action-btn {
  flex: 1 !important;                         /* 平分宽度 - 关键属性 */
  flex-shrink: 1 !important;                  /* 允许收缩 */
  flex-grow: 1 !important;                    /* 允许增长 */
  flex-basis: 0% !important;                  /* 基础宽度为0，确保flex:1完全生效 */
  min-width: 0 !important;                    /* 【关键修复】允许flex子元素收缩到内容以下，解决小屏挤压问题 */
  width: auto !important;                     /* 宽度自动 */
  max-width: none !important;                 /* 取消最大宽度限制 */
  height: 46px !important;                    /* 高度固定46px */
  border-radius: 23px !important;             /* 圆角23px */
  display: flex !important;                   /* 内部flex布局 */
  align-items: center !important;             /* 垂直居中 */
  justify-content: center !important;         /* 水平居中 */
  gap: 8px !important;                        /* 图标与文字间距 */
  font-size: 15px !important;                 /* 字体大小 */
  font-weight: 500 !important;                /* 字重 */
  cursor: pointer !important;                 /* 鼠标指针 */
  transition: all 0.2s ease !important;       /* 过渡动画 */
  white-space: nowrap !important;             /* 禁止换行，保证布局稳定 */
  overflow: hidden !important;                /* 隐藏溢出内容 */
  text-overflow: ellipsis !important;         /* 溢出时显示省略号 */
  user-select: none !important;               /* 禁止选中文本 */
  -webkit-user-select: none !important;       /* Safari禁止选中文本 */
  -webkit-tap-highlight-color: transparent !important; /* 移除移动端点击高亮 */
}

/* ==================== 复制行程按钮 - 左侧白色按钮 ==================== */
.copy-btn {
  background: #fff !important;                 /* 纯白色背景 */
  color: #333 !important;                      /* 深灰色文字 */
  border: 1px solid rgba(147, 51, 234, 0.15) !important; /* 淡紫色细边框 */
}

.copy-btn:active {
  background: #f5f5f5 !important;             /* 点击时背景变浅 */
  transform: scale(0.98) !important;           /* 缩小动画 */
}

.copy-btn.disabled {
  opacity: 0.4 !important;                     /* 禁用时降低透明度 */
  cursor: not-allowed !important;              /* 禁止点击指针 */
  pointer-events: none !important;             /* 禁止交互 */
}

.copy-btn.disabled:active {
  transform: none !important;                  /* 禁用时取消动画 */
}

/* ==================== 咨询AI助手按钮 - 右侧紫色渐变按钮 ==================== */
.ai-btn {
  background: linear-gradient(135deg, #9333ea, #6366f1) !important; /* 紫蓝渐变 */
  color: #fff !important;                      /* 白色文字 */
  border: none !important;                     /* 无边框 */
  font-weight: 600 !important;                /* 加粗字重 */
  box-shadow: 0 4px 14px rgba(147, 51, 234, 0.3) !important; /* 紫色阴影 */
}

.ai-btn:active {
  transform: scale(0.98) !important;           /* 缩小动画 */
  box-shadow: 0 2px 8px rgba(147, 51, 234, 0.2) !important; /* 阴影减弱 */
}

.bottom-safe-area {
  height: env(safe-area-inset-bottom);
}

.action-bar-integrated.hidden {
  display: none !important;
}

.chat-popup {
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
  z-index: 20000 !important;
}

.chat-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 12px;
  background: linear-gradient(135deg, rgba(233, 213, 255, 0.85) 0%, rgba(240, 249, 255, 0.85) 50%, rgba(253, 244, 255, 0.85) 100%);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(200, 190, 220, 0.2);
  flex-shrink: 0;
}

.chat-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #222;
}

.view-plan-btn {
  border-color: #8b5cf6 !important;
  color: #8b5cf6 !important;
  font-size: 12px;
  padding: 0 12px;
}

.chat-panel-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 0 16px;
}

.chat-guide {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 20px;
}

.chat-guide-icon {
  font-size: 48px;
  color: #c0c4cc;
  margin-bottom: 16px;
}

.chat-guide-title {
  font-size: 18px;
  font-weight: 500;
  color: #333;
  margin: 0 0 8px;
}

.chat-guide-desc {
  font-size: 13px;
  color: #999;
  line-height: 1.6;
  margin: 0 0 24px;
}

.chat-quick-questions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

.chat-q-btn {
  border-color: #dcdcdc !important;
  color: #666 !important;
  font-size: 12px;
  padding: 0 14px;
}

.chat-message-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.chat-message-list::-webkit-scrollbar {
  width: 0;
  display: none;
}

.chat-msg-wrapper {
  margin-bottom: 0;
}

.chat-msg-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.chat-user-row {
  justify-content: flex-end;
}

.chat-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.chat-user-avatar {
  background: linear-gradient(135deg, #c4b5fd 0%, #8b5cf6 100%);
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.3);
}

.chat-ai-avatar {
  background: linear-gradient(135deg, #e0e7ff 0%, #c7d2fe 100%);
  box-shadow: 0 2px 8px rgba(199, 210, 254, 0.4);
}

.chat-bubble {
  max-width: 340px;
  padding: 14px 18px;
  border-radius: 22px;
  font-size: 14px;
  line-height: 1.65;
  word-wrap: break-word;
}

.chat-user-bubble {
  background: linear-gradient(135deg, #c4b5fd 0%, #a78bfa 50%, #8b5cf6 100%);
  color: #fff;
  border-bottom-right-radius: 8px;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.25);
}

.chat-ai-bubble {
  background-color: #fff;
  color: #444;
  border: none;
  border-bottom-left-radius: 8px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.06);
  min-height: 56px;
  min-width: 80px;
}

.chat-thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
}

.chat-thinking-dots {
  display: flex;
  gap: 4px;
}

.chat-thinking-dots span {
  width: 8px;
  height: 8px;
  background-color: #8b5cf6;
  border-radius: 50%;
  animation: chatThinking 1.4s infinite ease-in-out both;
}

.chat-thinking-dots span:nth-child(1) { animation-delay: -0.32s; }
.chat-thinking-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes chatThinking {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.chat-thinking-text {
  font-size: 13px;
  color: #999;
}

.chat-markdown {
  word-break: break-word;
}

.chat-markdown :deep(h2) {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 12px 0 8px;
}

.chat-markdown :deep(h3) {
  font-size: 14px;
  font-weight: 600;
  color: #444;
  margin: 10px 0 6px;
}

.chat-markdown :deep(p) {
  margin: 6px 0;
}

.chat-markdown :deep(ul),
.chat-markdown :deep(ol) {
  padding-left: 20px;
  margin: 6px 0;
}

.chat-markdown :deep(li) {
  margin: 4px 0;
}

.chat-markdown :deep(strong) {
  font-weight: 600;
  color: #333;
}

.chat-markdown :deep(a) {
  color: #667eea;
  text-decoration: none;
}

.chat-input-footer {
  padding: 12px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
  background-color: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid rgba(139, 92, 246, 0.08);
  flex-shrink: 0;
  position: relative;
  z-index: 10;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.04);
}

.chat-input-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 18px;
  background-color: #f7f8fc;
  border-radius: 28px;
  border: 1px solid rgba(139, 92, 246, 0.08);
}

.chat-input-field {
  flex: 1;
  background-color: transparent;
}

.chat-input-field :deep(.van-field__body) {
  background: transparent !important;
  border: none !important;
  border-radius: 0 !important;
}

.chat-input-field :deep(.van-field__control) {
  padding: 0 !important;
  height: auto !important;
  min-height: 32px;
}

.chat-send-btn {
  height: 36px;
  padding: 0 20px;
  border-radius: 16px;
  background: linear-gradient(135deg, #9333ea 0%, #6366f1 100%) !important;
  border: none !important;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(147, 51, 234, 0.3);
  transition: all 0.2s ease;
}

.chat-send-btn:active {
  transform: scale(0.96);
}

.chat-send-btn:disabled {
  background: linear-gradient(135deg, #c4b5fd 0%, #a5b4fc 100%) !important;
  box-shadow: none;
}

/* ==================== 媒体查询 - 大屏适配 ==================== */
/* 大屏时页面内容和底部操作栏都居中，最大宽度480px */
@media (min-width: 481px) {
  .planning-page {
    display: flex;
    justify-content: center;
  }
}

@media (max-width: 375px) {
  .action-btn {
    font-size: 13px !important;                /* 小屏缩小字体，避免挤压 */
  }
}
/* 流式逐天动画 */
@keyframes fadeInDay { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
.skeleton-day { background: #fff; border-radius: 16px; padding: 20px; box-shadow: 0 4px 12px rgba(0,0,0,0.04); }
.sk-header { height: 20px; width: 45%; background: linear-gradient(90deg,#e2e8f0 25%,#f1f5f9 50%,#e2e8f0 75%); background-size:200% 100%; animation:shimmer 1.5s infinite; border-radius:6px; margin-bottom:14px; }
.sk-line { height: 14px; background: linear-gradient(90deg,#e2e8f0 25%,#f1f5f9 50%,#e2e8f0 75%); background-size:200% 100%; animation:shimmer 1.5s infinite; border-radius:4px; margin-bottom:10px; }
.sk-line.short { width: 60%; }
.empty-hint { font-size: 12px; color: #94a3b8; margin-top: 8px; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
</style>