# AI 智能旅游助手 - 前端

> 基于 Vue 3 + Vite 8 + Vant 4 的移动端 AI 旅游助手 H5 应用。iOS 风格磨砂玻璃主题，悬浮椭圆胶囊 Tab 栏，Apple Photos 透明玻璃效果，携程风格图片网格，抖音风格短视频播放器。

## 📱 项目简介

AI 智能旅游助手是一款面向移动端用户的智能旅游规划应用，采用 4 Tab SPA 架构（首页/社区/行程/我的），核心功能包括：

- **AI 智能对话**：SSE 流式对话，Markdown 渲染，多会话管理，语音输入
- **智能行程规划**：7 阶段进度推送 + 逐天行程生成，任务取消，地图可视化
- **山水大图 Banner**：首页/社区/我的三大 Banner 采用 Unsplash 高清自然风光背景 + 渐变遮罩 + 白色文字
- **社区游记**：携程风格图片网格（1-5+ 张智能排版），图片内联预览，视频内联播放，评论/回复（抖音风格）
- **短视频播放器**：抖音风格全屏沉浸式视频，滑动切换，原生 Fullscreen API，侧边操作栏
- **用户系统**：注册/登录，JWT 认证，多账户数据隔离，关注/粉丝，收藏，订单，优惠券

## 🎬 功能演示

📥 [**下载完整演示视频 (MP4, 7.8MB)**](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/full-demo.mp4)

> 下载后观看完整功能演示：山水 Banner · AI 智能对话 · 行程规划 · 携程风格图片网格 · 视频内联播放 · 评论区 · 收藏/订单 · 登录注册

## 📸 界面截图

| 首页 | AI 智能对话 |
|------|------------|
| ![首页](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%E9%A6%96%E9%A1%B5.jpg) | ![AI对话](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/ai%E5%AF%B9%E8%AF%9D.jpg) |

| AI 行程规划 | 社区游记 |
|------------|---------|
| ![AI规划](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/ai%E8%A7%84%E5%88%92%E5%A5%BD1.jpg) | ![社区](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%E7%A4%BE%E5%8C%BA.jpg) |

| 行程管理 | 个人中心 |
|---------|---------|
| ![行程](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%E8%A1%8C%E7%A8%8B.jpg) | ![我的](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%E6%88%91%E7%9A%84.jpg) |

| 登录 | 注册 |
|-----|------|
| ![登录](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%E7%99%BB%E5%BD%95.jpg) | ![注册](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%E6%B3%A8%E5%86%8C.jpg) |

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
| 图标 | 内联 SVG（不依赖图标库） | — |

## 🎨 UI 设计系统

### 磨砂玻璃主题（v3.0）

整个应用以 iOS 系统玻璃效果为设计灵感：

- **全局背景**：深色微妙渐变 `linear-gradient(175deg, #ede9f6 → #f8f7fd)`，让玻璃卡片更有层次感
- **玻璃公式**：`background: linear-gradient(160deg, 高光→衰减→反弹光) + 半透白底 + backdrop-filter: blur() saturate() + inset 0 1px 顶高光线`
- **Blur 梯度系统**：轻元素 10px → 卡片 14-16px → 导航栏 18-22px → Tab 栏/弹窗 25px+
- **悬浮椭圆胶囊 Tab 栏**：`48×48px`，`border-radius: 24px`，`blur(20px)`，仅底部圆角，左右悬浮不贴边
- **滑动指示器**：选中态白色椭圆块在 Tab 之间平滑平移（`transition: left 0.35s`）
- **非 Tab 页自动隐藏**：Tab 栏 `translateY(120%)` 向下滑出，带过渡动画
- **底部弹窗浮层化**：`width: calc(100vw - 20px)`，左右各 10px 留白，四角圆角

### CSS 变量体系

```css
--primary: #8B5CF6;           /* 品牌紫 */
--bg-glass: rgba(255,255,255,0.65);
--glass-blur: 18px;
--glass-saturate: saturate(160%);
--glass-border: rgba(255,255,255,0.45);
--shadow-purple: 0 8px 24px rgba(139,92,246,0.12);
```

## 📝 最近更新（v3.0 — 2026-07-29）

### 视觉系统重构
- **全站磨砂玻璃**：全局 Vant 组件（NavBar/Popup/Dialog）统一玻璃化 + iOS 透光反射
- **悬浮椭圆胶囊 Tab 栏**：4 Tab，SVG 实心图标，选中态滑动指示器，非 Tab 页自动隐藏动画
- **山水大图 Banner**：首页/社区/我的顶部 Banner 使用 Unsplash 高清自然风光 + 渐变遮罩 + 白色文字
- **所有抽屉浮层化**：底部弹窗左右留白 + 独立圆角，模拟浮层卡片

### 社区与图片系统
- **携程风格图片网格**：
  - 1 张：通栏 16:9
  - 2 张：并排 1:1
  - 3 张：左大（2/3 宽）+ 右二（各为大的一半）
  - 4 张：2×2
  - 5+ 张：上 2 大 + 下 3 小，第 5 张带 +N 蒙层
