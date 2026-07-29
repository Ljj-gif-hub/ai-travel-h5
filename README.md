# AI 智能旅游助手

> 基于 Vue 3 + Spring Boot 3.2 的全栈 AI 旅游规划应用。iOS 风格磨砂玻璃主题，悬浮椭圆胶囊 Tab 栏，Apple Photos 风格透明模糊效果，支持多供应商 LLM、SSE 流式行程生成、社区游记、短视频播放。

## 📁 项目结构

```
├── trval-h5/          # 前端 — Vue 3 + Vite 8 + Vant 4 移动端 H5
├── travel-java/       # 后端 — Spring Boot 3.2 + Java 17 + JWT + Redis
└── README.md          # 本文件
```

## 🎬 功能演示

📥 [**下载完整演示视频 (MP4, 7.8MB)**](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/full-demo.mp4)

> 下载后观看完整功能演示：山水 Banner · AI 智能对话 · 行程规划 · 携程风格图片网格 · 视频内联播放 · 评论区 · 收藏/订单 · 登录注册

## 🔗 子项目文档

- 📱 [前端详细文档](trval-h5/README.md) — 技术栈、功能列表、UI 设计系统、项目结构
- 🖥️ [后端详细文档](travel-java/README.md) — API 列表、安全机制、AI 供应商配置、部署指南

## 🚀 快速启动

### 前端

```bash
cd trval-h5
npm install
npm run dev        # → http://localhost:5173
```

### 后端

```bash
cd travel-java
mvn spring-boot:run   # → http://localhost:3200
```

### 前置条件

- **前端**：Node.js >= 18，npm >= 9
- **后端**：JDK >= 17，Maven >= 3.8，Redis（可选，限流用）

## 📸 界面截图

| 首页 | AI 行程规划 |
|------|------------|
| ![首页](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e9%a6%96%e9%a1%b5.jpg) | ![AI规划](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/ai%e8%a7%84%e5%88%92%e5%a5%bd%e7%9a%84%e8%a1%8c%e7%a8%8b1.jpg) |

| 社区动态 | 视频详情 |
|---------|---------|
| ![社区](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e7%a4%be%e5%8c%ba.jpg) | ![视频详情](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%a7%86%e9%a2%91%e8%af%a6%e6%83%85.jpg) |

| 图片详情 & 评论 | 写游记 |
|---------------|-------|
| ![图片评论](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e5%9b%be%e7%89%87%e8%af%a6%e6%83%85%e8%af%84%e8%ae%ba.jpg) | ![写游记](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e5%86%99%e6%b8%b8%e8%ae%b0.jpg) |

| AI 智能对话 | 热门目的地 |
|------------|-----------|
| ![AI对话](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/ai%e5%af%b9%e8%af%9d.jpg) | ![热门目的地](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e7%83%ad%e9%97%a8%e7%9b%ae%e7%9a%84%e5%9c%b0.jpg) |

| 热门景点详情 | 选择出行偏好 |
|------------|------------|
| ![景点详情](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e7%83%ad%e9%97%a8%e6%99%af%e7%82%b9%e8%af%a6%e6%83%85.jpg) | ![出行偏好](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e9%80%89%e6%8b%a9%e5%87%ba%e8%a1%8c%e5%81%8f%e5%a5%bd.jpg) |

| 选择起点 | 规划日期 |
|---------|---------|
| ![选择起点](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e9%80%89%e6%8b%a9%e8%b5%b7%e7%82%b9.jpg) | ![日期选择](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%a7%84%e5%88%92%e6%97%a5%e6%9c%9f%e9%80%89%e6%8b%a9.jpg) |

| 行程管理 | 个人中心 |
|---------|---------|
| ![行程](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%a1%8c%e7%a8%8b.jpg) | ![我的](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e6%88%91%e7%9a%84.jpg) |

| 我的收藏 | 订单中心 |
|---------|---------|
| ![收藏](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e6%88%91%e7%9a%84%e6%94%b6%e8%97%8f.jpg) | ![订单](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%ae%a2%e5%8d%95%e4%b8%ad%e5%bf%83.jpg) |

| 更多产品服务 | AI 规划（旧版） |
|------------|---------------|
| ![更多产品](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e6%9b%b4%e5%a4%9a%e4%ba%a7%e5%93%81.jpg) | ![规划](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%a7%84%e5%88%92.jpg) |

| 视频评论区 | 登录 / 注册 |
|----------|-----------|
| ![视频评论](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%a7%86%e9%a2%91%e8%af%a6%e6%83%85%e7%95%8c%e9%9d%a2%e8%af%84%e8%ae%ba%e5%8c%ba.jpg) | ![登录](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e7%99%bb%e5%bd%95.jpg) |

## 🛠️ 技术栈概览

| 层 | 技术 |
|----|------|
| 前端框架 | Vue 3.5（Composition API） |
| 构建工具 | Vite 8 |
| UI 组件库 | Vant 4.10 |
| 后端框架 | Spring Boot 3.2.5 |
| 数据库 | H2 File Mode（持久化） |
| AI 服务 | DeepSeek / OpenAI / Claude / Gemini / Custom |
| 地图 | 百度地图 WebGL / Leaflet |
| 认证 | JWT (jjwt 0.12.5) + BCrypt |
| 限流 | Redis 滑动窗口 |

## 📝 最近更新（v3.0 — 2026-07-29）

### 前端重大更新
- **iOS 磨砂玻璃主题**：全站 backdrop-filter blur + saturate，模拟 iOS 系统玻璃透光反射
- **悬浮椭圆胶囊 Tab 栏**：4 Tab 椭圆悬浮设计，选中态滑动指示器平移动画，非 Tab 页自动隐藏动画
- **Apple Photos 风格透明模糊**：底部 Tab 栏透明磨砂 + 内容滚动穿透效果
- **山水大图 Banner**：首页/社区/我的三大 Banner 全宽高清自然风光背景图 + 渐变遮罩
- **所有抽屉/弹窗浮层化**：底部弹窗四周留白 + 圆角，不贴边，独立悬浮卡片
- **统一滑动指示器**：Tab 切换（登录页、收藏页、订单页）全部使用白色滑动块 + 磨砂玻璃底
- **图片网格智能布局**：1-5+ 张图片不同排版（携程风格），图片点击内联预览
- **视频内联播放**：社区卡片内直接播放视频，metadata 预加载第一帧
- **GPU 性能优化**：will-change + backface-visibility + 精确属性过渡

### 后端
- 评论回复系统（parentId 二级嵌套）
- 关注/粉丝数据隔离（JWT 提取 userId）

## 📄 许可证

MIT License
