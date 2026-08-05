import { getToken, removeToken } from '../utils/auth';
import { clearSession } from '../utils/userAccountStorage';

const BASE_URL = import.meta.env.VITE_API_BASE || '/api';

/**
 * 统一请求封装 — 自动携带Token、统一错误处理、标准响应解析
 * 后端返回格式：{ code: 0 (成功) | -1 (失败), message: string, data: any }
 */
const request = async (url, options = {}) => {
  const token = getToken();

  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${url}`, {
    ...options,
    headers,
  });

  // HTTP 401 → Token过期/无效 → 清除登录态并跳转
  if (response.status === 401) {
    removeToken();
    // 【多账号隔离】401仅清空会话缓存，保留账号持久化数据
    clearSession();
    // hash 路由下用 hash 跳转登录页，避免全页跳 /login 落到首页
    if (!window.location.hash.includes('/login')) {
      localStorage.setItem('redirectUrl', window.location.hash || '#/');
      window.location.hash = '#/login';
    }
    const err = new Error('登录已过期，请重新登录');
    err.status = response.status;
    err.response = { status: response.status };
    throw err;
  }

  // HTTP 403 → 权限不足
  if (response.status === 403) {
    const err = new Error('权限不足，无法执行此操作');
    err.status = response.status;
    err.response = { status: response.status };
    throw err;
  }

  // HTTP 500+ → 服务器错误
  if (response.status >= 500) {
    const err = new Error('服务器繁忙，请稍后重试');
    err.status = response.status;
    err.response = { status: response.status };
    throw err;
  }

  // 尝试解析JSON
  let data;
  try {
    data = await response.json();
  } catch (e) {
    if (!response.ok) {
      const err = new Error(`请求失败 (${response.status})`);
      err.status = response.status;
      err.response = { status: response.status };
      throw err;
    }
    return null;
  }

  // 即使HTTP 200，后端也可能返回业务错误 code: -1
  // 调用方自行判断 response.code === 0
  return data;
};

export const userApi = {
  getProfile: () => request('/user/profile'),
  updateProfile: (data) => request('/user/profile', { method: 'PUT', body: JSON.stringify(data) }),
  logout: () => request('/user/logout', { method: 'POST' }),
};

export const favoriteApi = {
  getFavorites: (type) => {
    const url = type ? `/favorites?type=${type}` : '/favorites';
    return request(url);
  },
  addFavorite: (data) => request('/favorites', { method: 'POST', body: JSON.stringify(data) }),
  deleteFavorite: (id) => request(`/favorites/${id}`, { method: 'DELETE' }),
  getFavoriteCount: (type) => {
    const url = type ? `/favorites/count?type=${type}` : '/favorites/count';
    return request(url);
  },
};

export const couponApi = {
  getCoupons: (status) => {
    const url = status ? `/coupons?status=${status}` : '/coupons';
    return request(url);
  },
  getCouponCount: (status) => {
    const url = status ? `/coupons/count?status=${status}` : '/coupons/count';
    return request(url);
  },
  useCoupon: (id, orderId) => request(`/coupons/use/${id}`, { 
    method: 'POST', 
    body: JSON.stringify({ orderId }) 
  }),
};

export const orderApi = {
  getOrders: (type) => {
    const url = type ? `/orders?type=${type}` : '/orders';
    return request(url);
  },
  createOrder: (data) => request('/orders', { method: 'POST', body: JSON.stringify(data) }),
  updateOrderStatus: (id, status) => request(`/orders/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status })
  }),
  cancelOrder: (id) => request(`/orders/${id}/cancel`, { method: 'POST' }),
  getOrderCount: (status) => {
    const url = status ? `/orders/count?status=${status}` : '/orders/count';
    return request(url);
  },
};

export const paymentApi = {
  /** 发起支付 → { orderNo, payUrl, providerTradeNo, channel } */
  createPayment: (orderId) => request('/payment/create', { method: 'POST', body: JSON.stringify({ orderId }) }),
  /** 模拟渠道确认支付（mock 专用，orderNo 完成支付） */
  mockPay: (orderNo) => request(`/payment/mock-pay?orderNo=${encodeURIComponent(orderNo)}`),
  /** 支付渠道回调（真实渠道由渠道服务器回调，前端一般不用） */
  notify: (params) => request('/payment/notify', { method: 'POST', body: JSON.stringify(params) }),
};

export const hotelApi = {
  searchHotels: (city, params = {}) => {
    const qs = new URLSearchParams({ city, ...params }).toString();
    return request(`/hotel/search?${qs}`);
  },
  getHotel: (id) => request(`/hotel/${id}`),
  /** 预订酒店 → 创建 hotel 订单 {hotelId, checkIn, checkOut, rooms} */
  bookHotel: (data) => request('/hotel/book', { method: 'POST', body: JSON.stringify(data) }),
};

export const flightApi = {
  /** 航班搜索（mock/real 供应方）— GET /api/flight/search */
  searchFlights: (fromCity, toCity, date) =>
    request(`/flight/search?fromCity=${encodeURIComponent(fromCity)}&toCity=${encodeURIComponent(toCity)}&date=${date}`),
  /** 机票下单（需登录）— POST /api/flight/book */
  bookFlight: (data) => request('/flight/book', { method: 'POST', body: JSON.stringify(data) }),
};