- **图片内联预览**：点击图片调用 `showImagePreview`，不跳转详情页
- **视频内联播放**：社区卡片内直接播放，`preload="metadata"` 显示第一帧

### 交互优化
- **统一滑动指示器**：登录/注册、收藏页、订单页的 Tab 切换全部使用白色滑动块
- **Tab 栏全局显隐管理**：MutationObserver 监听 Vant Overlay，弹出层打开时 Tab 栏自动向下滑出
- **路由过渡 GPU 加速**：`will-change: transform, opacity` + `backface-visibility: hidden`
- **底部间距全面重算**：所有页面底部内边距适配悬浮椭圆胶囊高度

### 性能优化
- GPU 加速：`will-change` + `backface-visibility` + 精确属性过渡
- 禁用无限动画和漂浮粒子
- `preload="metadata"` 视频第一帧预加载

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
# 手机端访问：npm run dev -- --host 0.0.0.0
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
│   └── images/landmarks/             # 地标图片
├── src/
│   ├── api/                          # API 接口封装
│   │   ├── index.js                  # 核心 API（16 个 API 对象）
│   │   ├── destination.js            # 目的地 API
│   │   └── tripNew.js                # SSE 流式行程生成 API
│   ├── components/                   # 公共组件
│   │   ├── AIChatDialog.vue          # AI 对话弹窗（可复用）
│   │   ├── DragSheet.vue             # 三阶段底部抽屉
│   │   ├── EmptyState.vue            # 空状态组件
│   │   ├── SearchBar.vue             # 地点搜索栏
│   │   ├── TripCompletedState.vue    # 已完成行程展示
│   │   ├── TripGeneratingState.vue   # 行程生成进度
│   │   └── VoiceInput.vue            # 语音输入
│   ├── router/                       # 路由配置
│   │   └── index.js                  # 4 Tab + 子路由 + 认证守卫
│   ├── utils/                        # 工具函数
│   │   ├── avatar.js                 # 本地 SVG 头像生成器
│   │   ├── auth.js                   # JWT Token 管理
│   │   ├── chatSession.js            # 多账户聊天会话管理
│   │   ├── security.js               # XSS 防护
│   │   ├── streamRequest.js          # SSE 流式请求
│   │   ├── tabBarHide.js             # Tab 栏全局显隐控制
│   │   └── userAccountStorage.js     # 多账户数据隔离
│   ├── views/                        # 页面组件
│   │   ├── HomeView.vue              # 首页（山水 Banner + AI 规划 + 信息流）
│   │   ├── CommunityView.vue         # 社区（山水 Banner + 图片网格 + 视频内联）
│   │   ├── TripsView.vue             # 行程（AI 规划 + 周边地图 + 行程列表）
│   │   ├── Profile.vue               # 我的（山水 Banner + 统计 + 服务列表）
│   │   ├── LoginView.vue             # 登录/注册（滑动指示器切换）
│   │   ├── VideoDetailView.vue       # 抖音风格短视频播放器
│   │   ├── NoteDetailView.vue        # 游记详情 + 评论系统
│   │   ├── AITripPlanner.vue         # AI 行程规划
│   │   └── ...                       # 其他子页面
│   ├── App.vue                       # 根组件（自定义 4 Tab 悬浮胶囊导航）
│   ├── main.js                       # 入口文件
│   └── style.css                     # 全局样式设计系统（CSS 变量 + 动画 + 玻璃主题）
├── vite.config.js                    # Vite 配置
├── postcss.config.js                 # PostCSS 配置
└── package.json                      # 依赖配置
```

## 🏗️ 应用架构

```
Tab 0: 首页 (/)            — 山水 Banner、AI 规划卡片、热门目的地、服务入口、游记信息流
Tab 1: 社区 (/community)    — 山水 Banner、图片/视频信息流、城市筛选、关注 Tab
Tab 2: 行程 (/trips)        — 轮播 Banner、周边地图、城市攻略、AI 规划入口
Tab 3: 我的 (/profile)      — 山水 Banner、统计、AI 对话记录、服务列表、订单/收藏

子路由以 slide-left 转场进入，Tab 栏自动隐藏（向下滑出动画）
```

## 🗺️ 后续迭代规划

### 短期目标
- [x] 4 Tab SPA 架构 + 悬浮椭圆胶囊导航
- [x] iOS 磨砂玻璃主题 + 透光反射效果
- [x] 山水大图 Banner（社区/我的同步）
- [x] 携程风格图片智能网格布局
- [x] 视频内联播放 + 图片内联预览
- [x] 统一滑动指示器（登录/收藏/订单）
- [x] 底部弹窗浮层化
- [x] GPU 性能优化
- [ ] 深色模式
- [ ] PWA 离线支持

### 中期目标
- [ ] RAG 知识库整合旅游攻略
- [ ] 酒店预订对接
- [ ] 行程日历视图
- [ ] 行程分享功能

### 长期目标
- [ ] 多语言国际化
- [ ] 接入第三方支付
- [ ] 离线地图功能

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！
