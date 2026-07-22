<script setup>
/**
 * LoginView.vue — 智能旅游助手 登录/注册 合并页（全量优化版）
 *
 * === 优化清单 ===
 * 一、视觉: 航拍风景虚化背景 + 薄荷青蓝蒙版 + 云朵漂浮动效 + 远山沙滩插画
 * 二、Tab: 激活渐变块+阴影+图标, 表单左右滑动过渡
 * 三、登录: 磨砂输入框+旅行图标+密码显隐+「立即出发登录」+悬浮阴影按钮
 * 四、注册: 磨砂输入框+验证码60s倒计时+协议弹窗+前置强制校验+实时表单校验
 * 五、全局: 入场动画+统一hover/点击反馈+try-catch+情感化文案
 * 六、品牌: 行李箱logo+产品slogan+旅行氛围文案替代生硬工具文字
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showLoadingToast, closeToast, showDialog } from 'vant'
import { setToken } from '../utils/auth'
import { authApi } from '../api'
import {
  setCurrentUser,
  initAccountData,
  accountExists,
  getAccountData,
  setAccountData,
} from '../utils/userAccountStorage'

const router = useRouter()
const route = useRoute()

// ==================== Tab 切换 ====================
const activeTab = ref(route.meta?.initialTab === 'register' ? 'register' : 'login')
const formSlideDir = ref('') // 'left' | 'right' — 表单滑动方向

const switchTab = (tab) => {
  if (tab === activeTab.value) return
  formSlideDir.value = tab === 'register' ? 'left' : 'right'
  activeTab.value = tab
}

// ==================== 密码显隐 ====================
const showLoginPwd = ref(false)
const showRegPwd = ref(false)
const showRegConfirmPwd = ref(false)

// ==================== 登录表单 ====================
const loginForm = reactive({ username: '', password: '' })
const loginErrors = reactive({ username: '', password: '' })
const loginLoading = ref(false)

// ==================== 注册表单 ====================
const registerForm = reactive({
  username: '', phone: '', verifyCode: '',
  password: '', confirmPassword: '',
})
const registerErrors = reactive({
  username: '', phone: '', verifyCode: '',
  password: '', confirmPassword: '',
})
const agreeTerms = ref(false)
const registerLoading = ref(false)

// ==================== 协议弹窗 ====================
const showTermsPopup = ref(false)
const termsPopupTitle = ref('')
const termsPopupContent = ref('')
const termsData = {
  userAgreement: {
    title: '用户协议',
    content: `【首部及导言】
欢迎使用智能旅游助手！

为使用智能旅游助手软件及服务，您应当阅读并遵守《智能旅游助手用户协议》（以下简称"本协议"）。

一、协议的范围
1.1 本协议是您与智能旅游助手之间关于使用本软件及相关服务所订立的协议。
1.2 本协议内容包括协议正文以及所有智能旅游助手已经发布或将来可能发布的各类规则。

二、账号注册与使用
2.1 您在注册账号时，应当提供真实、准确、完整的个人资料。
2.2 您应当妥善保管账号和密码，因您保管不善导致的损失由您自行承担。
2.3 每个手机号仅可注册一个账号，账号不可转让、赠与或继承。

三、用户行为规范
3.1 您在使用本服务时，应当遵守国家法律法规，不得制作、复制、发布、传播违法违规信息。
3.2 您不得利用本服务进行任何可能对互联网正常运转造成不利影响的行为。

四、隐私保护
4.1 我们重视您的隐私保护，具体内容详见《隐私政策》。
4.2 未经您同意，我们不会向第三方提供您的个人信息。`,
  },
  privacyPolicy: {
    title: '隐私政策',
    content: `【隐私政策】

生效日期：2026年1月1日

智能旅游助手（以下简称"我们"）深知个人信息对您的重要性，我们将按照法律法规的规定，保护您的个人信息安全。

一、我们收集的信息
1.1 账号信息：手机号、用户名、密码（加密存储）。
1.2 位置信息：当您使用地图导航功能时，我们会收集您的位置信息。
1.3 设备信息：设备型号、操作系统版本、唯一设备标识符。

二、信息的使用
2.1 为您提供旅行规划、景点推荐等核心服务。
2.2 优化产品体验，改进服务质量。
2.3 向您发送重要的服务通知。

三、信息的存储与保护
3.1 您的个人信息存储于境内服务器。
3.2 我们采用SSL加密传输、数据脱敏等技术手段保护您的信息安全。
3.3 我们制定了严格的数据管理制度，限制员工接触您的个人信息。

四、您的权利
4.1 您可以随时查看、修改您的个人信息。
4.2 您可以注销账号，我们将删除您的所有个人数据。
4.3 您可以撤回已同意的授权。`,
  },
}

const openTerms = (type) => {
  const data = termsData[type]
  if (!data) return
  termsPopupTitle.value = data.title
  termsPopupContent.value = data.content
  showTermsPopup.value = true
}

// ==================== 验证码 ====================
const codeCountdown = ref(0)
let countdownTimer = null

const isPhoneValid = computed(() => /^1[3-9]\d{9}$/.test(registerForm.phone.trim()))

const sendVerifyCode = () => {
  if (codeCountdown.value > 0) return
  if (!isPhoneValid.value) {
    showToast({ message: '请输入正确的11位手机号', position: 'middle', duration: 1800 })
    return
  }
  // TODO: 调用后端验证码发送API
  showToast({ message: '验证码已发送（开发中，请输入 123456）', position: 'middle', duration: 2000 })
  codeCountdown.value = 60
  countdownTimer = setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

// ==================== 表单校验 ====================
const validateLogin = () => {
  loginErrors.username = ''
  loginErrors.password = ''
  let valid = true
  if (!loginForm.username.trim()) { loginErrors.username = '请输入用户名'; valid = false }
  if (!loginForm.password.trim()) { loginErrors.password = '请输入密码'; valid = false }
  return valid
}

const validateRegister = () => {
  registerErrors.username = ''
  registerErrors.phone = ''
  registerErrors.verifyCode = ''
  registerErrors.password = ''
  registerErrors.confirmPassword = ''
  let valid = true

  if (!registerForm.username.trim()) {
    registerErrors.username = '请输入用户名'
    valid = false
  }
  if (!registerForm.phone.trim()) {
    registerErrors.phone = '请输入手机号'
    valid = false
  } else if (!isPhoneValid.value) {
    registerErrors.phone = '请输入正确的11位手机号'
    valid = false
  }
  if (!registerForm.verifyCode.trim()) {
    registerErrors.verifyCode = '请输入短信验证码'
    valid = false
  }
  if (!registerForm.password.trim()) {
    registerErrors.password = '请输入密码'
    valid = false
  } else if (registerForm.password.length < 6) {
    registerErrors.password = '密码至少6位字符'
    valid = false
  }
  if (!registerForm.confirmPassword.trim()) {
    registerErrors.confirmPassword = '请确认密码'
    valid = false
  } else if (registerForm.password !== registerForm.confirmPassword) {
    registerErrors.confirmPassword = '两次输入密码不相同'
    valid = false
  }
  if (!agreeTerms.value) {
    showToast({ message: '请阅读并同意用户协议与隐私政策后再注册', position: 'middle', duration: 2200 })
    valid = false
  }
  return valid
}

// ==================== 注册按钮是否可点击（前端实时控制） ====================
const canRegister = computed(() => {
  return (
    registerForm.username.trim() &&
    isPhoneValid.value &&
    registerForm.verifyCode.trim() &&
    registerForm.password.length >= 6 &&
    registerForm.confirmPassword.trim() &&
    registerForm.password === registerForm.confirmPassword &&
    agreeTerms.value
  )
})

// ==================== 清除校验错误 ====================
const clearLoginError = (field) => { loginErrors[field] = '' }
const clearRegisterError = (field) => { registerErrors[field] = '' }

// ==================== 登录 ====================
const handleLogin = async () => {
  if (!validateLogin()) return

  loginLoading.value = true
  showLoadingToast({ message: '正在登录...', duration: 0, forbidClick: true, loadingType: 'spinner' })

  try {
    const response = await authApi.login({
      username: loginForm.username.trim(),
      password: loginForm.password,
    })

    if (response.code === 0) {
      const data = response.data
      setToken(data.token)

      /*
       * 【多账号隔离】登录成功后：
       * 1. 标记当前登录用户
       * 2. 从后端响应填充用户信息
       * 3. 将用户信息写入该账号的独立存储空间
       */
      const username = data.user?.username || loginForm.username.trim()
      setCurrentUser(username)

      // 确保账号数据空间存在（兼容旧数据迁移）
      if (!accountExists(username)) {
        initAccountData(username)
      }

      const userInfo = {
        avatar: data.user?.avatar || '',
        nickname: data.user?.nickname || loginForm.username.trim(),
        username: username,
        level: data.user?.level || '普通会员',
        points: data.user?.points || 0,
        following: data.user?.following || 0,
        followers: data.user?.followers || 0,
        travelNotes: data.user?.travelNotes || 0,
        bio: data.user?.bio || '',
        citiesVisited: data.user?.citiesVisited || 0,
        totalDays: data.user?.totalDays || 0,
        totalSpent: data.user?.totalSpent || 0,
        totalPhotos: data.user?.totalPhotos || 0,
      }
      // 写入账号独立存储
      setAccountData(username, 'userInfo', userInfo)
      // 兼容旧代码：保留全局 userInfo 缓存（供未迁移的组件读取）
      localStorage.setItem('userInfo', JSON.stringify(userInfo))

      closeToast()
      showToast({ message: '旅途已就绪，马上为你推荐热门目的地✨', position: 'middle', duration: 2000 })
      setTimeout(() => {
        const redirectUrl = localStorage.getItem('redirectUrl')
        if (redirectUrl) { localStorage.removeItem('redirectUrl'); router.push(redirectUrl) }
        else { router.push('/') }
      }, 800)
    } else {
      closeToast()
      showToast({ message: response.message || '登录失败', position: 'middle', duration: 1800 })
    }
  } catch (error) {
    console.error('登录失败:', error)
    closeToast()
    showToast({ message: '网络异常，请稍后重试', position: 'middle', duration: 1800 })
  } finally {
    loginLoading.value = false
  }
}

