/**
 * geocode.js — 行程坐标补齐（B2）
 *
 * 为 planData.dayPlans[].timeSlots 补充经纬度坐标，来源优先级：
 *   1) 匹配已有 mapMarkers（零请求）
 *   2) 命中 localStorage 缓存（key: "city:name" → [lat,lng]）
 *   3) 缺失时调后端 /api/map/geocode 地理编码，成功后写入缓存
 *
 * 坐标补齐后随 planJson 持久化，供日历视图 / 分享海报 / 离线地图 / 地图路线复用。
 */
import { mapApi } from '../api'

const GEO_CACHE_KEY = 'geo_cache_v1'

const normalize = (name) => (name || '').replace(/[【】·、\s]/g, '').trim()

function readCache() {
  try { return JSON.parse(localStorage.getItem(GEO_CACHE_KEY) || '{}') } catch { return {} }
}

function writeCache(cache) {
  try { localStorage.setItem(GEO_CACHE_KEY, JSON.stringify(cache)) } catch { /* 存储满/禁用时忽略 */ }
}

async function geocodeOne(city, name) {
  try {
    const address = city ? `${city} ${name}` : name
    const res = await mapApi.geocode(address)
    if (res && res.code === 0 && Array.isArray(res.data) && res.data.length >= 2) {
      return [Number(res.data[0]), Number(res.data[1])]
    }
  } catch (e) { /* 单点失败忽略 */ }
  return null
}

/**
 * 补齐行程坐标（async：含地理编码请求，缺失项并发拉取）
 * @param {Object} planData  行程计划（含 dayPlans[].timeSlots[]）
 * @param {Array}  mapMarkers 已有地图标注 [{name, latitude, longitude}]
 * @returns {Promise<Object>} 补齐坐标后的 planData
 */
export async function enrichTimeSlotCoords(planData, mapMarkers = []) {
  if (!planData || !Array.isArray(planData.dayPlans)) return planData

  const markerMap = new Map()
  for (const m of mapMarkers || []) {
    const key = normalize(m.name)
    if (key && m.latitude != null && m.longitude != null && !markerMap.has(key)) {
      markerMap.set(key, { latitude: m.latitude, longitude: m.longitude })
    }
  }

  const cache = readCache()
  const city = planData.destination || ''
  const tasks = []

  for (const dp of planData.dayPlans) {
    for (const slot of (dp.timeSlots || [])) {
      const name = normalize(slot.attraction)
      if (!name) continue
      if (slot.latitude != null && slot.longitude != null) continue // 已有坐标

      // 1) 匹配已有 marker（零请求）
      const hit = markerMap.get(name)
      if (hit) { slot.latitude = hit.latitude; slot.longitude = hit.longitude; continue }

      // 2) 本地缓存
      const cacheKey = `${city}:${name}`
      const cached = cache[cacheKey]
      if (cached) { slot.latitude = cached[0]; slot.longitude = cached[1]; continue }

      // 3) 异步地理编码（并发拉取）
      tasks.push(geocodeOne(city, name).then((coord) => {
        if (coord) {
          slot.latitude = coord[0]
          slot.longitude = coord[1]
          cache[cacheKey] = coord
          writeCache(cache)
        }
      }))
    }
  }

  await Promise.all(tasks)
  return planData
}

export default enrichTimeSlotCoords
