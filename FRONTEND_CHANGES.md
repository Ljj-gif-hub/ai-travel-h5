# 前端变更记录 (trval-h5)

> Vue 3 + Vite 6 + Vant 4 AI 智能旅游助手 H5 前端

---

## 一、项目概览

从最小化的 Vue 3 + Vite 脚手架（3 个基础页面：Home、Chat、Profile/Planning）重构为功能完整的 AI 旅游助手移动端 SPA，包含自定义 5 Tab 底部导航、keep-alive 页面缓存、路由过渡动画、多账户数据隔离等。

### 技术栈

- **框架**: Vue 3（Composition API）
- **构建**: Vite 6
- **UI 库**: Vant 4（按需导入）
- **路由**: Vue Router 4
- **HTTP**: Axios + 原生 Fetch（SSE 流式）
- **Markdown**: markdown-it + highlight.js
- **地图**: 百度地图 / Leaflet 兜底

---

## 二、文件变更统计

| 类型 | 数量 | 说明 |
|------|------|------|
| 修改文件 | 16 | 核心框架文件重构 |
| 删除文件 | 1 | `src/api/plan.js`（已废弃） |
| 新增视图 | 25 | 完整业务页面 |
| 新增组件 | 7 | 可复用 UI 组件 |
| 新增 API 模块 | 3 | 统一请求封装 |
| 新增 Store | 1 | 行程状态管理 |
| 新增工具 | 5 | 认证/安全/会话/存储/请求 |
| **总计** | **~55 文件** | |

---

## 三、修改文件详情

### 项目配置

- 后端 [.gitignore](travel-java/.gitignore) 已配置忽略上传视频资源（`uploads/*.mp4`、`*.avi`、`*.mov`、`*.flv`），避免大文件提交到仓库

### 构建与配置

| 文件 | 变更内容 |
|------|----------|
| [.gitignore](trval-h5/.gitignore) | 添加 `.env` 忽略规则 |
| [index.html](trval-h5/index.html) | 语言改为 `zh-CN`；添加安全 meta 标签（`X-Content-Type-Options`、`referrer`）；引入 Leaflet CSS CDN 兜底；标题改为"AI智能旅游助手" |
| [package.json](trval-h5/package.json) | 新增依赖：`axios` ^1.18.1、`highlight.js` ^11.11.1、`markdown-it` ^14.3.0、`marked` ^18.0.6；devDependency: `cheerio` ^1.2.0；新增 `scrape-images` / `import-images` npm 脚本 |
| [postcss.config.js](trval-h5/postcss.config.js) | 修复 `exclude` 从正则改为函数，兼容 Windows 反斜杠路径，防止 Vant Toast/Dialog px 值被错误转换 |
| [vite.config.js](trval-h5/vite.config.js) | 添加 `@` 路径别名 → `src/`；代理超时延长至 30 分钟；新增 `/uploads` 代理；SSE 流式请求头支持；构建输出哈希配置 |

### 核心框架文件

| 文件 | 变更内容 |
|------|----------|
| [main.js](trval-h5/src/main.js) | 显式 `import 'vant/lib/index.css'` 修复命令式 API CSS 缺失；添加根字体大小计算（rem 响应式，最大宽度 500px） |
| [App.vue](trval-h5/src/App.vue) | **完全重写**：从 3 Tab Vant Tabbar → 自定义 5 Tab 底部导航。新增：玻璃态 Tab 栏（backdrop-filter blur）、活跃指示条、呼吸图标动画、GPU 加速点击缩放、路由过渡动画系统（fade / slide-left / slide-right）、导航方向检测、keep-alive 缓存（5 个 Tab 页面名称白名单）、Tab 点击防抖（300ms）、特定路由隐藏 Tab 栏 |
| [router/index.js](trval-h5/src/router/index.js) | **完全重写**（~40 行 → 282 行）：5 个 Tab 页（首页/消息/社区/行程/我的）；行程子页（地图/AI规划/进度/城市选择/景点选择）；社区子页（游记/详情/视频/写游记/发帖）；目的地子页；个人中心子页（编辑资料/关注/粉丝/订单/收藏/优惠券/反馈）；认证页（登录/注册）；旧路由重定向（`/chat`→`/messages`、`/Profile`→`/profile`、`/planning`→`/trip-map`）；路由 meta（transition、hideTabBar）；滚动位置记忆；认证守卫 |