// ==================== 注册 ====================
const handleRegister = async () => {
  if (!validateRegister()) return

  registerLoading.value = true
  showLoadingToast({ message: '注册中...', duration: 0, forbidClick: true, loadingType: 'spinner' })

  try {
    const response = await authApi.register({
      username: registerForm.username.trim(),
      password: registerForm.password,
      confirmPassword: registerForm.confirmPassword,
      phone: registerForm.phone.trim() || null,
    })

    if (response.code === 0) {
      /*
       * 【多账号隔离】注册成功后：
       * 1. 以用户名为 key 创建全新独立数据空间
       * 2. 如果本地已存在同名账号数据（如离线注册），跳过覆盖
       */
      const newUsername = registerForm.username.trim()
      if (!accountExists(newUsername)) {
        initAccountData(newUsername)
      }

      closeToast()
      showToast({ message: '欢迎加入，开始规划你的第一场专属旅行！🎒', position: 'middle', duration: 2200 })
      activeTab.value = 'login'
      loginForm.username = registerForm.username
      Object.assign(registerForm, { username: '', phone: '', verifyCode: '', password: '', confirmPassword: '' })
      agreeTerms.value = false
    } else if (response.code === -2 || (response.message && response.message.includes('已存在'))) {
      // 后端返回用户名已存在
      closeToast()
      showToast({ message: '该用户名已被注册，请更换用户名', position: 'middle', duration: 2200 })
    } else {
      closeToast()
      showToast({ message: response.message || '注册失败', position: 'middle', duration: 1800 })
    }
  } catch (error) {
    console.error('注册失败:', error)
    closeToast()
    showToast({ message: '网络异常，请稍后重试', position: 'middle', duration: 1800 })
  } finally {
    registerLoading.value = false
  }
}

