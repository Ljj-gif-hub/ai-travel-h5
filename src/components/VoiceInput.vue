<template>
  <div class="voice-input-bar">
    <!-- 按住说话按钮 -->
    <div
      class="voice-btn"
      :class="{ recording: isRecording, disabled: disabled }"
      @touchstart.prevent="startRecord"
      @touchend.prevent="stopRecord"
      @touchcancel.prevent="stopRecord"
      @mousedown.prevent="startRecord"
      @mouseup.prevent="stopRecord"
      @mouseleave.prevent="stopRecord"
    >
      <van-icon :name="isRecording ? 'audio' : 'audio-o'" size="20" />
      <span>{{ isRecording ? '松开发送' : '按住说话' }}</span>
    </div>

    <!-- 录音波形指示器 -->
    <div class="voice-wave" v-if="isRecording">
      <span
        v-for="i in 5"
        :key="i"
        class="wave-bar"
        :style="{ animationDelay: (i * 0.1) + 's' }"
      />
    </div>

    <!-- 不可用提示 -->
    <div class="voice-unavailable" v-if="!isSupported">
      <span>语音识别不可用</span>
    </div>
  </div>
</template>

<script setup>
/**
 * VoiceInput.vue — 移动端"按住说话"语音输入组件
 *
 * 基于浏览器原生 SpeechRecognition API（Web Speech API）实现
 * 兼容性：优先使用 window.SpeechRecognition，回退到 webkitSpeechRecognition
 * 不支持时显示提示文字并禁用按钮
 *
 * 使用方式：
 *   <VoiceInput @result="onVoiceText" @recording-start="onStart" @recording-end="onEnd" />
 */

import { ref, onMounted, onUnmounted } from 'vue'

/* ==================== Props ==================== */
const props = defineProps({
  /** 是否禁用语音输入 */
  disabled: { type: Boolean, default: false },
})

/* ==================== Emits ==================== */
const emit = defineEmits(['result', 'recording-start', 'recording-end'])

/* ==================== 状态 ==================== */
/** 是否正在录音 */
const isRecording = ref(false)
/** 浏览器是否支持语音识别 */
const isSupported = ref(false)
/** SpeechRecognition 实例 */
let recognition = null
/** 防重复触发标记 */
let isStarting = false

/* ==================== 初始化检查 ==================== */
/**
 * 检测并获取 SpeechRecognition 构造函数
 * 优先标准 API，回退到 webkit 前缀
 */
function getSpeechRecognition() {
  const SpeechRecognition =
    window.SpeechRecognition || window.webkitSpeechRecognition
  return SpeechRecognition || null
}

/**
 * 检测浏览器是否支持语音识别
 */
function checkSupport() {
  const Constructor = getSpeechRecognition()
  isSupported.value = !!Constructor
  return Constructor
}

/* ==================== 录音控制 ==================== */

/**
 * 开始录音
 * 创建识别实例，配置中文识别，开始监听
 */
function startRecord() {
  // 防抖：避免快速连续触发
  if (isStarting) return
  if (props.disabled) {
    showToast('语音输入已禁用')
    return
  }
  if (!isSupported.value) {
    showToast('当前浏览器不支持语音识别，请使用 Chrome 或 Edge')
    return
  }

  // 如果已在录音中，先停止
  if (isRecording.value && recognition) {
    try {
      recognition.stop()
    } catch (e) {
      /* 忽略 */
    }
    isRecording.value = false
  }

  isStarting = true

  try {
    const SpeechRecognition = getSpeechRecognition()
    if (!SpeechRecognition) {
      isStarting = false
      return
    }

    // 创建新的识别实例
    recognition = new SpeechRecognition()

    // 配置参数
    recognition.lang = 'zh-CN' // 中文普通话
    recognition.continuous = false // 单次识别，说完自动停止
    recognition.interimResults = false // 不返回中间结果，只返回最终文本

    // ==================== 事件处理 ====================

    /**
     * 识别成功 — 获取最终转写文本
     */
    recognition.onresult = (event) => {
      // 从结果中提取转写文本
      let transcript = ''
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i]
        if (result.isFinal) {
          transcript += result[0].transcript
        }
      }

      // 去除首尾空格
      transcript = transcript.trim()

      if (transcript) {
        emit('result', transcript)
      } else {
        showToast('未识别到语音内容，请重试')
      }
    }

    /**
     * 识别出错 — 显示错误提示并重置状态
     */
    recognition.onerror = (event) => {
      console.error('[VoiceInput] 语音识别错误:', event.error, event.message)

      // 根据错误类型给出不同提示
      const errorMessages = {
        'no-speech': '未检测到语音，请再试一次',
        'aborted': '语音识别已取消',
        'audio-capture': '无法访问麦克风，请检查权限',
        'network': '网络连接失败，请检查网络',
        'not-allowed': '麦克风权限被拒绝，请在设置中允许',
        'service-not-allowed': '语音识别服务不可用',
        'bad-grammar': '语法错误',
        'language-not-supported': '不支持当前语言',
      }

      const msg = errorMessages[event.error] || '语音识别失败，请重试'
      showToast(msg)

      // 重置状态
      isRecording.value = false
      isStarting = false
      emit('recording-end')
    }

    /**
     * 识别结束 — 重置 UI 状态
     */
    recognition.onend = () => {
      isRecording.value = false
      isStarting = false
      emit('recording-end')
    }

    /**
     * 开始说话检测 — 用于 UI 反馈
     */
    recognition.onspeechstart = () => {
      // 用户开始说话，可以在这里添加 UI 反馈
    }

    /**
     * 停止说话检测
     */
    recognition.onspeechend = () => {
      // 用户停止说话，识别即将完成
    }

    // 启动识别
    recognition.start()
    isRecording.value = true
    isStarting = false
    emit('recording-start')
  } catch (error) {
    console.error('[VoiceInput] 启动语音识别失败:', error)
    isRecording.value = false
    isStarting = false
    showToast('启动语音识别失败，请重试')
    emit('recording-end')
  }
}

