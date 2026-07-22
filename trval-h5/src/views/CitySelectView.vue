<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  municipalities, hkMacauTW, allProvinces, domesticGroups,
  overseasContinents, domesticHotCities, overseasHotCities,
  getAllCityNames,
} from '../data/cityData.js'

defineOptions({ name: 'CitySelectView' })
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
const currentLocation = ref('')
const locationStatus = ref('idle') // 'idle'|'loading'|'success'|'failed'|'denied'

// 定位 — 四态处理 + 权限预检
const requestLocation = () => {
  locationStatus.value = 'loading'
  currentLocation.value = '正在定位...'

  if (!navigator.geolocation) {
    locationStatus.value = 'failed'
    currentLocation.value = '浏览器不支持定位'
    return
  }

  // 预检权限状态
  if (navigator.permissions) {
    navigator.permissions.query({ name: 'geolocation' }).then(res => {
      if (res.state === 'denied') {
        locationStatus.value = 'denied'
        currentLocation.value = '定位权限已关闭，请在系统设置中开启'
      }
    })
  }

  navigator.geolocation.getCurrentPosition(
    async (pos) => {
      locationStatus.value = 'success'
      const { latitude, longitude } = pos.coords

      // 1) 优先调用后端百度逆地理编码
      try {
        const resp = await fetch(`/api/city/location?lat=${latitude}&lng=${longitude}`)
        const data = await resp.json()
        if (data.code === 0 && data.data) {
          const d = data.data
          const addr = d.address || [d.province, d.city, d.district].filter(Boolean).join('')
          if (addr) { currentLocation.value = addr; return }
        }
      } catch {}

      // 2) 兜底：调用免费 Nominatim 逆地理编码
      try {
        const nResp = await fetch(
          `https://nominatim.openstreetmap.org/reverse?format=json&lat=${latitude}&lon=${longitude}&zoom=10&accept-language=zh`
        )
        const nData = await nResp.json()
        if (nData && nData.display_name) {
          const parts = nData.address || {}
          const addr = parts.city || parts.town || parts.county || parts.state || nData.display_name
          if (addr) { currentLocation.value = addr; return }
        }
      } catch {}

      // 3) 都失败
      currentLocation.value = '已定位'
    },
    (err) => {
      if (err.code === 1) {
        locationStatus.value = 'denied'
        currentLocation.value = '定位权限未开启，请在设置中授权'
      } else if (err.code === 2) {
        locationStatus.value = 'failed'
        currentLocation.value = '定位暂时不可用，请检查网络'
      } else {
        locationStatus.value = 'failed'
        currentLocation.value = '定位失败，点击重试'
      }
    },
    { timeout: 10000, enableHighAccuracy: true, maximumAge: 300000 }
  )
}

onMounted(() => {
  try { const raw = localStorage.getItem('city_select_history'); if (raw) historyCities.value = JSON.parse(raw) } catch {}
  requestLocation()
  loadImageMap()
})

const saveHistory = (city) => {
  historyCities.value = [city, ...historyCities.value.filter(c => c !== city)].slice(0, 8)
  localStorage.setItem('city_select_history', JSON.stringify(historyCities.value))
}
const clearHistory = () => { historyCities.value = []; localStorage.removeItem('city_select_history') }
const selectCity = (city) => { saveHistory(city); sessionStorage.setItem('selected_origin_city', city); router.back() }

// ====== 搜索 ======
const allCityNames = getAllCityNames()
const searchResults = computed(() => {
  if (!searchText.value.trim()) return []
  const kw = searchText.value.trim().toLowerCase()
  return allCityNames.filter(c => c.toLowerCase().includes(kw)).slice(0, 40)
})
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

// ====== 城市图片（素材库API → 静态JSON兜底 → 渐变色） ======
const cityImageMap = ref({})