// ==================== 第三方登录 ====================
const handleThirdPartyLogin = (platform) => {
  try {
    showToast({ message: `${platform}登录功能开发中`, position: 'middle', duration: 1500 })
  } catch (e) { console.error('第三方登录失败:', e) }
}

// ==================== 返回 & 忘记密码 ====================
const goBack = () => {
  try {
    const redirectUrl = localStorage.getItem('redirectUrl')
    if (redirectUrl) { router.push(redirectUrl) }
    else { router.push('/') }
  } catch (e) { router.push('/') }
}

const handleForgetPassword = () => {
  try {
    showDialog({
      title: '找回密码',
      message: '重置密码功能需联系客服处理，\n客服邮箱：support@travel-assistant.com',
      confirmButtonText: '我知道了',
      confirmButtonColor: '#14B8A6',
    }).catch(() => {})
  } catch (e) { console.error('忘记密码弹窗失败:', e) }
}

// ==================== 计算属性 ====================
const isLogin = computed(() => activeTab.value === 'login')

// ==================== 入场动画 ====================
const pageReady = ref(false)
onMounted(() => {
  // 延迟一帧触发入场动画，确保DOM已挂载
  requestAnimationFrame(() => {
    pageReady.value = true
  })
})
</script>

<template>
  <div class="auth-page" :class="{ ready: pageReady }">

    <!--
      ====== 图层0 · z-index:0 · 底层全屏山河风景背景 ======
      Pixabay国内CDN → 稳定加载，无水印免费商用
      无全局模糊/降饱和滤镜，100%保留原图鲜亮色彩
    -->
    <div class="bg-scenery"></div>

    <!-- ====== 返回按钮 ====== -->
    <button class="back-btn" @click="goBack" aria-label="返回">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="15 18 9 12 15 6" />
      </svg>
    </button>

    <!-- ==================== 主内容滚动区 ==================== -->
    <div class="auth-scroll">
      <div class="auth-inner">

        <!-- ======== 品牌标题区 ======== -->
        <div class="brand-section" :class="{ 'entrance-1': pageReady }">
          <div class="logo-ring">
            <!-- 行李箱图标 -->
            <svg viewBox="0 0 44 48" width="38" height="42" fill="none">
              <rect x="8" y="14" width="28" height="30" rx="5" stroke="white" stroke-width="2.2" fill="rgba(255,255,255,0.1)" />
              <path d="M14 14 V8 A4 4 0 0 1 18 4 H26 A4 4 0 0 1 30 8 V14" stroke="white" stroke-width="2.2" fill="none" />
              <rect x="18" y="22" width="8" height="10" rx="2" fill="rgba(255,255,255,0.25)" />
              <line x1="22" y1="8" x2="22" y2="14" stroke="white" stroke-width="2" />
            </svg>
          </div>
          <h1 class="app-title">智能旅游助手</h1>
          <p class="app-tagline">探索世界，从这里出发</p>
          <p class="app-slogan">AI定制行程 &middot; 景点地图 &middot; 热门目的地推荐</p>
        </div>

        <!-- ======== Tab 切换器 ======== -->
        <div class="tab-switcher" :class="{ 'entrance-2': pageReady }">
          <button class="tab-btn" :class="{ active: isLogin }" @click="switchTab('login')">
            <svg viewBox="0 0 18 18" width="16" height="16" fill="currentColor" class="tab-icon">
              <circle cx="9" cy="6" r="3.5" />
              <path d="M2 16 Q2 11 9 11 Q16 11 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
            <span>登录</span>
          </button>
          <button class="tab-btn" :class="{ active: !isLogin }" @click="switchTab('register')">
            <svg viewBox="0 0 18 18" width="16" height="16" fill="currentColor" class="tab-icon">
              <circle cx="9" cy="6" r="3.5" />
              <path d="M2 16 Q2 11 9 11 Q16 11 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <line x1="13" y1="4" x2="13" y2="12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <line x1="9" y1="8" x2="17" y2="8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
            <span>注册</span>
          </button>
          <div class="tab-indicator" :class="{ right: !isLogin }"></div>
        </div>

        <!-- ======== 表单卡片（磨砂玻璃） ======== -->
        <div class="form-card" :class="{ 'entrance-3': pageReady }">

          <!-- ---- 表单滑动容器 ---- -->
          <div class="form-slide-track" :class="{ 'slide-left': formSlideDir === 'left', 'slide-right': formSlideDir === 'right' }">

            <!-- ==================== 登录表单 ==================== -->
            <div v-show="isLogin" class="form-body">
              <!-- 用户名 -->
              <div class="input-group" :class="{ error: loginErrors.username }">
                <span class="input-icon">
                  <svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor">
                    <circle cx="10" cy="7" r="4" />
                    <path d="M3 18 Q3 12 10 12 Q17 12 17 18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                  </svg>
                </span>
                <input
                  v-model="loginForm.username"
                  type="text"
                  placeholder="请输入用户名"
                  class="form-input"
                  @focus="clearLoginError('username')"
                  @input="clearLoginError('username')"
                />
              </div>

              <!-- 密码 -->
              <div class="input-group" :class="{ error: loginErrors.password }">
                <span class="input-icon">
                  <svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor">
                    <rect x="4" y="7" width="12" height="10" rx="2.5" />
                    <path d="M7 7 V5 A3 3 0 0 1 13 5 V7" fill="none" stroke="currentColor" stroke-width="1.8" />
                    <circle cx="10" cy="12.5" r="1.2" />
                    <line x1="10" y1="13.5" x2="10" y2="15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
                  </svg>
                </span>
                <input
                  v-model="loginForm.password"
                  :type="showLoginPwd ? 'text' : 'password'"
                  placeholder="请输入密码"
                  class="form-input"
                  @focus="clearLoginError('password')"
                  @input="clearLoginError('password')"
                />
                <button class="pwd-toggle" @click="showLoginPwd = !showLoginPwd" type="button" :aria-label="showLoginPwd ? '隐藏密码' : '显示密码'">
                  <svg v-if="!showLoginPwd" viewBox="0 0 22 22" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M2 11 S6 6 11 6 S20 16 20 11 S16 6 11 6" />
                    <circle cx="11" cy="11" r="3" />
                  </svg>
                  <svg v-else viewBox="0 0 22 22" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                    <path d="M2 2 L20 20" />
                    <path d="M7 7 Q5 9 4 11 Q7 16 11 16 Q13 16 15 14" />
                    <circle cx="11" cy="11" r="3" />
                  </svg>
                </button>
              </div>

              <!-- 忘记密码 -->
              <div class="link-row">
                <span></span>
                <span class="text-link" @click="handleForgetPassword">忘记密码？</span>
              </div>
            </div>

            <!-- ==================== 注册表单 ==================== -->
            <div v-show="!isLogin" class="form-body">
              <!-- 用户名 -->
              <div class="input-group" :class="{ error: registerErrors.username }">
                <span class="input-icon">
                  <svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor">
                    <circle cx="10" cy="7" r="4" />
                    <path d="M3 18 Q3 12 10 12 Q17 12 17 18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                  </svg>
                </span>
                <input
                  v-model="registerForm.username"
                  type="text"
                  placeholder="请输入用户名"
                  class="form-input"
                  @focus="clearRegisterError('username')"
                  @input="clearRegisterError('username')"
                />
              </div>

              <!-- 手机号 -->
              <div class="input-group" :class="{ error: registerErrors.phone }">
                <span class="input-icon">
                  <svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor">
                    <rect x="5" y="1" width="10" height="18" rx="2.5" />
                    <line x1="8" y1="15" x2="12" y2="15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
                  </svg>
                </span>
                <input
                  v-model="registerForm.phone"
                  type="tel"
                  maxlength="11"
                  placeholder="请输入手机号"
                  class="form-input"
                  @focus="clearRegisterError('phone')"
                  @input="clearRegisterError('phone')"
                />
              </div>

              <!-- 验证码 -->
              <div class="input-group" :class="{ error: registerErrors.verifyCode }">
                <span class="input-icon">
                  <svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor">
                    <rect x="2" y="4" width="16" height="12" rx="2" />
                    <polyline points="3 5 10 11 17 5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </span>
                <input
                  v-model="registerForm.verifyCode"
                  type="text"
                  maxlength="6"
                  placeholder="请输入验证码"
                  class="form-input"
                  @focus="clearRegisterError('verifyCode')"
                  @input="clearRegisterError('verifyCode')"
                />
                <button
                  class="code-btn"
                  :class="{ counting: codeCountdown > 0, disabled: !isPhoneValid }"
                  :disabled="codeCountdown > 0 || !isPhoneValid"
                  @click="sendVerifyCode"
                  type="button"
                >
                  <svg v-if="codeCountdown === 0" viewBox="0 0 16 16" width="13" height="13" fill="currentColor" class="code-btn-icon">
                    <rect x="1" y="3" width="14" height="10" rx="1.5" />
                    <polyline points="2 4 8 8 14 4" fill="none" stroke="currentColor" stroke-width="1.2" />
                  </svg>
                  <span>{{ codeCountdown > 0 ? `${codeCountdown}秒后重发` : '获取验证码' }}</span>
                </button>
              </div>

              <!-- 密码 -->
              <div class="input-group" :class="{ error: registerErrors.password }">
                <span class="input-icon">
                  <svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor">
                    <rect x="4" y="7" width="12" height="10" rx="2.5" />
                    <path d="M7 7 V5 A3 3 0 0 1 13 5 V7" fill="none" stroke="currentColor" stroke-width="1.8" />
                    <circle cx="10" cy="12.5" r="1.2" />
                    <line x1="10" y1="13.5" x2="10" y2="15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
                  </svg>
                </span>
                <input
                  v-model="registerForm.password"
                  :type="showRegPwd ? 'text' : 'password'"
                  placeholder="请输入密码（至少6位）"
                  class="form-input"
                  @focus="clearRegisterError('password')"
                  @input="clearRegisterError('password')"
                />
                <button class="pwd-toggle" @click="showRegPwd = !showRegPwd" type="button" :aria-label="showRegPwd ? '隐藏密码' : '显示密码'">
                  <svg v-if="!showRegPwd" viewBox="0 0 22 22" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M2 11 S6 6 11 6 S20 16 20 11 S16 6 11 6" />
                    <circle cx="11" cy="11" r="3" />
                  </svg>
                  <svg v-else viewBox="0 0 22 22" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                    <path d="M2 2 L20 20" />
                    <path d="M7 7 Q5 9 4 11 Q7 16 11 16 Q13 16 15 14" />
                    <circle cx="11" cy="11" r="3" />
                  </svg>
                </button>
              </div>

              <!-- 确认密码 -->
              <div class="input-group" :class="{ error: registerErrors.confirmPassword }">
                <span class="input-icon">
                  <svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor">
                    <rect x="3" y="6" width="6" height="9" rx="2" />
                    <rect x="11" y="6" width="6" height="9" rx="2" />
                    <path d="M6 6 V4 A2 2 0 0 1 8 2" fill="none" stroke="currentColor" stroke-width="1.5" />
                    <path d="M14 6 V4 A2 2 0 0 1 16 2" fill="none" stroke="currentColor" stroke-width="1.5" />
                    <circle cx="6" cy="11" r="0.8" />
                    <circle cx="14" cy="11" r="0.8" />
                  </svg>
                </span>
                <input
                  v-model="registerForm.confirmPassword"
                  :type="showRegConfirmPwd ? 'text' : 'password'"
                  placeholder="请确认密码"
                  class="form-input"
                  @focus="clearRegisterError('confirmPassword')"
                  @input="clearRegisterError('confirmPassword')"
                />
                <button class="pwd-toggle" @click="showRegConfirmPwd = !showRegConfirmPwd" type="button" :aria-label="showRegConfirmPwd ? '隐藏密码' : '显示密码'">
                  <svg v-if="!showRegConfirmPwd" viewBox="0 0 22 22" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M2 11 S6 6 11 6 S20 16 20 11 S16 6 11 6" />
                    <circle cx="11" cy="11" r="3" />
                  </svg>
                  <svg v-else viewBox="0 0 22 22" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                    <path d="M2 2 L20 20" />
                    <path d="M7 7 Q5 9 4 11 Q7 16 11 16 Q13 16 15 14" />
                    <circle cx="11" cy="11" r="3" />
                  </svg>
                </button>
              </div>

              <!-- 用户协议 -->
              <div class="terms-row">
                <button class="terms-check" @click="agreeTerms = !agreeTerms" type="button" :aria-label="agreeTerms ? '取消勾选' : '勾选同意'">
                  <svg v-if="agreeTerms" viewBox="0 0 20 20" width="18" height="18">
                    <circle cx="10" cy="10" r="9" fill="#14B8A6" />
                    <polyline points="6 10.5 9 13 14 7" fill="none" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                  <svg v-else viewBox="0 0 20 20" width="18" height="18">
                    <circle cx="10" cy="10" r="8.5" fill="none" stroke="rgba(255,255,255,0.45)" stroke-width="1.8" />
                  </svg>
                </button>
                <span class="terms-label">
                  我已阅读并同意
                  <span class="terms-highlight" @click.stop="openTerms('userAgreement')">《用户协议》</span>
                  和
                  <span class="terms-highlight" @click.stop="openTerms('privacyPolicy')">《隐私政策》</span>
                </span>
              </div>
            </div>
          </div>

          <!-- ---- 提交按钮 ---- -->
          <button
            v-if="isLogin"
            class="submit-btn login-submit"
            :disabled="loginLoading"
            @click="handleLogin"
          >
            <svg v-if="!loginLoading" viewBox="0 0 20 20" width="18" height="18" fill="currentColor" class="btn-deco-icon">
              <path d="M2 10 L8 4 L8 8 Q14 8 18 12 L16 8 Q12 4 8 4 L8 2 Z" transform="rotate(-45 10 10)" />
            </svg>
            <span v-if="!loginLoading" class="btn-text">立即出发登录</span>
            <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" class="btn-spinner">
              <circle cx="12" cy="12" r="10" stroke="rgba(255,255,255,0.3)" stroke-width="3" />
              <path d="M12 2 A10 10 0 0 1 22 12" stroke="white" stroke-width="3" stroke-linecap="round">
                <animateTransform attributeName="transform" type="rotate" from="0 12 12" to="360 12 12" dur="0.8s" repeatCount="indefinite" />
              </path>
            </svg>
          </button>
          <button
            v-else
            class="submit-btn register-submit"
            :class="{ disabled: !canRegister }"
            :disabled="!canRegister || registerLoading"
            @click="handleRegister"
          >
            <svg v-if="!registerLoading" viewBox="0 0 44 48" width="18" height="20" fill="none" class="btn-deco-icon">
              <rect x="8" y="14" width="28" height="30" rx="5" stroke="white" stroke-width="2.2" />
              <path d="M14 14 V8 A4 4 0 0 1 18 4 H26 A4 4 0 0 1 30 8 V14" stroke="white" stroke-width="2.2" fill="none" />
            </svg>
            <span v-if="!registerLoading" class="btn-text">开启旅途注册</span>
            <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" class="btn-spinner">
              <circle cx="12" cy="12" r="10" stroke="rgba(255,255,255,0.3)" stroke-width="3" />
              <path d="M12 2 A10 10 0 0 1 22 12" stroke="white" stroke-width="3" stroke-linecap="round">
                <animateTransform attributeName="transform" type="rotate" from="0 12 12" to="360 12 12" dur="0.8s" repeatCount="indefinite" />
              </path>
            </svg>
          </button>

          <!-- ---- 第三方登录 ---- -->
          <div class="third-party">
            <div class="divider-row">
              <span class="divider-line"></span>
              <span class="divider-text">快捷登录</span>
              <span class="divider-line"></span>
            </div>
            <div class="social-icons">
              <!-- 微信 — 聊天气泡风格图标 -->
              <button class="social-btn wechat" @click="handleThirdPartyLogin('微信')" title="微信快捷登录" aria-label="微信登录">
                <svg viewBox="0 0 28 28" width="23" height="23" fill="none">
                  <rect x="2" y="4" width="16" height="12" rx="5" fill="#fff" />
                  <polygon points="8,16 6,20 10,16" fill="#fff" />
                  <circle cx="7" cy="10" r="1.5" fill="#07C160" />
                  <circle cx="13" cy="10" r="1.5" fill="#07C160" />
                  <rect x="12" y="12" width="14" height="11" rx="5" fill="#fff" />
                  <polygon points="18,23 16,27 20,23" fill="#fff" />
                  <circle cx="17" cy="17.5" r="1.3" fill="#07C160" />
                  <circle cx="22" cy="17.5" r="1.3" fill="#07C160" />
                </svg>
              </button>
              <!-- 支付宝 — 蓝色圆形「支」风格 -->
              <button class="social-btn alipay" @click="handleThirdPartyLogin('支付宝')" title="支付宝快捷登录" aria-label="支付宝登录">
                <svg viewBox="0 0 28 28" width="23" height="23" fill="none">
                  <circle cx="14" cy="14" r="12" fill="#fff" />
                  <path d="M22 14.5 H18 V17 H16.5 V14.5 H13 V17 H11.5 V14.5 H8 V13 H11.5 V10.5 H13 V13 H16.5 V10.5 H18 V13 H22 V14.5 Z" fill="#1677FF" />
                </svg>
              </button>
            </div>
          </div>
        </div>

        <!-- ======== 底部版权 ======== -->
        <p class="footer-text" :class="{ 'entrance-4': pageReady }">©2026 智能旅游助手 &mid; 陪你走遍山河湖海</p>

      </div>
    </div>

    <!-- ======== 协议弹窗 ======== -->
    <van-popup v-model:show="showTermsPopup" position="bottom" :style="{ height: '65%', borderRadius: '20px 20px 0 0' }" round safe-area-inset-bottom>
      <div class="terms-popup">
        <div class="terms-popup-header">
          <span class="terms-popup-title">{{ termsPopupTitle }}</span>
          <button class="terms-popup-close" @click="showTermsPopup = false">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
        <div class="terms-popup-body">
          <pre class="terms-content">{{ termsPopupContent }}</pre>
        </div>
        <div class="terms-popup-footer">
          <button class="terms-agree-btn" @click="agreeTerms = true; showTermsPopup = false">我已阅读并同意</button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
