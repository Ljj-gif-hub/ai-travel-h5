<script setup>
/**
 * Edge 风格搜索栏 — 复刻微软 Edge 地址栏下拉 UI
 * 移动端适配：360px~414px 无横向溢出
 *
 * Props:
 *   modelValue  — v-model 绑定的搜索文本
 *   placeholder — 占位提示文字
 *   history     — 历史记录 [{text, url, tags?: ['攻略','机票',...]}]
 *
 * Events:
 *   update:modelValue — 文本变化
 *   select            — 选中条目，payload: {text, url}
 */
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '搜索目的地、景点或网址' },
  history: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'select'])

const inputRef = ref(null)
const panelRef = ref(null)
const searchText = ref(props.modelValue)
const showPanel = ref(false)
const activeFilter = ref('history')

watch(() => props.modelValue, (v) => { searchText.value = v })

const openPanel = () => { showPanel.value = true }
const closePanel = () => { showPanel.value = false }

const handleFocus = () => {
  openPanel()
  nextTick(() => inputRef.value?.select?.())
}

const handleClickOutside = (e) => {
  if (panelRef.value && !panelRef.value.contains(e.target)) {
    if (inputRef.value && inputRef.value.contains(e.target)) return
    closePanel()
  }
}

const handleKeydown = (e) => {
  if (e.key === 'Escape') { closePanel(); inputRef.value?.blur() }
}

const selectHistory = (item) => {
  searchText.value = item.text
  emit('update:modelValue', item.text)
  emit('select', item)
  closePanel()
  nextTick(() => inputRef.value?.blur())
}

const handleInput = (e) => {
  const v = e.target.value
  searchText.value = v
  emit('update:modelValue', v)
}

const setFilter = (key) => { activeFilter.value = key }