### 样式

| 文件 | 变更内容 |
|------|----------|
| [style.css](trval-h5/src/style.css) | **完全重写**（~460 行设计系统）：CSS 自定义属性（薰衣草紫 `#8B5CF6` 主色调、渐变、阴影系统、圆角系统）；中文字体栈；容器溢出防护；卡片组件类（`.card-macaron`、`.card-glass`）；Vant 组件覆盖样式；路由过渡动画（fade、slide-left、slide-right）；18 个关键帧动画（`fadeInUp`、`slideUpCard`、`cloudDrift`、`iconBreathe`、`gradientFlow`、`elasticBounce`、`dotPulse` 等）；工具类（`.btn-tap-scale`、`.input-focus-ring`、`.h-scroll`、`.card-hover-lift`）；图片内联 SVG 占位符（绿色山水骨架屏）；图片加载闪光动画 |

### API 与工具

| 文件 | 变更内容 |
|------|----------|
| [api/plan.js](trval-h5/src/api/plan.js) | **已删除**。旧的行程 API（硬编码 Doubao/ARK API Key），功能由 `tripNew.js` 和 `api/index.js` 替代 |
| [utils/streamRequest.js](trval-h5/src/utils/streamRequest.js) | 从硬编码 API Key → 基于 Token 认证；端点前缀 `/doubao-api` → `/api`；改进错误处理；新增 `fetchPlan()`、`fetchChat()` REST 风格 API |

### 视图

| 文件 | 主要变更 |
|------|----------|
| [ChatView.vue](trval-h5/src/views/ChatView.vue) | 添加 `defineOptions({ name: 'ChatView' })`（keep-alive）；切换到 `chatApi.getChatStream()` SSE 流式；添加 markdown-it + highlight.js 渲染；移动端键盘适配；语音输入（Web Speech API）；登录门控 + 会话持久化（多账户隔离 localStorage）；消息保存（防抖写入）；首次使用引导标签；"思考中"动画；玻璃态输入栏（语音+发送按钮）；快捷追问栏；SSE 中止（Tab 失活时） |
| [HomeView.vue](trval-h5/src/views/HomeView.vue) | **大幅扩展**（~554 → ~2566 行）：添加 `defineOptions`；集成 `SearchBar`、`AIChatDialog`、`EmptyState` 组件；"更多产品"弹窗（14 项旅行服务）；输入校验（预算/天数/人数）；城市快捷标签；6 图标快捷入口（AI对话、机票、酒店、景点、美食、游记）；双行服务图标网格（每行 10 个）；快捷 Tab 标签；Banner 轮播；热门目的地卡片；活动 Banner + 城市卡片双列布局；目的地 API 加载 + 兜底种子数据；本地活动卡片；游记信息流 |
| [PlanningView.vue](trval-h5/src/views/PlanningView.vue) | 仍在代码库中但通过路由重定向不再直接访问（`/planning` → `/trip-map`） |
| [Profile.vue](trval-h5/src/views/Profile.vue) | 添加 `defineOptions({ name: 'ProfileView' })`；使用 `getToken()` 检测认证状态；从 API 和多账户存储读取用户数据 |

---

## 四、新增文件详情

### API 模块（3 个）