/* ================================================================
   全局设计规范
   主色: 薄荷青 #14B8A6 / #0D9488 / #5EEAD4
   辅助: 浅湖蓝 #7DD3FC / #38BDF8
   点缀: 淡紫 #A78BFA / #8B5CF6
   圆角: 卡片20px / 输入框14px / 按钮28px
   阴影: 统一低透明度柔和阴影
   字体: 标题24px/700, 正文15px, 辅助12-13px
   ================================================================ */

/* ==================== 页面容器 ==================== */
.auth-page {
  position: relative;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/*
 * ================================================================
 * 图层0【z-index:0 · 底层全屏山河峡谷风景】
 * 双图兜底：CDN实景 → CSS渐变 → 纯色，层层降级永不空白
 * ================================================================
 */
.bg-scenery {
  position: absolute;
  z-index: 0;
  inset: -5%;
  /*
   * 两层 background-image 堆叠：
   * 第1层(上)：picsum 山湖实景（Fastly 全球 CDN，无防盗链）
   * 第2层(下)：CSS 薄荷青渐变（无需网络，永不失效）
   * 背景色：最终兜底纯色（前两层都失败时）
   */
  background-image:
    url('/pexels-sammccool47-34031036.jpg'),
    linear-gradient(180deg, #87CEEB 0%, #5FADC9 25%, #2D7A6E 55%, #4DB8A0 80%, #7EC8C8 100%);
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
  background-color: #7EC8C8;
  animation: kenBurns 120s ease-in-out infinite alternate;
  will-change: transform;
}

@keyframes kenBurns {
  0%   { transform: scale(1.01) translate(0, 0); }
  33%  { transform: scale(1.04) translate(-0.8%, -0.3%); }
  66%  { transform: scale(1.02) translate(0.4%, -0.5%); }
  100% { transform: scale(1.03) translate(-0.2%, 0.1%); }
}

/* ==================== 返回按钮（虚化悬浮） ==================== */
.back-btn {
  position: absolute;
  top: max(44px, env(safe-area-inset-top, 12px));
  left: 18px;
  z-index: 10;
  width: 44px;
  height: 44px;
  min-width: 44px;
  min-height: 44px;
  border-radius: 50%;
  /* 同卡片玻璃参数 */
  background: rgba(255, 255, 255, 0.22);
  backdrop-filter: blur(9px) saturate(170%) brightness(105%);
  -webkit-backdrop-filter: blur(9px) saturate(170%) brightness(105%);
  border: 1px solid rgba(255,255,255,0.45);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s ease;
  box-shadow: 0 4px 16px rgba(0,0,0,0.10);
}
.back-btn:hover {
  background: rgba(255, 255, 255, 0.32);
  box-shadow: 0 6px 22px rgba(0,0,0,0.14);
}
.back-btn:active {
  transform: scale(0.9);
}

/* ==================== 滚动区 ==================== */
.auth-scroll {
  position: relative;
  z-index: 2;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  padding: 0 24px 40px;
}
.auth-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: max(50px, calc(env(safe-area-inset-top, 12px) + 40px));
  padding-bottom: 50px;
}

/* ==================== 品牌区 ==================== */
.brand-section {
  text-align: center;
  margin-bottom: 28px;
  opacity: 0;
  transform: translateY(16px);
  transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}
.brand-section.entrance-1 {
  opacity: 1;
  transform: translateY(0);
}

.logo-ring {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  /* 同卡片玻璃参数 */
  background: rgba(255, 255, 255, 0.22);
  backdrop-filter: blur(9px) saturate(170%) brightness(105%);
  -webkit-backdrop-filter: blur(9px) saturate(170%) brightness(105%);
  border: 1px solid rgba(255, 255, 255, 0.50);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.10);
}

