# AI 智能旅游助手 - 前端

> 基于 Vue 3 + Vite 8 + Vant 4 的移动端 AI 旅游助手 H5 应用，提供智能旅行规划、AI 对话、社区游记、短视频、地图展示等功能。

## 📱 项目简介

AI 智能旅游助手是一款面向移动端用户的智能旅游规划应用，采用 5 Tab SPA 架构，核心功能包括：

- **AI 智能对话**：SSE 流式对话，Markdown 渲染，多会话管理，支持语音输入
- **智能行程规划**：7 阶段进度推送 + 逐天行程生成，支持任务取消，地图可视化
- **热门目的地**：全国热门旅游城市，城市详情和景点列表，百度地图集成
- **社区游记**：游记发布/浏览/点赞/评论/回复（抖音风格），视频详情，城市筛选，信息流
- **用户系统**：注册/登录，多账户数据隔离，关注/粉丝，收藏，订单，优惠券，邀请好友

## 🛠️ 技术栈清单

| 分类 | 技术 | 版本 |
|------|------|------|
| 框架 | Vue 3（Composition API） | 3.5.x |
| 构建工具 | Vite | 8.x |
| UI 组件库 | Vant 4（按需导入） | 4.10.x |
| 路由 | Vue Router | 4.x |
| HTTP 客户端 | Axios + 原生 Fetch（SSE） | 1.x |
| Markdown | markdown-it + highlight.js | — |
| 样式 | CSS3 / PostCSS + postcss-pxtorem | — |
| 状态管理 | reactive() 单例（无 Pinia 依赖） | — |
| 地图 | 百度地图 WebGL / Leaflet 兜底 | — |

## 🎬 功能演示

### AI 对话与行程规划

https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/public/demos/ai-chat.mp4

### 社区视频界面和社区图片分享界面展示

https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/public/demos/community.mp4

### 热门目的地选择展示以及 AI 自由选择规划演示

https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/public/demos/destinations.mp4

### 登录与注册

https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/public/demos/login.mp4

## 📝 最近更新（2026-07-27）

### GPU 性能优化
- 全局移除 `will-change` 属性（AIChatDialog、ChatView、App.vue），避免每条消息预分配 GPU 合成层导致 OOM
- 全局 `backdrop-filter: blur()` 从 20px 降至 8px，GPU 模糊卷积负载降低 60%
- 禁用所有无限动画：`tabIconBreathe`、`iconBreathe`、`cloudDrift`、`pulseGlow`
- 移除 5 个页面 20+ 个 `cloud-dot` 漂浮粒子 DOM 节点
- `transition: all` 全面替换为精确属性过渡（`opacity` / `transform` / `background`），消除全属性重算

### 页面视觉升级
- 全局页面背景：`linear-gradient(180deg, #F8F7FF → #FFFFFF)` 品牌淡紫渐变
- `#app::before` / `#app::after` 装饰光圈（淡紫径向渐变，6%-8% 透明度）
- 底部悬浮 AI 输入栏：收缩至 1/3 屏宽紧凑胶囊样式
- AI 行程规划卡片：白底圆角卡片 + 分层边距（外层 margin + 内层 padding）
- 统一卡片阴影：`0 4px 20px rgba(0,0,0,0.06)`

### SearchBar 穿透修复
- 遮罩 `position: fixed` + `z-index: 99999`，移动端触摸事件正确拦截
- 移除 `.search-row` 的 `z-index: 2`，避免 stacking context 限制 fixed 定位
- `style.css` 入口动画不再残留 `transform`，防止创建 containing block

### 社区评论系统（抖音风格）
- 评论区支持回复 + `@用户名` 紫色高亮
- 评论按根评论分组，默认显示 2 条热评 + 「展开剩下的 N 条回复」
- 评论列表抽屉（65% 屏高）+ 底部写评论按钮
- 回复输入框动态 placeholder + 回复提示条
- 分享面板：微信好友 / 朋友圈 / 复制链接 / QQ / 微博

### 短视频播放器
- 抖音风格进度条（2px 白线 + 拖拽圆点）+ 播放时间
- 全屏按钮（竖屏右下角 + CSS 横屏全屏旋转）+ 控件 3 秒自动隐藏

### 社区关注功能
- 「关注」Tab：筛选关注用户的笔记，API 拉取关注列表
- 关注/取关状态全局同步（同一用户在多条笔记中状态一致）
- 页面切换不丢失关注状态（`onMounted` + `onActivated` 刷新）

### 其他
- DiceBear 头像 API → 本地 SVG 头像生成器（`utils/avatar.js`），零外部依赖
- PostView 发动态：真实图片上传 + 3 列网格 + 字符计数
- Profile 邀请好友：底部弹出分享面板（微信/朋友圈/复制链接/QQ/微博/二维码）
- NoteDetailView / HomeView：关注状态同步 + 页面背景透明化
- LoginView：`transition: all` → 精确属性过渡
- VideoDetailView：回复逻辑修复（@用户名 + 根评论分组）
- 修复 `window.location.origin` 在 Vue 3 template 不可用的问题

## 🚀 环境启动步骤

### 前置条件

- Node.js >= 18.0.0
- npm >= 9.0.0

### 安装依赖

```bash
cd trval-h5
npm install
```

### 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:5173 查看应用。

### 构建生产版本

```bash
npm run build     # 构建
npm run preview   # 预览构建产物
```

## 📁 项目结构

