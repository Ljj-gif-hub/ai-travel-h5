<script setup>
import { ref, nextTick, onMounted, onUnmounted, onDeactivated, onActivated, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showDialog, showConfirmDialog } from 'vant'

/*
 * 【Bug修复】显式声明组件名，供 keep-alive 的 include 白名单匹配
 * 缺失会导致 Tab 切换时组件无法命中缓存，每次销毁重建 → 空白
 */
defineOptions({ name: 'ChatView' })
import { getToken } from '../utils/auth'
import { chatApi } from '../api'

import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

const router = useRouter()
const route = useRoute()

/* ==================== 规划参数 ==================== */
const destination = route.query.destination || ''
const budget = route.query.budget || ''
const days = route.query.days || ''

/* ==================== 状态 ==================== */
const messages = ref([])
const inputText = ref('')
const chatContent = ref(null)
const isSending = ref(false)
const isThinking = ref(false)
const isListening = ref(false)
const recognition = ref(null)
const isAutoScrollEnabled = ref(true)
/* 【性能优化】SSE连接控制器：Tab切走时中断请求，减少后台带宽 */
let abortController = null
const showQuickBar = ref(true)

import { getCurrentUser } from '../utils/userAccountStorage'
// 【多账号隔离】聊天记录键名带用户名后缀，账号间数据完全隔离
const STORAGE_KEY = () => {
  const user = getCurrentUser()
  return user ? `travel_chat_history:${user}` : 'travel_chat_history'
}

/*
 * ==================== 键盘 & 视口适配 ====================
 * 修复手机端软键盘弹出时输入框被截断/遮挡的核心逻辑：
 * 1. pageHeight — 动态减去底部 Tab 栏高度 + safe-area，确保输入框不被 Tab 遮挡
 * 2. visualViewport resize 监听 — 键盘弹出/收起时实时调整页面高度
 * 3. keyboardOffset — 键盘弹出时输入框需要上移的偏移量
 */
const TAB_BAR_H = 56 // 底部 Tab 栏高度（与 CSS 变量 --tabbar-height 保持一致）
const SAFE_BOTTOM = () => parseInt(getComputedStyle(document.documentElement).getPropertyValue('--safe-area-bottom').trim()) || 0

const pageHeight = ref('100dvh')
const keyboardOpen = ref(false)

const updatePageHeight = () => {
  if (window.visualViewport) {
    const vv = window.visualViewport
    const windowH = window.innerHeight
    const viewportH = vv.height
    // 键盘弹出时 viewport 高度会显著减小
    const keyboardVisible = windowH - viewportH > 80
    keyboardOpen.value = keyboardVisible

    // 页面可用高度 = 视口高度 - Tab栏 - safe-area底部
    const safeB = SAFE_BOTTOM()
    const availableH = viewportH - TAB_BAR_H - safeB
    pageHeight.value = `${Math.max(availableH, 300)}px`
  } else {
    // 不支持 visualViewport 的降级方案
    pageHeight.value = `calc(100dvh - ${TAB_BAR_H}px - env(safe-area-inset-bottom, 0px))`
  }
}

/* ==================== 快捷问题 ==================== */
const quickQuestions = [
  { label: '💰 换低价方案', query: '能否帮我调整成更省钱的方案？' },
  { label: '🍜 增加美食推荐', query: '请多推荐一些当地必吃的美食' },
  { label: '⏱️ 缩短天数', query: '帮我压缩行程天数' },
  { label: '👨‍👩‍👧 亲子优化', query: '帮我优化成适合带孩子的亲子游方案' },
]

/* ==================== Markdown 渲染 ==================== */
const md = new MarkdownIt({
  html: false, linkify: true, typographer: true,
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try { return `<pre><code class="hljs language-${lang}">${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>` } catch (__) {}
    }
    return `<pre><code class="hljs">${md.utils.escapeHtml(str)}</code></pre>`
  }
})