| 文件 | 行数 | 说明 |
|------|------|------|
| [api/index.js](trval-h5/src/api/index.js) | 222 | **核心 API 模块**。统一 `fetch()` 封装 + Bearer Token 自动注入 + 401/403/500 错误处理。导出 16 个 API 对象：`userApi`、`favoriteApi`、`couponApi`、`orderApi`、`noteApi`、`commentApi`、`uploadApi`、`postApi`、`feedbackApi`、`followApi`、`planApi`、`tripAIApi`、`sceneApi`、`chatApi`、`mapApi`、`authApi` |
| [api/destination.js](trval-h5/src/api/destination.js) | 40 | 轻量级目的地 API：`getHotDestinations()`、`getCityAttractions(city)`、`getNearbyAttractions(lat,lng)` |
| [api/tripNew.js](trval-h5/src/api/tripNew.js) | 260 | **SSE 流式行程生成 API**。核心方法：`generateTrip()` 创建任务、`generateAndStream()` 单端点 SSE（支持事件：progress-update/day-update/hotel-update/tips-update/transport-update/text-update/cost-update/generate-finish/stream-error/task-stop）、`startSSE()` 传统两阶段订阅（最多 3 次重试）、`stopTrip()` 停止生成、`searchHotels()`、`getCostBreakdown()`、`getMapMarkers()`、`getMetroStations()`、`voiceToText()` |

### 组件（7 个）

| 文件 | 行数 | 说明 |
|------|------|------|
| [AIChatDialog.vue](trval-h5/src/components/AIChatDialog.vue) | 1056 | 可复用 AI 对话弹窗。Markdown 渲染、旅行规划上下文、快捷追问、首次使用引导标签、`chatSession.js` 会话持久化、消息历史、计划保存。v-model 控制可见性 |
| [DragSheet.vue](trval-h5/src/components/DragSheet.vue) | 236 | 三阶段底部抽屉组件。触摸拖动 + 吸附点（min 20%、mid 65%、max 95%）。滚动隔离、叠加层透明度可配 |
| [EmptyState.vue](trval-h5/src/components/EmptyState.vue) | 143 | 空状态组件。旅行主题 SVG 装饰（热气球、背包、海岸线、山、云）。可配置图标/标题/描述/操作按钮 |
| [SearchBar.vue](trval-h5/src/components/SearchBar.vue) | 259 | 地点搜索栏。300ms 防抖自动补全、调用 `mapApi.getSuggestion()`、下拉面板（地点图标+名称+地址）、键盘/点击外部处理 |
| [TripCompletedState.vue](trval-h5/src/components/TripCompletedState.vue) | 196 | 已完成行程展示。标题栏、概览、操作按钮（分享/喜欢/不喜欢/保存）、路线预览卡片、天导航标签、交通卡片、Markdown 内容渲染、每日时间线 |
| [TripGeneratingState.vue](trval-h5/src/components/TripGeneratingState.vue) | 375 | 行程生成进度展示。SVG 环形进度条 + 渐变色、百分比 + 步骤名称、步骤列表（完成/活跃/等待状态指示器）、骨架屏加载卡片（3 张闪光动画）、动画标题 |
| [VoiceInput.vue](trval-h5/src/components/VoiceInput.vue) | 317 | "按住说话"语音输入。Web Speech API（SpeechRecognition）、浏览器支持检测、`zh-CN` 语言、波形条视觉反馈、不支持的浏览器降级 UI |

### Store（1 个）

| 文件 | 行数 | 说明 |
|------|------|------|
| [stores/trip.js](trval-h5/src/stores/trip.js) | 215 | **无 Pinia 依赖**，Vue 3 `reactive()` 单例模式。管理：phase（generating/completed）、progress（0-100）、currentStep、7 步进度列表、planData、costBreakdown、hotelList、mapMarkers、drawerState、taskId、用户参数。导出 `useTripStore()` composable + reset/update 函数 |

### 工具函数（5 个）

