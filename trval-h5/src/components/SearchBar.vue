<script setup>
/**
 * Edge 风格搜索栏
 * 单行结构：图标 + 城市名 | 功能标签（flex-wrap 自动换行）
 * Props: modelValue, placeholder, history[{text,url,tags}]
 * Events: update:modelValue, select
 *
 * 【穿透修复】祖先 .search-row 的 z-index:2 已移除，避免创建 stacking context 导致
 * position:fixed 遮罩/面板的 z-index 被限制在该上下文中。
 * 同时 style.css 的 entranceUp 动画不再残留 transform，防止 creating containing block。
 */
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  history: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'select'])

const inputRef = ref(null)
const panelRef = ref(null)
const panelElRef = ref(null)
const searchText = ref(props.modelValue)
const showPanel = ref(false)
const activeFilter = ref('history')

watch(() => props.modelValue, (v) => { searchText.value = v })

const openPanel = () => {
  showPanel.value = true
  nextTick(() => positionPanel())
}
const closePanel = () => { showPanel.value = false }

const positionPanel = () => {
  if (!inputRef.value || !panelElRef.value) return
  try {
    const r = inputRef.value.getBoundingClientRect()
    if (r.width === 0) return
    panelElRef.value.style.top = (r.bottom + 6) + 'px'
    panelElRef.value.style.left = r.left + 'px'
    panelElRef.value.style.width = r.width + 'px'
  } catch {}
}

const handleFocus = () => { openPanel(); nextTick(() => inputRef.value?.select?.()) }

const handleClickOutside = (e) => {
  if (!showPanel.value) return
  // 点击 .edge-search 内部任何地方（输入框/wrapper/面板/遮罩）→ 不关闭
  if (panelRef.value && panelRef.value.contains(e.target)) return
  closePanel()
}
const handleKeydown = (e) => { if (e.key === 'Escape') { closePanel(); inputRef.value?.blur() } }

const selectHistory = (item) => {
  searchText.value = item.text
  emit('update:modelValue', item.text)
  emit('select', item)
  closePanel()
  nextTick(() => inputRef.value?.blur())
}
const handleInput = (e) => { searchText.value = e.target.value; emit('update:modelValue', e.target.value) }
const setFilter = (key) => { activeFilter.value = key }

// BUGID L-COMP-4 修复：历史/收藏/标签三态按钮不再只切高亮，真正按 activeFilter 过滤 history 列表
const filteredHistory = computed(() => {
  if (activeFilter.value === 'favorites') {
    return props.history.filter(item => item && !!item.url) // 收藏：有 url 的条目
  }
  if (activeFilter.value === 'tabs') {
    return props.history.filter(item => item && item.tags && item.tags.length > 0) // 标签：带 tags 的条目
  }
  return props.history // history（默认）：全部
})

onMounted(() => {
  document.addEventListener('click', handleClickOutside, true)
  document.addEventListener('keydown', handleKeydown)
  window.addEventListener('scroll', positionPanel, true)
})
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside, true)
  document.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('scroll', positionPanel, true)
})
</script>