/* ==================== 工具 ==================== */
const goBack = () => { if (window.history.length <= 1) router.push('/'); else router.back() }
const generateUniqueId = () => `${Date.now()}_${Math.random().toString(36).slice(2, 9)}`

/* ==================== 消息存储 ==================== */
const loadMessagesFromStorage = async () => {
  if (!(await checkLogin())) return false
  try {
    // 【修复】迁移旧全局聊天记录到当前账号专属空间
    const user = getCurrentUser()
    if (user) {
      const oldRaw = localStorage.getItem('travel_chat_history')
      if (oldRaw) {
        const newKey = STORAGE_KEY()
        if (!localStorage.getItem(newKey)) {
          localStorage.setItem(newKey, oldRaw)  // 复制到账号空间
        }
        localStorage.removeItem('travel_chat_history')  // 清除旧数据
      }
    }
    const saved = localStorage.getItem(STORAGE_KEY())
    if (saved) {
      const parsed = JSON.parse(saved)
      if (Array.isArray(parsed) && parsed.length > 0) { messages.value = parsed; showToast({ message: '历史记录已恢复', position: 'top' }); return true }
    }
    showToast({ message: '暂无历史记录', position: 'top' })
  } catch (e) { localStorage.removeItem(STORAGE_KEY()); showToast({ message: '加载失败', position: 'top' }) }
  return false
}

const saveMessagesToStorage = () => {
  if (!getToken()) return
  try { localStorage.setItem(STORAGE_KEY(), JSON.stringify(messages.value)) } catch (e) { showToast('存储空间不足') }
}

const checkLogin = async () => {
  if (!getToken()) {
    try {
      await showDialog({ title: '需要登录', message: '此功能需要登录后才能使用，是否立即登录？', confirmButtonText: '去登录', cancelButtonText: '取消' })
      localStorage.setItem('redirectUrl', '/chat'); router.push('/login'); return false
    } catch { return false }
  }
  return true
}

const clearChat = async () => {
  if (!(await checkLogin())) return
  try {
    await showConfirmDialog({ title: '清空对话', message: '确定要清空所有对话记录吗？', confirmButtonText: '清空', cancelButtonText: '取消' })
    messages.value = [{ id: 1, type: 'system', content: '👋 你好！我是 AI 旅行规划师，告诉我你的旅行计划，我来帮你设计完美行程～' }]
    localStorage.removeItem(STORAGE_KEY()); showToast('对话已清空'); await nextTick(); scrollToBottom(true)
  } catch { /* 取消 */ }
}

/* ==================== Markdown 预处理 ==================== */
const preprocessMarkdown = (text) => {
  if (!text) return ''
  let result = text
  result = result.replace(/\\n/g, '\n')
  result = result.replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
  result = result.replace(/^(\s*[-*+])(\S)/gm, '$1 $2')
  result = result.replace(/^(\s*\d+\.)([^\s])/gm, '$1 $2')
  result = result.replace(/<strong>([\s\S]*?)<\/strong>/g, '**$1**')
  result = result.replace(/<\/?strong>/g, '**')
  result = result.replace(/<em>(.*?)<\/em>/g, '*$1*')
  result = result.replace(/<\/?em>/g, '*')
  result = result.replace(/<br\s*\/?>/gi, '\n')
  result = result.replace(/\*\*\s*\*\*/g, '')
  return result
}

const renderMarkdown = (text) => text ? md.render(preprocessMarkdown(text)) : ''

const sendQuickQuestion = (question) => { inputText.value = question; sendMessage() }
/* 【性能优化】发送节流：500ms内禁止重复发送 */
let sendDebounce = false

/* ==================== 滚动 ==================== */
let scrollScheduled = false; let lastScrollTime = 0
const isNearBottom = () => {
  if (!chatContent.value) return true
  const el = chatContent.value; return el.scrollHeight - el.scrollTop - el.clientHeight < 100
}
const handleScroll = () => { isAutoScrollEnabled.value = isNearBottom() }

