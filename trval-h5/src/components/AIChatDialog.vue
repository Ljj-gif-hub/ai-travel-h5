<script setup>
import { ref, nextTick, watch, computed, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { showToast } from 'vant'
import { getToken } from '../utils/auth'
import { chatApi, planApi } from '../api'
import { CHAT_SYSTEM_PROMPT } from '../constants/systemPrompts'
import {
  getCurrentSessionId, getCurrentSessionMessages,
  saveCurrentSessionMessages, clearCurrentSession, createNewSession,
  genMsgId,
} from '../utils/chatSession'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

defineOptions({ name: 'AIChatDialog' })

const { t } = useI18n()

/* ==================== Props ==================== */
const props = defineProps({
  visible: { type: Boolean, default: false },
  contextQuery: {
    type: Object,
    default: () => ({ destination: '', budget: '', days: '' }),
  },
  initialMessages: {
    type: Array,
    default: () => [],
  },
})

/* ==================== Emits ==================== */
const emit = defineEmits(['update:visible', 'close', 'plan-saved'])

/* v-model 双向绑定：localVisible 同步 props.visible */
const localVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

/* ==================== State ==================== */
const messages = ref([])
const inputText = ref('')
const chatContent = ref(null)
const isSending = ref(false)
const isThinking = ref(false)
const isListening = ref(false)
const recognition = ref(null)
const isAutoScrollEnabled = ref(true)
let abortController = null
const showQuickBar = ref(true)
const isSavingPlan = ref(false)

/* ==================== Markdown 渲染 ==================== */
const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre><code class="hljs language-${lang}">${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>`
      } catch (__) {}
    }
    return `<pre><code class="hljs">${md.utils.escapeHtml(str)}</code></pre>`
  },
})

/* ==================== Helpers ==================== */
const generateUniqueId = () => `${Date.now()}_${Math.random().toString(36).slice(2, 9)}`

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

const renderMarkdown = (text) => (text ? md.render(preprocessMarkdown(text)) : '')

/* ==================== Quick Questions ==================== */
// 用 computed：切换语言后快捷问题/引导项即时更新，而非 setup 期一次性求值
// 正文 query 同样走 i18n，与 label 同步切换语言
const quickQuestions = computed(() => [
  { label: t('chat.quickCheaper'), query: t('chat.quickCheaperQuery') },
  { label: t('chat.quickFood'), query: t('chat.quickFoodQuery') },
  { label: t('chat.quickShorter'), query: t('chat.quickShorterQuery') },
  { label: t('chat.quickFamily'), query: t('chat.quickFamilyQuery') },
])

/* ==================== Guide Chips ==================== */
const guideChips = computed(() => [
  { label: t('chat.guideCouples'), query: t('chat.guideCouplesQuery') },
  { label: t('chat.guideParents'), query: t('chat.guideParentsQuery') },
  { label: t('chat.guideBudget3000'), query: t('chat.guideBudget3000Query') },
  { label: t('chat.guideBeijing'), query: t('chat.guideBeijingQuery') },
])

/* ==================== Plan Detection ==================== */
const hasPlanContent = (content) => {
  if (!content || content.length < 100) return false
  const planPatterns = [
    /第\d+天/, /Day\s*\d/i, /行程/, /费用/, /住宿/, /交通/,
    /美食/, /景点/, /注意事项/, /预算汇总/, /总计/,
  ]
  const matchCount = planPatterns.filter((p) => p.test(content)).length
  return matchCount >= 2
}

const lastAIMessageHasPlan = computed(() => {
  if (messages.value.length === 0) return false
  const lastAI = [...messages.value].reverse().find((m) => m.type === 'ai')
  return lastAI && !lastAI.isStreaming && hasPlanContent(lastAI.content)
})

/* ==================== Scroll Management ==================== */
let scrollScheduled = false
let lastScrollTime = 0

const isNearBottom = () => {
  if (!chatContent.value) return true
  const el = chatContent.value
  return el.scrollHeight - el.scrollTop - el.clientHeight < 100
}

const handleScroll = () => {
  isAutoScrollEnabled.value = isNearBottom()
}

const scrollToBottom = async (force = false) => {
  await nextTick()
  if (!chatContent.value) return
  const now = Date.now()
  if (now - lastScrollTime < 50) {
    if (!scrollScheduled) {
      scrollScheduled = true
      setTimeout(() => { scrollToBottom(force); scrollScheduled = false }, 60)
    }
    return
  }
  lastScrollTime = now
  if (force || isAutoScrollEnabled.value) {
    chatContent.value.scrollTo({
      top: chatContent.value.scrollHeight,
      behavior: isSending.value ? 'auto' : 'smooth',
    })
  }
}

/* ==================== Voice Recognition ==================== */
const initSpeechRecognition = () => {
  if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
    recognition.value = new SpeechRecognition()
    recognition.value.lang = 'zh-CN'
    recognition.value.continuous = false
    recognition.value.interimResults = true
    recognition.value.onstart = () => {
      isListening.value = true
      showToast(t('chat.listening'))
    }
    recognition.value.onresult = (event) => {
      let finalTranscript = ''
      let interimTranscript = ''
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript
        if (event.results[i].isFinal) finalTranscript += transcript
        else interimTranscript += transcript
      }
      if (interimTranscript) inputText.value = finalTranscript + interimTranscript
      if (finalTranscript) inputText.value = finalTranscript
    }
    recognition.value.onerror = (event) => {
      isListening.value = false
      const errors = {
        'no-speech': t('chat.errNoSpeech'),
        'audio-capture': t('chat.errAudioCapture'),
        'not-allowed': t('chat.errNotAllowed'),
      }
      showToast(errors[event.error] || t('chat.recognitionFailed'))
    }
    recognition.value.onend = () => {
      isListening.value = false
    }
  }
}

const toggleVoiceInput = () => {
  if (!recognition.value) {
    showToast(t('chat.voiceNotSupported'))
    return
  }
  if (isListening.value) {
    recognition.value.stop()
  } else {
    inputText.value = ''
    recognition.value.start()
  }
}

const stopVoice = () => {
  if (recognition.value) {
    try { recognition.value.stop() } catch (e) { /* ignore */ }
    isListening.value = false
  }
}

/* ==================== Send Message (SSE Streaming) ==================== */
let sendDebounce = false

/* ==================== SSE 断线重连 ==================== */
// 【注意】后端限流 20 次/分钟，重连退避间隔必须 ≥2s（2s / 4s，最多 2 次）
const SSE_RECONNECT_DELAYS = [2000, 4000]
const isReconnecting = ref(false)

/**
 * 流式对话 + 网络错误自动重连：
 * - AbortError（主动取消/关闭弹层）→ 原样上抛，静默不提示
 * - 网络错误 → 丢弃半截回复，退避后重新发送完整消息列表（后端重新生成）
 * - 重连等待期间弹层关闭/组件卸载（abortController 被置 null）→ 中止，不复活
 */
const streamChatWithReconnect = async (chatHistory, aiMsgIndex) => {
  let reconnectCount = 0
  for (;;) {
    abortController = new AbortController()
    try {
      const response = await chatApi.getChatStream([
        {
          role: 'system',
          content: CHAT_SYSTEM_PROMPT,
        },
        ...chatHistory,
      ], abortController.signal)

      if (!response.ok) throw new Error(`HTTP ${response.status}`)

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
            // 所有行都保留：首行剥掉 data: 前缀，续行原样保留（含真实换行的分帧内容不再丢失）
            let content = dataLines
              .map((line) => line.replace(/^data:\s?/, ''))
              .join('\n')
            if (content && content !== '[DONE]' && content.trim().toLowerCase() !== 'null') {
              if (isThinking.value) isThinking.value = false
              // 防御：过滤可能残留的 "null" 分片
              messages.value[aiMsgIndex].content += content
              scrollToBottom()
            }
          } catch (e) {
            /* skip malformed SSE lines */
          }
        }
      }
      return // 流正常结束
    } catch (e) {
      if (e?.name === 'AbortError') throw e // 主动取消，不重连
      if (reconnectCount >= SSE_RECONNECT_DELAYS.length) throw e // 重连次数耗尽
      const delay = SSE_RECONNECT_DELAYS[reconnectCount]
      reconnectCount += 1
      if (messages.value[aiMsgIndex]) messages.value[aiMsgIndex].content = '' // 丢弃半截回复，重发完整消息列表
      isReconnecting.value = true
      await new Promise((r) => setTimeout(r, delay))
      isReconnecting.value = false
      // 重连等待期间弹层关闭/卸载 → 中止，避免后台悄悄重连
      if (!abortController) {
        const err = new Error('aborted during reconnect wait')
        err.name = 'AbortError'
        throw err
      }
    }
  }
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || isSending.value) {
    showToast(t('chat.inputRequired'))
    return
  }
  if (sendDebounce) return
  sendDebounce = true
  setTimeout(() => { sendDebounce = false }, 500)

  isSending.value = true
  isThinking.value = true
  showQuickBar.value = false

  messages.value.push({ id: generateUniqueId(), type: 'user', content: text })
  inputText.value = ''
  await nextTick()
  scrollToBottom(true)

  const aiMsgIndex = messages.value.length
  messages.value.push({ id: generateUniqueId(), type: 'ai', content: '', isStreaming: true })

  const { destination, budget, days } = props.contextQuery
  let prompt = text
  if (destination && budget && days) {
    prompt = `我计划去${destination}旅游，预算${budget}元，共${days}天。${text}`
  }

  const chatHistory = messages.value
    .filter((m) => m.type === 'user' || (m.type === 'ai' && m.content && m.content.length > 0))
    .map((m) => ({ role: m.type === 'user' ? 'user' : 'assistant', content: m.content }))
  // BUGID 修复：此前构造的上下文 prompt 从未传给后端（死代码），这里把它注入为当前这条
  // user 消息的 content，让 AI 真正收到目的地/预算/天数上下文；与 sendQuickWithContext 共用同一套逻辑
  if (prompt !== text) {
    const lastUserMsg = [...chatHistory].reverse().find(m => m.role === 'user')
    if (lastUserMsg) {
      lastUserMsg.content = prompt
    } else {
      chatHistory.push({ role: 'user', content: prompt })
    }
  }

  try {
    await streamChatWithReconnect(chatHistory, aiMsgIndex)
    if (messages.value[aiMsgIndex]) messages.value[aiMsgIndex].isStreaming = false
    isSending.value = false
    isThinking.value = false
    showQuickBar.value = true
  } catch (e) {
    if (e?.name === 'AbortError') return
    if (messages.value[aiMsgIndex]) messages.value[aiMsgIndex].isStreaming = false
    isSending.value = false
    isThinking.value = false
    isReconnecting.value = false
    showQuickBar.value = true
    showToast(t('chat.requestFailed'))
  }
}

const sendQuickQuestion = (question) => {
  inputText.value = question
  sendMessage()
}

/* 【修复】快捷指令携带上下文：将已有行程内容附带给AI */
const sendQuickWithContext = (label, query) => {
  const lastAIMsg = [...messages.value].reverse().find(m => m.type === 'ai' && m.content?.length > 50)
  if (lastAIMsg) {
    inputText.value = `【当前行程参考】\n${lastAIMsg.content.slice(0, 500)}\n\n【新需求】${query}`
  } else {
    inputText.value = query
  }
  sendMessage()
}

/* ==================== Save Plan ==================== */
const savePlan = async () => {
  if (isSavingPlan.value) return
  const { destination, budget, days } = props.contextQuery
  const lastAI = [...messages.value].reverse().find((m) => m.type === 'ai')
  if (!lastAI) return

  try {
    isSavingPlan.value = true
    await planApi.savePlan({
      destination: destination || '未指定',
      budget: Number(budget) || null,
      days: Number(days) || null,
      // 后端 SavedPlanRequest 只有 planData 字段，content 会被丢弃 → 内容放进 planData
      planData: { content: lastAI.content },
      source: 'home',
    })
    showToast(t('chat.planSaved'))
    emit('plan-saved', {
      destination: destination || '未指定',
      budget: budget || '',
      days: days || '',
      content: lastAI.content,
    })
  } catch (e) {
    showToast(t('chat.saveFailed'))
  } finally {
    isSavingPlan.value = false
  }
}

/* ==================== Dialog Controls ==================== */
/** 保存会话前处理：最后一条若仍在流式生成（被中断），先标记为已中断再持久化，
 *  避免重开时恢复半截的流式气泡 / isStreaming 残留 */
const saveSessionMessagesSafe = () => {
  const list = messages.value
  if (Array.isArray(list) && list.length) {
    const last = list[list.length - 1]
    if (last && last.type === 'ai' && last.isStreaming) {
      last.isStreaming = false
      last.interrupted = true
    }
  }
  saveCurrentSessionMessages(list)
}

const closeDialog = () => {
  saveSessionMessagesSafe()
  localVisible.value = false
  emit('close')
  if (abortController) { abortController.abort(); abortController = null }
  stopVoice()
  isSending.value = false
  isThinking.value = false
  isReconnecting.value = false
}

/* ==================== Init Messages ==================== */
/* 清洗历史消息里遗留的 "nullnullnull..."（旧版后端推理分片产生的脏数据） */
const stripLeadingNulls = (content) =>
  typeof content === 'string' ? content.replace(/^(null\s*)+/i, '') : content || ''

const initMessages = () => {
  // 【修复】优先从持久存储加载当前会话消息
  const saved = getCurrentSessionMessages()
  const { destination, budget, days } = props.contextQuery

  if (props.initialMessages && props.initialMessages.length > 0) {
    messages.value = [...props.initialMessages]
  } else if (saved.length > 1) {
    // 有历史会话记录 → 恢复（清洗旧 AI 消息里遗留的 null 前缀）
    messages.value = saved.map((m) => (m.type === 'ai' ? { ...m, content: stripLeadingNulls(m.content) } : m))
  } else {
    // 全新会话 → 种子消息
    messages.value = [{ id: genMsgId(), type: 'system', content: t('chat.greeting') }]
    if (destination && budget && days) {
      messages.value.push({ id: genMsgId(), type: 'system', content: t('chat.loadedInfo', { dest: destination, days: days, budget: budget }) })
    }
  }
}

/* 【修复】新建会话 */
const newConversation = () => {
  saveSessionMessagesSafe() // 先保存当前
  createNewSession()
  messages.value = [{
    id: genMsgId(), type: 'system',
    content: t('chat.greetingNew'),
  }]
  showQuickBar.value = false
  showToast({ message: t('chat.newConversationStarted'), position: 'top' })
}

/* 【修复】清空当前会话 */
const clearConversation = () => {
  clearCurrentSession()
  messages.value = [{
    id: genMsgId(), type: 'system',
    content: t('chat.greeting'),
  }]
  showQuickBar.value = false
  showToast({ message: t('chat.conversationCleared'), position: 'top' })
}

/* ==================== Lifecycle ==================== */
watch(
  () => props.visible,
  async (val) => {
    if (val) {
      // 【修复】打开时从持久存储恢复会话消息
      initMessages()
      showQuickBar.value = messages.value.length > 1
      await nextTick(); await nextTick()
      scrollToBottom(true)
    } else {
      // 【修复】关闭时保存会话，不断开SSE连接（由closeDialog处理）
      saveSessionMessagesSafe()
      if (abortController) {
        abortController.abort()
        abortController = null
      }
      stopVoice()
      isSending.value = false
      isThinking.value = false
      isReconnecting.value = false
    }
  }
)

// Initialize voice recognition on creation
initSpeechRecognition()

// 组件销毁时兜底中止：父组件直接卸载（未走 visible=false）时也要断开 SSE、停语音，
// 防止流式写入已卸载组件的响应式状态、后台请求泄漏
onBeforeUnmount(() => {
  if (abortController) { abortController.abort(); abortController = null }
  isReconnecting.value = false
  stopVoice()
})
</script>

<template>
  <van-popup
    v-model:show="localVisible"
    position="bottom"
    :style="{ height: '88%', maxHeight: '88vh' }"
    lock-scroll
    close-on-click-overlay
    round
    safe-area-inset-bottom
    @update:show="(val) => !val && closeDialog()"
  >
    <div class="dialog-root">
      <!-- ======== Header ======== -->
      <div class="dialog-header">
        <div class="header-left">
          <button class="header-action-btn" @click="newConversation" :title="t('chat.newConversation')" :aria-label="t('chat.newConversation')">
            <van-icon name="add-o" size="18" color="#64748B" />
          </button>
        </div>
        <span class="header-title">{{ t('chat.assistantName') }}</span>
        <div class="header-right">
          <button class="header-action-btn" @click="clearConversation" :title="t('chat.clearConversation')" :aria-label="t('chat.clearConversation')">
            <van-icon name="delete-o" size="18" color="#EF4444" />
          </button>
          <van-icon name="cross" size="20" color="#64748B" class="header-close" role="button" :aria-label="t('chat.close')" @click="closeDialog" />
        </div>
      </div>

      <!-- ======== Chat Body ======== -->
      <div ref="chatContent" class="chat-body" @scroll="handleScroll">
        <div class="chat-inner">
          <!-- Guide State -->
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
            <p class="guide-heading">{{ t('chat.guideHeading') }}</p>
            <p class="guide-hint">{{ t('chat.guideHint') }}</p>
            <div class="guide-chips">
              <button v-for="(chip, i) in guideChips" :key="i" class="guide-chip" @click="sendQuickQuestion(chip.query)">
                {{ chip.label }}
              </button>
            </div>
          </div>

          <!-- Message List -->
          <div v-else class="msg-list">
            <div v-for="msg in messages" :key="msg.id" :class="['msg-row', msg.type]">
              <!-- System message -->
              <div v-if="msg.type === 'system'" class="sys-msg">
                <span>{{ msg.content }}</span>
              </div>

              <!-- User message -->
              <div v-else-if="msg.type === 'user'" class="user-msg-row">
                <div class="user-bubble">{{ msg.content }}</div>
                <div class="user-avatar"><van-icon name="user-o" color="#fff" size="16" /></div>
              </div>

              <!-- AI message -->
              <div v-else-if="msg.type === 'ai'" class="ai-msg-row">
                <div class="ai-avatar">🤖</div>
                <div class="ai-bubble" :class="{ streaming: msg.isStreaming }">
                  <!-- Thinking animation -->
                  <div v-if="isThinking && msg === messages[messages.length - 1]" class="thinking">
                    <span class="think-dot" /><span class="think-dot" /><span class="think-dot" />
                    <span class="think-text">{{ t('chat.thinking') }}</span>
                  </div>
                  <!-- Rendered markdown -->
                  <div v-else class="md-body" v-html="renderMarkdown(msg.content)" />
                </div>
              </div>
            </div>

            <!-- Save plan button -->
            <div v-if="lastAIMessageHasPlan" class="save-plan-row">
              <button class="save-plan-btn" :disabled="isSavingPlan" @click="savePlan">
                <van-loading v-if="isSavingPlan" size="14" color="#fff" />
                <span v-else>💾 {{ t('chat.saveToPlan') }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- ======== Footer Input ======== -->
      <div class="chat-footer">
        <!-- SSE reconnect hint -->
        <div v-if="isReconnecting" class="reconnect-tip">
          <van-loading size="14" color="#8B5CF6" />{{ t('chat.reconnecting') }}
        </div>
        <!-- Quick chips bar -->
        <div v-if="showQuickBar && messages.length > 1" class="quick-bar">
          <button v-for="(q, i) in quickQuestions" :key="i" class="quick-chip" @click="sendQuickWithContext(q.label, q.query)">
            {{ q.label }}
          </button>
        </div>

        <!-- Input row -->
        <div class="input-row">
          <div class="input-glass">
            <button class="input-action" :class="{ listening: isListening }" :aria-label="t('chat.voiceInput')" @click="toggleVoiceInput">
              <van-icon :name="isListening ? 'volume' : 'volume-o'" size="20" :color="isListening ? '#8B5CF6' : '#94A3B8'" />
            </button>
            <input
              v-model="inputText"
              type="text"
              :placeholder="t('chat.inputPlaceholder')"
              class="msg-input"
              @keyup.enter="sendMessage"
              @focus="scrollToBottom(true)"
            />
            <button
              class="send-btn"
              :class="{ disabled: !inputText.trim() || isSending }"
              :disabled="!inputText.trim() || isSending"
              :aria-label="t('chat.send')"
              @click="sendMessage"
            >
              <van-icon v-if="!isSending" name="arrow-up" size="20" color="#fff" />
              <van-loading v-else size="16" color="#fff" />
            </button>
          </div>
        </div>

        <!-- Voice toast -->
        <div v-if="isListening" class="voice-toast">
          <span class="voice-pulse" />{{ t('chat.listening') }}
        </div>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
/* ================================================================
   AIChatDialog — 全屏遮罩AI聊天弹窗
   【修复】z-index: 10000 → 高于底部5Tab导航(9999)和悬浮AI按钮(9995)
   底部预留Tab栏高度(56px) + safe-area 缓冲空白
   ================================================================ */

/* 【修复】弹窗遮罩层级：高于Tab栏(9999)，低于Toast(99999) */
:deep(.van-overlay) { z-index: 9990 !important; }
:deep(.van-popup) { z-index: 10000 !important; }

/* ==================== Dialog Root ==================== */
.dialog-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: linear-gradient(175deg, #ede9f6 0%, #f0ecf9 30%, #f4f1fb 60%, #ede9f6 100%);
}

/* ==================== Header ==================== */
.dialog-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(18px) saturate(170%);
  -webkit-backdrop-filter: blur(18px) saturate(170%);
  border-bottom: 0.5px solid rgba(0,0,0,0.05);
}
.header-left, .header-right { display: flex; align-items: center; gap: 6px; }
.header-title {
  font-size: 17px;
  font-weight: 700;
  color: #1E293B;
}
.header-action-btn {
  width: 36px; height: 36px; min-width: 36px; min-height: 36px;
  display: flex; align-items: center; justify-content: center;
  border: none; background: transparent; border-radius: 50%;
  cursor: pointer; transition: background 0.2s;
}
.header-action-btn:active { background: rgba(0,0,0,0.05); }
.header-close {
  cursor: pointer;
  width: 36px; height: 36px; min-width: 36px; min-height: 36px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%;
  transition: background 0.2s;
}
.header-close:active {
  background: rgba(0, 0, 0, 0.05);
}

/* ==================== Chat Body ==================== */
.chat-body {
  /* 【修复】动态计算高度：100% - header(52px) - footer(~130px) */
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px 14px;
  padding-bottom: 20px;
  -webkit-overflow-scrolling: touch;
  isolation: isolate;
}
.chat-inner {
  max-width: 500px;
  margin: 0 auto;
  width: 100%;
}

/* ==================== Guide ==================== */
.chat-guide {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 30px;
  text-align: center;
}
.guide-illustration {
  margin-bottom: 20px;
}
.guide-heading {
  font-size: 20px;
  font-weight: 700;
  color: #1E293B;
  margin: 0 0 8px;
}
.guide-hint {
  font-size: 13px;
  color: #94A3B8;
  margin: 0 0 24px;
}
.guide-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  max-width: 340px;
}
.guide-chip {
  padding: 10px 18px;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(10px) saturate(150%);
  -webkit-backdrop-filter: blur(10px) saturate(150%);
  border: 1px solid rgba(255,255,255,0.4);
  border-radius: 22px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  transition: all 0.2s;
}
.guide-chip:hover {
  border-color: #c4b5fd;
  color: #7C3AED;
}
.guide-chip:active {
  transform: scale(0.96);
  background: #faf5ff;
}

/* ==================== Message List ==================== */
.msg-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-bottom: 8px;
}
.msg-row.user,
.msg-row.ai,
.msg-row.system {
  /* base class for potential extensions */
}

/* System message */
.sys-msg {
  align-self: center;
  background: rgba(139, 92, 246, 0.08);
  color: #7C3AED;
  font-size: 12px;
  padding: 8px 16px;
  border-radius: 20px;
  text-align: center;
  max-width: 85%;
}

/* User message */
.user-msg-row {
  display: flex;
  justify-content: flex-end;
  align-items: flex-end;
  gap: 8px;
  animation: msgIn 0.3s ease-out;
}
.user-bubble {
  max-width: 75%;
  padding: 12px 18px;
  background: linear-gradient(135deg, #A78BFA 0%, #8B5CF6 100%);
  color: #fff;
  border-radius: 20px 20px 6px 20px;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  box-shadow: 0 4px 14px rgba(139, 92, 246, 0.25);
}
.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #c4b5fd, #8B5CF6);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* AI message */
.ai-msg-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  animation: msgIn 0.35s ease-out;
}
.ai-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ede9fe, #ddd6fe);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.ai-bubble {
  max-width: 80%;
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 6px 20px 20px 20px;
  border: 1px solid rgba(139, 92, 246, 0.08);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  font-size: 14px;
  line-height: 1.7;
  color: #334155;
  word-break: break-word;
  min-width: 70px;
}
.ai-bubble.streaming {
  min-height: 48px;
}

@keyframes msgIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.user-msg-row,
.ai-msg-row {
  /* will-change 已禁用 — 每条消息预分配GPU层导致OOM */
}

/* ==================== Thinking Animation ==================== */
.thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.think-dot {
  width: 7px;
  height: 7px;
  background: #8B5CF6;
  border-radius: 50%;
  animation: thinkBounce 1.4s ease-in-out infinite both;
}
.think-dot:nth-child(2) {
  animation-delay: 0.16s;
}
.think-dot:nth-child(3) {
  animation-delay: 0.32s;
}
.think-text {
  font-size: 13px;
  color: #94A3B8;
}
@keyframes thinkBounce {
  0%, 80%, 100% {
    transform: scale(0.5);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* ==================== Markdown ==================== */
.md-body {
  line-height: 1.75;
}
.md-body :deep(pre) {
  background: #f8f7ff;
  border-radius: 12px;
  padding: 14px;
  overflow-x: auto;
  margin: 10px 0;
  border: 1px solid rgba(139, 92, 246, 0.08);
}
.md-body :deep(code) {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 12px;
}
.md-body :deep(p code) {
  background: rgba(139, 92, 246, 0.08);
  padding: 2px 6px;
  border-radius: 6px;
  color: #7C3AED;
}
.md-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 14px 0;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(139, 92, 246, 0.08);
  display: block;
  overflow-x: auto;
}
.md-body :deep(th),
.md-body :deep(td) {
  border-bottom: 1px solid rgba(139, 92, 246, 0.06);
  padding: 9px 12px;
  text-align: left;
  font-size: 13px;
}
.md-body :deep(th) {
  background: rgba(139, 92, 246, 0.04);
  font-weight: 600;
  color: #1E293B;
}
.md-body :deep(tr:nth-child(even)) {
  background: rgba(139, 92, 246, 0.02);
}
.md-body :deep(blockquote) {
  margin: 14px 0;
  padding: 12px 16px;
  border-left: 4px solid #A78BFA;
  background: rgba(139, 92, 246, 0.04);
  border-radius: 0 12px 12px 0;
}
.md-body :deep(h2),
.md-body :deep(h3) {
  margin: 16px 0 8px;
  font-weight: 600;
  color: #1E293B;
}
.md-body :deep(h2) {
  font-size: 1.2em;
}
.md-body :deep(h3) {
  font-size: 1.1em;
}
.md-body :deep(ul),
.md-body :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}
.md-body :deep(li) {
  margin: 5px 0;
}
.md-body :deep(p) {
  margin: 6px 0;
}
.md-body :deep(strong) {
  color: #7C3AED;
}
.md-body :deep(hr) {
  border: none;
  border-top: 1px solid rgba(139, 92, 246, 0.08);
  margin: 14px 0;
}

/* ==================== Save Plan Button ==================== */
.save-plan-row {
  display: flex;
  justify-content: center;
  padding: 4px 0;
}
.save-plan-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 24px;
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  color: #fff;
  border: none;
  border-radius: 24px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(139, 92, 246, 0.3);
  transition: all 0.2s;
}
.save-plan-btn:active {
  transform: scale(0.95);
}
.save-plan-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* ==================== Footer ==================== */
.chat-footer {
  flex-shrink: 0;
  position: relative;
  z-index: 100;
  padding: 6px 14px;
  /* 【修复】底部预留 Tab栏高度(56px) + safe-area + 12px间隙，输入框完全不被遮挡 */
  padding-bottom: calc(56px + env(safe-area-inset-bottom, 0px) + 12px);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-top: 1px solid rgba(139, 92, 246, 0.08);
  box-shadow: 0 -4px 20px rgba(139, 92, 246, 0.06);
}

/* SSE reconnect hint */
.reconnect-tip {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  font-size: 12px; color: #8B5CF6; padding: 2px 0 6px;
}

/* Quick chips bar */
.quick-bar {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 4px 0 8px;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}
.quick-bar::-webkit-scrollbar {
  display: none;
}
.quick-chip {
  flex-shrink: 0;
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  border: 1px solid rgba(139, 92, 246, 0.12);
  border-radius: 20px;
  font-size: 12px;
  color: #7C3AED;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
}
.quick-chip:active {
  background: rgba(139, 92, 246, 0.1);
  border-color: #c4b5fd;
  transform: scale(0.95);
}

/* Input row */
.input-row {
  max-width: 500px;
  margin: 0 auto;
}
.input-glass {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 6px 6px 16px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1.5px solid rgba(139, 92, 246, 0.1);
  border-radius: 28px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.03);
  transition: border-color 0.25s, box-shadow 0.25s;
}
.input-glass:focus-within {
  border-color: rgba(139, 92, 246, 0.35);
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.08);
}

.input-action {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 50%;
  flex-shrink: 0;
  transition: background 0.2s;
}
.input-action.listening {
  background: rgba(139, 92, 246, 0.1);
  animation: pulse-ring 1.5s infinite;
}
@keyframes pulse-ring {
  0% {
    box-shadow: 0 0 0 0 rgba(139, 92, 246, 0.3);
  }
  70% {
    box-shadow: 0 0 0 12px rgba(139, 92, 246, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(139, 92, 246, 0);
  }
}

.msg-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 16px;
  color: #1E293B;
  padding: 10px 0;
}
.msg-input::placeholder {
  color: #94A3B8;
  opacity: 1;
}
.msg-input::-webkit-input-placeholder {
  color: #94A3B8;
  opacity: 1;
}

.send-btn {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.3);
}
.send-btn:active {
  transform: scale(0.92);
}
.send-btn.disabled {
  background: #CBD5E1;
  box-shadow: none;
  cursor: not-allowed;
}

/* Voice toast */
.voice-toast {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 12px;
  color: #8B5CF6;
}
.voice-pulse {
  width: 8px;
  height: 8px;
  background: #8B5CF6;
  border-radius: 50%;
  animation: pulse-ring 1s infinite;
}

/* ==================== Small screen adaptation ==================== */
@media screen and (max-width: 360px) {
  .chat-body {
    padding: 12px 10px;
    padding-bottom: calc(120px + env(safe-area-inset-bottom, 0px));
  }
  .chat-footer {
    padding: 4px 10px;
    padding-bottom: calc(6px + env(safe-area-inset-bottom, 0px));
  }
  .input-glass {
    padding: 4px 4px 4px 12px;
  }
}
</style>
