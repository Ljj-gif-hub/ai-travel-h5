<script setup>
/**
 * ShareLandingView — 分享落地页（公开免登录）
 * 通过分享短链 token 只读渲染行程内容 + 可生成分享海报
 */
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { shareApi } from '../api'
import EmptyState from '../components/EmptyState.vue'
import SharePoster from '../components/SharePoster.vue'

const route = useRoute()
const plan = ref(null)
const loading = ref(true)
const error = ref('')
const showPoster = ref(false)

const stripTags = (name) => (name || '').replace(/【[^】]*】/g, '').trim()

const load = async () => {
  const token = route.params.token
  if (!token) { error.value = '分享链接无效'; loading.value = false; return }
  try {
    const res = await shareApi.getSharedPlan(token)
    if (res.code === 0 && res.data) {
      let data = res.data.planData || res.data
      if (typeof data === 'string') {
        try { data = JSON.parse(data) } catch (e) { data = null }
      }
      plan.value = data
      loading.value = false
    } else {
      error.value = res.message || '分享内容不存在'
      loading.value = false
    }
  } catch (e) {
    error.value = '加载失败，请稍后重试'
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="landing-page">
    <van-nav-bar title="旅行规划分享" safe-area-inset-top class="nav-bar" />

    <div v-if="loading" class="center"><van-loading color="#8B5CF6" size="28" /></div>

    <div v-else-if="error" class="center">
      <EmptyState icon="warn-o" :title="error" desc="链接可能已失效" />
    </div>

    <div v-else-if="plan" class="content">
      <!-- 头部概览 -->
      <div class="hero">
        <div class="hero-title">{{ plan.destination || '旅行' }} · {{ plan.days || '' }}天</div>
        <div v-if="plan.people" class="hero-sub">{{ plan.people }} 人同行</div>
        <p v-if="plan.overview" class="overview">{{ plan.overview }}</p>
        <van-button size="small" round type="primary" class="poster-btn" @click="showPoster = !showPoster">
          {{ showPoster ? '收起海报' : '生成分享海报' }}
        </van-button>
      </div>

      <!-- 分享海报 -->
      <SharePoster v-if="showPoster" :plan="plan" />

      <!-- 每日行程 -->
      <div v-for="(dp, di) in (plan.dayPlans || [])" :key="di" class="day-card">
        <div class="day-head">{{ dp.day_title || `第${dp.day || di + 1}天` }}</div>
        <div v-for="(slot, si) in (dp.timeSlots || [])" :key="si" class="slot">
          <span class="slot-time">{{ slot.time || '' }}</span>
          <span class="slot-name">{{ stripTags(slot.attraction) }}</span>
        </div>
        <div v-if="dp.meals && dp.meals.length" class="meals">
          <div class="meals-title">🍽 餐饮</div>
          <div v-for="(m, mi) in dp.meals" :key="mi" class="meal">{{ m }}</div>
        </div>
      </div>

      <!-- 出行贴士 -->
      <div v-if="plan.tips && plan.tips.length" class="tips-card">
        <div class="tips-title">💡 出行贴士</div>
        <div v-for="(t, ti) in plan.tips" :key="ti" class="tip">· {{ t }}</div>
      </div>

      <div class="brand-line">—— 由 AI 智能旅游助手生成 ——</div>
    </div>
  </div>
</template>

<style scoped>
.landing-page {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(175deg, #ede9f6 0%, #f8f7fd 100%);
  box-sizing: border-box;
  padding-bottom: calc(60px + var(--safe-area-bottom));
  overflow-x: hidden;
}
.nav-bar { background: rgba(255,255,255,0.6); backdrop-filter: blur(16px); }
.center { display: flex; justify-content: center; padding: 80px 0; }

.content { max-width: 480px; margin: 0 auto; padding: 8px 16px; }

.hero {
  background: linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%);
  border-radius: 20px; padding: 22px 20px; color: #fff; margin-bottom: 16px;
  box-shadow: 0 8px 24px rgba(139,92,246,0.28);
}
.hero-title { font-size: 24px; font-weight: 700; margin-bottom: 4px; }
.hero-sub { font-size: 14px; opacity: 0.85; margin-bottom: 8px; }
.overview { font-size: 13px; line-height: 1.6; opacity: 0.92; margin-bottom: 12px; }
.poster-btn { background: rgba(255,255,255,0.18) !important; border: 1px solid rgba(255,255,255,0.4) !important; color: #fff !important; }

.day-card {
  background: rgba(255,255,255,0.85); border-radius: 16px; padding: 16px;
  margin-bottom: 12px; box-shadow: 0 4px 16px rgba(0,0,0,0.05);
  border: 1px solid rgba(139,92,246,0.08);
}
.day-head { font-size: 16px; font-weight: 700; color: #7C3AED; margin-bottom: 10px; }
.slot { display: flex; align-items: baseline; gap: 10px; padding: 6px 0; border-bottom: 1px dashed #F1F5F9; }
.slot:last-of-type { border-bottom: none; }
.slot-time { font-size: 12px; color: #94A3B8; width: 52px; flex-shrink: 0; }
.slot-name { font-size: 14px; color: #1E293B; font-weight: 500; }
.meals { margin-top: 10px; }
.meals-title { font-size: 13px; color: #64748B; font-weight: 600; margin-bottom: 4px; }
.meal { font-size: 12px; color: #64748B; line-height: 1.6; }

.tips-card {
  background: rgba(255,255,255,0.85); border-radius: 16px; padding: 16px;
  margin-bottom: 16px; box-shadow: 0 4px 16px rgba(0,0,0,0.05);
}
.tips-title { font-size: 14px; font-weight: 700; color: #1E293B; margin-bottom: 8px; }
.tip { font-size: 12px; color: #64748B; line-height: 1.8; }

.brand-line { text-align: center; font-size: 12px; color: #B8A6D9; padding: 12px 0 20px; }
</style>