/**
 * 停止录音
 * 主动停止识别，触发结果回调
 */
function stopRecord() {
  if (!isRecording.value || !recognition) {
    isStarting = false
    return
  }

  try {
    recognition.stop()
  } catch (e) {
    // 可能已经停止，忽略错误
    console.warn('[VoiceInput] 停止识别时出错:', e)
  }

  // recognition.onend 回调会处理状态重置
}

/* ==================== 生命周期 ==================== */
onMounted(() => {
  checkSupport()
})

onUnmounted(() => {
  // 组件卸载时清理识别实例
  if (recognition) {
    try {
      recognition.abort()
    } catch (e) {
      /* 忽略 */
    }
    recognition = null
  }
  isRecording.value = false
  isStarting = false
})

/**
 * 动态导入 showToast（与项目现有模式一致）
 * 使用 import() 避免在 SSR 中报错
 */
function showToast(message) {
  // 动态导入 vant Toast，避免模块循环依赖
  import('vant').then(({ showToast: toast }) => {
    toast({
      message,
      position: 'middle',
      duration: 2000,
    })
  }).catch(() => {
    // 回退：如果动态导入失败，尝试全局注册的方式
    console.warn('[VoiceInput] Toast 不可用:', message)
  })
}
</script>

<style scoped>
/* ==================== 语音输入容器 ==================== */
.voice-input-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  width: 100%;
  padding: 10px 16px;
  position: relative;
}

/* ==================== 按住说话按钮 ==================== */
.voice-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  max-width: 300px;
  height: 44px;
  background: #f3f4f6;
  border-radius: 22px;
  color: #64748b;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  user-select: none;
  -webkit-user-select: none;
  transition: all 0.2s ease;
  border: 1px solid rgba(139, 92, 246, 0.08);
}

.voice-btn:active {
  background: #e5e7eb;
  transform: scale(0.98);
}

/* 禁用状态 */
.voice-btn.disabled {
  opacity: 0.4;
  cursor: not-allowed;
  pointer-events: none;
}

/* 录音中状态 — 紫色高亮 + 脉冲动画 */
.voice-btn.recording {
  background: linear-gradient(135deg, #ede9fe 0%, #ddd6fe 100%);
  color: #8b5cf6;
  border-color: rgba(139, 92, 246, 0.3);
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.12);
  animation: voicePulse 1.5s ease-in-out infinite;
}

@keyframes voicePulse {
  0%,
  100% {
    box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.12);
  }
  50% {
    box-shadow: 0 0 0 12px rgba(139, 92, 246, 0);
  }
}

/* ==================== 波形指示器 ==================== */
.voice-wave {
  display: flex;
  align-items: center;
  gap: 3px;
  height: 32px;
}

.wave-bar {
  display: block;
  width: 3px;
  height: 100%;
  background: linear-gradient(180deg, #8b5cf6 0%, #a78bfa 100%);
  border-radius: 2px;
  animation: waveBounce 0.6s ease-in-out infinite alternate;
}

@keyframes waveBounce {
  0% {
    transform: scaleY(0.3);
    opacity: 0.5;
  }
  100% {
    transform: scaleY(1);
    opacity: 1;
  }
}

/* ==================== 不可用状态 ==================== */
.voice-unavailable {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
}

.voice-unavailable span {
  font-size: 12px;
  color: #94a3b8;
}
</style>