<template>
  <div class="edge-search" ref="panelRef">
    <!-- ══ 输入框 ══ -->
    <div class="edge-wrap" @click="inputRef?.focus()">
      <svg class="edge-ico" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="#7b42f5" stroke-width="1.5" stroke-linecap="round"><circle cx="8.5" cy="8.5" r="5.5"/><line x1="12.5" y1="12.5" x2="17.5" y2="17.5"/></svg>
      <input ref="inputRef" v-model="searchText" type="text" :placeholder="placeholder || t('components.searchPlaceholder')" class="edge-inp" @input="handleInput" @focus="handleFocus"/>
    </div>

    <!-- ══ 全屏遮罩 + 下拉面板（fixed定位） ══ -->
    <Transition name="em">
      <div v-if="showPanel" class="edge-mask" @click.stop="closePanel" @touchend.stop="closePanel" @touchmove.stop/>
    </Transition>
    <Transition name="ep">
      <div v-if="showPanel" class="edge-panel" ref="panelElRef" @click.stop @touchstart.stop>
        <button class="edge-x" @click="closePanel">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="#7b42f5" stroke-width="1.5" stroke-linecap="round"><line x1="4" y1="4" x2="12" y2="12"/><line x1="12" y1="4" x2="4" y2="12"/></svg>
        </button>
        <div class="edge-list">
          <!-- BUGID L-COMP-4 修复：按 activeFilter 过滤后的列表；无匹配时显示空态 -->
          <div v-if="filteredHistory.length === 0" class="edge-empty">{{ t('components.filterEmpty', undefined, '该分类暂无内容') }}</div>
          <div v-for="(item, idx) in filteredHistory" :key="idx" class="edge-row" @mousedown.prevent="selectHistory(item)">
            <svg class="edge-clock" viewBox="0 0 16 16" width="15" height="15" fill="none" stroke="#7b42f5" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="8" r="6.5"/><polyline points="8 4.5 8 8 11 10"/><path d="M1.5 4 A6.5 6.5 0 0 1 5.5 1.5"/><polyline points="2 2 1.5 4 4 4.5"/></svg>
            <span class="edge-city">{{ item.text }}</span>
            <div v-if="item.tags && item.tags.length" class="edge-tags">
              <span v-for="tag in item.tags" :key="tag" class="edge-tag">{{ tag }}</span>
            </div>
          </div>
        </div>
        <div class="edge-foot">
          <span class="edge-flbl">{{ t('components.filterSearch') }}</span>
          <button :class="['edge-fbtn',{on:activeFilter==='history'}]" @click="setFilter('history')">
            <svg viewBox="0 0 16 16" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="8" r="6.5"/><polyline points="8 4.5 8 8 11 10"/><path d="M1.5 4 A6.5 6.5 0 0 1 5.5 1.5"/><polyline points="2 2 1.5 4 4 4.5"/></svg>
            <span class="edge-ftxt">{{ t('components.history') }}</span>
          </button>
          <button :class="['edge-fbtn',{on:activeFilter==='favorites'}]" @click="setFilter('favorites')">
            <svg viewBox="0 0 16 16" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"><path d="M8 1.5l1.8 4.2 4.7.4-3.5 3 1 4.4L8 11.2 4 13.5l1-4.4-3.5-3 4.7-.4L8 1.5z"/></svg>
            <span class="edge-ftxt">{{ t('components.favorites') }}</span>
          </button>
          <button :class="['edge-fbtn',{on:activeFilter==='tabs'}]" @click="setFilter('tabs')">
            <svg viewBox="0 0 16 16" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"><rect x="1.5" y="2.5" width="13" height="11" rx="1.5"/><line x1="1.5" y1="5.5" x2="14.5" y2="5.5"/><line x1="4" y1="7.5" x2="12" y2="7.5" opacity=".4"/><line x1="4" y1="9.5" x2="9" y2="9.5" opacity=".4"/></svg>
            <span class="edge-ftxt">{{ t('components.tabs') }}</span>
          </button>
          <button class="edge-gear">
            <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="#7b42f5" stroke-width="1.4" stroke-linecap="round"><circle cx="8" cy="8" r="2.5"/><path d="M8 1.5v1.5M8 13v1.5M2.5 3l1.2 1M12.3 12l1.2 1M1.5 8h1.5M13 8h1.5M2.5 13l1.2-1M12.3 4l1.2-1"/></svg>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* ================================================================
   品牌主色: #7b42f5  圆角: 输入20px / 面板10px / 标签6px
   结构: 图标+城市(左) | 标签(右,flex-wrap)
   ================================================================ */

.edge-search { position:relative; width:100%; font-family:system-ui,-apple-system,"Segoe UI",Roboto,"Microsoft YaHei",sans-serif; }