export const noteApi = {
  /** 社区发现页：分页获取所有用户已发布的游记 */
  getAllNotes: (page = 1, size = 10) => request(`/notes?page=${page}&size=${size}`),
  getMyNotes: () => request('/notes/my'),
  getNoteDetail: (id) => request(`/notes/${id}`),
  createNote: (data) => request('/notes', { method: 'POST', body: JSON.stringify(data) }),
  updateNote: (id, data) => request(`/notes/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteNote: (id) => request(`/notes/${id}`, { method: 'DELETE' }),
  likeNote: (id) => request(`/notes/${id}/like`, { method: 'POST' }),
  getNoteCount: () => request('/notes/count'),
};

export const commentApi = {
  getComments: (noteId) => request(`/notes/${noteId}/comments`),
  /** 获取某条评论的所有回复 */
  getReplies: (commentId) => request(`/comments/${commentId}/replies`),
  addComment: (noteId, content, image, video, parentId) => request(`/notes/${noteId}/comments`, {
    method: 'POST',
    body: JSON.stringify({ content, image, video, parentId: parentId ? String(parentId) : undefined }),
  }),
  deleteComment: (id) => request(`/comments/${id}`, { method: 'DELETE' }),
  /** 点赞评论 */
  likeComment: (id) => request(`/comments/${id}/like`, { method: 'POST' }),
};

export const uploadApi = {
  uploadFile: async (file) => {
    const token = getToken();
    const formData = new FormData();
    formData.append('file', file);
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const response = await fetch(`${BASE_URL}/upload`, {
      method: 'POST',
      headers,
      body: formData,
    });
    return response.json();
  },
};

export const postApi = {
  getPosts: () => request('/posts'),
  createPost: (data) => request('/posts', { method: 'POST', body: JSON.stringify(data) }),
  deletePost: (id) => request(`/posts/${id}`, { method: 'DELETE' }),
  likePost: (id) => request(`/posts/${id}/like`, { method: 'POST' }),
};

export const feedbackApi = {
  getFeedbacks: () => request('/feedback'),
  createFeedback: (data) => request('/feedback', { method: 'POST', body: JSON.stringify(data) }),
};

export const followApi = {
  getFollowing: () => request('/user/following'),
  getFollowers: () => request('/user/followers'),
  follow: (id) => request(`/user/follow/${id}`, { method: 'POST' }),
  unfollow: (id) => request(`/user/unfollow/${id}`, { method: 'POST' }),
  getFollowingCount: () => request('/user/following/count'),
  getFollowersCount: () => request('/user/followers/count'),
};

export const planApi = {
  getSavedPlans: () => request('/travel/plan/saved'),
  getPlanById: (id) => request(`/travel/plan/saved/${id}`),
  savePlan: (data) => request('/travel/plan/save', { method: 'POST', body: JSON.stringify(data) }),
  deletePlan: (id) => request(`/travel/plan/saved/${id}`, { method: 'DELETE' }),
  generatePlan: (data, options = {}) => request('/travel/plan/structured', { method: 'POST', body: JSON.stringify(data), ...options }),
};

// 行程专属 AI 接口
export const tripAIApi = {
  generateTrip: (data) => request('/trip/ai/generateTrip', { method: 'POST', body: JSON.stringify(data) }),
  optimizeRoute: (data) => request('/trip/ai/optimizeRoute', { method: 'POST', body: JSON.stringify(data) }),
  tripChat: (data) => request('/trip/ai/chat', { method: 'POST', body: JSON.stringify(data) }),
  generateRemark: (data) => request('/trip/ai/generateRemark', { method: 'POST', body: JSON.stringify(data) }),
  travelInspiration: (data) => request('/trip/ai/travelInspiration', { method: 'POST', body: JSON.stringify(data) }),
  saveToPlan: (data) => request('/trip/ai/saveToPlan', { method: 'POST', body: JSON.stringify(data) }),
};

export const sceneApi = {
  getSceneImage: (scenicName, scenicDesc) => {
    const params = new URLSearchParams();
    params.append('scenicName', scenicName);
    if (scenicDesc) {
      params.append('scenicDesc', scenicDesc);
    }
    return request(`/scene/image?${params.toString()}`);
  },
};

export const chatApi = {
  getChatStream: (messages) => {
    return fetch(`${BASE_URL}/travel/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(getToken() ? { 'Authorization': `Bearer ${getToken()}` } : {}),
      },
      body: JSON.stringify(messages),
    });
  },
};

export const mapApi = {
  getSuggestion: (keyword) => request(`/map/suggestion?keyword=${encodeURIComponent(keyword)}`),
  geocode: (address) => request(`/map/geocode?address=${encodeURIComponent(address)}`),
};

export const recommendApi = {
  /** 个性化/热门目的地推荐 — GET /api/recommend/destinations */
  getRecommendDestinations: (limit = 10) => request(`/recommend/destinations?limit=${limit}`),
  /** 通用推荐（收藏/游记驱动）— GET /api/recommend/items */
  getRecommendItems: (params = {}) => {
    const qs = new URLSearchParams(params).toString();
    return request(`/recommend/items${qs ? `?${qs}` : ''}`);
  },
};

export const shareApi = {
  /** 创建行程分享链接（需登录）→ { token, shareUrl, destination } */
  createShare: (planId) => request('/share', { method: 'POST', body: JSON.stringify({ planId }) }),
  /** 公开读取分享行程（免登录，只读快照） */
  getSharedPlan: (token) => request(`/share/${encodeURIComponent(token)}`),
};

export const authApi = {
  login: (data) => request('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  register: (data) => request('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  socialLogin: (platform, code, redirectUri) =>
    request('/auth/social-login', { method: 'POST', body: JSON.stringify({ platform, code, redirectUri }) }),
};