const loadImageMap = async () => {
  // 1) 优先从后端 API 加载
  try {
    const resp = await fetch('/api/city/images/map')
    if (resp.ok) {
      const data = await resp.json()
      if (data.code === 0 && data.data) {
        Object.assign(cityImageMap.value, data.data)
      }
    }
  } catch {}

  // 2) 兜底：加载静态 city-images.json（弥补 API 未覆盖的城市）
  try {
    const staticResp = await fetch('/city-images.json')
    if (staticResp.ok) {
      const staticData = await staticResp.json()
      for (const [k, v] of Object.entries(staticData)) {
        if (v && !cityImageMap.value[k]) {
          cityImageMap.value[k] = v
        }
      }
    }
  } catch {}
}

const goBack = () => { try { router.back() } catch { router.push('/trips') } }

const cityImage = (name) => cityImageMap.value[name] || ''

/** HSL 渐变色 — 永远正确 */
const cityBg = (name) => {
  let h = 0
  for (let i = 0; i < name.length; i++) h = name.charCodeAt(i) + ((h << 5) - h)
  return `linear-gradient(135deg, hsl(${Math.abs(h) % 360}, 50%, 65%), hsl(${(Math.abs(h) + 30) % 360}, 55%, 50%))`
}

const onImgError = (e) => {
  e.target.style.display = 'none'
}
</script>