/* ── 输入框 ── */
.edge-wrap {
  display:flex; align-items:center; gap:8px;
  background:#f1f5f9; border:none; border-radius:20px;
  padding:10px 14px; cursor:text; transition:background .15s,box-shadow .15s;
}
.edge-wrap:focus-within { background:#fff; box-shadow:0 0 0 2px rgba(123,66,245,.10); }
.edge-ico { flex-shrink:0; opacity:.5; }
.edge-inp {
  flex:1; min-width:0; border:none; outline:none;
  font-size:14px; color:#1e293b; background:transparent;
}
.edge-inp::placeholder { color:#94a3b8; }

/* ── 全屏遮罩：fixed定位覆盖整个视口，拦截背后所有交互 ── */
.edge-mask {
  position:fixed; inset:0; z-index:99999;
  background:rgba(0,0,0,0.001); /* 近乎透明但确保移动端捕获触摸事件 */
  pointer-events:auto;
  touch-action:none;
}
.em-enter-active,.em-leave-active { transition:opacity .15s; }
.em-enter-from,.em-leave-to { opacity:0; }

/* ── 下拉面板 ── */
.edge-panel {
  position:fixed; top:0; left:0; width:100%;
  background:#fff; border:1px solid #d1d5db; border-radius:10px;
  box-shadow:0 4px 24px rgba(0,0,0,.15); z-index:100000;
  overflow:hidden; box-sizing:border-box;
  pointer-events:auto;
}
.ep-enter-active { transition:opacity .12s,transform .12s; }
.ep-leave-active { transition:opacity .08s,transform .08s; }
.ep-enter-from,.ep-leave-to { opacity:0; transform:translateY(-4px); }

/* ── 关闭按钮 ── */
.edge-x {
  position:absolute; top:10px; right:10px; z-index:2;
  display:flex; align-items:center; justify-content:center;
  width:26px; height:26px; border:none; border-radius:6px; background:transparent;
  cursor:pointer; transition:background .12s;
}
.edge-x:hover,.edge-x:active { background:#f3f4f6; }

/* ── 历史列表 ── */
.edge-list { max-height:50vh; overflow-y:auto; overflow-x:hidden; padding:6px 0 2px; -webkit-overflow-scrolling:touch; }

/* BUGID L-COMP-4 修复：筛选空态文案样式 */
.edge-empty { padding: 24px 12px; text-align: center; font-size: 13px; color: #9ca3af; }

.edge-row {
  display:flex; align-items:center; gap:10px; flex-wrap:wrap;
  padding:11px 12px; cursor:pointer; user-select:none;
  transition:background .10s; -webkit-tap-highlight-color:transparent;
}
.edge-row:hover,.edge-row:active { background:#f3f4f6; }

.edge-clock { flex-shrink:0; opacity:.55; }
.edge-city {
  font-size:14px; color:#1f2937; font-weight:500; line-height:1.3;
  white-space:nowrap; flex-shrink:1; min-width:0; overflow:hidden; text-overflow:ellipsis;
}

.edge-tags { display:flex; flex-wrap:wrap; gap:4px; margin-left:auto; flex-shrink:0; }
.edge-tag {
  font-size:11px; color:#6b7280; background:#f3f4f6; border:1px solid #e5e7eb;
  border-radius:6px; padding:2px 8px; white-space:nowrap; line-height:1.4;
  cursor:pointer; transition:all .12s;
}
.edge-tag:hover,.edge-tag:active { background:rgba(123,66,245,.08); color:#7b42f5; border-color:rgba(123,66,245,.2); }

/* ── 底部筛选栏 ── */
.edge-foot {
  display:flex; align-items:center; gap:2px;
  padding:8px 12px; min-height:38px;
  border-top:1px solid #e5e7eb; background:#f9fafb;
}
.edge-flbl { font-size:11px; color:#9ca3af; margin-right:4px; flex-shrink:0; white-space:nowrap; }
.edge-fbtn {
  display:inline-flex; align-items:center; gap:4px;
  padding:4px 8px; border:none; border-radius:6px;
  background:transparent; color:#4b5563;
  font-size:11px; font-family:inherit; cursor:pointer;
  transition:all .12s; white-space:nowrap; flex-shrink:1; min-width:0;
}
.edge-fbtn:hover,.edge-fbtn:active { background:rgba(123,66,245,.06); color:#7b42f5; }
.edge-fbtn.on { background:rgba(123,66,245,.08); color:#7b42f5; font-weight:600; }
.edge-ftxt { }
.edge-gear {
  display:flex; align-items:center; justify-content:center;
  width:28px; height:28px; min-width:28px; border:none; border-radius:6px;
  background:transparent; cursor:pointer; margin-left:auto;
  transition:background .12s;
}
.edge-gear:hover,.edge-gear:active { background:rgba(123,66,245,.06); }

@media screen and (max-width:360px) {
  .edge-row { padding:10px 10px; gap:8px; }
  .edge-city { font-size:13px; }
  .edge-tag { font-size:10px; padding:2px 6px; }
  .edge-foot { padding:7px 10px; }
  .edge-fbtn { padding:3px 5px; gap:3px; }
  .edge-ftxt { display:none; }
}

@media screen and (min-width:768px) {
  .edge-row { padding:12px 16px; }
  .edge-city { font-size:15px; }
  .edge-foot { padding:9px 14px; }
  .edge-fbtn { padding:5px 10px; gap:5px; }
  .edge-wrap { padding:12px 16px; }
}
</style>