const scrollToBottom = async (force = false) => {
  await nextTick()
  if (!chatContent.value) return
  const now = Date.now()
  if (now - lastScrollTime < 50) { if (!scrollScheduled) { scrollScheduled = true; setTimeout(() => { scrollToBottom(force); scrollScheduled = false }, 60) } return }
  lastScrollTime = now
  if (force || isAutoScrollEnabled.value) chatContent.value.scrollTo({ top: chatContent.value.scrollHeight, behavior: isSending.value ? 'auto' : 'smooth' })
}

/* ==================== 语音识别 ==================== */
const initSpeechRecognition = () => {
  if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
    recognition.value = new SpeechRecognition()
    recognition.value.lang = 'zh-CN'; recognition.value.continuous = false; recognition.value.interimResults = true
    recognition.value.onstart = () => { isListening.value = true; showToast('正在聆听...') }
    recognition.value.onresult = (event) => {
      let finalTranscript = ''; let interimTranscript = ''
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript
        if (event.results[i].isFinal) finalTranscript += transcript; else interimTranscript += transcript
      }
      if (interimTranscript) inputText.value = finalTranscript + interimTranscript
      if (finalTranscript) inputText.value = finalTranscript
    }
    recognition.value.onerror = (event) => { isListening.value = false; const errors = { 'no-speech': '未检测到语音', 'audio-capture': '无法访问麦克风', 'not-allowed': '麦克风权限被拒绝' }; showToast(errors[event.error] || '语音识别失败') }
    recognition.value.onend = () => { isListening.value = false }
  }
}

const toggleVoiceInput = () => {
  if (!recognition.value) { showToast('您的浏览器不支持语音识别'); return }
  if (isListening.value) recognition.value.stop(); else { inputText.value = ''; recognition.value.start() }
}

/* ==================== 发送消息 ==================== */
const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || isSending.value) { showToast('请输入内容'); return }
  if (sendDebounce) return // 节流：500ms内重复点击忽略
  sendDebounce = true
  setTimeout(() => { sendDebounce = false }, 500)

  isSending.value = true; isThinking.value = true; showQuickBar.value = false

  messages.value.push({ id: generateUniqueId(), type: 'user', content: text })
  inputText.value = ''
  await nextTick(); scrollToBottom(true)

  const aiMsgIndex = messages.value.length
  messages.value.push({ id: generateUniqueId(), type: 'ai', content: '', isStreaming: true })

  let prompt = text
  if (destination && budget && days) prompt = `我计划去${destination}旅游，预算${budget}元，共${days}天。${text}`

  const chatHistory = messages.value
    .filter((m) => m.type === 'user' || (m.type === 'ai' && m.content && m.content.length > 0))
    .map((m) => ({ role: m.type === 'user' ? 'user' : 'assistant', content: m.content }))

  try {
    abortController = new AbortController()
    const response = await chatApi.getChatStream([
      { role: 'system', content: `你是一个专业的旅游规划助手，擅长提供详细、实用的旅行建议。排版规范：Markdown语法标准，##/###后须有空格，-和1.后须有空格，使用**加粗**，禁止HTML标签。内容区块空一行，步骤用列表，表格仅用于费用汇总。标题只用##/###两级。` },
      ...chatHistory,
    ])

    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = ''
    let isDone = false
    while (!isDone) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n'); buffer = events.pop() || ''
      for (const evt of events) {
        const trimmedEvt = evt.trim()
        if (!trimmedEvt) continue
        if (trimmedEvt === 'data: [DONE]' || trimmedEvt === 'data:[DONE]') { isDone = true; break }
        if (!trimmedEvt.startsWith('data:')) continue
        try {
          const dataLines = trimmedEvt.split('\n')
          let content = dataLines.map(line => { const t = line.trim(); if (t.startsWith('data: ')) return t.slice(6); if (t.startsWith('data:')) return t.slice(5); return '' }).join('\n')
          if (content && content !== '[DONE]') { if (isThinking.value) isThinking.value = false; messages.value[aiMsgIndex].content += content; scrollToBottom() }
        } catch (e) { /* SSE skip */ }
      }
    }
    if (messages.value[aiMsgIndex]) messages.value[aiMsgIndex].isStreaming = false
    isSending.value = false; isThinking.value = false; showQuickBar.value = true; saveMessagesToStorage()
  } catch (e) {
    if (e?.name === 'AbortError') return // SSE被主动中断，不提示错误
    isSending.value = false; isThinking.value = false; showQuickBar.value = true; showToast('请求失败')
  }
}