<template>
  <div class="city-select-page">
    <!-- ══════ 顶部 ══════ -->
    <div class="cs-header">
      <button class="cs-back" @click="goBack">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><polyline points="15 18 9 12 15 6"/></svg>
      </button>
      <div class="cs-search-wrap">
        <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="#94a3b8" stroke-width="1.8"><circle cx="9" cy="9" r="5"/><line x1="13" y1="13" x2="18" y2="18"/></svg>
        <input v-model="searchText" type="text" placeholder="请输入城市" class="cs-search-input" />
      </div>
    </div>

    <!-- ══════ 境内/境外 Tab（始终可见） ══════ -->
    <div class="cs-tabs">
      <span class="cs-tab" :class="{ active: activeTab === 'domestic' }" @click="activeTab = 'domestic'">境内</span>
      <span class="cs-tab" :class="{ active: activeTab === 'overseas' }" @click="activeTab = 'overseas'">境外</span>
    </div>

    <!-- ══════ 搜索模式 ══════ -->
    <div class="cs-search-results" v-show="searchText.trim() && activeTab === 'domestic'">
      <div class="cs-section-title">境内搜索结果</div>
      <div class="cs-city-grid">
        <div v-for="city in searchDomestic" :key="city" class="cs-city-card" @click="selectCity(city)">
          <div class="cs-city-img-wrap" :style="{ background: cityBg(city) }">
            <img v-if="cityImage(city)" :src="cityImage(city)" :alt="city" class="cs-city-img" loading="lazy" @error="onImgError" />
            <span class="cs-city-initial" :class="{ show: !cityImage(city) }">{{ city.charAt(0) }}</span>
          </div>
          <div class="cs-city-name">{{ city }}</div>
        </div>
      </div>
      <div v-if="searchDomestic.length === 0" class="cs-empty">未找到匹配的城市</div>
    </div>
    <div class="cs-search-results" v-show="searchText.trim() && activeTab === 'overseas'">
      <div class="cs-section-title">境外搜索结果</div>
      <div class="cs-city-grid">
        <div v-for="city in searchOverseas" :key="city" class="cs-city-card" @click="selectCity(city)">
          <div class="cs-city-img-wrap" :style="{ background: cityBg(city) }">
            <img v-if="cityImage(city)" :src="cityImage(city)" :alt="city" class="cs-city-img" loading="lazy" @error="onImgError" />
            <span class="cs-city-initial" :class="{ show: !cityImage(city) }">{{ city.charAt(0) }}</span>
          </div>
          <div class="cs-city-name">{{ city }}</div>
        </div>
      </div>
      <div v-if="searchOverseas.length === 0" class="cs-empty">未找到匹配的城市</div>
    </div>

    <!-- ══════ 正常模式 ══════ -->
    <div class="cs-content" v-show="!searchText.trim() && activeTab === 'domestic'">
      <div class="cs-left">
        <div class="cs-left-list">
          <div class="cs-left-item recommend" :class="{ active: domesticLeftKey === 'recommend' }" @click="selectDomesticLeft('recommend')">推荐</div>
          <div v-for="group in domesticGroups" :key="group.key" class="cs-left-item" :class="{ active: domesticLeftKey === group.key }" @click="selectDomesticLeft(group.key)">{{ group.label }}</div>
          <div v-for="prov in allProvinces" :key="prov.name" class="cs-left-item" :class="{ active: domesticLeftKey === prov.name }" @click="selectDomesticLeft(prov.name)">{{ prov.name }}</div>
        </div>
      </div>

      <div class="cs-right">
        <!-- 定位 — 四态 -->
        <div class="cs-section">
          <div
            class="cs-loc-row" :class="'loc--' + locationStatus"
            @click="locationStatus === 'success' ? selectCity(currentLocation) : (locationStatus === 'failed' || locationStatus === 'denied') ? requestLocation() : null"
          >
            <!-- idle / loading -->
            <template v-if="locationStatus === 'idle' || locationStatus === 'loading'">
              <span class="cs-loc-spinner"></span>
              <span class="cs-loc-text">{{ currentLocation || '定位中...' }}</span>
            </template>
            <!-- success -->
            <template v-else-if="locationStatus === 'success'">
              <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="#10B981" stroke-width="2"><circle cx="10" cy="10" r="3"/><path d="M10 2v3M10 15v3M2 10h3M15 10h3"/></svg>
              <span class="cs-loc-text loc-text--success">{{ currentLocation }}</span>
              <span class="cs-loc-tag">当前</span>
            </template>
            <!-- failed -->
            <template v-else-if="locationStatus === 'failed'">
              <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="#F59E0B" stroke-width="2"><circle cx="10" cy="10" r="8"/><line x1="10" y1="6" x2="10" y2="11"/><circle cx="10" cy="14" r="0.5" fill="#F59E0B"/></svg>
              <span class="cs-loc-text">{{ currentLocation }}</span>
              <span class="cs-loc-retry">点击重试</span>
            </template>
            <!-- denied -->
            <template v-else-if="locationStatus === 'denied'">
              <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="#EF4444" stroke-width="2"><rect x="3" y="7" width="14" height="8" rx="2"/><path d="M7 7V5a3 3 0 016 0v2"/></svg>
              <span class="cs-loc-text">{{ currentLocation }}</span>
              <span class="cs-loc-retry loc-retry--denied">去设置</span>
            </template>
          </div>
        </div>

        <!-- 历史（仅推荐模式） -->
        <div class="cs-section" v-if="domesticLeftKey === 'recommend' && historyCities.length > 0">
          <div class="cs-section-title-row">
            <span class="cs-section-title">历史选择</span>
            <span class="cs-clear-btn" @click="clearHistory">清除历史</span>
          </div>
          <div class="cs-history-grid">
            <div v-for="city in historyCities" :key="city" class="cs-history-chip" @click="selectCity(city)">{{ city }}</div>
          </div>
        </div>

        <!-- 分组子项 -->
        <div class="cs-section" v-if="domesticLeftKey !== 'recommend' && domesticSubItems.length > 0">
          <div class="cs-section-title">{{ currentDomesticGroup?.label || '' }}</div>
          <div class="cs-city-grid">
            <div v-for="item in domesticSubItems" :key="item.name" class="cs-city-card" :class="{ active: domesticSubKey === item.name }" @click="selectDomesticSub(item.name)">
              <div class="cs-city-img-wrap" :style="{ background: cityBg(item.name) }">
                <img v-if="cityImage(item.name)" :src="cityImage(item.name)" :alt="item.name" class="cs-city-img" loading="lazy" @error="onImgError" />
                <span class="cs-city-initial" :class="{ show: !cityImage(item.name) }">{{ item.name.charAt(0) }}</span>
              </div>
              <div class="cs-city-name">{{ item.name }}</div>
            </div>
          </div>
        </div>

        <!-- 城市列表 -->
        <div class="cs-section" v-if="domesticCityList.length > 0">
          <div class="cs-section-title">{{ domesticLeftKey === 'recommend' ? '热门城市' : (domesticSubKey || domesticLeftKey) }}</div>
          <div class="cs-city-grid">
            <div v-for="city in domesticCityList" :key="city" class="cs-city-card" @click="selectCity(city)">
              <div class="cs-city-img-wrap" :style="{ background: cityBg(city) }">
                <img v-if="cityImage(city)" :src="cityImage(city)" :alt="city" class="cs-city-img" loading="lazy" @error="onImgError" />
                <span class="cs-city-initial" :class="{ show: !cityImage(city) }">{{ city.charAt(0) }}</span>
              </div>
              <div class="cs-city-name">{{ city }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ══════ 境外 ══════ -->
    <div class="cs-content" v-show="!searchText.trim() && activeTab === 'overseas'">
      <div class="cs-left">
        <div class="cs-left-list">
          <div class="cs-left-item recommend" :class="{ active: overseasLeftKey === 'recommend' }" @click="selectOverseasLeft('recommend')">推荐</div>
          <div v-for="cont in overseasContinents" :key="cont.name" class="cs-left-item" :class="{ active: overseasLeftKey === cont.name }" @click="selectOverseasLeft(cont.name)">{{ cont.name }}</div>
        </div>
      </div>

      <div class="cs-right">
        <div class="cs-section">
          <div
            class="cs-loc-row" :class="'loc--' + locationStatus"
            @click="locationStatus === 'success' ? selectCity(currentLocation) : (locationStatus === 'failed' || locationStatus === 'denied') ? requestLocation() : null"
          >
            <template v-if="locationStatus === 'idle' || locationStatus === 'loading'">
              <span class="cs-loc-spinner"></span>
              <span class="cs-loc-text">{{ currentLocation || '定位中...' }}</span>
            </template>
            <template v-else-if="locationStatus === 'success'">
              <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="#10B981" stroke-width="2"><circle cx="10" cy="10" r="3"/><path d="M10 2v3M10 15v3M2 10h3M15 10h3"/></svg>
              <span class="cs-loc-text loc-text--success">{{ currentLocation }}</span>
              <span class="cs-loc-tag">当前</span>
            </template>
            <template v-else-if="locationStatus === 'failed'">
              <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="#F59E0B" stroke-width="2"><circle cx="10" cy="10" r="8"/><line x1="10" y1="6" x2="10" y2="11"/><circle cx="10" cy="14" r="0.5" fill="#F59E0B"/></svg>
              <span class="cs-loc-text">{{ currentLocation }}</span>
              <span class="cs-loc-retry">点击重试</span>
            </template>
            <template v-else-if="locationStatus === 'denied'">
              <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="#EF4444" stroke-width="2"><rect x="3" y="7" width="14" height="8" rx="2"/><path d="M7 7V5a3 3 0 016 0v2"/></svg>
              <span class="cs-loc-text">{{ currentLocation }}</span>
              <span class="cs-loc-retry loc-retry--denied">去设置</span>
            </template>
          </div>
        </div>

        <div class="cs-section" v-if="overseasLeftKey === 'recommend' && historyCities.length > 0">
          <div class="cs-section-title-row">
            <span class="cs-section-title">历史选择</span>
            <span class="cs-clear-btn" @click="clearHistory">清除历史</span>
          </div>
          <div class="cs-history-grid">
            <div v-for="city in historyCities" :key="city" class="cs-history-chip" @click="selectCity(city)">{{ city }}</div>
          </div>
        </div>

        <div class="cs-section" v-if="overseasLeftKey !== 'recommend' && selectedContinent">
          <div class="cs-country-scroll">
            <span v-for="c in selectedContinent.countries" :key="c.name" class="cs-country-chip" :class="{ active: overseasSubKey === c.name }" @click="selectOverseasSub(c.name)">{{ c.name }}</span>
          </div>
        </div>

        <div class="cs-section">
          <div class="cs-section-title">{{ overseasLeftKey === 'recommend' ? '热门城市' : (overseasSubKey || '热门目的地') }}</div>
          <div class="cs-city-grid">
            <div v-for="city in overseasCityList" :key="city" class="cs-city-card" @click="selectCity(city)">
              <div class="cs-city-img-wrap" :style="{ background: cityBg(city) }">
                <img v-if="cityImage(city)" :src="cityImage(city)" :alt="city" class="cs-city-img" loading="lazy" @error="onImgError" />
                <span class="cs-city-initial" :class="{ show: !cityImage(city) }">{{ city.charAt(0) }}</span>
              </div>
              <div class="cs-city-name">{{ city }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.city-select-page { width: 100%; height: 100dvh; background: #f5f7fa; display: flex; flex-direction: column; overflow: hidden; }

.cs-header { display: flex; align-items: center; gap: 10px; padding: calc(env(safe-area-inset-top) + 8px) 14px 10px; background: #fff; flex-shrink: 0; }
.cs-back { width: 36px; height: 36px; min-width: 36px; border: none; background: transparent; color: #333; cursor: pointer; display: flex; align-items: center; justify-content: center; border-radius: 50%; }
.cs-back:active { background: #f1f5f9; }
.cs-search-wrap { flex: 1; display: flex; align-items: center; gap: 8px; background: #f1f5f9; border-radius: 20px; padding: 9px 14px; }
.cs-search-input { flex: 1; border: none; outline: none; background: transparent; font-size: 14px; color: #1e293b; }
.cs-search-input::placeholder { color: #94a3b8; }

.cs-tabs { display: flex; padding: 10px 16px; background: #fff; border-bottom: 1px solid #f1f5f9; flex-shrink: 0; }
.cs-tab { flex: 1; text-align: center; padding: 8px 0; font-size: 15px; color: #94a3b8; cursor: pointer; font-weight: 500; position: relative; transition: color 0.2s; }
.cs-tab.active { color: #1e293b; font-weight: 700; }
.cs-tab.active::after { content: ''; position: absolute; bottom: -11px; left: 50%; transform: translateX(-50%); width: 32px; height: 3px; border-radius: 2px; background: linear-gradient(90deg, #6366f1, #8b5cf6); }
.cs-tab:active { opacity: 0.6; }

.cs-content { flex: 1; display: flex; overflow: hidden; }

/* 左列 */
.cs-left { width: 22%; min-width: 70px; max-width: 100px; background: #f8fafc; display: flex; flex-direction: column; border-right: 1px solid #e8ecf1; }
.cs-left-list { flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; padding-bottom: env(safe-area-inset-bottom, 12px); }
.cs-left-item { padding: 12px 10px; font-size: 13px; color: #64748b; cursor: pointer; transition: all 0.15s; white-space: nowrap; text-align: center; border-left: 3px solid transparent; }
.cs-left-item.recommend { font-weight: 700; color: #f59e0b; }
.cs-left-item.active { color: #7c3aed; font-weight: 600; background: #fff; border-left-color: #7c3aed; }
.cs-left-item.recommend.active { color: #f59e0b; border-left-color: #f59e0b; }
.cs-left-item:active { background: #f1f5f9; }

/* 右列 */
.cs-right { flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; padding: 12px 14px calc(14px + env(safe-area-inset-bottom, 0px)); background: #fff; }
.cs-section { margin-bottom: 18px; }
.cs-section-title { font-size: 14px; font-weight: 700; color: #1e293b; margin-bottom: 10px; }
.cs-section-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.cs-clear-btn { font-size: 12px; color: #94a3b8; cursor: pointer; padding: 2px 8px; }
.cs-clear-btn:active { color: #ef4444; }

/* ====== 定位行 — 四态 ====== */
.cs-loc-row {
  display: flex; align-items: center; gap: 8px;
  padding: 11px 14px; border-radius: 12px; font-size: 13px;
  transition: all 0.25s; min-height: 42px;
}
.cs-loc-row.loc--idle,
.cs-loc-row.loc--loading { background: #f8f7ff; border: 1px solid rgba(139,92,246,0.12); }
.cs-loc-row.loc--success { background: #f0fdf4; border: 1px solid rgba(16,185,129,0.2); cursor: pointer; }
.cs-loc-row.loc--success:active { background: #dcfce7; }
.cs-loc-row.loc--failed { background: #fffbeb; border: 1px solid rgba(245,158,11,0.25); cursor: pointer; }
.cs-loc-row.loc--failed:active { background: #fef3c7; }
.cs-loc-row.loc--denied { background: #fef2f2; border: 1px solid rgba(239,68,68,0.2); cursor: pointer; }
.cs-loc-row.loc--denied:active { background: #fee2e2; }

.cs-loc-text { flex: 1; color: #475569; font-weight: 500; }
.loc-text--success { color: #059669; font-weight: 600; }

.cs-loc-tag {
  font-size: 10px; padding: 2px 8px; border-radius: 8px;
  background: #10B981; color: #fff; font-weight: 600;
}
.cs-loc-retry {
  font-size: 12px; color: #F59E0B; font-weight: 600; white-space: nowrap;
}
.loc-retry--denied { color: #EF4444; }

.cs-loc-spinner {
  width: 14px; height: 14px; min-width: 14px;
  border: 2px solid #e2e8f0; border-top-color: #7c3aed;
  border-radius: 50%; animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 历史 */
.cs-history-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.cs-history-chip { padding: 7px 14px; background: #f1f5f9; border-radius: 8px; font-size: 13px; color: #475569; cursor: pointer; transition: all 0.15s; }
.cs-history-chip:active { background: #e2e8f0; transform: scale(0.95); }

/* 境外国家 */
.cs-country-scroll { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; -webkit-overflow-scrolling: touch; scrollbar-width: none; }
.cs-country-scroll::-webkit-scrollbar { display: none; }
.cs-country-chip { padding: 7px 14px; border-radius: 8px; font-size: 13px; white-space: nowrap; cursor: pointer; flex-shrink: 0; background: #f1f5f9; color: #64748b; transition: all 0.15s; }
.cs-country-chip.active { background: #f8f7ff; color: #7c3aed; font-weight: 600; border: 1px solid rgba(139,92,246,0.2); }
.cs-country-chip:active { transform: scale(0.95); }

/* 城市卡片 */
.cs-city-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.cs-city-card { cursor: pointer; transition: transform 0.15s; }
.cs-city-card:active { transform: scale(0.95); }
.cs-city-img-wrap {
  aspect-ratio: 4/3; border-radius: 10px; overflow: hidden;
  margin-bottom: 6px; position: relative;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.cs-city-img {
  width: 100%; height: 100%; object-fit: cover; display: block;
  position: relative; z-index: 1; border-radius: 10px;
}
.cs-city-initial {
  position: absolute; inset: 0; z-index: 0;
  display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,0.85); font-size: 32px; font-weight: 800;
  text-shadow: 0 2px 4px rgba(0,0,0,0.15); user-select: none;
}
.cs-city-initial:not(.show) { display: none; }
.cs-city-name { font-size: 10px; color: #64748b; text-align: center; font-weight: 500; line-height: 1.2; }

/* 搜索模式 */
.cs-search-results { flex: 1; overflow-y: auto; padding: 12px 14px; background: #fff; }
.cs-empty { text-align: center; color: #94a3b8; font-size: 14px; padding: 40px 0; }

@media screen and (max-width: 360px) { .cs-left { width: 25%; } }
</style>