onMounted(() => {
  document.addEventListener('click', handleClickOutside, true)
  document.addEventListener('keydown', handleKeydown)
})
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside, true)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="edge-search" ref="panelRef">
    <!-- ══════ 输入框 ══════ -->
    <div class="edge-search-input-wrap" @click="inputRef?.focus()">
      <svg class="edge-search-icon" viewBox="0 0 20 20" width="18" height="18" fill="none">
        <circle cx="8.5" cy="8.5" r="5.5" stroke="#6B7280" stroke-width="1.6"/>
        <line x1="12.5" y1="12.5" x2="17" y2="17" stroke="#6B7280" stroke-width="1.6" stroke-linecap="round"/>
      </svg>
      <input ref="inputRef" v-model="searchText" type="text" :placeholder="placeholder"
        class="edge-search-input" @input="handleInput" @focus="handleFocus" />
    </div>

    <!-- ══════ 下拉面板 ══════ -->
    <Transition name="edge-panel">
      <div v-if="showPanel" class="edge-panel">
        <!-- ──── 历史列表 ──── -->
        <div class="edge-panel-list">
          <div v-for="(item, idx) in history" :key="idx"
            class="edge-history-item" @mousedown.prevent="selectHistory(item)">
            <svg class="edge-history-icon" viewBox="0 0 16 16" width="16" height="16" fill="none">
              <circle cx="8" cy="8" r="6.5" stroke="#6B7280" stroke-width="1.2"/>
              <polyline points="8 4.5 8 8 10.5 9.5" stroke="#6B7280" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2.5 5.5 A6.5 6.5 0 0 1 5 2.8" stroke="#6B7280" stroke-width="1.2" stroke-linecap="round"/>
              <polyline points="3 2.5 2.5 5.5 5.5 5" stroke="#6B7280" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <div class="edge-history-body">
              <span class="edge-history-text">{{ item.text }}</span>
              <!-- 标签按钮：空间充足横向排列，不够自动换行 -->
              <div v-if="item.tags && item.tags.length" class="edge-history-tags">
                <span v-for="tag in item.tags" :key="tag" class="edge-history-tag">{{ tag }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- ══════ 底部筛选栏 ══════ -->
        <div class="edge-panel-footer">
          <span class="edge-footer-label">筛选搜索:</span>
          <div class="edge-footer-btns">
            <button :class="['edge-filter-btn', { active: activeFilter === 'history' }]" @click="setFilter('history')">
              <svg viewBox="0 0 16 16" width="13" height="13" fill="none">
                <circle cx="8" cy="8" r="6.5" stroke="currentColor" stroke-width="1.2"/>
                <polyline points="8 4.5 8 8 10.5 9.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M2.5 5.5 A6.5 6.5 0 0 1 5 2.8" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <polyline points="3 2.5 2.5 5.5 5.5 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <span class="edge-filter-text">历史记录</span>
            </button>
            <button :class="['edge-filter-btn', { active: activeFilter === 'favorites' }]" @click="setFilter('favorites')">
              <svg viewBox="0 0 16 16" width="13" height="13" fill="none">
                <path d="M8 1.5l1.8 4.2 4.7.4-3.5 3 1 4.4L8 11.2 4 13.5l1-4.4-3.5-3 4.7-.4L8 1.5z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
              </svg>
              <span class="edge-filter-text">收藏夹</span>
            </button>
            <button :class="['edge-filter-btn', { active: activeFilter === 'tabs' }]" @click="setFilter('tabs')">
              <svg viewBox="0 0 16 16" width="13" height="13" fill="none">
                <rect x="1.5" y="2.5" width="13" height="11" rx="1.5" stroke="currentColor" stroke-width="1.2"/>
                <line x1="1.5" y1="5.5" x2="14.5" y2="5.5" stroke="currentColor" stroke-width="1.2"/>
                <rect x="3" y="7" width="10" height="1" rx="0.5" fill="currentColor" opacity="0.3"/>
                <rect x="3" y="9" width="7" height="1" rx="0.5" fill="currentColor" opacity="0.3"/>
              </svg>
              <span class="edge-filter-text">标签页</span>
            </button>
          </div>
          <button class="edge-settings-btn" title="设置">
            <svg viewBox="0 0 16 16" width="15" height="15" fill="none">
              <circle cx="8" cy="8" r="2.8" stroke="#6B7280" stroke-width="1.2"/>
              <path d="M8 1.5v1.8M8 12.7v1.8M2.4 3.7l1.4 1M12.2 11.3l1.4 1M1.5 8h1.8M12.7 8h1.8M2.4 12.3l1.4-1M12.2 4.7l1.4-1" stroke="#6B7280" stroke-width="1.2" stroke-linecap="round"/>
            </svg>
          </button>
        </div>

        <!-- ──── 关闭按钮 ──── -->
        <button class="edge-panel-close" @click="closePanel" title="关闭">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none">
            <line x1="4" y1="4" x2="12" y2="12" stroke="#6B7280" stroke-width="1.5" stroke-linecap="round"/>
            <line x1="12" y1="4" x2="4" y2="12" stroke="#6B7280" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* ================================================================
   Edge 搜索栏 — CSS
   移动端优先：360px ~ 414px 无横向溢出，flex 自适应
   ================================================================ */

/* ──── 容器 ──── */
.edge-search {
  position: relative;
  width: 100%;
  font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Microsoft YaHei", sans-serif;
}

/* ──── 输入框 ──── */
.edge-search-input-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f1f5f9;
  border: none;
  border-radius: 20px;
  padding: 10px 14px;
  cursor: text;
  transition: background 0.15s;
}
.edge-search-input-wrap:focus-within {
  background: #fff;
  box-shadow: 0 0 0 2px rgba(139,92,246,0.10);
}
.edge-search-icon {
  flex-shrink: 0;
  opacity: 0.45;
  width: 15px;
  height: 15px;
}
.edge-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  font-size: 14px;
  color: #1e293b;
  background: transparent;
}
.edge-search-input::placeholder {
  color: #94a3b8;
}

/* ──── 下拉面板 ──── */
.edge-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.10), 0 2px 6px rgba(0,0,0,0.04);
  z-index: 9999;
  overflow-x: hidden;
  overflow-y: visible;
  box-sizing: border-box;
}