/* ==================== 生命周期：视口 + 键盘监听 ==================== */
onMounted(async () => {
  if (getToken()) await loadMessagesFromStorage()
  if (messages.value.length === 0) messages.value = [{ id: 1, type: 'system', content: '👋 你好！我是 AI 旅行规划师，告诉我你的旅行计划，我来帮你设计完美行程～' }]
  if (destination && budget && days) messages.value.push({ id: generateUniqueId(), type: 'system', content: `📋 已加载：${destination} · ${days}天 · ¥${budget} · 继续问我细节吧～` })

  // 【关键修复】监听 visualViewport 变化，处理软键盘弹出/收起
  updatePageHeight()
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', updatePageHeight)
    window.visualViewport.addEventListener('scroll', updatePageHeight)
  }
  // 降级：传统 resize 监听
  window.addEventListener('resize', updatePageHeight)

  initSpeechRecognition()
  await nextTick(); await nextTick(); scrollToBottom(true)
})

/* 【性能优化】Tab切走：中断SSE、停止语音、解绑监听 */
onDeactivated(() => {
  if (abortController) { abortController.abort(); abortController = null }
  if (recognition.value) { recognition.value.stop(); isListening.value = false }
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null }
  isSending.value = false
  isThinking.value = false
})

/* 【性能优化】Tab切回：恢复页面高度计算、滚动到底部 */
onActivated(() => {
  updatePageHeight()
  nextTick(() => scrollToBottom())
})

onUnmounted(() => {
  if (window.visualViewport) {
    window.visualViewport.removeEventListener('resize', updatePageHeight)
    window.visualViewport.removeEventListener('scroll', updatePageHeight)
  }
  window.removeEventListener('resize', updatePageHeight)
  if (recognition.value) recognition.value.stop()
})

let saveTimer = null
watch(messages, () => { if (saveTimer) clearTimeout(saveTimer); saveTimer = setTimeout(() => saveMessagesToStorage(), 1000) }, { deep: true })
</script>

