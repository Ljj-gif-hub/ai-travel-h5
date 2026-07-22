# AI 智能旅游助手 - 前端

> 基于 Vue 3 + Vite 6 + Vant 4 的移动端 AI 旅游助手 H5 应用，提供智能旅行规划、AI 对话、社区游记、地图展示等功能。

## 📱 项目简介

AI 智能旅游助手是一款面向移动端用户的智能旅游规划应用，采用 5 Tab SPA 架构，核心功能包括：

- **AI 智能对话**：SSE 流式对话，Markdown 渲染，多会话管理，支持语音输入
- **智能行程规划**：7 阶段进度推送 + 逐天行程生成，支持任务取消，地图可视化
- **热门目的地**：全国热门旅游城市，城市详情和景点列表，百度地图集成
- **社区游记**：游记发布/浏览/点赞/评论，城市筛选，信息流
- **用户系统**：注册/登录，多账户数据隔离，关注/粉丝，收藏，订单，优惠券

## 🛠️ 技术栈清单

| 分类 | 技术 | 版本 |
|------|------|------|
| 框架 | Vue 3（Composition API） | 3.x |
| 构建工具 | Vite | 6.x |
| UI 组件库 | Vant 4（按需导入） | 4.x |
| 路由 | Vue Router | 4.x |
| HTTP 客户端 | Axios + 原生 Fetch（SSE） | 1.x |
| Markdown | markdown-it + highlight.js | — |
| 样式 | CSS3 / PostCSS + postcss-pxtorem | — |
| 状态管理 | reactive() 单例（无 Pinia 依赖） | — |
| 地图 | 百度地图 WebGL / Leaflet 兜底 | — |

## 🎬 功能演示

### AI 对话与行程规划

https://github.com/user-attachments/assets/a02487bb-390b-4563-9174-b71a59b8a97b

### 社区视频界面和社区图片分享界面展示

https://github.com/user-attachments/assets/7dd8b4ef-7253-46e8-aac6-c146825331e5

### 热门目的地选择展示以及 AI 自由选择规划演示

https://github.com/user-attachments/assets/aa18def1-3a37-4e70-9962-eb7c6142699c

### 登录与注册

https://github.com/user-attachments/assets/da9b5800-e74a-46d4-be4d-cd6127fe0654

## 🚀 环境启动步骤

### 前置条件

- Node.js >= 18.0.0
- npm >= 9.0.0

### 安装依赖

```bash
cd trval-h5
npm install
```

### 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 文件，填写百度地图 AK
# VITE_BAIDU_MAP_AK=your_baidu_map_ak_here
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
│   │   ├── AIChatDialog.vue          # 可复用 AI 对话弹窗（1056行）
│   │   ├── DragSheet.vue             # 三阶段底部抽屉
│   │   ├── EmptyState.vue            # 空状态组件
│   │   ├── SearchBar.vue             # 地点搜索栏（防抖自动补全）
│   │   ├── TripCompletedState.vue    # 已完成行程展示
│   │   ├── TripGeneratingState.vue   # 行程生成进度（环形进度条）
│   │   └── VoiceInput.vue            # "按住说话"语音输入
│   ├── stores/                       # 状态管理
│   │   └── trip.js                   # 行程状态（reactive 单例）
│   ├── router/                       # 路由配置
│   │   └── index.js                  # 5 Tab + 子路由 + 认证守卫
│   ├── utils/                        # 工具函数
│   │   ├── auth.js                   # JWT Token 管理
│   │   ├── request.js                # Axios 封装
│   │   ├── streamRequest.js          # SSE 流式请求
│   │   ├── security.js               # XSS 防护 + 图片代理
│   │   ├── chatSession.js            # 多账户聊天会话管理
│   │   └── userAccountStorage.js     # 多账户数据隔离
│   ├── views/                        # 页面组件（30个）
│   │   ├── HomeView.vue              # 首页（2566行）
│   │   ├── MessagesView.vue          # 消息 Tab
│   │   ├── CommunityView.vue         # 社区 Tab
│   │   ├── TripsView.vue             # 行程 Tab
│   │   ├── Profile.vue               # 我的 Tab
│   │   ├── ChatView.vue              # AI 对话页
│   │   ├── PlanningView.vue          # 行程规划页（重定向至 TripMapView）
│   │   ├── LoginView.vue             # 登录/注册合一（1360行）
│   │   ├── RegisterView.vue          # 注册入口（重定向至 LoginView）
│   │   ├── TripMapView.vue           # 全屏地图 + 抽屉行程展示
│   │   ├── AITripPlanner.vue         # 单页 AI 行程规划
│   │   ├── AITripPlannerProgress.vue # AI 规划进度页
│   │   ├── CitySelectView.vue        # 城市选择（字母分组）
│   │   ├── AttractionSelectView.vue  # 景点选择
│   │   ├── DestinationsView.vue      # 热门目的地列表
│   │   ├── DestinationDetailView.vue # 目的地详情 + 地图
│   │   ├── NotesView.vue             # 游记列表
│   │   ├── NoteDetailView.vue        # 游记详情 + 评论
│   │   ├── WriteNoteView.vue         # 写游记
│   │   ├── PostView.vue              # 发帖
│   │   ├── VideoDetailView.vue       # 视频详情
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
│   └── style.css                     # 全局样式（460行设计系统）
├── .env.example                      # 环境变量模板
├── vite.config.js                    # Vite 配置（代理+别名+哈希）
├── postcss.config.js                 # PostCSS 配置（pxtorem）
└── package.json                      # 依赖配置
```

## 🏗️ 应用架构

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
API 调用 → api/index.js（统一 fetch + Token）→ 后端 :3200
         → api/tripNew.js（SSE 流式）→ 后端 :3200
         → api/destination.js（轻量目的地）→ 后端 :3200

认证 Token → utils/auth.js（localStorage）
多账户隔离 → utils/userAccountStorage.js（localStorage 命名空间）
聊天会话   → utils/chatSession.js（多账户会话持久化）
行程状态   → stores/trip.js（跨组件 reactive 单例）
```

### 设计系统

- **主色调**：薰衣草紫 `#8B5CF6`
- **风格**：玻璃态（backdrop-filter blur）、渐变背景
- **动画**：18 个 CSS 关键帧 + 路由过渡动画（fade/slide-left/slide-right）
- **响应式**：rem 基准 + 最大宽度 500px
- **安全**：XSS 过滤（`sanitizeHtml`/`filterXss`）、图片代理白名单、输入校验

## 🔧 配置说明

### Vite 代理配置

前端通过 Vite 代理转发 API 请求到后端（超时 30 分钟，适合长时间 SSE）：

```javascript
// vite.config.js
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:3200',
      changeOrigin: true,
      timeout: 1800000,      // 30 分钟
    },
    '/uploads': {
      target: 'http://localhost:3200',
      changeOrigin: true,
    }
  }
}
```

### 路由白名单

以下路由无需登录即可访问：

- `/` — 首页
- `/messages` — 消息
- `/community` — 社区
- `/trips` — 行程
- `/profile` — 我的
- `/login` — 登录
- `/register` — 注册
- `/destinations` — 目的地列表

## 🗺️ 后续迭代规划

### 短期目标

- [x] 5 Tab SPA 架构 + 自定义底部导航
- [x] SSE 流式行程生成（7 阶段进度 + 逐天生成）
- [x] 多账户数据隔离
- [x] Markdown 渲染 + 语音输入
- [ ] 完善用户头像上传功能
- [ ] 优化图片加载体验（渐进式加载）
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
