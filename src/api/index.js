import { getToken, removeToken } from '../utils/auth';
import { clearSession } from '../utils/userAccountStorage';

const BASE_URL = '/api';

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
    // 避免在登录页循环跳转
    if (!window.location.pathname.includes('/login')) {
      localStorage.setItem('redirectUrl', window.location.pathname + window.location.search);
      window.location.href = '/login';
    }
    throw new Error('登录已过期，请重新登录');
  }

  // HTTP 403 → 权限不足
  if (response.status === 403) {
    throw new Error('权限不足，无法执行此操作');
  }

  // HTTP 500+ → 服务器错误
  if (response.status >= 500) {
    throw new Error('服务器繁忙，请稍后重试');
  }

  // 尝试解析JSON
  let data;
  try {
    data = await response.json();
  } catch (e) {
    if (!response.ok) {
      throw new Error(`请求失败 (${response.status})`);
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

export const noteApi = {
  /** 【新增】社区发现页：获取所有用户已发布的游记 */
  getAllNotes: () => request('/notes'),
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
    const response = await fetch('/api/upload', {
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
  generatePlan: (data) => request('/travel/plan/structured', { method: 'POST', body: JSON.stringify(data) }),
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
};

export const authApi = {
  login: (data) => request('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  register: (data) => request('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
};