/* 图层3：标题文字纯白实色，添加黑色文字阴影保证强光风景下清晰 */
.app-title {
  font-size: 28px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 1px;
  margin: 0 0 10px;
  text-shadow: 0 2px 16px rgba(0, 0, 0, 0.35);
  line-height: 1.25;
}

.app-tagline {
  font-size: 14px;
  color: #fff;
  margin: 0 0 8px;
  letter-spacing: 0.6px;
  font-weight: 400;
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.28);
}

.app-slogan {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
  margin: 0;
  letter-spacing: 0.8px;
  font-weight: 400;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.22);
}

/* ==================== 入场动画延迟 ==================== */
.entrance-1 { transition-delay: 0.05s; }
.entrance-2 { transition-delay: 0.15s; }
.entrance-3 { transition-delay: 0.25s; }
.entrance-4 { transition-delay: 0.35s; }

/* ==================== Tab 切换器 ==================== */
.tab-switcher {
  position: relative;
  display: flex;
  width: 100%;
  max-width: 280px;
  /* 同卡片玻璃参数 */
  background: rgba(255, 255, 255, 0.22);
  backdrop-filter: blur(9px) saturate(170%) brightness(105%);
  -webkit-backdrop-filter: blur(9px) saturate(170%) brightness(105%);
  border-radius: 28px;
  padding: 4px;
  margin-bottom: 24px;
  z-index: 5;
  opacity: 0;
  transform: translateY(16px);
  transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1);
  border: 1px solid rgba(255, 255, 255, 0.45);
}
.tab-switcher.entrance-2 {
  opacity: 1;
  transform: translateY(0);
}

