<template>
  <div class="generating-state">
    <!-- ==================== 进度环形图区域 ==================== -->
    <div class="progress-section">
      <div class="progress-ring">
        <!-- SVG 环形进度条 -->
        <svg viewBox="0 0 120 120" class="progress-svg">
          <!-- 背景灰色圆环 -->
          <circle
            cx="60"
            cy="60"
            r="52"
            class="progress-bg"
            fill="none"
            stroke="#f0f0f5"
            stroke-width="6"
          />
          <!-- 紫色渐变进度圆环 -->
          <circle
            cx="60"
            cy="60"
            r="52"
            class="progress-fill"
            fill="none"
            stroke="url(#progressGradient)"
            stroke-width="6"
            stroke-linecap="round"
            :stroke-dasharray="circumference"
            :stroke-dashoffset="strokeDashoffset"
          />
          <!-- SVG 渐变定义 -->
          <defs>
            <linearGradient id="progressGradient" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stop-color="#8B5CF6" />
              <stop offset="100%" stop-color="#6366F1" />
            </linearGradient>
          </defs>
        </svg>
        <!-- 环形中央文字 -->
        <div class="progress-text">
          <span class="progress-num">{{ displayProgress }}%</span>
          <span class="progress-label">{{ currentStep }}</span>
        </div>
      </div>
    </div>

    <!-- ==================== 步骤列表 ==================== -->
    <div class="step-list">
      <div
        v-for="(step, index) in stepList"
        :key="step.name"
        class="step-item"
        :class="[(step.status === 'doing' ? 'active' : step.status), { 'step-item--last': index === stepList.length - 1 }]"
      >
        <!-- 步骤状态圆点 -->
        <div class="step-dot">
          <van-icon
            v-if="step.status === 'done'"
            name="success"
            color="#52c41a"
            size="14"
          />
          <van-icon
            v-else-if="step.status === 'active' || step.status === 'doing'"
            name="clock-o"
            color="#8B5CF6"
            size="14"
          />
          <van-icon
            v-else
            name="ellipsis"
            color="#d1d5db"
            size="14"
          />
        </div>
        <!-- 步骤信息 -->
        <div class="step-info">
          <span class="step-name">{{ step.name }}</span>
          <span
            class="step-progress"
            v-if="step.status === 'active' && step.progress > 0"
          >
            {{ step.progress }}%
          </span>
        </div>
      </div>
    </div>

    <!-- ==================== 骨架屏加载卡片 ==================== -->
    <div class="skeleton-cards">
      <div class="skeleton-card" v-for="i in 3" :key="i">
        <!-- 标题行骨架 -->
        <div class="sk-row sk-title" />
        <!-- 文本行骨架 -->
        <div class="sk-row sk-text" />
        <!-- 短文本行骨架 -->
        <div class="sk-row sk-text short" />
        <!-- 额外短行 -->
        <div class="sk-row sk-text shorter" />
      </div>
    </div>

    <!-- ==================== 停止生成按钮 ==================== -->
    <div class="stop-btn-wrapper">
      <van-button
        round
        block
        type="default"
        class="stop-btn"
        @click="$emit('stop')"
      >
        <van-icon name="pause-circle-o" size="18" />
        <span>停止生成</span>
      </van-button>
    </div>
  </div>
</template>

<script setup>
/**
 * TripGeneratingState.vue — AI 行程生成中的状态展示组件
 *
 * 设计用于放置在 DragSheet 的 slot 中，展示：
 *   1. 环形进度条（SVG 方案，无需额外依赖）
 *   2. 分步状态列表（带时间轴风格的圆点指示器）
 *   3. 骨架屏加载卡片（shimmer 动画，与 PlanningView 风格一致）
 *   4. 停止生成按钮
 *
 * 使用方式：
 *   <DragSheet v-model="drawerState">
 *     <TripGeneratingState
 *       :progress="store.state.progress"
 *       :currentStep="store.state.currentStep"
 *       :stepList="store.state.stepList"
 *       @stop="handleStop"
 *     />
 *   </DragSheet>
 */

import { computed } from 'vue'

/* ==================== Props ==================== */
const props = defineProps({
  /** 总进度百分比 0-100 */
  progress: { type: Number, default: 0 },
  /** 当前步骤描述文字 */
  currentStep: { type: String, default: '正在连接...' },
  /** 步骤列表：{ name: string, progress: number, status: 'wait'|'active'|'done' } */
  stepList: {
    type: Array,
    default: () => [],
  },
})