```
trval-h5/
├── public/                           # 静态资源
│   ├── city-images.json              # 城市图片数据
│   └── attraction-images.json        # 景点图片数据
├── scripts/                          # 数据采集脚本
├── src/
│   ├── api/                          # API 接口封装
│   │   ├── index.js                  # 核心 API（16个API对象，fetch封装）
│   │   ├── destination.js            # 目的地 API
│   │   └── tripNew.js                # SSE 流式行程生成 API
│   ├── components/                   # 公共组件（7个）
│   │   ├── AIChatDialog.vue          # 可复用 AI 对话弹窗
│   │   ├── DragSheet.vue             # 三阶段底部抽屉
│   │   ├── EmptyState.vue            # 空状态组件
│   │   ├── SearchBar.vue             # 地点搜索栏
│   │   ├── TripCompletedState.vue    # 已完成行程展示
│   │   ├── TripGeneratingState.vue   # 行程生成进度
│   │   └── VoiceInput.vue            # "按住说话"语音输入
│   ├── stores/                       # 状态管理
│   │   └── trip.js                   # 行程状态（reactive 单例）
│   ├── router/                       # 路由配置
│   │   └── index.js                  # 5 Tab + 子路由 + 认证守卫
│   ├── utils/                        # 工具函数
│   │   ├── avatar.js                 # 本地 SVG 头像生成器
│   │   ├── auth.js                   # JWT Token 管理
│   │   ├── request.js                # Axios 封装
│   │   ├── streamRequest.js          # SSE 流式请求
│   │   ├── security.js               # XSS 防护 + 图片代理
│   │   ├── chatSession.js            # 多账户聊天会话管理
│   │   └── userAccountStorage.js     # 多账户数据隔离
│   ├── views/                        # 页面组件（30个）
│   │   ├── HomeView.vue              # 首页
│   │   ├── MessagesView.vue          # 消息 Tab
│   │   ├── CommunityView.vue         # 社区 Tab（广场 + 关注）
│   │   ├── TripsView.vue             # 行程 Tab
│   │   ├── Profile.vue               # 我的 Tab
│   │   ├── ChatView.vue              # AI 对话页
│   │   ├── LoginView.vue             # 登录/注册合一
│   │   ├── TripMapView.vue           # 全屏地图 + 抽屉行程展示
│   │   ├── AITripPlanner.vue         # 单页 AI 行程规划
│   │   ├── AITripPlannerProgress.vue # AI 规划进度页
│   │   ├── CitySelectView.vue        # 城市选择（字母分组）
│   │   ├── AttractionSelectView.vue  # 景点选择
│   │   ├── DestinationsView.vue      # 热门目的地列表
│   │   ├── DestinationDetailView.vue # 目的地详情 + 地图
│   │   ├── NoteDetailView.vue        # 游记详情 + 评论系统
│   │   ├── WriteNoteView.vue         # 写游记
│   │   ├── PostView.vue              # 发动态
│   │   ├── VideoDetailView.vue       # 短视频详情
│   │   ├── EditProfileView.vue       # 编辑资料
│   │   ├── FavoritesView.vue         # 我的收藏
│   │   ├── OrdersView.vue            # 我的订单
│   │   ├── CouponsView.vue           # 优惠券
│   │   ├── FeedbackView.vue          # 用户反馈
│   │   ├── FollowersView.vue         # 粉丝列表
│   │   ├── FollowingView.vue         # 关注列表
│   │   └── SavedPlansView.vue        # 已保存行程
│   ├── App.vue                       # 根组件（自定义5Tab导航）
│   ├── main.js                       # 入口文件
│   └── style.css                     # 全局样式设计系统
├── vite.config.js                    # Vite 配置（代理+别名+哈希）
├── postcss.config.js                 # PostCSS 配置（pxtorem）
└── package.json                      # 依赖配置
```

## 🏗️ 应用架构

```
Tab 0: 首页 (/)            — 目的地搜索、Banner、热门城市、AI行程规划、游记信息流
Tab 1: 消息 (/messages)     — AI 对话历史、通知
Tab 2: 社区 (/community)    — 广场/关注双Tab、游记信息流、城市筛选
Tab 3: 行程 (/trips)        — 行程计划、城市攻略、AI 规划入口
Tab 4: 我的 (/profile)      — 个人中心、订单、收藏、优惠券、邀请好友、设置

子路由以 slide-left 转场进入，Tab 栏可选择性隐藏
```

## 🗺️ 后续迭代规划

### 短期目标

- [x] 5 Tab SPA 架构 + 自定义底部导航
- [x] SSE 流式行程生成（7 阶段进度 + 逐天生成）
- [x] 多账户数据隔离
- [x] Markdown 渲染 + 语音输入
- [x] GPU 性能优化（blur 降级 / will-change 清除 / 无限动画禁用）
- [x] 全局品牌背景渐变 + 装饰光圈
- [x] 抖音风格评论区（@用户名 + 分组 + 展开折叠）
- [x] 短视频播放器（进度条 + 全屏按钮 + 横竖屏切换）
- [x] 社区关注功能（关注列表同步 + 关注Tab筛选）
- [x] 本地头像生成器（零外部依赖）
- [ ] 完善用户头像上传功能
- [ ] 支持深色模式

### 中期目标

- [ ] 接入 RAG 知识库整合旅游攻略
- [ ] 酒店预订对接
- [ ] 行程日历视图
- [ ] 行程分享功能

### 长期目标

- [ ] 多语言国际化
- [ ] 接入第三方支付
- [ ] 离线地图功能
- [ ] PWA 支持

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！