| 文件 | 行数 | 说明 |
|------|------|------|
| [utils/request.js](trval-h5/src/utils/request.js) | 35 | Axios 封装，拦截器注入 Token、响应状态码检查 |
| [utils/auth.js](trval-h5/src/utils/auth.js) | 64 | JWT Token 管理（get/set/remove），`getCurrentUsername()` 兼容旧 `userInfo` 格式迁移 |
| [utils/security.js](trval-h5/src/utils/security.js) | 96 | XSS 防护：`sanitizeHtml()`（转义 &<>"'/\`=）、`filterXss()`（移除 script/iframe/embed/object/svg 标签 + javascript: 协议 + 内联事件处理器）、`getProxyImageUrl()`（域名白名单图片代理 URL）、`validateInput()`（长度检查 + 危险模式检测） |
| [utils/chatSession.js](trval-h5/src/utils/chatSession.js) | 178 | 多账户聊天会话持久化（localStorage）。键模式：`travel_chat_sessions:{username}`。首次登录自动迁移旧全局存储。会话 CRUD、消息持久化、自动生成会话标题 |
| [utils/userAccountStorage.js](trval-h5/src/utils/userAccountStorage.js) | 214 | 多账户数据隔离系统。每个用户独立 localStorage 命名空间（`account:{username}`）。管理：userInfo、chatSessions、chatHistory、savedPlans、favorites、browsingHistory。API：`initAccountData()`、`getAccountData()`、`setAccountData()`、`getMyData()`、`setMyData()`、`clearSession()`、`deleteAccount()` |

### 视图 — 新增（25 个）

