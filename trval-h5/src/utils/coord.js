/**
 * coord.js — 前端坐标系转换工具（WGS-84 / GCJ-02 / BD-09）
 *
 * 与后端 CoordinateUtil 一致的公开算法。核心作用：把定位器各数据源返回的
 * 不同坐标系统一到「当前地图瓦片」的坐标系，避免高德(GCJ-02)图上出现 WGS-84
 * 坐标整体偏移约 600 米的问题。
 *
 * - WGS-84：GPS 原始坐标，国际标准（OSM / Leaflet 瓦片）
 * - GCJ-02：国测局火星坐标，高德 / 腾讯 / 谷歌中国
 * - BD-09 ：百度坐标（GCJ-02 二次加密）
 */
const PI = Math.PI
const X_PI = PI * 3000.0 / 180.0
const A = 6378245.0
const EE = 0.00669342162296594323

/** 是否在中国境内（需做火星偏移；境外直接用 WGS-84） */
export function isInChina(lat, lng) {
  return lng >= 72.004 && lng <= 137.8347 && lat >= 0.8293 && lat <= 55.8271
}

function transformLat(x, y) {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
  ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0
  ret += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0
  return ret
}
function transformLng(x, y) {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
  ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0
  ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0
  return ret
}

/** WGS-84 → GCJ-02 */
export function wgs84ToGcj02(lat, lng) {
  if (!isInChina(lat, lng)) return [lat, lng]
  const dLat = transformLat(lng - 105.0, lat - 35.0)
  const dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = lat / 180.0 * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  const nLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
  const nLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI)
  return [lat + nLat, lng + nLng]
}

/** GCJ-02 → WGS-84 */
export function gcj02ToWgs84(lat, lng) {
  if (!isInChina(lat, lng)) return [lat, lng]
  const [gLat, gLng] = wgs84ToGcj02(lat, lng)
  return [lat * 2 - gLat, lng * 2 - gLng]
}

/** GCJ-02 → BD-09 */
export function gcj02ToBd09(lat, lng) {
  const z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI)
  const theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI)
  return [z * Math.sin(theta) + 0.006, z * Math.cos(theta) + 0.0065]
}

/** BD-09 → GCJ-02 */
export function bd09ToGcj02(lat, lng) {
  const x = lng - 0.0065
  const y = lat - 0.006
  const z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI)
  const theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI)
  return [z * Math.sin(theta), z * Math.cos(theta)]
}

const SUPPORTED = ['wgs84', 'gcj02', 'bd09']

/**
 * 统一坐标转换（WGS-84 / GCJ-02 / BD-09）
 * @param {number} lat 纬度
 * @param {number} lng 经度
 * @param {string} from 源坐标系
 * @param {string} to 目标坐标系
 * @returns {[number, number]} [lat, lng]；境外或 from===to 时原样返回
 */
export function convert(lat, lng, from, to) {
  const f = String(from || '').toLowerCase()
  const t = String(to || '').toLowerCase()
  if (f === t) return [lat, lng]
  if (!SUPPORTED.includes(f) || !SUPPORTED.includes(t)) return [lat, lng]
  // 统一先转 GCJ-02
  let gcjLat, gcjLng
  if (f === 'wgs84') { const g = wgs84ToGcj02(lat, lng); gcjLat = g[0]; gcjLng = g[1] }
  else if (f === 'bd09') { const b = bd09ToGcj02(lat, lng); gcjLat = b[0]; gcjLng = b[1] }
  else { gcjLat = lat; gcjLng = lng }
  // 从 GCJ-02 转目标
  if (t === 'wgs84') return gcj02ToWgs84(gcjLat, gcjLng)
  if (t === 'bd09') return gcj02ToBd09(gcjLat, gcjLng)
  return [gcjLat, gcjLng]
}

/** 两点球面距离（米，haversine），用于城市 bbox 校验 */
export function distanceMeters(lat1, lng1, lat2, lng2) {
  const R = 6371000
  const rad = (d) => d * PI / 180
  const dLat = rad(lat2 - lat1)
  const dLng = rad(lng2 - lng1)
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

export default { isInChina, convert, distanceMeters, wgs84ToGcj02, gcj02ToWgs84 }