.tab-btn {
  flex: 1;
  position: relative;
  z-index: 2;
  padding: 12px 0;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.55);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  border-radius: 24px;
  transition: color 0.35s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.tab-btn.active {
  color: #0D9488;
  font-weight: 600;
}
.tab-btn:active {
  transform: scale(0.95);
}
.tab-icon {
  flex-shrink: 0;
}

.tab-indicator {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  background: #fff;
  border-radius: 24px;
  z-index: 1;
  transition: transform 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow:
    0 4px 16px rgba(0, 0, 0, 0.10),
    0 1px 4px rgba(0, 0, 0, 0.05);
}
.tab-indicator.right {
  transform: translateX(100%);
}

/*
 * ================================================================
 * 图层1【z-index:1 · 中层磨砂玻璃卡片 — 携程同款参数】
 * backdrop-filter 仅模糊卡片覆盖区域, 其余风景100%清晰
 * position:relative 保证层级高于背景·低于内部控件
 * ================================================================
 */
.form-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 380px;
  /* 携程玻璃参数 */
  background: rgba(255, 255, 255, 0.22);
  backdrop-filter: blur(9px) saturate(170%) brightness(105%);
  -webkit-backdrop-filter: blur(9px) saturate(170%) brightness(105%);
  border-radius: 22px;
  padding: 28px 24px 24px;
  /* 1px细白描边 + 柔和下层阴影 → 卡片边界清晰, 不与背景融合 */
  box-shadow:
    0 12px 40px rgba(0, 0, 0, 0.14),
    0 4px 12px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.50);
  overflow: hidden;
  opacity: 0;
  transform: translateY(16px);
  transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}
