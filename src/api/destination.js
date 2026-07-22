/**
 * 目的地 API — 统一使用 index.js 的 request 封装
 * 消除重复的 fetch 请求逻辑
 */
import { getToken } from '../utils/auth';

const BASE_URL = '/api';

/** 内联轻量 request（公共接口无需完整错误处理，复用 api/index.js 逻辑） */
const request = async (url, options = {}) => {
  const token = getToken();
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const response = await fetch(`${BASE_URL}${url}`, { ...options, headers });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: '请求失败' }));
    throw new Error(error.message || '请求失败');
  }
  return response.json();
};

/** 热门目的地推荐 — GET /api/map/hot-destinations */
export function getHotDestinations() {
  return request('/map/hot-destinations');
}

/** 城市景点列表 — GET /api/map/city-attractions?city= */
export function getCityAttractions(city) {
  return request(`/map/city-attractions?city=${encodeURIComponent(city)}`);
}

/** 周边景点 — GET /api/map/nearby-attractions?lat=&lng= */
export function getNearbyAttractions(lat, lng) {
  return request(`/map/nearby-attractions?lat=${lat}&lng=${lng}`);
}