<template>
  <!--
    【三层结构布局】
    1. 顶部导航栏 — flex-shrink: 0，固定高度
    2. 中间对话内容区 — flex: 1 + overflow-y: auto，底部预留输入框高度
    3. 底部输入固定栏 — flex-shrink: 0，z-index 高于内容低于 Tab 栏
  -->
  <div class="chat-page" :style="{ height: pageHeight }">
    <!-- ======== 第一层：顶部导航栏 ======== -->
    <van-nav-bar safe-area-inset-top class="chat-nav" @click-left="goBack">
      <template #left>
        <van-icon name="arrow-left" size="20" color="#fff" />
      </template>
      <template #title>
        <div class="nav-title-row">
          <span class="nav-ai-icon">🤖</span>
          <span class="nav-title-text">AI 旅行规划师</span>
        </div>
      </template>
      <template #right>
        <van-icon name="replay" size="20" color="#fff" class="nav-btn" @click="loadMessagesFromStorage" />
        <van-icon name="delete-o" size="20" color="#fff" class="nav-btn" @click="clearChat" />
      </template>
    </van-nav-bar>

    <!-- ======== 第二层：可滚动对话内容区 ======== -->
    <div ref="chatContent" class="chat-body" @scroll="handleScroll">
      <div class="chat-inner">
        <!-- 引导页 -->
        <div v-if="messages.length <= 1" class="chat-guide">
          <div class="guide-illustration">
            <svg viewBox="0 0 120 120" width="90" height="90">
              <circle cx="60" cy="45" r="24" fill="rgba(139,92,246,0.12)" />
              <circle cx="60" cy="45" r="14" fill="rgba(139,92,246,0.2)" />
              <ellipse cx="60" cy="85" rx="30" ry="8" fill="rgba(139,92,246,0.06)" />
              <circle cx="48" cy="40" r="4" fill="white" opacity="0.6" />
              <circle cx="66" cy="38" r="4" fill="white" opacity="0.6" />
              <line x1="60" y1="55" x2="60" y2="72" stroke="rgba(139,92,246,0.3)" stroke-width="3" stroke-linecap="round" />
              <path d="M46 68 Q60 78 74 68" fill="none" stroke="rgba(139,92,246,0.25)" stroke-width="2.5" stroke-linecap="round" />
            </svg>
          </div>
          <p class="guide-heading">和我聊聊你的旅行计划吧 ✈️</p>
          <p class="guide-hint">输入目的地、预算、天数，我帮你生成专属行程</p>
          <div class="guide-chips">
            <button class="guide-chip" @click="sendQuickQuestion('推荐几个适合情侣的国内旅游目的地')">💑 情侣旅行推荐</button>
            <button class="guide-chip" @click="sendQuickQuestion('带父母去哪里旅游比较合适？')">👨‍👩‍👦 适合带父母</button>
            <button class="guide-chip" @click="sendQuickQuestion('预算3000元可以去哪里玩？')">💰 预算3000元</button>
            <button class="guide-chip" @click="sendQuickQuestion('推荐北京三日游攻略')">🏯 北京三日游</button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-else class="msg-list">
          <div v-for="msg in messages" :key="msg.id" :class="['msg-row', msg.type]">
            <!-- 系统消息 -->
            <div v-if="msg.type === 'system'" class="sys-msg">
              <span>{{ msg.content }}</span>
            </div>

            <!-- 用户消息 -->
            <div v-else-if="msg.type === 'user'" class="user-msg-row">
              <div class="user-bubble">{{ msg.content }}</div>
              <div class="user-avatar"><van-icon name="user-o" color="#fff" size="16" /></div>
            </div>

            <!-- AI 消息 -->
            <div v-else-if="msg.type === 'ai'" class="ai-msg-row">
              <div class="ai-avatar">🤖</div>
              <div class="ai-bubble" :class="{ streaming: msg.isStreaming }">
                <div v-if="isThinking && msg === messages[messages.length - 1]" class="thinking">
                  <span class="think-dot" /><span class="think-dot" /><span class="think-dot" />
                  <span class="think-text">AI正在思考...</span>
                </div>
                <div v-else class="md-body" v-html="renderMarkdown(msg.content)" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ======== 第三层：底部输入固定栏 ======== -->
    <div class="chat-footer" :class="{ 'keyboard-up': keyboardOpen }">
      <!-- 快捷提问栏 — 固定于输入框上方 -->
      <div v-if="showQuickBar && messages.length > 1" class="quick-bar">
        <button v-for="(q, i) in quickQuestions" :key="i" class="quick-chip" @click="sendQuickQuestion(q.query)">
          {{ q.label }}
        </button>
      </div>

      <div class="input-row">
        <div class="input-glass">
          <button class="input-action" @click="toggleVoiceInput" :class="{ listening: isListening }">
            <van-icon :name="isListening ? 'volume' : 'volume-o'" size="20" :color="isListening ? '#8B5CF6' : '#94A3B8'" />
          </button>
          <input
            v-model="inputText"
            type="text"
            placeholder="和AI旅行规划师聊聊..."
            class="msg-input"
            @keyup.enter="sendMessage"
            @focus="scrollToBottom(true)"
          />
          <button class="send-btn" :class="{ disabled: !inputText.trim() || isSending }" :disabled="!inputText.trim() || isSending" @click="sendMessage">
            <van-icon v-if="!isSending" name="arrow-up" size="20" color="#fff" />
            <van-loading v-else size="16" color="#fff" />
          </button>
        </div>
      </div>

      <!-- 语音提示 -->
      <div v-if="isListening" class="voice-toast">
        <span class="voice-pulse" />正在聆听...
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ================================================================
   整体布局 — 三层结构修复遮挡
   - 页面高度 = 动态视口高度 - Tab栏高度 - safe-area底部
   - flex 列布局：nav(固定) → body(可滚动) → footer(固定)
   - 杜绝 absolute/fixed 定位悬浮错位
   ================================================================ */

