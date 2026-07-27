<script setup>
/**
 * 全局空状态组件 — 旅行主题轻量化插画风格
 * 统一紫色品牌基调，增加旅行装饰元素消除大面积空白
 */
defineProps({
  icon: { type: String, default: 'orders-o' },
  iconSize: { type: [String, Number], default: 72 },
  iconColor: { type: String, default: '#A78BFA' },
  title: { type: String, default: '暂无数据' },
  desc: { type: String, default: '' },
  btnText: { type: String, default: '' },
  btnType: { type: String, default: 'outline' }, // 'outline' | 'gradient'
})

const emit = defineEmits(['btn-click'])
</script>

<template>
  <div class="empty-root">
    <!-- 旅行装饰 SVG -->
    <div class="empty-decor">
      <svg viewBox="0 0 200 180" width="100%" height="100%" preserveAspectRatio="xMidYMid meet" class="decor-svg">
        <!-- 热气球 -->
        <g transform="translate(30, 25)" opacity="0.18">
          <ellipse cx="30" cy="26" rx="20" ry="22" fill="#A78BFA" />
          <line x1="10" y1="48" x2="12" y2="62" stroke="#A78BFA" stroke-width="1.2" />
          <line x1="50" y1="48" x2="48" y2="62" stroke="#A78BFA" stroke-width="1.2" />
          <rect x="14" y="58" width="32" height="8" rx="3" fill="#A78BFA" opacity="0.6" />
        </g>
        <!-- 背包 -->
        <g transform="translate(160, 105)" opacity="0.14">
          <rect x="0" y="12" width="22" height="28" rx="5" fill="#6366F1" />
          <path d="M5 12 Q5 4 11 3 Q17 4 17 12" stroke="#6366F1" stroke-width="1.5" fill="none" />
        </g>
        <!-- 海岸线 -->
        <path d="M0 155 Q30 148 60 155 Q90 162 120 155 Q150 148 180 155 Q210 162 240 155" fill="none" stroke="#A78BFA" stroke-width="1.5" opacity="0.12" />
        <path d="M0 165 Q30 158 60 165 Q90 172 120 165 Q150 158 180 165 Q210 172 240 165" fill="none" stroke="#6366F1" stroke-width="1" opacity="0.08" />
        <!-- 山峰 -->
        <path d="M0 180 L50 130 L85 160 L120 118 L155 150 L180 125 L220 155 L240 180 Z" fill="#A78BFA" opacity="0.06" />
        <!-- 云朵 -->
        <g transform="translate(100, 15) scale(0.4)" opacity="0.1">
          <ellipse cx="40" cy="20" rx="40" ry="14" fill="white" />
          <ellipse cx="65" cy="14" rx="30" ry="12" fill="white" />
          <ellipse cx="20" cy="16" rx="28" ry="11" fill="white" />
        </g>
      </svg>
    </div>

    <!-- 图标区域 -->
    <div class="empty-icon-wrap">
      <van-icon :name="icon" :size="iconSize" :color="iconColor" class="empty-icon-main" />
    </div>

    <!-- 文字 -->
    <p class="empty-title">{{ title }}</p>
    <p v-if="desc" class="empty-desc">{{ desc }}</p>

    <!-- 按钮 -->
    <van-button
      v-if="btnText"
      round
      :class="['empty-btn', 'btn-tap-scale', btnType === 'gradient' ? 'btn-gradient' : 'btn-outline']"
      @click="emit('btn-click')"
    >
      {{ btnText }}
    </van-button>
  </div>
</template>

<style scoped>
.empty-root {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px 40px;
  text-align: center;
  min-height: 340px;
  overflow: hidden;
}

/* 装饰 SVG */
.empty-decor {
  position: absolute;
  inset: 0;
  pointer-events: none;
  display: flex;
  align-items: center;
  justify-content: center;
}
.decor-svg { max-width: 280px; max-height: 200px; opacity: 0.7; }

/* 图标 */
.empty-icon-wrap {
  position: relative;
  z-index: 2;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(139,92,246,0.06), rgba(99,102,241,0.04));
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
}
.empty-icon-main { opacity: 0.65; }

/* 文字 */
.empty-title {
  position: relative;
  z-index: 2;
  font-size: 17px;
  font-weight: 600;
  color: #475569;
  margin: 0 0 8px;
}
.empty-desc {
  position: relative;
  z-index: 2;
  font-size: 13px;
  color: #94A3B8;
  margin: 0 0 28px;
  line-height: 1.5;
  max-width: 260px;
}

/* 按钮 */
.empty-btn {
  position: relative;
  z-index: 2;
  font-size: 14px;
  padding: 10px 30px;
  border-radius: 22px !important;
  font-weight: 500;
  transition: all 0.2s ease;
}

.btn-outline {
  color: #7C3AED !important;
  border: 1.5px solid #C4B5FD !important;
  background: #fff !important;
}
.btn-outline:hover { background: #faf5ff !important; border-color: #A78BFA !important; }
.btn-outline:active { transform: scale(0.95); }

.btn-gradient {
  background: linear-gradient(135deg, #A78BFA 0%, #8B5CF6 50%, #6366F1 100%) !important;
  color: #fff !important;
  border: none !important;
  box-shadow: 0 6px 20px rgba(139,92,246,0.3);
}
.btn-gradient:hover { box-shadow: 0 10px 28px rgba(139,92,246,0.4); transform: translateY(-2px); }
.btn-gradient:active { transform: scale(0.95); }
</style>
