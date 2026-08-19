import { getToken, setToken, getRefreshToken, setRefreshToken, removeRefreshToken } from '../utils/auth';
import { clearSession } from '../utils/userAccountStorage';

const BASE_URL = import.meta.env.VITE_API_BASE || '/api';

/* ==================== 重试 / 去重 / 刷新 常量 ==================== */
// GET 网络错误与 5xx 重试：最多 2 次，退避 300ms / 900ms（非 GET 不重试，保证幂等）
const RETRY_DELAYS = [300, 900];
const MAX_GET_RETRIES = RETRY_DELAYS.length;
// GET in-flight 去重窗口：并发请求共享同一 promise，settle 后 200ms 内复用，之后允许重新发起
const DEDUP_TTL = 200;
// 登录/刷新等自身请求不触发 401 刷新流程（避免死循环）
const AUTH_EXEMPT_URLS = ['/auth/login', '/auth/register', '/auth/social-login', '/auth/refresh'];

const inflight = new Map();
let refreshPromise = null; // 401 单飞刷新：并发 401 共享同一个 refresh promise

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const isAbortError = (e) => e?.name === 'AbortError';
const isTimeoutError = (e) => !!e?.timeout;
// fetch 网络层失败（断网/DNS/连接被拒）在浏览器中抛 TypeError
const isNetworkError = (e) => e instanceof TypeError;

/** 401 兜底：清会话 + hash 跳登录页（保留 redirectUrl 供登录后回跳） */
function redirectToLogin() {
  removeRefreshToken();
  // 【多账号隔离】仅清空会话缓存，保留账号持久化数据
  clearSession();
  if (typeof window !== 'undefined' && !window.location.hash.includes('/login')) {
    localStorage.setItem('redirectUrl', window.location.hash || '#/');
    window.location.hash = '#/login';
  }
}

/**
 * 单飞刷新 Token：POST /auth/refresh {refreshToken} → {token, refreshToken}（旋转刷新）。
 * 并发请求排队共享同一个 promise；返回 true = 刷新成功（新 token 已入库）。
 */