.form-card.entrance-3 {
  opacity: 1;
  transform: translateY(0);
}

/* ---- 表单滑动容器 ---- */
.form-slide-track {
  transition: transform 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
.form-slide-track.slide-left {
  animation: slideFormLeft 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
.form-slide-track.slide-right {
  animation: slideFormRight 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideFormLeft {
  0% { transform: translateX(0); opacity: 1; }
  40% { transform: translateX(-16px); opacity: 0.6; }
  60% { transform: translateX(12px); opacity: 0.8; }
  100% { transform: translateX(0); opacity: 1; }
}
@keyframes slideFormRight {
  0% { transform: translateX(0); opacity: 1; }
  40% { transform: translateX(16px); opacity: 0.6; }
  60% { transform: translateX(-12px); opacity: 0.8; }
  100% { transform: translateX(0); opacity: 1; }
}

.form-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/*
 * ================================================================
 * 图层3【输入框 — 100%实色不透明白底，不受下层玻璃/风景干扰】
 * ================================================================
 */
.input-group {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.92);
  /* 无 backdrop-filter！顶层实色，不参与玻璃模糊 */
  border-radius: 14px;
  padding: 0 16px;
  height: 52px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  transition: border-color 0.25s ease, box-shadow 0.25s ease;
}
.input-group:focus-within {
  border-color: #10B981;
  box-shadow:
    0 0 0 3px rgba(16, 185, 129, 0.18),
    0 0 12px rgba(16, 185, 129, 0.08);
}
.input-group.error {
  border-color: rgba(239, 68, 68, 0.50);
}
.input-group.error:focus-within {
  border-color: rgba(239, 68, 68, 0.70);
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.12);
}

.input-icon {
  display: flex;
  align-items: center;
  margin-right: 10px;
  color: rgba(0, 0, 0, 0.35);
  flex-shrink: 0;
  transition: color 0.3s ease;
}
.input-group:focus-within .input-icon {
  color: #10B981;
}
.input-group.error .input-icon {
  color: #EF4444;
}

.form-input {
  flex: 1;
  height: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: #1E293B;
  min-width: 0;
}
.form-input::placeholder {
  color: rgba(0, 0, 0, 0.30);
  font-size: 14px;
}

/* 密码显隐按钮 */
.pwd-toggle {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  min-width: 36px;
  min-height: 36px;
  border: none;
  background: transparent;
  color: rgba(0, 0, 0, 0.30);
  cursor: pointer;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  margin-left: 4px;
}
.pwd-toggle:hover {
  background: rgba(16, 185, 129, 0.08);
  color: #10B981;
}
.pwd-toggle:active {
  transform: scale(0.88);
}

/* ==================== 验证码按钮 ==================== */
.code-btn {
  flex-shrink: 0;
  margin-left: 8px;
  padding: 7px 14px;
  border: none;
  border-radius: 22px;
  background: #10B981;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 5px;
}
.code-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.35);
}
.code-btn:active {
  transform: scale(0.94);
}
.code-btn.disabled {
  background: rgba(0, 0, 0, 0.06);
  color: rgba(0, 0, 0, 0.25);
  cursor: not-allowed;
  pointer-events: none;
}
.code-btn.counting {
  background: rgba(0, 0, 0, 0.06);
  color: rgba(0, 0, 0, 0.25);
  cursor: not-allowed;
}
.code-btn-icon {
  flex-shrink: 0;
}

/* ==================== 链接行 + 协议文字 ==================== */
.link-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px;
  margin-top: -4px;
}

.text-link {
  font-size: 13px;
  color: #10B981;
  cursor: pointer;
  transition: all 0.25s ease;
  padding: 4px 8px;
  font-weight: 500;
}
.text-link:hover {
  color: #059669;
}
.text-link:active {
  transform: scale(0.95);
}

