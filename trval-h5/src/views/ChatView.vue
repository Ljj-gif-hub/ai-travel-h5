<script setup>
// ... 保留你原本的代码不变 ...
import { ref, nextTick, onMounted, onUnmounted, onActivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showLoadingToast, closeToast } from 'vant'
import { generateTravelPlan } from '../api/plan'

const router = useRouter()
const route = useRoute()

const destination = route.query.destination || ''
const budget = route.query.budget || ''
const days = route.query.days || ''

const messages = ref([
  {
    id: 1,
    type: 'system',
    content: '欢迎来到 AI 智能旅游助手，我可以帮您规划完美的旅行！',
  },
])
const inputText = ref('')
const messageList = ref(null)
const isSending = ref(false)
const initialWindowHeight = ref(window.innerHeight)

const goBack = () => {
  if (window.history.length <= 1) {
    router.push('/')
  } else {
    router.back()
  }
}

const generateUniqueId = () => {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
}

const formatMarkdown = (text) => {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/^\d+\.\s(.+)$/gm, '<li>$1</li>')
    .replace(/\n/g, '')
}

const sendMessage = async () => {
  if (!inputText.value.trim() || isSending.value) {
    showToast('请输入内容')
    return
  }
  isSending.value = true

  messages.value.push({
    id: generateUniqueId(),
    type: 'user',
    content: inputText.value,
  })

  const userInput = inputText.value
  inputText.value = ''
  await nextTick()
  scrollToBottom()

  const aiMsg = {
    id: generateUniqueId(),
    type: 'ai',
    content: '',
  }
  messages.value.push(aiMsg)

  let prompt = userInput
  if (destination && budget && days) {
    prompt = `我计划去${destination}旅游，预算${budget}元，共${days}天。${userInput}`
  }

  try {
    await generateTravelPlan(
      {
        destination,
        budget,
        days,
        question: prompt,
      },
      (token) => {
        aiMsg.content += token
        scrollToBottom()
      }
    )
  } catch (e) {
    aiMsg.content = 'AI 回复失败，请稍后重试'
  } finally {
    isSending.value = false
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messageList.value) {
    messageList.value.scrollTo({
      top: messageList.value.scrollHeight,
      behavior: isSending.value ? 'auto' : 'smooth',
    })
  }
}

const handleResize = () => {
  const currentHeight = window.innerHeight
  if (currentHeight < initialWindowHeight.value * 0.75) {
    setTimeout(scrollToBottom, 100)
  }
}

onMounted(() => {
  scrollToBottom()
  window.addEventListener('resize', handleResize)

  if (destination && budget && days) {
    messages.value.push({
      id: generateUniqueId(),
      type: 'system',
      content: `已为您加载 ${destination} ${days} 天 ${budget} 元的行程，您可以继续问我细节哦～`,
    })
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

onActivated(() => {
  nextTick(() => scrollToBottom())
})
</script>

<template>
  <div class="chat-container">
    <van-nav-bar
      title="AI 旅游助手"
      left-text="返回"
      left-arrow
      safe-area-inset-top
      class="nav-bar"
      @click-left="goBack"
    />

    <!-- ✅ 修改 1：调整高度计算，减去输入框高度(约70px)，防止遮挡底部Tab -->
    <div 
      ref="messageList" 
      class="message-list"
    >
      <div
        v-for="msg in messages"
        :key="msg.id"
        :class="['message-item', msg.type]"
      >
        <div v-if="msg.type === 'system'" class="system-card">
          <van-icon name="info-o" size="18" color="#667eea" />
          <span>{{ msg.content }}</span>
        </div>

        <template v-else-if="msg.type === 'user'">
          <div class="message-bubble user-bubble">
            <div class="bubble-content">{{ msg.content }}</div>
          </div>
          <div class="avatar user-avatar">
            <van-icon name="user-o" size="20" color="#fff" />
          </div>
        </template>

        <template v-else-if="msg.type === 'ai'">
          <div class="avatar ai-avatar">
            <van-icon name="bot" size="20" color="#667eea" />
          </div>
          <div class="message-bubble ai-bubble">
            <div class="bubble-content" v-html="formatMarkdown(msg.content)"></div>
          </div>
        </template>
      </div>
    </div>

    <!-- ✅ 修改 2：去掉 fixed 定位，改为自然流布局 -->
    <div class="input-footer">
      <div class="input-area">
        <van-icon name="plus" size="24" color="#999" />
        <van-field
          v-model="inputText"
          placeholder="输入您的问题..."
          clearable
          @keyup.enter="sendMessage"
        />
        <van-button
          type="primary"
          size="small"
          class="send-btn"
          :disabled="!inputText.trim() || isSending"
          @click="sendMessage"
        >
          <van-loading v-if="isSending" size="14px" color="#fff" />
          <span v-else>发送</span>
        </van-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-container {
  min-height: 100vh;
  background-color: #f7f8fa;
  padding-bottom: 80px; /* ✅ 增加底部内边距，给固定Tab栏留出空间 */
  box-sizing: border-box;
  position: relative; /* ✅ 确保内部绝对定位元素相对于此容器 */
}

.nav-bar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
:deep(.van-nav-bar__title) {
  color: #fff;
}
:deep(.van-nav-bar__left) {
  color: #fff;
}

.message-list {
  /* ✅ 核心修复：高度 = 全屏 - 顶部导航栏(46) - 输入框高度(约70) - 上下间距 */
  height: calc(100vh - 180px); 
  overflow-y: auto;
  padding: 16px;
  box-sizing: border-box;
  background: #ffffff;
  margin: 16px;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border: 1px solid #e8e8e8;
  scrollbar-width: none;
}
.message-list::-webkit-scrollbar {
  display: none;
}

.message-item {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
  width: 100%;
}

.message-item.system {
  justify-content: center;
}
.system-card {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: #f5f7fa;
  border: 1px solid #e0e4ed;
  border-radius: 20px;
  padding: 10px 18px;
  font-size: 13px;
  color: #666;
}

.message-item.user {
  flex-direction: row-reverse;
  padding-right: 10px;
}
.message-item.ai {
  justify-content: flex-start;
  padding-left: 10px;
}

.avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.user-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.ai-avatar {
  background: #fff;
  border: 1.5px solid #e0e0e0;
}

.message-bubble {
  max-width: 68%;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.user-bubble {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border-radius: 18px 4px 18px 18px;
}
.ai-bubble {
  background: #ffffff;
  color: #333;
  border: 1px solid #e8e8e8;
  border-radius: 4px 18px 18px 18px;
}

/* ✅ 修改 3：移除 fixed 定位，恢复静态布局 */
.input-footer {
  position: static; /* ✅ 改为静态定位，跟随文档流 */
  bottom: auto;
  left: auto;
  right: auto;
  z-index: auto;
  padding: 8px 16px;
  background: #f7f8fa;
  border-top: 1px solid #e8e8e8;
  /* ✅ 增加底部间距，防止内容被手机底部横条遮挡 */
  padding-bottom: calc(8px + env(safe-area-inset-bottom)); 
}
.input-area {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border-radius: 16px;
  padding: 4px 12px;
}
.input-area :deep(.van-field) {
  flex: 1;
  background: #f5f7fa;
  border-radius: 20px;
  padding: 0 12px;
}
.send-btn {
  padding: 4px 24px;
  border-radius: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  border: none !important;
  color: #fff;
}
</style>