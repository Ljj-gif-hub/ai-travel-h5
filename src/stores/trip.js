/**
 * trip.js — 行程页面共享状态管理（无 Pinia 依赖的 reactive 单例模式）
 *
 * 设计理念：
 *   使用 Vue 3 的 reactive() 创建全局单例响应式状态，
 *   通过 useTripStore() 获取状态和操作函数。
 *   不依赖 Pinia，降低外部依赖，可在任何组件中通过 composable 方式使用。
 *
 * 状态划分：
 *   - phase: 行程页面当前阶段（generating / completed）
 *   - progress: 生成进度（0-100）
 *   - currentStep: 当前步骤描述文字
 *   - stepList: 分步进度列表
 *   - planData: 生成的完整行程数据
 *   - costBreakdown: 费用明细
 *   - hotelList: 酒店推荐列表
 *   - mapMarkers: 地图标注点
 *   - drawerState: 抽屉吸附状态
 *   - taskId: 当前生成任务ID
 *   - params: 用户输入参数
 *
 * 使用方式：
 *   import { useTripStore } from '@/stores/trip'
 *   const { state, resetState } = useTripStore()
 */

import { reactive, computed, toRefs } from 'vue'

/* ==================== 全局共享响应式状态（单例） ==================== */
const state = reactive({
  /* ---------- 行程阶段 ---------- */
  /** 当前阶段：'generating'（生成中）| 'completed'（已完成） */
  phase: 'generating',

  /* ---------- 生成进度 ---------- */
  /** 总进度百分比（0-100） */
  progress: 0,
  /** 当前步骤描述文字，如 "正在分析目的地..." */
  currentStep: '正在连接...',
  /** 分步进度列表 */
  stepList: [
    { name: '分析目的地', progress: 0, status: 'wait' },
    { name: '生成线路概览', progress: 0, status: 'wait' },
    { name: '规划每日行程', progress: 0, status: 'wait' },
    { name: '筛选酒店推荐', progress: 0, status: 'wait' },
    { name: '整理出行贴士', progress: 0, status: 'wait' },
    { name: '汇总费用明细', progress: 0, status: 'wait' },
    { name: '全部完成', progress: 0, status: 'wait' },
  ],

  /* ---------- 行程数据 ---------- */
  /** 完整行程计划数据（包含 dayPlans / tips 等） */
  planData: null,
  /** 费用明细对象 */
  costBreakdown: null,
  /** 酒店推荐列表 */
  hotelList: [],
  /** 地图标注点列表（景点 + 地铁站） */
  mapMarkers: [],

  /* ---------- UI 状态 ---------- */
  /** 抽屉吸附状态：'min' | 'mid' | 'max' */
  drawerState: 'mid',
  /** 当前生成任务ID */
  taskId: null,

  /* ---------- 用户参数 ---------- */
  /** 用户输入的行程参数 */
  params: {
    destination: '', // 目的地
    days: 3, // 旅行天数
    people: 2, // 出行人数
    budget: 5000, // 预算（元）
  },
})

/* ==================== 派生计算属性 ==================== */

/** 当前是否处于生成阶段 */
export const isGenerating = () => state.phase === 'generating'

/** 当前是否已完成 */
export const isCompleted = () => state.phase === 'completed'

/* ==================== 操作函数 ==================== */

/**
 * 重置所有状态到初始值
 * 在新一轮生成开始前调用
 */
function resetState() {
  state.phase = 'generating'
  state.progress = 0
  state.currentStep = '正在连接...'
  state.planData = null
  state.costBreakdown = null
  state.hotelList = []
  state.mapMarkers = []
  state.drawerState = 'mid'
  state.taskId = null

  // 重置步骤列表（含名称，防止跨轮次状态泄漏）
  const defaultSteps = [
    { name: '分析目的地', progress: 0, status: 'wait' },
    { name: '生成线路概览', progress: 0, status: 'wait' },
    { name: '规划每日行程', progress: 0, status: 'wait' },
    { name: '筛选酒店推荐', progress: 0, status: 'wait' },
    { name: '整理出行贴士', progress: 0, status: 'wait' },
    { name: '汇总费用明细', progress: 0, status: 'wait' },
    { name: '全部完成', progress: 0, status: 'wait' },
  ]
  state.stepList.splice(0, state.stepList.length, ...defaultSteps.map(s => ({...s})))
}

/**
 * 更新生成进度
 * @param {number} progress - 总进度 0-100
 * @param {string} currentStep - 当前步骤描述
 */
function updateProgress(progress, currentStep) {
  state.progress = Math.min(100, Math.max(0, progress))
  if (currentStep !== undefined) {
    state.currentStep = currentStep
  }
}

/**
 * 更新步骤列表状态
 * @param {Array} steps - 新的步骤数组 [{ name, progress, status }]
 */
function updateStepList(steps) {
  if (!Array.isArray(steps)) return
  // 逐个更新，保留已有步骤的引用（用于动画过渡）
  steps.forEach((step, i) => {
    if (state.stepList[i]) {
      state.stepList[i].progress = step.progress ?? 0
      state.stepList[i].status = step.status || 'wait'
    }
  })
}

/**
 * 设置行程数据（生成完成后调用）
 * @param {Object} planData - 行程计划数据
 * @param {Object} costBreakdown - 费用明细
 * @param {Array} hotelList - 酒店列表
 */
function setTripData(planData, costBreakdown, hotelList) {
  state.planData = planData || null
  state.costBreakdown = costBreakdown || null
  state.hotelList = hotelList || []
  state.phase = 'completed'
  state.progress = 100
}

/**
 * 设置地图标注点
 * @param {Array} markers - 标注点数组
 */
function setMapMarkers(markers) {
  state.mapMarkers = markers || []
}

/**
 * 设置任务ID
 * @param {string|number} taskId - 生成任务ID
 */
function setTaskId(taskId) {
  state.taskId = taskId
}

/**
 * 设置用户参数
 * @param {Object} params - { destination, days, people, budget }
 */
function setParams(params) {
  if (params) {
    Object.assign(state.params, params)
  }
}

/* ==================== 导出 ==================== */

/**
 * useTripStore — 获取行程共享状态的 composable
 *
 * @returns {{ state, resetState, updateProgress, updateStepList, setTripData, setMapMarkers, setTaskId, setParams, isGenerating, isCompleted }}
 */
export function useTripStore() {
  return {
    /** 响应式状态对象（直接访问 state.xxx 即可触发响应式更新） */
    state,
    /** 重置所有状态 */
    resetState,
    /** 更新生成进度 */
    updateProgress,
    /** 更新步骤列表 */
    updateStepList,
    /** 设置行程完成数据 */
    setTripData,
    /** 设置地图标注点 */
    setMapMarkers,
    /** 设置任务ID */
    setTaskId,
    /** 设置用户参数 */
    setParams,
    /** 是否生成中 */
    isGenerating,
    /** 是否已完成 */
    isCompleted,
  }
}

export default useTripStore
