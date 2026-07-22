<script setup>
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { showToast } from 'vant'
import { mapApi } from '../api'

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  placeholder: {
    type: String,
    default: '搜索目的地、景点',
  },
})

const emit = defineEmits(['update:modelValue', 'select'])

const searchInput = ref(null)
const searchKeyword = ref(props.modelValue)
const suggestions = ref([])
const showSuggestions = ref(false)
const isLoading = ref(false)
let debounceTimer = null

watch(() => props.modelValue, (newVal) => {
  searchKeyword.value = newVal
})

const fetchSuggestions = async (keyword) => {
  if (!keyword || keyword.trim().length < 1) {
    suggestions.value = []
    showSuggestions.value = false
    return
  }

  isLoading.value = true
  try {
    const response = await mapApi.getSuggestion(keyword.trim())

    if (response.code === 0 && response.data) {
      suggestions.value = response.data
      showSuggestions.value = suggestions.value.length > 0
    } else {
      suggestions.value = []
      showSuggestions.value = false
    }
  } catch (error) {
    console.error('获取地点联想失败:', error)
    suggestions.value = []
    showSuggestions.value = false
  } finally {
    isLoading.value = false
  }
}

const handleInput = (e) => {
  const value = e.target.value
  searchKeyword.value = value
  emit('update:modelValue', value)

  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }

  debounceTimer = setTimeout(() => {
    fetchSuggestions(value)
  }, 300)
}

const selectSuggestion = (item) => {
  searchKeyword.value = item.name
  emit('update:modelValue', item.name)
  emit('select', item)
  showSuggestions.value = false
  nextTick(() => {
    searchInput.value?.blur()
  })
}

const handleFocus = () => {
  if (searchKeyword.value && suggestions.value.length > 0) {
    showSuggestions.value = true
  }
}

const handleBlur = () => {
  setTimeout(() => {
    showSuggestions.value = false
  }, 200)
}

const handleClickOutside = (e) => {
  const container = document.querySelector('.search-bar-container')
  if (container && !container.contains(e.target)) {
    showSuggestions.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
})
</script>

<template>
  <div class="search-bar-container">
    <div class="search-bar" @click="searchInput?.focus()">
      <van-icon name="search" size="18" color="#a0aec0" />
      <input
        ref="searchInput"
        v-model="searchKeyword"
        type="text"
        :placeholder="placeholder"
        class="search-input"
        @input="handleInput"
        @focus="handleFocus"
        @blur="handleBlur"
      />
      <van-icon name="map" size="18" color="#a0aec0" />
    </div>

    <div v-if="showSuggestions" class="suggestions-panel">
      <div class="suggestions-header">
        <span class="header-text">搜索结果</span>
        <van-icon name="x" size="16" color="#999" @click.stop="showSuggestions = false" />
      </div>
      <div class="suggestions-list">
        <div
          v-for="(item, index) in suggestions"
          :key="item.uid || index"
          class="suggestion-item"
          @click.stop="selectSuggestion(item)"
        >
          <van-icon name="location-o" size="16" color="#8b5cf6" />
          <div class="suggestion-content">
            <div class="suggestion-name">{{ item.name }}</div>
            <div class="suggestion-address">{{ item.address }}</div>
          </div>
          <van-icon name="arrow" size="14" color="#cbd5e1" />
        </div>
        <div v-if="!isLoading && suggestions.length === 0" class="empty-tip">
          <van-icon name="search" size="24" color="#c0c4cc" />
          <span>未找到相关地点</span>
        </div>
      </div>
      <div v-if="isLoading" class="loading-tip">
        <van-loading size="14px" />
        <span>搜索中...</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-bar-container {
  position: relative;
  width: 100%;
}

.search-bar {
  display: flex;
  align-items: center;
  background-color: #ffffff;
  border-radius: 24px;
  padding: 14px 20px;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.08);
  border: 1px solid rgba(139, 92, 246, 0.08);
  transition: box-shadow 0.3s ease;
}

.search-bar:focus-within {
  box-shadow: 0 4px 20px rgba(139, 92, 246, 0.15);
  border-color: rgba(139, 92, 246, 0.2);
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  color: #333;
  background: transparent;
  margin: 0 12px;
}

.search-input::placeholder {
  color: #a0aec0;
}

.suggestions-panel {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  right: 0;
  background-color: #ffffff;
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(139, 92, 246, 0.12);
  z-index: 9999;
  overflow: hidden;
  animation: panelFadeIn 0.2s ease-out;
}

@keyframes panelFadeIn {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.suggestions-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #f5f5f5;
  background: linear-gradient(135deg, rgba(233, 213, 255, 0.1) 0%, rgba(240, 249, 255, 0.1) 100%);
}

.header-text {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.suggestions-list {
  max-height: 300px;
  overflow-y: auto;
}

.suggestion-item {
  display: flex;
  align-items: center;
  padding: 15px 18px;
  transition: background-color 0.2s ease;
  cursor: pointer;
}

.suggestion-item:active {
  background-color: #f7f5ff;
}

.suggestion-content {
  flex: 1;
  margin-left: 12px;
}

.suggestion-name {
  font-size: 15px;
  color: #333;
  font-weight: 500;
}

.suggestion-address {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #999;
}

.empty-tip span {
  margin-top: 10px;
  font-size: 14px;
}

.loading-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  font-size: 13px;
  color: #999;
}
</style>