/* ==================== 页面根容器 ==================== */
.chat-page {
  width: 100%;
  /* 高度由 JS 动态计算：visualViewport.height - Tab栏(56px) - safe-area-bottom */
  /* CSS 降级默认值：100dvh - 56px - env(safe-area-inset-bottom) */
  min-height: 300px;
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ==================== 第一层：顶部导航栏 ==================== */
.chat-nav {
  flex-shrink: 0;
  z-index: 200;
  background: linear-gradient(135deg, #8B5CF6 0%, #7C3AED 60%, #6366F1 100%) !important;
}
:deep(.chat-nav .van-nav-bar__title) { color: #fff; font-weight: 600; }
:deep(.chat-nav .van-nav-bar__left) { color: #fff; }
.nav-title-row { display: flex; align-items: center; gap: 6px; }
.nav-ai-icon { font-size: 18px; }
.nav-title-text { font-size: 16px; }
.nav-btn { margin-left: 10px; cursor: pointer; }

/* ==================== 第二层：可滚动对话内容区 ==================== */
.chat-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px 14px;
  /*
   * 【关键修复】底部内边距 = 快捷提问栏(~36px) + 输入框(~54px) + 语音提示(~30px) + safe-area + 安全间隙
   * 确保最后一条消息不会被底部输入框遮挡
   */
  padding-bottom: calc(130px + env(safe-area-inset-bottom, 0px));
  -webkit-overflow-scrolling: touch;
  /* 独立层叠上下文，不参与 footer 的 z-index 层级竞争 */
  isolation: isolate;
}
.chat-inner { max-width: 500px; margin: 0 auto; width: 100%; }

/* ==================== 引导页 ==================== */
.chat-guide {
  display: flex; flex-direction: column; align-items: center;
  padding-top: 40px; text-align: center;
}
.guide-illustration { margin-bottom: 20px; }
.guide-heading { font-size: 20px; font-weight: 700; color: #1E293B; margin: 0 0 8px; }
.guide-hint { font-size: 13px; color: #94A3B8; margin: 0 0 24px; }
.guide-chips { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; max-width: 340px; }
.guide-chip {
  padding: 10px 18px; background: #fff; border: 1px solid #E2E8F0;
  border-radius: 22px; font-size: 13px; color: #475569; cursor: pointer;
  box-shadow: 0 2px 8px rgba(0,0,0,0.03); transition: all 0.2s;
}
.guide-chip:hover { border-color: #c4b5fd; color: #7C3AED; }
.guide-chip:active { transform: scale(0.96); background: #faf5ff; }

/* ==================== 消息列表 ==================== */
.msg-list { display: flex; flex-direction: column; gap: 18px; padding-bottom: 8px; }

.sys-msg {
  align-self: center; background: rgba(139,92,246,0.08);
  color: #7C3AED; font-size: 12px; padding: 8px 16px;
  border-radius: 20px; text-align: center; max-width: 85%;
}

.user-msg-row {
  display: flex; justify-content: flex-end; align-items: flex-end; gap: 8px;
  animation: msgIn 0.3s ease-out;
}
.user-bubble {
  max-width: 75%; padding: 12px 18px;
  background: linear-gradient(135deg, #A78BFA 0%, #8B5CF6 100%);
  color: #fff; border-radius: 20px 20px 6px 20px;
  font-size: 15px; line-height: 1.6; word-break: break-word;
  box-shadow: 0 4px 14px rgba(139,92,246,0.25);
}
.user-avatar {
  width: 34px; height: 34px; border-radius: 50%;
  background: linear-gradient(135deg, #c4b5fd, #8B5CF6);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}

.ai-msg-row {
  display: flex; align-items: flex-start; gap: 8px;
  animation: msgIn 0.35s ease-out;
}
.ai-avatar {
  width: 34px; height: 34px; border-radius: 50%;
  background: linear-gradient(135deg, #ede9fe, #ddd6fe);
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; flex-shrink: 0;
}
.ai-bubble {
  max-width: 80%; padding: 14px 18px;
  background: rgba(255,255,255,0.8);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 6px 20px 20px 20px;
  border: 1px solid rgba(139,92,246,0.08);
  box-shadow: 0 4px 16px rgba(0,0,0,0.04);
  font-size: 14px; line-height: 1.7; color: #334155;
  word-break: break-word; min-width: 70px;
}
.ai-bubble.streaming { min-height: 48px; }

@keyframes msgIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
/* GPU加速：消息行动画 */
.user-msg-row, .ai-msg-row { will-change: transform, opacity; }

/* ==================== 思考动画 ==================== */
.thinking { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
.think-dot {
  width: 7px; height: 7px; background: #8B5CF6; border-radius: 50%;
  animation: thinkBounce 1.4s ease-in-out infinite both;
}
.think-dot:nth-child(2) { animation-delay: 0.16s; }
.think-dot:nth-child(3) { animation-delay: 0.32s; }
.think-text { font-size: 13px; color: #94A3B8; }
@keyframes thinkBounce {
  0%, 80%, 100% { transform: scale(0.5); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* ==================== Markdown 样式 ==================== */
.md-body { line-height: 1.75; }
.md-body :deep(pre) { background: #f8f7ff; border-radius: 12px; padding: 14px; overflow-x: auto; margin: 10px 0; border: 1px solid rgba(139,92,246,0.08); }
.md-body :deep(code) { font-family: 'Monaco','Menlo',monospace; font-size: 12px; }
.md-body :deep(p code) { background: rgba(139,92,246,0.08); padding: 2px 6px; border-radius: 6px; color: #7C3AED; }
.md-body :deep(table) { border-collapse: collapse; width: 100%; margin: 14px 0; border-radius: 12px; overflow: hidden; border: 1px solid rgba(139,92,246,0.08); display: block; overflow-x: auto; }
.md-body :deep(th), .md-body :deep(td) { border-bottom: 1px solid rgba(139,92,246,0.06); padding: 9px 12px; text-align: left; font-size: 13px; }
.md-body :deep(th) { background: rgba(139,92,246,0.04); font-weight: 600; color: #1E293B; }
.md-body :deep(tr:nth-child(even)) { background: rgba(139,92,246,0.02); }
.md-body :deep(blockquote) { margin: 14px 0; padding: 12px 16px; border-left: 4px solid #A78BFA; background: rgba(139,92,246,0.04); border-radius: 0 12px 12px 0; }
.md-body :deep(h2), .md-body :deep(h3) { margin: 16px 0 8px; font-weight: 600; color: #1E293B; }
.md-body :deep(h2) { font-size: 1.2em; }
.md-body :deep(h3) { font-size: 1.1em; }
.md-body :deep(ul), .md-body :deep(ol) { padding-left: 20px; margin: 8px 0; }
.md-body :deep(li) { margin: 5px 0; }
.md-body :deep(p) { margin: 6px 0; }
.md-body :deep(strong) { color: #7C3AED; }
.md-body :deep(hr) { border: none; border-top: 1px solid rgba(139,92,246,0.08); margin: 14px 0; }

/* ================================================================
   第三层：底部输入固定栏
   - flex-shrink: 0 保证不被内容区压缩
   - z-index: 100 高于对话内容，低于底部 Tab 栏 (9999)
   - 键盘弹出时自动上移（通过页面高度动态调整实现）
   ================================================================ */
.chat-footer {
  flex-shrink: 0;
  position: relative;
  z-index: 100;
  padding: 6px 14px;
  padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
  /* 半透明磨砂背景 — 统一旅行APP视觉规范 */
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-top: 1px solid rgba(139, 92, 246, 0.08);
  box-shadow: 0 -4px 20px rgba(139, 92, 246, 0.06);
}

/* 键盘弹出时微调 */
.chat-footer.keyboard-up {
  padding-bottom: 8px;
}

/* ==================== 快捷提问栏 ==================== */
.quick-bar {
  display: flex; gap: 8px; overflow-x: auto;
  padding: 4px 0 8px;
  scrollbar-width: none; -webkit-overflow-scrolling: touch;
}
.quick-bar::-webkit-scrollbar { display: none; }

.quick-chip {
  flex-shrink: 0; padding: 8px 14px;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  border: 1px solid rgba(139,92,246,0.12);
  border-radius: 20px; font-size: 12px; color: #7C3AED;
  cursor: pointer; white-space: nowrap; transition: all 0.2s;
}
.quick-chip:active {
  background: rgba(139,92,246,0.1);
  border-color: #c4b5fd; transform: scale(0.95);
}

/* ==================== 输入行 ==================== */
.input-row { max-width: 500px; margin: 0 auto; }

.input-glass {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 6px 6px 16px;
  background: rgba(255,255,255,0.8);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1.5px solid rgba(139,92,246,0.1);
  border-radius: 28px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.03);
  transition: border-color 0.25s, box-shadow 0.25s;
}
.input-glass:focus-within {
  border-color: rgba(139,92,246,0.35);
  box-shadow: 0 0 0 3px rgba(139,92,246,0.08);
}

.input-action {
  width: 36px; height: 36px; display: flex; align-items: center; justify-content: center;
  border: none; background: transparent; cursor: pointer; border-radius: 50%; flex-shrink: 0;
  transition: background 0.2s;
}
.input-action.listening { background: rgba(139,92,246,0.1); animation: pulse-ring 1.5s infinite; }
@keyframes pulse-ring {
  0% { box-shadow: 0 0 0 0 rgba(139,92,246,0.3); }
  70% { box-shadow: 0 0 0 12px rgba(139,92,246,0); }
  100% { box-shadow: 0 0 0 0 rgba(139,92,246,0); }
}

.msg-input {
  flex: 1; min-width: 0; border: none; outline: none; background: transparent;
  font-size: 16px; /* 16px 防止 iOS 自动缩放 */
  color: #1E293B; padding: 10px 0;
}
.msg-input::placeholder { color: #94A3B8; opacity: 1; }
.msg-input::-webkit-input-placeholder { color: #94A3B8; opacity: 1; }

.send-btn {
  width: 38px; height: 38px; border-radius: 50%; border: none;
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; flex-shrink: 0; transition: all 0.2s;
  box-shadow: 0 4px 12px rgba(139,92,246,0.3);
}
.send-btn:active { transform: scale(0.92); }
.send-btn.disabled { background: #CBD5E1; box-shadow: none; cursor: not-allowed; }

/* ==================== 语音提示 ==================== */
.voice-toast {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 4px 0; font-size: 12px; color: #8B5CF6;
}
.voice-pulse {
  width: 8px; height: 8px; background: #8B5CF6; border-radius: 50%;
  animation: pulse-ring 1s infinite;
}

/* ==================== 小屏手机适配 ==================== */
@media screen and (max-width: 360px) {
  .chat-body { padding: 12px 10px; padding-bottom: calc(120px + env(safe-area-inset-bottom, 0px)); }
  .chat-footer { padding: 4px 10px; padding-bottom: calc(6px + env(safe-area-inset-bottom, 0px)); }
  .input-glass { padding: 4px 4px 4px 12px; }
}
</style>
