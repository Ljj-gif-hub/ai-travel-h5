<script setup>
import { ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showLoadingToast } from 'vant'
import { areaList } from '@vant/area-data'

const router = useRouter()

const destination = ref('')
const budget = ref('')
const days = ref('')
const showCityPicker = ref(false)
const cityAreaRef = ref(null)
const wheelHandlers = ref([])

const quickEntries = [
  { name: 'AI对话', icon: 'message-circle-o', color: '#667eea', path: '/chat' },
  { name: '机票查询', icon: 'plane', color: '#52c41a', path: '/' },
  { name: '酒店预订', icon: 'hotel', color: '#faad14', path: '/' },
  { name: '攻略推荐', icon: 'book-o', color: '#1890ff', path: '/' },
  { name: '景点门票', icon: 'ticket', color: '#eb2f96', path: '/' },
  { name: '当地美食', icon: 'food', color: '#fa541c', path: '/' },
]

const hotDestinations = [
  { name: '北京', image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Beijing%20Forbidden%20City%20landmark%20travel%20photography&image_size=landscape_4_3' },
  { name: '上海', image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Shanghai%20skyline%20bund%20night%20view%20travel%20photography&image_size=landscape_4_3' },
  { name: '广州', image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Guangzhou%20city%20skyline%20canton%20tower%20travel&image_size=landscape_4_3' },
  { name: '深圳', image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Shenzhen%20futian%20CBD%20skyline%20modern%20city&image_size=landscape_4_3' },
  { name: '成都', image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Chengdu%20panda%20city%20traditional%20architecture%20travel&image_size=landscape_4_3' },
  { name: '杭州', image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Hangzhou%20West%20Lake%20scenic%20travel%20photography&image_size=landscape_4_3' },
  { name: '西安', image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Xi%27an%20Terracotta%20Army%20ancient%20China%20travel&image_size=landscape_4_3' },
  { name: '重庆', image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Chongqing%20city%20mountain%20night%20view%20travel&image_size=landscape_4_3' },
]

const startPlanning = () => {
  if (!destination.value || String(destination.value).trim() === '') {
    showToast({
      message: '请输入目的地',
      position: 'middle',
      style: {
        background: 'rgba(0, 0, 0, 0.85)',
        color: '#ffffff',
        fontSize: '16px',
        borderRadius: '12px',
        padding: '20px 30px',
      },
    })
    return
  }
  if (!budget.value || String(budget.value).trim() === '') {
    showToast({
      message: '请输入预算',
      position: 'middle',
      style: {
        background: 'rgba(0, 0, 0, 0.85)',
        color: '#ffffff',
        fontSize: '16px',
        borderRadius: '12px',
        padding: '20px 30px',
      },
    })
    return
  }
  if (!days.value || String(days.value).trim() === '') {
    showToast({
      message: '请输入天数',
      position: 'middle',
      style: {
        background: 'rgba(0, 0, 0, 0.85)',
        color: '#ffffff',
        fontSize: '16px',
        borderRadius: '12px',
        padding: '20px 30px',
      },
    })
    return
  }
  if (Number(days.value) < 1) {
    showToast({
      message: '天数不能低于1天',
      position: 'middle',
      style: {
        background: 'rgba(0, 0, 0, 0.85)',
        color: '#ffffff',
        fontSize: '16px',
        borderRadius: '12px',
        padding: '20px 30px',
      },
    })
    return
  }
  if (Number(budget.value) < 100) {
    showToast({
      message: '预算不能低于100元',
      position: 'middle',
      style: {
        background: 'rgba(0, 0, 0, 0.85)',
        color: '#ffffff',
        fontSize: '16px',
        borderRadius: '12px',
        padding: '20px 30px',
      },
    })
    return
  }
  showLoadingToast({
    message: '加载中...',
    duration: 500,
    position: 'middle',
    forbidClick: true,
    loadingType: 'spinner',
    style: {
      background: 'rgba(0, 0, 0, 0.85)',
      color: '#ffffff',
      fontSize: '16px',
      borderRadius: '12px',
      padding: '30px 40px',
    },
  })
  setTimeout(() => {
    router.push({
      path: '/planning',
      query: {
        destination: destination.value,
        budget: budget.value,
        days: days.value,
      },
    })
  }, 500)
}

const handleQuickEntry = (entry) => {
  if (entry.path) {
    router.push(entry.path)
  }
}

const handleDestination = (dest) => {
  destination.value = dest.name
}

const onCityConfirm = (value) => {
  if (value && value.selectedOptions) {
    destination.value = value.selectedOptions[1]?.text || value.selectedOptions[0]?.text || ''
  }
  showCityPicker.value = false
}

const openCityPicker = () => {
  showCityPicker.value = true
}

const wheelGesture = new WeakMap()

const dispatchTouch = (el, type, x, y) => {
  const touch = new Touch({ identifier: 0, target: el, clientX: x, clientY: y })
  el.dispatchEvent(new TouchEvent(type, {
    cancelable: true,
    bubbles: true,
    touches: type === 'touchend' ? [] : [touch],
    targetTouches: type === 'touchend' ? [] : [touch],
    changedTouches: [touch],
  }))
}

const handlePickerWheel = (e) => {
  e.preventDefault()
  e.stopPropagation()
  if (!(window.TouchEvent && typeof Touch === 'function')) return

  const picker = document.querySelector('.van-popup .van-picker')
  if (!picker) return
  const columns = picker.querySelectorAll('.van-picker-column')
  if (columns.length === 0) return

  const col = Array.from(columns).find(c => {
    const r = c.getBoundingClientRect()
    return e.clientX >= r.left && e.clientX <= r.right
  }) || columns[0]
  if (!col) return

  const r = col.getBoundingClientRect()
  const cx = r.left + r.width / 2
  const cy = r.top + r.height / 2
  const itemHeight = 44

  let st = wheelGesture.get(col)
  if (!st) {
    dispatchTouch(col, 'touchstart', cx, cy)
    st = { targetY: 0, currentY: 0, timer: null, rafId: 0, animating: false }
    wheelGesture.set(col, st)
  }

  st.targetY -= Math.sign(e.deltaY) * itemHeight

  if (!st.animating) {
    st.animating = true
    const animate = () => {
      const diff = st.targetY - st.currentY
      if (Math.abs(diff) > 0.5) {
        st.currentY += diff * 0.35
        dispatchTouch(col, 'touchmove', cx, cy + st.currentY)
        st.rafId = requestAnimationFrame(animate)
      } else {
        st.currentY = st.targetY
        dispatchTouch(col, 'touchmove', cx, cy + st.currentY)
        st.animating = false
      }
    }
    st.rafId = requestAnimationFrame(animate)
  }

  clearTimeout(st.timer)
  st.timer = setTimeout(() => {
    if (st.rafId) cancelAnimationFrame(st.rafId)
    st.currentY = st.targetY
    dispatchTouch(col, 'touchmove', cx, cy + st.currentY)
    dispatchTouch(col, 'touchend', cx, cy + st.currentY)
    wheelGesture.delete(col)
  }, 320)
}

const addWheelListeners = () => {
  setTimeout(() => {
    const popup = document.querySelector('.van-popup')
    if (popup) {
      const picker = popup.querySelector('.van-picker')
      if (picker) {
        const handler = (e) => handlePickerWheel(e)
        popup.addEventListener('wheel', handler, { passive: false })
        wheelHandlers.value.push({ column: popup, handler })
      }
    }
  }, 500)
}

const removeWheelListeners = () => {
  wheelHandlers.value.forEach(({ column, handler }) => {
    column.removeEventListener('wheel', handler)
  })
  wheelHandlers.value = []
}

watch(showCityPicker, (newVal) => {
  if (newVal) {
    addWheelListeners()
  } else {
    removeWheelListeners()
  }
})
</script>

<template>
  <div class="home-container">
    <van-nav-bar title="智能旅游助手" safe-area-inset-top class="home-nav" />
    <div class="header">
      <div class="header-top">
        <van-icon name="search" size="20" color="#999" />
        <input type="text" placeholder="搜索目的地、景点" class="search-input" />
        <van-icon name="map" size="20" color="#999" />
      </div>
    </div>

    <div class="planning-section">
      <div class="section-header">
        <van-icon name="compass" size="18" color="#667eea" />
        <span class="section-title">规划你的旅程</span>
      </div>
      <van-cell-group inset class="planning-group">
        <van-cell title="目的地">
          <template #extra>
            <div class="destination-selector" @click="openCityPicker">
              <input
                v-model="destination"
                type="text"
                placeholder="请输入目的地"
                class="planning-input"
                readonly
              />
              <van-icon name="arrow" size="16" color="#999" />
            </div>
          </template>
        </van-cell>
        <van-cell title="预算（元）">
          <template #extra>
            <input
              v-model="budget"
              type="number"
              placeholder="请输入预算"
              class="planning-input"
            />
          </template>
        </van-cell>
        <van-cell title="天数">
          <template #extra>
            <input
              v-model="days"
              type="number"
              placeholder="请输入天数"
              class="planning-input"
            />
          </template>
        </van-cell>
      </van-cell-group>
      <van-button type="primary" block class="planning-btn" @click="startPlanning">
        开始规划
      </van-button>
    </div>

    <div class="quick-section">
      <div class="section-header">
        <van-icon name="apps-o" size="18" color="#667eea" />
        <span class="section-title">快捷入口</span>
      </div>
      <van-grid column-num="4" :border="false" class="quick-grid">
        <van-grid-item
          v-for="(item, index) in quickEntries"
          :key="index"
          :icon="item.icon"
          :text="item.name"
          @click="handleQuickEntry(item)"
          class="quick-grid-item"
        >
          <template #icon>
            <div :style="{ background: `${item.color}20`, borderRadius: '12px', padding: '12px' }" class="quick-icon-wrapper">
              <van-icon :name="item.icon" :color="item.color" size="24" />
            </div>
          </template>
        </van-grid-item>
      </van-grid>
    </div>

    <div class="hot-section">
      <div class="section-header">
        <van-icon name="star-o" size="18" color="#667eea" />
        <span class="section-title">热门目的地</span>
        <van-icon name="arrow" size="14" color="#999" />
      </div>
      <div class="hot-scroll">
        <span
          v-for="(dest, index) in hotDestinations"
          :key="index"
          class="hot-tag"
          @click="handleDestination(dest)"
        >
          {{ dest.name }}
        </span>
      </div>
    </div>

    <div class="bottom-spacer"></div>

    <van-popup v-model:show="showCityPicker" position="bottom" round safe-area-inset-bottom>
      <van-area
        ref="cityAreaRef"
        title="选择城市"
        :columns-num="2"
        :area-list="areaList"
        @confirm="onCityConfirm"
        @cancel="showCityPicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped>
.home-container {
  min-height: 100vh;
  background: #f7f8fa;
}

.header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 16px 20px;
}

.header-top {
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 24px;
  padding: 10px 16px;
}

.search-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: #fff;
  font-size: 14px;
}

.search-input::placeholder {
  color: rgba(255, 255, 255, 0.7);
}

.planning-section {
  margin: 20px;
  background: #ffffff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
  border: 1px solid #d0d0d0;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.planning-group :deep(.van-cell) {
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}

.planning-group :deep(.van-cell:last-child) {
  border-bottom: none;
}

.planning-group :deep(.van-cell__title) {
  font-size: 14px;
  color: #666;
}

.planning-input {
  text-align: right;
  border: none;
  outline: none;
  font-size: 14px;
  color: #333;
  width: 100px;
  background: transparent;
}

.planning-input::placeholder {
  color: #ccc;
}

.destination-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}

.planning-btn {
  margin-top: 16px;
  border-radius: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  border: none !important;
}

.quick-section {
  margin: 20px;
  background: #ffffff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
  border: 1px solid #d0d0d0;
}

.quick-grid :deep(.van-grid-item) {
  padding: 8px 0;
}

.quick-grid :deep(.van-grid-item__text) {
  font-size: 12px;
  color: #666;
  margin-top: 8px;
}

.quick-grid-item :deep(.van-grid-item__icon) {
  color: #999;
}

.quick-icon-wrapper {
  cursor: pointer;
  transition: transform 0.2s ease;
}

.quick-icon-wrapper:hover {
  transform: scale(1.1);
}

.quick-icon-wrapper:active {
  transform: scale(0.95);
}

.hot-section {
  margin: 20px;
  background: #ffffff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
  border: 1px solid #d0d0d0;
}

.hot-section .section-header {
  justify-content: space-between;
}

.hot-scroll {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hot-tag {
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  color: #666;
  border: 1px solid #e0e0e0;
  cursor: pointer;
  transition: transform 0.2s ease, background 0.2s ease;
}

.hot-tag:hover {
  transform: scale(1.05);
  background: linear-gradient(135deg, #e4e8ec 0%, #d0d4dc 100%);
}

.hot-tag:active {
  transform: scale(0.95);
}

.bottom-spacer {
  height: 80px;
}

:deep(.van-nav-bar) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

:deep(.van-nav-bar__title) {
  color: #fff;
}

:deep(.van-picker-column) {
  touch-action: pan-y;
  overflow-y: auto;
}
</style>