/* ==================== Emits ==================== */
defineEmits(['stop'])

/* ==================== SVG 环形进度条计算 ==================== */
/** 圆环周长（r=52 时的圆周） */
const circumference = 2 * Math.PI * 52 // ≈ 326.73

/** 显示的进度值（取整） */
const displayProgress = computed(() => Math.round(props.progress))

/** 动态计算 stroke-dashoffset，实现进度递增动画 */
const strokeDashoffset = computed(() => {
  return circumference * (1 - Math.min(props.progress, 100) / 100)
})
</script>

<style scoped>
/* ==================== 容器 ==================== */
.generating-state {
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* ==================== 进度环形图 ==================== */
.progress-section {
  display: flex;
  justify-content: center;
  margin-bottom: 24px;
}

.progress-ring {
  position: relative;
  width: 140px;
  height: 140px;
}

.progress-svg {
  width: 100%;
  height: 100%;
  /* 逆时针旋转90度，让进度从顶部开始 */
  transform: rotate(-90deg);
}

/* 进度填充圆环过渡动画 */
.progress-fill {
  transition: stroke-dashoffset 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 中央文字区域 */
.progress-text {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.progress-num {
  font-size: 28px;
  font-weight: 700;
  color: #8b5cf6;
  line-height: 1.1;
}

.progress-label {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
  text-align: center;
  max-width: 100px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ==================== 步骤列表 ==================== */
.step-list {
  width: 100%;
  max-width: 340px;
  margin-bottom: 20px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  position: relative;
}

/* 步骤间连接线 */
.step-item:not(.step-item--last)::after {
  content: '';
  position: absolute;
  left: 10px;
  top: 34px;
  bottom: -6px;
  width: 2px;
  background: #e5e7eb;
}

/* 已完成步骤的连接线变绿 */
.step-item.done:not(.step-item--last)::after {
  background: #52c41a;
}

/* 进行中步骤的连接线变紫 */
.step-item.active:not(.step-item--last)::after {
  background: linear-gradient(180deg, #8b5cf6 0%, #e5e7eb 100%);
}

/* 步骤状态圆点 */
.step-dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #f3f4f6;
  border: 2px solid #e5e7eb;
  z-index: 1;
}

/* 已完成 — 绿色 */
.step-item.done .step-dot {
  background: #f0fdf4;
  border-color: #52c41a;
}

/* 进行中 — 紫色 + 脉冲动画 */
.step-item.active .step-dot {
  background: #f5f3ff;
  border-color: #8b5cf6;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.12);
  animation: stepDotPulse 1.8s ease-in-out infinite;
}

@keyframes stepDotPulse {
  0%,
  100% {
    box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.12);
  }
  50% {
    box-shadow: 0 0 0 10px rgba(139, 92, 246, 0);
  }
}

/* 步骤信息 */
.step-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.step-name {
  font-size: 14px;
  color: #9ca3af;
  transition: color 0.3s ease;
}

.step-item.active .step-name {
  color: #1e293b;
  font-weight: 600;
}

.step-item.done .step-name {
  color: #64748b;
}

/* 当前步骤的进度百分比 */
.step-progress {
  font-size: 11px;
  color: #8b5cf6;
  background: rgba(139, 92, 246, 0.08);
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

/* ==================== 骨架屏加载卡片 ==================== */
.skeleton-cards {
  width: 100%;
  max-width: 340px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.skeleton-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

/* 复用 PlanningView 的 sk-* 骨架样式 + 本项目 shimmer 动效 */
.sk-row {
  background: linear-gradient(
    90deg,
    #e2e8f0 25%,
    #f1f5f9 50%,
    #e2e8f0 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.8s ease-in-out infinite;
  border-radius: 6px;
}

.sk-title {
  height: 18px;
  width: 55%;
  margin-bottom: 14px;
}

.sk-text {
  height: 13px;
  margin-bottom: 10px;
}

.sk-text.short {
  width: 70%;
}

.sk-text.shorter {
  width: 40%;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* ==================== 停止生成按钮 ==================== */
.stop-btn-wrapper {
  width: 100%;
  max-width: 340px;
}

.stop-btn {
  height: 42px;
  font-size: 14px;
  color: #64748b !important;
  background: #f3f4f6 !important;
  border: 1px solid rgba(139, 92, 246, 0.12) !important;
  border-radius: 21px !important;
  transition: all 0.2s ease;
}

.stop-btn:active {
  background: #e5e7eb !important;
  transform: scale(0.98);
}

.stop-btn :deep(.van-button__content) {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
