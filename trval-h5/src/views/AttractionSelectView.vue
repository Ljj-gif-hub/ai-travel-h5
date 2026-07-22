<script setup>
/**
 * AttractionSelectView.vue — 目的地选择器（境内/境外双Tab）
 * 按地区（省份/大洲） → 城市分层展示
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  municipalities, hkMacauTW, allProvinces, domesticGroups,
  overseasContinents, domesticHotCities, overseasHotCities,
  getAllCityNames,
} from '../data/cityData.js'

defineOptions({ name: 'AttractionSelectView' })
const router = useRouter()

// ====== Tab ======
const activeTab = ref('domestic')

// ====== 状态 ======
const domesticLeftKey = ref('recommend')
const domesticSubKey = ref(null)
const overseasLeftKey = ref('recommend')
const overseasSubKey = ref(null)
const searchText = ref('')
const historyCities = ref([])

onMounted(() => {
  try { const raw = localStorage.getItem('attraction_select_history'); if (raw) historyCities.value = JSON.parse(raw) } catch {}
  loadImageMap()
})

const saveHistory = (city) => {
  historyCities.value = [city, ...historyCities.value.filter(c => c !== city)].slice(0, 8)
  localStorage.setItem('attraction_select_history', JSON.stringify(historyCities.value))
}
const clearHistory = () => { historyCities.value = []; localStorage.removeItem('attraction_select_history') }
const selectCity = (city) => {
  saveHistory(city)
  sessionStorage.setItem('selected_destination_spot', city)
  router.back()
}

// ====== 搜索 ======
const searchDomestic = computed(() => {
  if (!searchText.value.trim()) return []
  const kw = searchText.value.trim().toLowerCase()
  const all = [
    ...municipalities.flatMap(m => [m.name, ...m.cities]),
    ...hkMacauTW.flatMap(m => [m.name, ...m.cities]),
    ...allProvinces.flatMap(p => [p.name, ...p.cities]),
  ]
  return [...new Set(all)].filter(c => c.toLowerCase().includes(kw)).slice(0, 40)
})
const searchOverseas = computed(() => {
  if (!searchText.value.trim()) return []
  const kw = searchText.value.trim().toLowerCase()
  const all = overseasContinents.flatMap(c => c.countries.flatMap(co => [co.name, ...co.cities]))
  return [...new Set(all)].filter(c => c.toLowerCase().includes(kw)).slice(0, 40)
})

// 境内选择
const currentDomesticGroup = computed(() => domesticGroups.find(g => g.key === domesticLeftKey.value) || null)
const domesticSubItems = computed(() => domesticLeftKey.value === 'recommend' ? [] : (currentDomesticGroup.value?.items || []))
const directProvince = computed(() => {
  if (domesticLeftKey.value === 'recommend' || currentDomesticGroup.value) return null
  return allProvinces.find(p => p.name === domesticLeftKey.value) || null
})
const domesticCityList = computed(() => {
  if (domesticLeftKey.value === 'recommend') return domesticHotCities
  if (directProvince.value) return directProvince.value.cities || []
  if (domesticSubKey.value && domesticSubItems.value.length > 0) {
    const item = domesticSubItems.value.find(p => p.name === domesticSubKey.value)
    return item?.cities || []
  }
  return []
})

const selectDomesticLeft = (key) => { domesticLeftKey.value = key; domesticSubKey.value = null }
const selectDomesticSub = (name) => { domesticSubKey.value = name }

// 境外选择
const selectedContinent = computed(() => overseasContinents.find(c => c.name === overseasLeftKey.value) || null)
const overseasCityList = computed(() => {
  if (overseasLeftKey.value === 'recommend') return overseasHotCities
  if (!overseasSubKey.value) return selectedContinent.value?.countries.flatMap(c => c.cities.slice(0, 3)) || []
  const country = selectedContinent.value?.countries.find(c => c.name === overseasSubKey.value)
  return country?.cities || []
})

const selectOverseasLeft = (key) => { overseasLeftKey.value = key; overseasSubKey.value = null }
const selectOverseasSub = (name) => { overseasSubKey.value = name }

// ====== 图片相关 ======
const cityImageMap = ref({})

const loadImageMap = async () => {
  try {
    const resp = await fetch('/api/city/images/map')
    if (resp.ok) {
      const data = await resp.json()
      if (data.code === 0 && data.data) Object.assign(cityImageMap.value, data.data)
    }
  } catch {}
  try {
    const staticResp = await fetch('/city-images.json')
    if (staticResp.ok) {
      const staticData = await staticResp.json()
      for (const [k, v] of Object.entries(staticData)) {
        if (v && !cityImageMap.value[k]) cityImageMap.value[k] = v
      }
    }
  } catch {}
}

const cityImage = (name) => cityImageMap.value[name] || ''

const cityBg = (name) => {
  let h = 0
  for (let i = 0; i < name.length; i++) h = name.charCodeAt(i) + ((h << 5) - h)
  return `linear-gradient(135deg, hsl(${Math.abs(h) % 360}, 50%, 65%), hsl(${(Math.abs(h) + 30) % 360}, 55%, 50%))`
}

const onImgError = (e) => { e.target.style.display = 'none' }
const goBack = () => { try { router.back() } catch { router.push('/trips') } }
</script>

<template>
  <div class="as-page">
    <!-- ══════ 顶部 ══════ -->
    <div class="as-header">
      <button class="as-back" @click="goBack">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><polyline points="15 18 9 12 15 6"/></svg>
      </button>
      <div class="as-search-wrap">
        <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="#94a3b8" stroke-width="1.8"><circle cx="9" cy="9" r="5"/><line x1="13" y1="13" x2="18" y2="18"/></svg>
        <input v-model="searchText" type="text" placeholder="搜索目的地" class="as-search-input" />
      </div>
    </div>

    <!-- ══════ 境内/境外 Tab（始终可见） ══════ -->
    <div class="as-tabs">
      <span class="as-tab" :class="{ active: activeTab === 'domestic' }" @click="activeTab = 'domestic'">境内</span>
      <span class="as-tab" :class="{ active: activeTab === 'overseas' }" @click="activeTab = 'overseas'">境外</span>
    </div>

    <!-- ══════ 搜索模式 ══════ -->
    <div class="as-search-results" v-show="searchText.trim() && activeTab === 'domestic'">
      <div class="as-section-title">境内搜索结果</div>
      <div class="as-city-grid">
        <div v-for="city in searchDomestic" :key="city" class="as-city-card" @click="selectCity(city)">
          <div class="as-city-img-wrap" :style="{ background: cityBg(city) }">
            <img v-if="cityImage(city)" :src="cityImage(city)" :alt="city" class="as-city-img" loading="lazy" @error="onImgError" />
            <span class="as-city-initial" :class="{ show: !cityImage(city) }">{{ city.charAt(0) }}</span>
          </div>
          <div class="as-city-name">{{ city }}</div>
        </div>
      </div>
      <div v-if="searchDomestic.length === 0" class="as-empty">未找到匹配的目的地</div>
    </div>
    <div class="as-search-results" v-show="searchText.trim() && activeTab === 'overseas'">
      <div class="as-section-title">境外搜索结果</div>
      <div class="as-city-grid">
        <div v-for="city in searchOverseas" :key="city" class="as-city-card" @click="selectCity(city)">
          <div class="as-city-img-wrap" :style="{ background: cityBg(city) }">
            <img v-if="cityImage(city)" :src="cityImage(city)" :alt="city" class="as-city-img" loading="lazy" @error="onImgError" />
            <span class="as-city-initial" :class="{ show: !cityImage(city) }">{{ city.charAt(0) }}</span>
          </div>
          <div class="as-city-name">{{ city }}</div>
        </div>
      </div>
      <div v-if="searchOverseas.length === 0" class="as-empty">未找到匹配的目的地</div>
    </div>

    <!-- ══════ 正常模式 ══════ -->
    <div class="as-content" v-show="!searchText.trim() && activeTab === 'domestic'">
      <div class="as-left">
        <div class="as-left-list">
          <div class="as-left-item recommend" :class="{ active: domesticLeftKey === 'recommend' }" @click="selectDomesticLeft('recommend')">推荐</div>
          <div v-for="group in domesticGroups" :key="group.key" class="as-left-item" :class="{ active: domesticLeftKey === group.key }" @click="selectDomesticLeft(group.key)">{{ group.label }}</div>
          <div v-for="prov in allProvinces" :key="prov.name" class="as-left-item" :class="{ active: domesticLeftKey === prov.name }" @click="selectDomesticLeft(prov.name)">{{ prov.name }}</div>
        </div>
      </div>

      <div class="as-right">
        <!-- 历史（仅推荐模式） -->
        <div class="as-section" v-if="domesticLeftKey === 'recommend' && historyCities.length > 0">
          <div class="as-section-title-row">
            <span class="as-section-title">历史选择</span>
            <span class="as-clear-btn" @click="clearHistory">清除历史</span>
          </div>
          <div class="as-history-grid">
            <div v-for="city in historyCities" :key="city" class="as-history-chip" @click="selectCity(city)">{{ city }}</div>
          </div>
        </div>

        <!-- 分组子项 -->
        <div class="as-section" v-if="domesticLeftKey !== 'recommend' && domesticSubItems.length > 0">
          <div class="as-section-title">{{ currentDomesticGroup?.label || '' }}</div>
          <div class="as-city-grid">
            <div v-for="item in domesticSubItems" :key="item.name" class="as-city-card" :class="{ active: domesticSubKey === item.name }" @click="selectDomesticSub(item.name)">
              <div class="as-city-img-wrap" :style="{ background: cityBg(item.name) }">
                <img v-if="cityImage(item.name)" :src="cityImage(item.name)" :alt="item.name" class="as-city-img" loading="lazy" @error="onImgError" />
                <span class="as-city-initial" :class="{ show: !cityImage(item.name) }">{{ item.name.charAt(0) }}</span>
              </div>
              <div class="as-city-name">{{ item.name }}</div>
            </div>
          </div>
        </div>

        <!-- 城市列表 -->
        <div class="as-section" v-if="domesticCityList.length > 0">
          <div class="as-section-title">{{ domesticLeftKey === 'recommend' ? '热门目的地' : (domesticSubKey || domesticLeftKey) }}</div>
          <div class="as-city-grid">
            <div v-for="city in domesticCityList" :key="city" class="as-city-card" @click="selectCity(city)">
              <div class="as-city-img-wrap" :style="{ background: cityBg(city) }">
                <img v-if="cityImage(city)" :src="cityImage(city)" :alt="city" class="as-city-img" loading="lazy" @error="onImgError" />
                <span class="as-city-initial" :class="{ show: !cityImage(city) }">{{ city.charAt(0) }}</span>
              </div>
              <div class="as-city-name">{{ city }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ══════ 境外 ══════ -->
    <div class="as-content" v-show="!searchText.trim() && activeTab === 'overseas'">
      <div class="as-left">
        <div class="as-left-list">
          <div class="as-left-item recommend" :class="{ active: overseasLeftKey === 'recommend' }" @click="selectOverseasLeft('recommend')">推荐</div>
          <div v-for="cont in overseasContinents" :key="cont.name" class="as-left-item" :class="{ active: overseasLeftKey === cont.name }" @click="selectOverseasLeft(cont.name)">{{ cont.name }}</div>
        </div>
      </div>

      <div class="as-right">
        <div class="as-section" v-if="overseasLeftKey === 'recommend' && historyCities.length > 0">
          <div class="as-section-title-row">
            <span class="as-section-title">历史选择</span>
            <span class="as-clear-btn" @click="clearHistory">清除历史</span>
          </div>
          <div class="as-history-grid">
            <div v-for="city in historyCities" :key="city" class="as-history-chip" @click="selectCity(city)">{{ city }}</div>
          </div>
        </div>

        <div class="as-section" v-if="overseasLeftKey !== 'recommend' && selectedContinent">
          <div class="as-country-scroll">
            <span v-for="c in selectedContinent.countries" :key="c.name" class="as-country-chip" :class="{ active: overseasSubKey === c.name }" @click="selectOverseasSub(c.name)">{{ c.name }}</span>
          </div>
        </div>

        <div class="as-section">
          <div class="as-section-title">{{ overseasLeftKey === 'recommend' ? '热门目的地' : (overseasSubKey || '热门目的地') }}</div>
          <div class="as-city-grid">
            <div v-for="city in overseasCityList" :key="city" class="as-city-card" @click="selectCity(city)">
              <div class="as-city-img-wrap" :style="{ background: cityBg(city) }">
                <img v-if="cityImage(city)" :src="cityImage(city)" :alt="city" class="as-city-img" loading="lazy" @error="onImgError" />
                <span class="as-city-initial" :class="{ show: !cityImage(city) }">{{ city.charAt(0) }}</span>
              </div>
              <div class="as-city-name">{{ city }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.as-page { width: 100%; height: 100dvh; background: #f5f7fa; display: flex; flex-direction: column; overflow: hidden; }

.as-header { display: flex; align-items: center; gap: 10px; padding: calc(env(safe-area-inset-top) + 8px) 14px 10px; background: #fff; flex-shrink: 0; }
.as-back { width: 36px; height: 36px; min-width: 36px; border: none; background: transparent; color: #333; cursor: pointer; display: flex; align-items: center; justify-content: center; border-radius: 50%; }
.as-back:active { background: #f1f5f9; }
.as-search-wrap { flex: 1; display: flex; align-items: center; gap: 8px; background: #f1f5f9; border-radius: 20px; padding: 9px 14px; }
.as-search-input { flex: 1; border: none; outline: none; background: transparent; font-size: 14px; color: #1e293b; }
.as-search-input::placeholder { color: #94a3b8; }

.as-tabs { display: flex; padding: 10px 16px; background: #fff; border-bottom: 1px solid #f1f5f9; flex-shrink: 0; }
.as-tab { flex: 1; text-align: center; padding: 8px 0; font-size: 15px; color: #94a3b8; cursor: pointer; font-weight: 500; position: relative; transition: color 0.2s; }
.as-tab.active { color: #1e293b; font-weight: 700; }
.as-tab.active::after { content: ''; position: absolute; bottom: -11px; left: 50%; transform: translateX(-50%); width: 32px; height: 3px; border-radius: 2px; background: linear-gradient(90deg, #6366f1, #8b5cf6); }
.as-tab:active { opacity: 0.6; }

.as-content { flex: 1; display: flex; overflow: hidden; }

/* 左列 */
.as-left { width: 22%; min-width: 70px; max-width: 100px; background: #f8fafc; display: flex; flex-direction: column; border-right: 1px solid #e8ecf1; }
.as-left-list { flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; padding-bottom: env(safe-area-inset-bottom, 12px); }
.as-left-item { padding: 12px 10px; font-size: 13px; color: #64748b; cursor: pointer; transition: all 0.15s; white-space: nowrap; text-align: center; border-left: 3px solid transparent; }
.as-left-item.recommend { font-weight: 700; color: #f59e0b; }
.as-left-item.active { color: #7c3aed; font-weight: 600; background: #fff; border-left-color: #7c3aed; }
.as-left-item.recommend.active { color: #f59e0b; border-left-color: #f59e0b; }
.as-left-item:active { background: #f1f5f9; }

/* 右列 */
.as-right { flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; padding: 12px 14px calc(14px + env(safe-area-inset-bottom, 0px)); background: #fff; }
.as-section { margin-bottom: 18px; }
.as-section-title { font-size: 14px; font-weight: 700; color: #1e293b; margin-bottom: 10px; }
.as-section-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.as-clear-btn { font-size: 12px; color: #94a3b8; cursor: pointer; padding: 2px 8px; }
.as-clear-btn:active { color: #ef4444; }

/* 搜索 */
.as-search-results { flex: 1; overflow-y: auto; padding: 12px 14px; background: #fff; }
.as-empty { text-align: center; color: #94a3b8; font-size: 14px; padding: 40px 0; }

/* 历史 */
.as-history-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.as-history-chip { padding: 7px 14px; background: #f1f5f9; border-radius: 8px; font-size: 13px; color: #475569; cursor: pointer; transition: all 0.15s; }
.as-history-chip:active { background: #e2e8f0; transform: scale(0.95); }

/* 境外国家条 */
.as-country-scroll { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; -webkit-overflow-scrolling: touch; scrollbar-width: none; }
.as-country-scroll::-webkit-scrollbar { display: none; }
.as-country-chip { padding: 7px 14px; border-radius: 8px; font-size: 13px; white-space: nowrap; cursor: pointer; flex-shrink: 0; background: #f1f5f9; color: #64748b; transition: all 0.15s; }
.as-country-chip.active { background: #f8f7ff; color: #7c3aed; font-weight: 600; border: 1px solid rgba(139,92,246,0.2); }
.as-country-chip:active { transform: scale(0.95); }

/* 城市卡片 */
.as-city-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.as-city-card { cursor: pointer; transition: transform 0.15s; }
.as-city-card:active { transform: scale(0.95); }
.as-city-img-wrap {
  aspect-ratio: 4/3; border-radius: 10px; overflow: hidden;
  margin-bottom: 6px; position: relative;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.as-city-img {
  width: 100%; height: 100%; object-fit: cover; display: block;
  position: relative; z-index: 1; border-radius: 10px;
}
.as-city-initial {
  position: absolute; inset: 0; z-index: 0;
  display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,0.85); font-size: 32px; font-weight: 800;
  text-shadow: 0 2px 4px rgba(0,0,0,0.15); user-select: none;
}
.as-city-initial:not(.show) { display: none; }
.as-city-name { font-size: 10px; color: #64748b; text-align: center; font-weight: 500; line-height: 1.2; }

@media screen and (max-width: 360px) { .as-left { width: 25%; } }
</style>