/* ==================== 用户协议 ==================== */
.terms-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 2px 4px 0;
}
.terms-check {
  flex-shrink: 0;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding-top: 1px;
  border: none;
  background: transparent;
  transition: transform 0.2s ease;
}
.terms-check:active {
  transform: scale(0.9);
}
.terms-label {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
  line-height: 1.6;
}
.terms-highlight {
  color: #10B981;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 1px 2px;
}
.terms-highlight:hover {
  color: #059669;
  text-decoration: underline;
}
.terms-highlight:active {
  opacity: 0.7;
}

/* ==================== 提交按钮 — 图层3纯实色，无透明度，品牌绿鲜亮 ==================== */
.submit-btn {
  width: 100%;
  height: 54px;
  border: none;
  border-radius: 28px;
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1px;
  cursor: pointer;
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1),
              box-shadow 0.35s ease;
}
.submit-btn:hover {
  transform: translateY(-3px);
}
.submit-btn:active {
  transform: scale(0.94);
}
.submit-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  pointer-events: none;
}
.submit-btn:disabled:hover {
  transform: none;
}

/* 登录按钮：纯实色青绿渐变，无透明度！ */
.login-submit {
  background: linear-gradient(135deg, #059669 0%, #10B981 40%, #34D399 100%);
  box-shadow:
    0 8px 28px rgba(16, 185, 129, 0.45),
    0 2px 10px rgba(5, 150, 105, 0.25);
}
.login-submit:hover {
  box-shadow:
    0 12px 36px rgba(16, 185, 129, 0.55),
    0 4px 16px rgba(5, 150, 105, 0.35);
}

/* 注册按钮：纯实色蓝 */
.register-submit {
  background: linear-gradient(135deg, #2563EB 0%, #3B82F6 40%, #6366F1 100%);
  box-shadow:
    0 8px 28px rgba(59, 130, 246, 0.45),
    0 2px 10px rgba(37, 99, 235, 0.25);
}
.register-submit:hover {
  box-shadow:
    0 12px 36px rgba(59, 130, 246, 0.55),
    0 4px 16px rgba(37, 99, 235, 0.35);
}
.register-submit.disabled {
  background: rgba(0, 0, 0, 0.08);
  box-shadow: none;
  color: rgba(0, 0, 0, 0.25);
}

.btn-deco-icon {
  flex-shrink: 0;
}

.btn-spinner {
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ==================== 第三方登录 ==================== */
.third-party {
  margin-top: 22px;
}

.divider-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.divider-line {
  flex: 1;
  height: 1px;
  background: rgba(0, 0, 0, 0.10);
  border-radius: 0.5px;
}

.divider-text {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.35);
  white-space: nowrap;
}

.social-icons {
  display: flex;
  justify-content: center;
  gap: 28px;
}

.social-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 1.5px solid rgba(0, 0, 0, 0.12);
  background: rgba(255, 255, 255, 0.75);
  color: #333;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.social-btn:hover {
  transform: scale(1.06);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
}
.social-btn:active {
  transform: scale(0.92);
}
.social-btn.wechat:hover {
  box-shadow: 0 0 22px rgba(7, 193, 96, 0.35);
  border-color: rgba(7, 193, 96, 0.4);
}
.social-btn.alipay:hover {
  box-shadow: 0 0 22px rgba(22, 119, 255, 0.35);
  border-color: rgba(22, 119, 255, 0.4);
}

/* ==================== 底部版权 ==================== */
.footer-text {
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.78);
  margin: 28px 0 0;
  letter-spacing: 0.5px;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.20);
  opacity: 0;
  transform: translateY(10px);
  transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}
.footer-text.entrance-4 {
  opacity: 1;
  transform: translateY(0);
}

/* ==================== 协议弹窗 ==================== */
.terms-popup {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}
.terms-popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 14px;
  border-bottom: 1px solid #F1F5F9;
  flex-shrink: 0;
}
.terms-popup-title {
  font-size: 17px;
  font-weight: 700;
  color: #1E293B;
}
.terms-popup-close {
  width: 36px; height: 36px;
  border: none; background: #F8FAFC;
  border-radius: 50%; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: #94A3B8;
  transition: all 0.2s ease;
}
.terms-popup-close:active {
  background: #F1F5F9;
  transform: scale(0.9);
}
.terms-popup-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}
.terms-content {
  font-size: 13px;
  line-height: 1.8;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  margin: 0;
}
.terms-popup-footer {
  flex-shrink: 0;
  padding: 14px 20px;
  padding-bottom: calc(14px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid #F1F5F9;
}
.terms-agree-btn {
  width: 100%;
  padding: 14px;
  border: none;
  border-radius: 24px;
  background: linear-gradient(135deg, #14B8A6, #0D9488);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
}
.terms-agree-btn:hover {
  box-shadow: 0 6px 20px rgba(20, 184, 166, 0.35);
}
.terms-agree-btn:active {
  transform: scale(0.96);
}

/* ==================== Vant 弹窗覆盖 ==================== */
:deep(.van-popup) { z-index: 10000 !important; }
:deep(.van-overlay) { z-index: 9990 !important; }

/* ==================== 移动端适配 ==================== */
@media screen and (max-width: 360px) {
  .auth-scroll { padding: 0 16px 30px; }
  .form-card { padding: 20px 16px 18px; }
  .app-title { font-size: 22px; }
  .logo-ring { width: 60px; height: 60px; }
  .tab-switcher { max-width: 240px; }
  .tab-btn { font-size: 14px; padding: 9px 0; }
  .input-group { height: 46px; border-radius: 12px; }
  .submit-btn { height: 48px; font-size: 16px; }
  .social-btn { width: 42px; height: 42px; }
}

@media screen and (min-width: 420px) {
  .form-card { border-radius: 22px; padding: 28px 26px 26px; }
  .input-group { height: 52px; border-radius: 15px; }
  .submit-btn { height: 54px; }
}

/* 深色模式微调 — 控件实色不受影响 */
@media (prefers-color-scheme: dark) {
  .form-input { color: #1E293B; }
  .form-input::placeholder { color: rgba(0, 0, 0, 0.30); }
}
</style>