| 文件 | 行数 | 说明 |
|------|------|------|
| [LoginView.vue](trval-h5/src/views/LoginView.vue) | 1360 | **登录/注册合一页**。Tab 切换 + 滑动动画、密码可见性切换、验证码倒计时、协议弹窗、表单校验、航拍虚化背景 + 云漂移动画、山/海插画、"旅行情绪"文案。使用 `authApi` + `userAccountStorage` |
| [RegisterView.vue](trval-h5/src/views/RegisterView.vue) | 22 | 薄封装，重定向到 LoginView（`initialTab: 'register'`） |
| [HomeView.vue](trval-h5/src/views/HomeView.vue) | 2566 | （已在修改文件中详述） |
| [MessagesView.vue](trval-h5/src/views/MessagesView.vue) | 681 | 消息 Tab 页。4 类消息入口网格（订单/聊天/通知/VIP）、AI 对话历史列表（`chatSession.js` 会话切换+删除）、通知信息流、`AIChatDialog` 集成 |
| [CommunityView.vue](trval-h5/src/views/CommunityView.vue) | 1115 | 社区 Tab 页。Tab 切换（全部/关注/推荐）、12 城市选择器、游记信息流（图片+作者+点赞/评论/分享）、下拉刷新/分页、空状态、种子兜底数据 |
| [TripsView.vue](trval-h5/src/views/TripsView.vue) | 710 | 行程 Tab 页。Tab 筛选（全部/即将开始/进行中/已完成/草稿）、行程卡片（状态推断）、城市攻略横向滚动、API 加载行程列表、浮动 AI 聊天按钮、`AIChatDialog` 集成 |
| [TripMapView.vue](trval-h5/src/views/TripMapView.vue) | 767 | 全屏百度地图 + DragSheet 叠加层。集成行程 store、抽屉内显示 `TripGeneratingState` / `TripCompletedState`、顶栏返回/分享按钮、地图叠加层透明度与抽屉位置联动 |
| [AITripPlanner.vue](trval-h5/src/views/AITripPlanner.vue) | 335 | 单页 AI 行程规划。3 种状态：表单（出发地/目的地/天数/月份/偏好，含酒店等级/舱位/节奏/行程/同伴/风格）、生成中（进度展示）、完成（结果展示）。热门目的地快捷标签 |
| [AITripPlannerProgress.vue](trval-h5/src/views/AITripPlannerProgress.vue) | 318 | AI 规划进度页（独立路由），使用 trip store 展示生成状态 |
| [CitySelectView.vue](trval-h5/src/views/CitySelectView.vue) | 580 | 城市选择页。字母分组、搜索、热门城市快捷入口 |
| [AttractionSelectView.vue](trval-h5/src/views/AttractionSelectView.vue) | 408 | 景点选择页。搜索、分类筛选、多选（用于构建行程） |
| [DestinationsView.vue](trval-h5/src/views/DestinationsView.vue) | 262 | 热门目的地网格/列表，API 加载 + 图片卡片 |
| [DestinationDetailView.vue](trval-h5/src/views/DestinationDetailView.vue) | 499 | 目的地详情。城市信息、景点列表、周边景点、地图集成 |
| [NoteDetailView.vue](trval-h5/src/views/NoteDetailView.vue) | 814 | 游记详情。图片、评论区、点赞/评论、作者信息 |
| [NotesView.vue](trval-h5/src/views/NotesView.vue) | 179 | 游记列表页 |
| [WriteNoteView.vue](trval-h5/src/views/WriteNoteView.vue) | 446 | 写游记。图片上传、文本编辑、发布 |
| [PostView.vue](trval-h5/src/views/PostView.vue) | 185 | 社区帖子创建/编辑 |
| [VideoDetailView.vue](trval-h5/src/views/VideoDetailView.vue) | 454 | 视频内容详情页 |
| [EditProfileView.vue](trval-h5/src/views/EditProfileView.vue) | 213 | 个人资料编辑。头像、昵称、简介等字段 |
| [FavoritesView.vue](trval-h5/src/views/FavoritesView.vue) | 191 | 用户收藏列表 |
| [OrdersView.vue](trval-h5/src/views/OrdersView.vue) | 346 | 用户订单/行程页。状态 Tab + 订单卡片 |
| [CouponsView.vue](trval-h5/src/views/CouponsView.vue) | 212 | 优惠券列表。状态 Tab + 兑换 |
| [FeedbackView.vue](trval-h5/src/views/FeedbackView.vue) | 216 | 用户反馈提交表单 + 历史 |
| [FollowersView.vue](trval-h5/src/views/FollowersView.vue) | 163 | 粉丝列表（关注/取关操作） |
| [FollowingView.vue](trval-h5/src/views/FollowingView.vue) | 150 | 关注列表（取关操作） |
| [SavedPlansView.vue](trval-h5/src/views/SavedPlansView.vue) | 284 | 已保存行程列表，重定向到行程页 |

---

## 五、应用架构

```
Tab 0: 首页 (/)            — 目的地搜索、Banner、热门城市、游记信息流
Tab 1: 消息 (/messages)     — AI 对话历史、通知
Tab 2: 社区 (/community)    — 游记信息流、城市筛选
Tab 3: 行程 (/trips)        — 行程计划、城市攻略、AI 规划入口
Tab 4: 我的 (/profile)      — 个人中心、订单、收藏、优惠券、设置

子路由以 slide-left 转场进入，Tab 栏可选择性隐藏
```

### 数据流

```
API 调用 → api/index.js（统一 fetch + Token）→ 后端
         → api/tripNew.js（SSE 流式）→ 后端
         → api/destination.js（轻量目的地）→ 后端

认证 Token → utils/auth.js（localStorage 管理）
多账户隔离 → utils/userAccountStorage.js（账户数据）
           → utils/chatSession.js（聊天会话）

行程状态 → stores/trip.js（reactive 单例，跨组件共享）
```

### 设计系统

- **主色调**：薰衣草紫 `#8B5CF6`
- **风格**：玻璃态（backdrop-filter blur）、渐变背景
- **动画**：18 个 CSS 关键帧动画 + 路由过渡动画
- **响应式**：rem 基准 + 最大宽度 500px 容器
- **安全**：XSS 过滤、输入校验、图片代理白名单