function refreshAuthToken() {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const rt = getRefreshToken();
      if (!rt) return false;
      try {
        const res = await fetch(`${BASE_URL}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: rt }),
        });
        if (res.status === 401) return false; // 刷新过期 → 需重新登录
        const data = await res.json().catch(() => null);
        if (data?.code === 0 && data.data?.token) {
          setToken(data.data.token);
          if (data.data.refreshToken) setRefreshToken(data.data.refreshToken);
          return true;
        }
        return false;
      } catch {
        return false;
      }
    })().finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}

/** 单次请求（含 Token 注入、超时控制、外部 signal 转发、HTTP 状态 → 错误映射） */
async function fetchOnce(url, options) {
  const token = getToken();

  // BUGID FEAT-13 修复：FormData（上传）不预置 Content-Type，由浏览器自动带 multipart boundary，
  // 否则 multipart 表单因缺 boundary 解析失败
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
  const headers = {
    ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
    ...options.headers,
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  // 【超时控制】默认 30s，调用方可传 options.timeout 覆盖（如行程生成传 180000）。
  // 与外部 signal 兼容：内部 AbortController + 转发外部 abort，超时统一抛「请求超时」。
  const externalSignal = options.signal;
  const timeoutMs = typeof options.timeout === 'number' ? options.timeout : 30000;
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);
  const forwardAbort = () => controller.abort();
  if (externalSignal) {
    if (externalSignal.aborted) { timedOut = false; controller.abort(); }
    else externalSignal.addEventListener('abort', forwardAbort);
  }

  // 支持 options.params 对象 → 追加为 query string（与去重 key 对齐）
  let fullUrl = `${BASE_URL}${url}`;
  if (options.params && typeof options.params === 'object') {
    const qs = new URLSearchParams(options.params).toString();
    if (qs) fullUrl += (fullUrl.includes('?') ? '&' : '?') + qs;
  }

  let response;
  try {
    response = await fetch(fullUrl, {
      ...options,
      signal: controller.signal,
      headers,
    });
  } catch (e) {
    // 内部超时触发 → 统一超时错误；外部主动取消 → 原样抛 AbortError
    if (e?.name === 'AbortError' && timedOut) {
      const err = new Error('请求超时');
      err.timeout = true;
      throw err;
    }
    throw e;
  } finally {
    clearTimeout(timer);
    if (externalSignal) externalSignal.removeEventListener('abort', forwardAbort);
  }

  // HTTP 401 → Token过期/无效 → 交由外层 401 刷新流程处理
  // （客户端前置过期检测见 utils/auth.isTokenExpired，路由守卫处已接入）
  if (response.status === 401) {
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

  // BUGID FEAT-13 修复：413 请求体超限（上传超大文件），服务器常返回 HTML 错误页，
  // 不能按 JSON 解析（否则 response.json() 抛 Unexpected token），这里给出可读错误
  if (response.status === 413) {
    const err = new Error('文件过大，上传失败');
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
}

/** 带重试 + 401 刷新的请求主体 */
async function doRequest(url, options, method) {
  const maxAttempts = method === 'GET' ? 1 + MAX_GET_RETRIES : 1;
  let attempt = 0;
  let refreshedOnce = false; // 401 刷新后仅重放原请求一次

  for (;;) {
    attempt += 1;
    try {
      return await fetchOnce(url, options);
    } catch (e) {
      // 401 → 单飞刷新拿新 token 重试原请求一次；刷新失败/二次 401 → 清会话跳登录
      if (e?.status === 401) {
        if (!AUTH_EXEMPT_URLS.includes(url) && !refreshedOnce) {
          refreshedOnce = true;
          const ok = await refreshAuthToken();
          if (ok) continue; // 刷新成功，用新 token 重放原请求
        }
        if (!AUTH_EXEMPT_URLS.includes(url)) redirectToLogin();
        throw e;
      }

      // 重试仅限 GET：网络错误 / 5xx（非 AbortError、非超时；4xx 不重试）
      const retryable = method === 'GET'
        && !isAbortError(e)
        && !isTimeoutError(e)
        && (isNetworkError(e) || (typeof e?.status === 'number' && e.status >= 500));
      if (retryable && attempt < maxAttempts) {
        await sleep(RETRY_DELAYS[attempt - 1]);
        continue;
      }
      throw e;
    }
  }
}

/**
 * 统一请求封装 — 自动携带Token、统一错误处理、标准响应解析
 * 后端返回格式：{ code: 0 (成功) | -1 (失败), message: string, data: any }
 *
 * 增强能力（审查报告"可补充新功能"）：
 * 1. GET 网络错误/5xx 自动重试（最多 2 次，退避 300/900ms；非 GET 不重试）
 * 2. 401 单飞刷新 refreshToken + 原请求重放一次
 * 3. GET in-flight 去重（同 method+url+params 且无 signal 的并发共享 promise，TTL 200ms）
 */
export const request = async (url, options = {}) => {
  const method = (options.method || 'GET').toUpperCase();

  // 【请求去重】GET 且无外部 signal 时参与 in-flight 去重
  // （带 signal 的请求无法安全共享取消，不参与）
  if (method === 'GET' && !options.signal) {
    const paramsKey = options.params !== undefined ? JSON.stringify(options.params) : '';
    const key = `${method} ${url} ${paramsKey}`;
    const now = Date.now();
    const hit = inflight.get(key);
    if (hit && hit.expires > now) return hit.promise;

    const p = doRequest(url, options, method);
    const entry = { promise: p, expires: now + DEDUP_TTL };
    inflight.set(key, entry);
    p.finally(() => {
      // settle 后仍保留 200ms 供晚到的并发方复用，之后删除允许重新发起
      if (inflight.get(key) === entry) {
        setTimeout(() => { if (inflight.get(key) === entry) inflight.delete(key) }, DEDUP_TTL);
      }
    }).catch(() => {});
    return p;
  }

  return doRequest(url, options, method);
};

export const userApi = {
  getProfile: () => request('/user/profile'),
  updateProfile: (data) => request('/user/profile', { method: 'PUT', body: JSON.stringify(data) }),
  /** 附带 refreshToken 退出，后端一并撤销，退出后旧 refreshToken 彻底失效 */
  logout: () => request('/user/logout', { method: 'POST', body: JSON.stringify({ refreshToken: getRefreshToken() }) }),
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
  /** 社区发现页：分页获取所有用户已发布的游记（options 可传 signal/timeout 等） */
  getAllNotes: (page = 1, size = 10, options = {}) => request(`/notes?page=${page}&size=${size}`, options),
  getMyNotes: (options = {}) => request('/notes/my', options),
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
  // BUGID FEAT-13 修复：改用统一 request 封装——自动注入 Token、401 单飞刷新重放、
  // HTTP 错误映射（含 413 可读错误），不再裸 fetch 绕过（裸 fetch 不查 response.ok、
  // 无刷新重放、413 HTML 页 response.json() 会抛 Unexpected token）
  uploadFile: async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const res = await request('/upload', { method: 'POST', body: formData });
    return res ?? { code: -1, message: '上传失败' };
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
  /**
   * SSE 流式对话（无超时：流式长连接由调用方 AbortController 控制）
   * @param {Array} messages 对话消息数组
   * @param {AbortSignal} [signal] 取消信号 — 组件卸载/切走时 abort 真正断开连接
   */
  getChatStream: (messages, signal) => {
    return fetch(`${BASE_URL}/travel/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(getToken() ? { 'Authorization': `Bearer ${getToken()}` } : {}),
      },
      body: JSON.stringify(messages),
      ...(signal ? { signal } : {}),
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
  /** 旋转刷新：POST /auth/refresh {refreshToken} → {token, refreshToken}（401 = 刷新过期）。
   *  正常路径由 request() 内部单飞调用，此出口供手动刷新场景使用。 */
  refresh: (refreshToken) => request('/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken }) }),
};

/* ==================== 新功能：天气 / 行程模板 / 举报 / 退款 / 发票 / 收藏夹 ==================== */

/** 天气（匿名可访问）— GET /api/weather/{city} → { city, reportTime, weather, temperature, windDirection, windPower, humidity, forecast: [...] } */
export const weatherApi = {
  getWeather: (city) => request(`/weather/${encodeURIComponent(city)}`),
};

/** 行程模板市场 — GET /api/template/market（公开分页） */
export const templateApi = {
  getMarket: (params = {}) => request('/template/market', { params }),
  getTemplate: (id) => request(`/template/${id}`),
  /** 实例化为自己的行程（需登录）→ { planId, templateId, destination } */
  instantiate: (id) => request(`/template/${id}/instantiate`, { method: 'POST' }),
};

/** 举报（需登录）— POST /api/report { targetType: note|post|comment, targetId, reason } */
export const reportApi = {
  report: (data) => request('/report', { method: 'POST', body: JSON.stringify(data) }),
};

/** 退款（需登录）— POST /api/order/{orderId}/refund { reason }；GET /api/order/refunds */
export const refundApi = {
  requestRefund: (orderId, reason) =>
    request(`/order/${orderId}/refund`, { method: 'POST', body: JSON.stringify({ reason }) }),
  getMyRefunds: () => request('/order/refunds'),
};

/** 发票（需登录，一单一票）— POST /api/order/{orderId}/invoice { title, taxNo, type }；GET /api/order/invoices */
export const invoiceApi = {
  issueInvoice: (orderId, data) =>
    request(`/order/${orderId}/invoice`, { method: 'POST', body: JSON.stringify(data) }),
  getMyInvoices: () => request('/order/invoices'),
};

/** 游记收藏夹 — /api/collection 系列 */
export const collectionApi = {
  /** 创建 { name, description, isPublic } */
  create: (data) => request('/collection', { method: 'POST', body: JSON.stringify(data) }),
  /** 编辑（本人） */
  update: (id, data) => request(`/collection/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  /** 删除（本人） */
  remove: (id) => request(`/collection/${id}`, { method: 'DELETE' }),
  /** 添加笔记（去重）body { noteId } → { added, count } */
  addNote: (id, noteId) => request(`/collection/${id}/notes`, { method: 'POST', body: JSON.stringify({ noteId }) }),
  /** 移除笔记 → { removed, count } */
  removeNote: (id, noteId) => request(`/collection/${id}/notes/${noteId}`, { method: 'DELETE' }),
  /** 我的收藏夹（含 noteCount） */
  getMine: () => request('/collection/mine'),
  /** 公开收藏夹（keyword/page/size） */
  getPublic: (params = {}) => request('/collection/public', { params }),
  /** 详情（附笔记摘要；私有仅本人可见） */
  getDetail: (id) => request(`/collection/${id}`),
};