/* 面板动画 */
.edge-panel-enter-active { transition: opacity 0.12s ease, transform 0.12s ease; }
.edge-panel-leave-active { transition: opacity 0.08s ease, transform 0.08s ease; }
.edge-panel-enter-from,
.edge-panel-leave-to { opacity: 0; transform: translateY(-4px); }

/* ──── 历史列表 ──── */
.edge-panel-list {
  max-height: 50vh;
  overflow-y: auto;
  padding: 4px 0;
  -webkit-overflow-scrolling: touch;
}

/* 每条历史 */
.edge-history-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: background-color 0.10s;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}
.edge-history-item:hover,
.edge-history-item:active {
  background-color: #f3f4f6;
}

.edge-history-icon {
  flex-shrink: 0;
  margin-top: 1px;
}

.edge-history-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.edge-history-text {
  font-size: 0.8125rem;
  color: #1f2937;
  line-height: 1.4;
  word-break: break-word;
  overflow-wrap: break-word;
}

/* 标签按钮行 — flex-wrap 自动换行 */
.edge-history-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.edge-history-tag {
  display: inline-block;
  font-size: 0.6875rem;
  color: #6b7280;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 2px 7px;
  white-space: nowrap;
  line-height: 1.3;
}

/* ──── 底部筛选栏 ──── */
.edge-panel-footer {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 7px 10px;
  border-top: 1px solid #e5e7eb;
  background: #fafafa;
  flex-wrap: nowrap;
  min-height: 36px;
}

.edge-footer-label {
  font-size: 0.6875rem;
  color: #9ca3af;
  margin-right: 4px;
  flex-shrink: 0;
  white-space: nowrap;
}

/* 按钮组 — 自动压缩间距 */
.edge-footer-btns {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.edge-filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 4px 7px;
  border: none;
  border-radius: 5px;
  background: transparent;
  color: #4b5563;
  font-size: 0.6875rem;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.10s;
  white-space: nowrap;
  flex-shrink: 1;
  min-width: 0;
}
.edge-filter-btn:hover,
.edge-filter-btn:active {
  background-color: #e5e7eb;
}
.edge-filter-btn.active {
  background-color: #e5e7eb;
  color: #1f2937;
}

.edge-filter-text {
  /* 超小屏隐藏文字，只显示图标 */
}

.edge-settings-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  min-width: 28px;
  border: none;
  border-radius: 5px;
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
  margin-left: auto;
  transition: background-color 0.10s;
}
.edge-settings-btn:hover,
.edge-settings-btn:active {
  background-color: #e5e7eb;
}

/* ──── 关闭按钮 ──── */
.edge-panel-close {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 5px;
  background: transparent;
  cursor: pointer;
  transition: background-color 0.10s;
  z-index: 2;
  flex-shrink: 0;
}
.edge-panel-close:hover,
.edge-panel-close:active {
  background-color: #f3f4f6;
}

/* ================================================================
   响应式 — 超小屏 (< 360px) 进一步压缩
   ================================================================ */
@media screen and (max-width: 360px) {
  .edge-history-item {
    padding: 9px 10px;
    gap: 8px;
  }
  .edge-history-text {
    font-size: 0.75rem;
  }
  .edge-history-tag {
    font-size: 0.625rem;
    padding: 1px 5px;
  }
  .edge-panel-footer {
    padding: 6px 8px;
  }
  .edge-filter-btn {
    padding: 3px 5px;
    gap: 2px;
  }
  /* 超小屏隐藏筛选按钮文字，仅显示图标 */
  .edge-filter-text {
    display: none;
  }
  .edge-footer-label {
    font-size: 0.625rem;
  }
}

/* ================================================================
   平板/桌面 (> 768px) 恢复宽松间距
   ================================================================ */
@media screen and (min-width: 768px) {
  .edge-history-item {
    padding: 10px 16px;
  }
  .edge-history-text {
    font-size: 0.875rem;
  }
  .edge-panel-footer {
    padding: 8px 14px;
  }
  .edge-filter-btn {
    padding: 5px 10px;
    gap: 5px;
  }
  .edge-search-input-wrap {
    padding: 12px 16px;
  }
}
</style>
